# 2026-11-22 · 7-Zip writer for backups (SPEC:R22, SPEC:R112)

**Decision (owner, 2026-11-22):** MedicAll writes and reads its backups with Apache Commons Compress
1.28.0 (`org.apache.commons:commons-compress`, Apache-2.0, pure Java), on Android and Windows alike.
One class is a patched copy: `SevenZOutputFile`, which also seals the archive's header (the file
names) the way 7-Zip's "encrypt file names" (`-mhe=on`) does.

## 1. Why

The first trial chose 7-Zip-JBinding (23.01-2.2 on the desktop, 7-Zip-JBinding-4Android 16.02-2.03 on
Android). Both passed, but:
- **RAR code is built into both native libraries.** It is under the "unRAR restriction", an extra
  restriction that GPL-3.0 section 10 does not allow on a combined work.
- **The Android engine is old.** 7-Zip 16.02 predates published fixes such as CVE-2018-10115 and
  CVE-2023-31102, and a restore opens a file from a folder the person picked.
- **Separate native builds.** Each platform needs its own native library, and Android ships one
  for each processor type, about 2.8 MB for arm64 alone.

Commons Compress has none of these problems: no RAR code, Java memory safety, and one implementation
on both platforms. Since 1.23 it writes AES-256 content (7zAES) with a password. What it lacked was
sealing the header, so without the patch the file names stay readable without the password.

## 2. The patch

`SevenZOutputFile` (Apache-2.0, kept with its licence header) gains `writeEncryptedHeader`:
1. When a password is set, the plain header is sealed with AES under its own salt and IV, and
   written as a packed stream.
2. The archive then ends with a `kEncodedHeader` that names that stream, its AES coder, its plain
   size and its CRC.
3. The start header points at the encoded header.

The trial copy is in `_ref/commons-compress-trial/`, outside the repository. Task 12 brings it in
with tests.

## 3. Proof (desktop 7-Zip 25.01, invented content, passphrase `trial-Passphrase-42`)

| Check | Result |
|---|---|
| `7z l -slt -p… trial.7z` | both entries listed, `Method = LZMA2:23 7zAES:19`, `Encrypted = +` |
| `7z t -p… trial.7z` | `Everything is Ok`, `Files: 2` |
| `7z l trial.7z`, no password | `Enter password`, no names, `Cannot open encrypted archive. Wrong password?` |
| `7z l -pwrong trial.7z` | `Cannot open encrypted archive. Wrong password?` |
| Commons Compress `SevenZFile`, right password | both entries read back byte for byte |
| Commons Compress `SevenZFile`, wrong password | refused (`IOException`) |
| Commons Compress reading an archive made by `7z a -mhe=on -p…` | read back |

Unpatched Commons Compress 1.28.0, for comparison: content encrypted, but `7z l` without a
password lists both names.

## 4. Known limits, to handle in Task 12

- **Upstream reuses one IV.** Commons Compress uses one `AES256Options` (one salt and IV) for every
  entry in an archive. Nothing is decrypted by it, but it shows which entries start the same way.
  The patched copy uses a fresh IV per entry, tested.
- **The password is held in memory.** It is copied into a `char[]` inside the writer and cleared
  once the header is written.
- **The writer needs a seekable target:** a `File` or a `SeekableByteChannel`. It writes to a
  temporary file, and the caller copies the finished file to the backup folder (R22's
  write-then-replace).
- **Dependencies:** Commons Compress needs `org.tukaani:xz` for LZMA2, and commons-io and
  commons-lang3. Check their licences (xz: public domain/0BSD; commons-*: Apache-2.0) and the R8
  keep rules on Android.

## 5. The interface for Task 12

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

## 6. Ruled out

- **7-Zip-JBinding with a GPL-3.0 section 7 exception for the unRAR code:** it leaves the old
  Android engine in place.
- **A fork of 7-Zip-JBinding-4Android rebuilt without RAR on a newer engine:** two native builds
  to own for every engine update.
- **An AES ZIP holding one inner archive** (SPEC R112 Not): it takes two steps to open.
