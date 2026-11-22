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
1. When a password is set, the plain header is sealed with AES under the archive's key and its own
   IV, and written as a packed stream. There is no salt: upstream's `AES256Options(char[])` uses an
   empty one, so the header and every entry share one key, and only their IVs differ.
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

- **Upstream reuses one IV.** Commons Compress uses one `AES256Options` (one IV, and the empty salt)
  for every entry in an archive. Nothing is decrypted by it, but it shows which entries start the same way.
  The patched copy uses a fresh IV per entry, tested.
- **The password is held in memory.** It is copied into a `char[]` inside the writer and cleared
  once the header is written.
- **The writer needs a seekable target:** a `File` or a `SeekableByteChannel`. It writes to a
  temporary file, and the caller copies the finished file to the backup folder (R22's
  write-then-replace).
- **Dependencies:** Commons Compress needs `org.tukaani:xz` for LZMA2, and commons-io and
  commons-lang3. Check their licences (xz: public domain/0BSD; commons-*: Apache-2.0) and the R8
  keep rules on Android.

**As handled in Task 12 (2026-11-22):**

- **Fresh IV per entry:** done in `SealedSevenZOutputFile`. Each entry, and the header, gets the
  archive's one key with a random IV of its own. `SevenZipTest.everyEntryHasItsOwnIv` checks the
  first ciphertext blocks of two entries with the same bytes: equal under upstream's writer, the
  control, and different under the patched one.
- **One key derivation per archive (2026-11-23, review finding M4):** the Task 12 writer made one
  `AES256Options` per entry, and each derives the key again: 2^19 SHA-256 rounds, about 38 ms on the
  desktop, per entry. `AES256Options` is final and package-private, so the patched writer has its
  own small copy, `SealedAes256` (key bytes, IV; the salt empty and the rounds 2^19, as upstream
  writes), with upstream's property bytes and encoding stream. The key is derived once, in the
  writer's constructor. The reader, Commons Compress's own `SevenZFile`, still derives the key once
  per folder, and each entry is its own folder: a library limit, so every caller reads an archive in
  one pass (`readEach`), never one opening per entry, and the integrity check opens only the entries
  it wants. Timings below.
- **The patched class is renamed** `SealedSevenZOutputFile`, in the same package. Under its upstream
  name it would be a second `SevenZOutputFile` beside the library's own, which Android's dexer
  refuses as a duplicate class and the desktop resolves by classpath order.
- **The password:** the writer keeps no copy, only the derived key, which it clears when the archive
  is finished or closed, also when finishing fails (review finding L2). The UTF-16LE bytes it derives
  the key from are cleared at once. The reader's `char[]` copy is cleared once the archive is open.
  A library limit: Commons Compress keeps its own UTF-16LE copy of the password inside each open
  `SevenZFile`, and the expanded key inside each `Cipher`, and zeroes neither.
- **The seekable target:** `BackupWriter` writes to a temporary file, reads every entry back
  under the password, streamed, then hands the file to the backup folder, whose write (review
  finding H2) lands a temporary copy, forces it to the device, compares its streamed SHA-256, keeps
  the old file as `<name>.prev` until the new one is in place and compared again, and only then drops
  it. Nothing holds a whole archive in memory (review finding H1).
- **Dependencies and licences**, as resolved for `:data` (`./gradlew :data:dependencies`):

| Library | Version | Licence | Why |
|---|---|---|---|
| `org.apache.commons:commons-compress` | 1.28.0 | Apache-2.0 | the 7z reader and writer |
| `org.tukaani:xz` | 1.10 | 0BSD | LZMA2, added explicitly (optional in Commons Compress's POM) |
| `commons-io:commons-io` | 2.20.0 | Apache-2.0 | transitive |
| `org.apache.commons:commons-lang3` | 3.18.0 | Apache-2.0 | transitive |
| `commons-codec:commons-codec` | 1.19.0 | Apache-2.0 | transitive |

  Apache-2.0 and 0BSD both combine with GPL-3.0. The Apache libraries ship a `NOTICE` file, which the
  app's licence screen must reproduce when it is built.
- **R8:** not checked. The Android app does not minify yet (no `isMinifyEnabled`), so no keep rules
  were added. Commons Compress finds its coders through a static table, not by reflection, but it
  names optional libraries (Brotli, Zstandard, ASM, OSGi) that are absent: a minified build will need
  `-dontwarn` rules for them, and a check that a backup still writes and reads.
- **The reader's interface** gains `readEach`, every entry in one opening of the archive (section 5).
- **The entry cap is the reading device's (2026-11-23, review finding N2), a known limit.** The reader
  refuses an entry whose declared size passes a quarter of its own heap, before reading it: a restore
  or a repair holds one entry whole, and `Blobs.storeBytes` holds about 2 to 3 times an entry at once
  (the plain bytes, the sealed text and its UTF-8 bytes). So a file added on the desktop can be over a
  phone's cap. Such an entry is intact in the backup, and is not restored or repaired from on that
  device: `RestoreReport.tooLarge` names the entries, `IntegrityReport.tooLarge` the unrepaired hashes.
  No size limit is set on adding a file; that is an owner choice for later.
- **`blob_held` changed schema version 1 in place (2026-11-23, review finding N5).** Version 1 has not
  shipped, so no migration was written, but the schema's identity hash changed: any debug install made
  before commit `dedc343` must have its app data cleared, or Room refuses to open its database.
- **`.prev` keeps the backup that still held a file (2026-11-23, review findings N1, N3, N4).** An
  existing `.prev` is never overwritten: a write then moves the old file aside under a temporary
  name, puts it back if the new one fails its check, and deletes it once the new one is in place.
  `.prev` is deleted only after a write that misses no file, the bin's included. A failed check with
  no old file to put back deletes the bad new one.

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

Added in Task 12: `readEach(source, password, block: (name, bytes?) -> Unit): Boolean`, every entry in
one opening, a damaged entry read as null; `read` per entry opens the archive and derives the header's
key each time. Implemented once, in `data/src/jvmCommon/.../data/backup/SevenZip.kt`.

**As changed by the tasks 10-14 review (2026-11-23, findings H1, H2, M1, M2, M3, L3).** A backup of
scans and photos runs to hundreds of MB, so nothing takes or returns a whole archive or backup as a
`ByteArray` any more: archives are files, entries and backups are streams, and memory holds one
entry at a time.

```kotlin
// Archive.kt
class ArchiveEntry(val name: String, val size: Long, val open: () -> java.io.InputStream)   // unchanged

interface ArchiveWriter {
    /** Each entry streamed from [ArchiveEntry.open]. */
    fun write(target: java.io.File, password: CharArray, entries: List<ArchiveEntry>)        // unchanged
}

interface ArchiveReader {
    /** Null if it does not open with [password]; with a password, also null when it opens without one.
     *  Throws IOException only when the file itself cannot be read: that is not a wrong password. */
    fun list(source: java.io.File, password: CharArray?): List<String>?
    /** Every entry in one opening, each an ArchiveEntry to stream inside [block]: its size the header's,
     *  its stream throwing IOException when it does not read back intact, its open() throwing
     *  EntryTooLargeException when the declared size passes the cap. False as list's null. */
    fun readEach(source: java.io.File, password: CharArray, block: (ArchiveEntry) -> Unit): Boolean
}
class EntryTooLargeException(val name: String, val size: Long, val cap: Long) : java.io.IOException

// SevenZip.kt: `read(source, password, name)` is gone; tests keep it as a helper over readEach.
open class SevenZipArchives(maxEntryBytes: Long = Runtime.getRuntime().maxMemory() / 4,
                            maxMemoryKiB: Int = 64 * 1024) : ArchiveWriter, ArchiveReader
object SevenZip : SevenZipArchives()

// BackupTarget.kt
interface BackupTarget {
    /** Temporary copy, fsync, streamed SHA-256 compared, old copy kept as `<name>.prev` (an existing
     *  `.prev` is never overwritten), moved over [name], compared again; `.prev` dropped unless
     *  [keepPrevious], which BackupWriter sets while any file is missing. Old or new, never neither. */
    fun write(name: String, source: java.io.File, keepPrevious: Boolean = false)
    fun openRead(name: String): java.io.InputStream?
    fun list(): List<String>          // `.prev` copies included, temporary ones not
    fun reachable(): Boolean
    companion object { fun prev(name: String): String }
}
class DirectoryBackupTarget(root: java.nio.file.Path) : BackupTarget   // + an internal Disk hook for tests

// BackupWriter.kt: constructor unchanged; it runs IntegrityCheck first.
data class BackupResult.Written(val name: String, val files: Int,
                                val missing: List<String> = emptyList(), val keptPrevious: Boolean = false)
fun BackupWriter.Companion.newestFirst(names: List<String>): List<String>  // each backup then its `.prev`

// BackupRestore.kt: the archive is a file; workDir is gone; a wall clock for LineGuard's rule.
class BackupRestore internal constructor(writes: LocalWrites, blobs: Blobs,
    reader: ArchiveReader = SevenZip, wallMillis: () -> Long = System::currentTimeMillis)
suspend fun BackupRestore.restore(archive: java.io.File, password: CharArray): RestoreReport?
data class RestoreReport(val rows: Int, val files: Int, val skipped: Int, val ahead: Int = 0,
                         val tooLarge: List<String> = emptyList())   // N2: entry names over the cap

// IntegrityCheck.kt: constructor unchanged.
data class IntegrityReport(val checked: Int, val damaged: List<String>, val repaired: List<String>,
                           val unrepaired: List<String>, val notYetArrived: List<String> = emptyList(),
                           val tooLarge: List<String> = emptyList())   // N2: unrepaired, copy over the cap

// blob/: `Blobs.markHeld(names)` and `Blobs.heldOnce()` (internal), over a new local table `blob_held`.
```

The per-entry cap defaults to a quarter of the heap: a restore holds one entry's plain bytes and its
sealed copy at once, about 3.7 times its size. `SevenZFile`'s own memory limit is set with
`setMaxMemoryLimitKiB(65536)`: the builder's `setMaxMemoryLimitKb`, which the review named, divides
its argument by 1024.

**Timings, desktop (Windows 11, JDK 21), before and after the key-once writer:**

| Run | Before (Task 12) | After |
|---|---|---|
| `SevenZipInteropTest`: 30 files, backup written and read back | 2,686 ms (3,194 ms in a second run) | 1,487 ms (1,488) |
| `BackupWriterTest.threeHundredFilesBackUpInTime_M4`: 300 small files, written and read back | 23,216 ms | 12,424 ms (12,890) |
| `SevenZipTest.keyIsDerivedOncePerArchive_M4`: the writer alone, 300 entries | 11,377 ms | 2,109 ms (2,095) |

One key derivation takes 30 to 38 ms here (the tests measured 30 ms in the final run, so limits of
15,000 ms and 6,000 ms). What remains after the fix is the reader's derivation per
entry (301 for the 300-file read-back), and the writer's LZMA2 encoder, set up again for each entry.
The two tests set their limits from a derivation measured on the machine running them: the writer
alone under 100 derivations plus 3 s, the whole backup under 400 derivations plus 3 s; the Task 12
code is past both.

## 6. Ruled out

- **7-Zip-JBinding with a GPL-3.0 section 7 exception for the unRAR code:** it leaves the old
  Android engine in place.
- **A fork of 7-Zip-JBinding-4Android rebuilt without RAR on a newer engine:** two native builds
  to own for every engine update.
- **An AES ZIP holding one inner archive** (SPEC R112 Not): it takes two steps to open.
