# 2026-11-22 · 7-Zip library for backups (SPEC:R22, SPEC:R112)

**Decision:** MedicAll writes its backups with 7-Zip-JBinding, the same Java API on both platforms:

| Platform | Coordinates | 7-Zip engine | Repository |
|---|---|---|---|
| Desktop (Windows, `desktop` JVM target) | `net.sf.sevenzipjbinding:sevenzipjbinding:23.01-2.2` + `net.sf.sevenzipjbinding:sevenzipjbinding-windows-amd64:23.01-2.2` | 7-Zip 23.01 | Maven Central |
| Android | `com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03`, with `exclude(group = "androidx.appcompat")` | 7-Zip 16.02 (p7zip) | JitPack (`includeGroup("com.github.omicronapps")` only) |

The trial wrote a 7z archive with AES-256 (7zAES) and an encrypted header on both platforms; desktop
7-Zip 25.01 lists both entries with the passphrase and shows no names without it. **One licence
question is open for the owner (section 3, the unRAR code); the choice stands only once it is answered.**

## 1. Why these versions

- Maven Central holds 9.20-2.00beta, 16.02-2.01, 23.01-2.2 (published 2026-09-21) and 26.06-2.3
  (published 2026-11-18). All three recent versions passed the desktop trial. 23.01-2.2 is chosen:
  it carries the 7-Zip 23.01 fixes (CVE-2023-31102, CVE-2023-40481) and has been public for two
  months; 26.06-2.3 was four days old at the trial. Move to it in a later, separate bump.
- The desktop needs only the Windows build: `sevenzipjbinding-windows-amd64` is 1,984,043 bytes
  against 24,168,593 for `sevenzipjbinding-all-platforms` (which the trial used).
- Android has one maintained build: 7-Zip-JBinding-4Android by Fredrik Claesson (163 stars, last
  release 2025-09-28, not archived). Its engine is 7-Zip 16.02. The Maven Central Linux-arm64 jars
  are glibc builds and do not load on Android. No other maintained Android 7z writer with header
  encryption was found.
- Artifact hashes (SHA-256) as downloaded:
  - `sevenzipjbinding-23.01-2.2.jar` 2cb5368ef44372df91b6def8722f6e68357453160872a918f931762954a78d60
  - `sevenzipjbinding-all-platforms-23.01-2.2.jar` 2f60e6075a7bb2290970774fabcbd61cbeca9e9b0cdbbe36999d5438c69f536a
  - `7-Zip-JBinding-4Android-Release-16.02-2.03.aar` 88b2860ddb0fe8eac45a8fc59222cee1bee1c48b58f050f155b0b78074a147d9

## 2. The API that does it

- `SevenZip.openOutArchive7z()` returns `IOutCreateArchive7z`, which extends
  `IOutFeatureSetEncryptHeader` (`setHeaderEncryption(boolean)`), `IOutFeatureSetLevel`,
  `IOutFeatureSetSolid` and `IOutFeatureSetMultithreading` (checked with `javap` on 23.01-2.2).
- The password comes from the create callback: the class that implements
  `IOutCreateCallback<IOutItem7z>` also implements `ICryptoGetTextPassword`
  (`String cryptoGetTextPassword()`). Setting a password gives 7zAES, which is AES-256 with a
  SHA-256 key derivation; 7-Zip reports it as `7zAES:19` (2^19 rounds).
- Reading: `SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, IInStream, callback)`, with the open
  callback implementing `ICryptoGetTextPassword`; `IInArchive.extractSlow(index, out, password)`.
  With header encryption and no or a wrong password, `openInArchive` throws `SevenZipException`.

Trial code (deleted after this file): one Java class shared by both platforms that writes into a
`net.sf.sevenzipjbinding.util.ByteArrayStream`, level 5, header encryption on, two entries of
invented content: `records/results.csv` and `attachments/invented note.txt`, passphrase
`trial-Passphrase-42`.

## 3. Licence

| Part | Licence | Verdict for a GPL-3.0 app |
|---|---|---|
| 7-Zip-JBinding Java + JNI code (both builds) | LGPL-2.1 (POMs: "LGPL", lgpl-2.1; GitHub: LGPL-2.1) | Compatible: LGPL-2.1 §3 allows the code to be relicensed under GPL-2.0-or-later, so GPL-3.0. Keep it as a separate, replaceable library (it is: a jar and a `.so`/`.dll`) and ship its licence and source offer. |
| 7-Zip code inside the native library | GNU LGPL-2.1-or-later | Compatible, as above. |
| `CPP/7zip/Compress/Rar*`, `Archive/Rar/*`, `Crypto/Rar*` | GNU LGPL **+ unRAR restriction** | **Open question.** |
| 7-Zip 23.01 (desktop) also has BSD-3-clause and BSD-2-clause files | BSD | Compatible; reproduce the notices. |
| `androidx.appcompat` 1.7.1 (a transitive dependency of the Android AAR) | Apache-2.0 | Excluded; the AAR's classes do not reference `androidx` (checked by grep over `classes.jar`). |

**The RAR code is built in, on both platforms.** It is not optional at runtime:
- the Android port's `sevenzipjbinding/CMakeLists.txt` compiles `Archive/Rar/RarHandler.cpp`,
  `Rar5Handler.cpp`, `Compress/Rar1Decoder.cpp`…`Rar5Decoder.cpp`, `Crypto/Rar20Crypto.cpp`,
  `Rar5Aes.cpp`, `RarAes.cpp` (lines 186-265);
- the desktop `Windows-amd64/lib7-Zip-JBinding.dll` (23.01-2.2) contains `RarHandler` and
  `Rar5Handler` (5 hits each), and `ArchiveFormat` has `RAR` and `RAR5`;
- both POMs list a second licence, "unRAR restriction", http://www.7-zip.org/license.txt.

The restriction (7-Zip `License.txt`): the unRAR sources cannot be used to re-create the RAR
compression algorithm, and documentation must say the code may not be used to develop a
RAR-compatible archiver. MedicAll does neither, so the restriction itself is easy to follow. The
problem is that it is an extra restriction, which GPL-3.0 §10 does not allow on a combined work;
the FSF lists the unRAR licence as non-free, and Debian ships 7-Zip's RAR code only in
non-free. Two ways out, both the owner's call:

1. **Add a GPL-3.0 §7 additional permission** to MedicAll's licence: permission to link and
   distribute with 7-Zip-JBinding and 7-Zip under their own terms, including the unRAR
   restriction. Possible because the owner holds MedicAll's copyright; contributors' code then
   comes in under the same permission.
2. **Build the native library without RAR**: drop the RAR files from the CMake source list (Android:
   a fork of 7-Zip-JBinding-4Android built in CI; desktop: a custom build of 7-Zip-JBinding for
   Windows). MedicAll never reads RAR, so nothing is lost, but it means owning two native builds.

## 4. Commands and their output

All from Git Bash in the throwaway build `spikes/sevenzip/` (Gradle 9.7.1 wrapper, AGP 9.3.2, JDK 21).
7-Zip on the desktop: `7-Zip 25.01 (x64) : Copyright (c) 1999-2025 Igor Pavlov : 2025-08-03`.

### Desktop

`../../gradlew :desktop:run -Psjb=23.01-2.2 --args=".../trial.7z" > build.log 2>&1; echo "EXIT=$?"`

```
EXIT=0
7-Zip 23.01 / 7-Zip-JBinding 23.01-2.2
wrote .../trial.7z (350 bytes)
list(pw)      = [attachments/invented note.txt, records/results.csv]
list(null)    = null
list(wrong)   = null
read round trip equal = true
```

The same run with `-Psjb=16.02-2.01` (326 bytes) and `-Psjb=26.06-2.3` (350 bytes) gave the same
lists and `7z` results.

`"/c/Program Files/7-Zip/7z.exe" l -ptrial-Passphrase-42 trial.7z`

```
Method = LZMA2:12 7zAES
2026-11-22 22:38:31 .....           32           80  attachments\invented note.txt
2026-11-22 22:38:31 .....           40               records\results.csv
2026-11-22 22:38:31                 72           80  2 files
```

`7z l -slt -ptrial-Passphrase-42 trial.7z` (16.02-2.01 archive, same for the others): every entry
`Encrypted = +`, `Method = LZMA2:12 7zAES:19`. `7z t -p…`: `Everything is Ok`, `Files: 2`.
`7z x -p… -so trial.7z records/results.csv` printed the invented CSV.

`"/c/Program Files/7-Zip/7z.exe" l trial.7z < /dev/null`

```
Listing archive: trial.7z
Enter password (will not be echoed):
Break signaled
EXIT=255
```

No names are printed before the password prompt. `7z l -pwrong trial.7z`:
`ERROR: trial.7z : Cannot open encrypted archive. Wrong password?` (exit 2).
`grep -c -a -E "results|invented|records" trial.7z`: `0`.

### Android (OnePlus 6T, `<6T serial>`, Android 11, page size 4096)

Foreground check before installing: `mResumedActivity: ActivityRecord{… net.oneplus.launcher/.Launcher t1046}`.

```
adb -s <6T serial> install -r android-debug.apk                -> Success
adb -s <6T serial> install -r android-debug-androidTest.apk    -> Success
adb -s <6T serial> shell am instrument -w -r spike.sevenzip.trial.test/androidx.test.runner.AndroidJUnitRunner
  OK (1 test)
logcat -s SevenZipTrial:
  I SevenZipTrial: 7-Zip 16.02 / 7-Zip-JBinding 16.02-2.03
  I SevenZipTrial: wrote /storage/emulated/0/Android/data/spike.sevenzip.trial/files/trial-6t.7z (326 bytes)
  I SevenZipTrial: list(pw) = [attachments/invented note.txt, records/results.csv]
  I SevenZipTrial: round trip ok
adb -s <6T serial> pull /storage/emulated/0/Android/data/spike.sevenzip.trial/files/trial-6t.7z trial-6t.7z
  1 file pulled, 0 skipped. (326 bytes in 0.007s)
```

The test also asserted `list(archive, null) == null`, `list(archive, "wrong") == null`, and the
CSV read back byte-for-byte.

`"/c/Program Files/7-Zip/7z.exe" l -ptrial-Passphrase-42 trial-6t.7z`

```
Method = LZMA2:12 7zAES
2026-11-22 22:47:28 .....           32           80  attachments\invented note.txt
2026-11-22 22:47:28 .....           40               records\results.csv
2026-11-22 22:47:28                 72           80  2 files
```

`7z l -slt -p…`: both entries `Encrypted = +`, `Method = LZMA2:12 7zAES:19`. `7z t -p…`:
`Everything is Ok`, `Files: 2`. `7z l -pwrong`: `Cannot open encrypted archive. Wrong password?`.
`7z l trial-6t.7z < /dev/null`: `Enter password (will not be echoed):` with no names, exit 255.
`grep -c -a` for the names: `0`.

Afterwards both trial packages were uninstalled (`Success`, `Success`), their data folder is gone,
and the phone is on `net.oneplus.launcher/.Launcher`.

## 5. Cost

**APK** (release, R8 on, `-keep class net.sf.sevenzipjbinding.** { *; }`):

| Build | APK bytes | Added |
|---|---|---|
| Empty trial app, no library | 19,446 | — |
| Library as published, all four ABIs, appcompat pulled in | 10,688,279 | +10.67 MB |
| arm64-v8a only, appcompat excluded | 2,777,679 | **+2.76 MB** (about 1.06 MB gzip-compressed) |

Native library per ABI (stored uncompressed, `extractNativeLibs=false`): arm64-v8a 2,676,496;
armeabi-v7a 1,801,136; x86 2,633,496; x86_64 2,663,640 bytes. Java classes: about 78 KB of dex.
Ship arm64-v8a for phones, plus x86_64 only if an emulator build is wanted; armeabi-v7a only if
32-bit phones are to be supported (minSdk 30 makes that rare).

**16 KB pages:** ready. Every ABI's `lib7-Zip-JBinding.so` has `LOAD` segments aligned to `0x4000`
(`llvm-readelf -lW`, NDK 30.0.16248370), the port links with `-z max-page-size=16384` and
`-z common-page-size=16384`, and `zipalign -c -P 16 -v 4` gives `Verification successful` with each
`.so` `(OK)`. `NEEDED` is only `liblog.so libm.so libdl.so libc.so` (libc++ is static, so no
`libc++_shared.so` clash with other native libraries). Not yet run on a 16 KB device.

**Desktop:** `sevenzipjbinding-windows-amd64` adds 1.98 MB to the Windows package; the library
extracts its DLL to a temporary folder on first use (`SevenZip.initSevenZipFromPlatformJAR()`,
which can be given a folder of MedicAll's own).

## 6. The interface for Task 12

The plan's signatures need three changes to fit the library:

1. **The writer needs a seekable target.** `createArchive` takes an `IOutStream` (seek + write),
   because the 7z header is written last and its start is patched into the front. A plain
   `java.io.OutputStream` (for example a Storage Access Framework stream on Android) works only by
   building the whole archive in memory first, which a backup with attachments must not do. The
   writer takes a `java.io.File` (wrapped in `RandomAccessFileOutStream`) and the caller copies the
   finished file to the backup folder.
2. **Entries must be counted before writing.** `createArchive(out, numItems, callback)` needs the
   count up front and asks for items by index, so the entries are a `List`, not a `Sequence`. Each
   entry gives its size and opens its stream on demand, so attachments are never all in memory.
3. **The reader works on a file too** (`RandomAccessFileInStream`), for the same reason.

```kotlin
class ArchiveEntry(val name: String, val size: Long, val open: () -> java.io.InputStream)

interface ArchiveWriter {
    /** Writes a 7z archive, AES-256 with an encrypted header, to [target], replacing it. */
    fun write(target: java.io.File, password: CharArray, entries: List<ArchiveEntry>)
}

interface ArchiveReader {
    /** Entry names with '/' separators, or null if the archive does not open with [password]. */
    fun list(source: java.io.File, password: CharArray?): List<String>?
    /** The bytes of [name], or null if the archive does not open or has no such entry. */
    fun read(source: java.io.File, password: CharArray, name: String): ByteArray?
}
```

Notes for the implementation:
- 7-Zip-JBinding's `PropID.PATH` comes back with the OS separator (`records\results.csv` on
  Windows); normalise to `/` in `list` and before comparing in `read`. In the trial, matching the
  raw path against `records/results.csv` failed on Windows until normalised.
- The archive does not keep the entries' order (7-Zip sorts them for solid compression); callers
  and tests compare as sets.
- The password crosses the API as a `String` (`ICryptoGetTextPassword`), so it cannot be zeroed
  after use; the `CharArray` can be, and the `String` should live only inside the callback.
- The same Java API is on both platforms, so one Kotlin implementation can serve both: a shared
  JVM source set compiled against the desktop jar, with each platform supplying its own artifact.
- On Android, R8 needs the keep rule above: the AAR ships an empty `proguard.txt`, and the JNI code
  calls back into the Java classes by name. The trial's device run was a debug build; the minified
  release build was only measured, not run.
- Set the level explicitly (5 in the trial) and leave solid mode on.

## 7. Risks recorded

- **Old engine on Android.** 7-Zip 16.02 predates the fixes for CVE-2018-10115 (RAR),
  CVE-2023-31102 (PPMd in 7z) and others. MedicAll reads only its own backups, but a restore opens
  a file from a folder the person chose, so a tampered archive is the exposure. A restore that
  checks the 7z coder list before extracting (LZMA2 + 7zAES only) narrows it; building the Android
  library from a newer 7-Zip (option 2 in section 3 would do both) removes it.
- **Single maintainer for the Android build**, distributed through JitPack (built on demand, not
  signed). Pin the version and the hash above; vendor the AAR into the repo if JitPack disappears.
- **Engine versions differ** (16.02 on Android, 23.01 on Windows). The archive format is the same
  (7zAES + LZMA2) and both trial archives opened in 7-Zip 25.01, but the R112 acceptance test should
  open an Android-written backup on Windows and the reverse in every release.
