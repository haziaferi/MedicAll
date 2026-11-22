# MedicAll phase 1: data model, encryption, sync and backup, implementation plan

**Goal:** the foundations every later phase stands on. Records sit in field groups with stamps (R15). They sync through a Syncthing folder with every line and file encrypted (R16). The database is SQLCipher on Android (R10), unlocked by one passphrase (R109). Removed records go to a 30-day bin (R18, R19). Encrypted files travel by their SHA-256 and are checked (R23). Backups are AES-256 7-Zip archives that open without MedicAll and restore into the app (R22, R112). The phase gate: R16 converges and R22 restores, on two devices (R100).

**Approach:**
- Port Factotum's multiplatform sync (`Merger`, `FolderSync`, `StagedStore`, `BlobSync`, `openOrRecover`) and its tests into MedicAll's `:core` and `:data`.
- Add encryption around it: a key from the passphrase, an encrypted form for each line and each file, and SQLCipher on Android.
- Add the bin, backups, integrity checks, an Android folder that works through the Storage Access Framework, and a debug-only harness screen for the two-device walk.

**Stack:**
- Kotlin 2.4.10, Kotlin Multiplatform (Android plus a `desktop` JVM target used for tests).
- Room 2.8.4 with KSP 2.3.11, AGP 9.3.2, Gradle 9.7.1, JDK 21.
- compileSdk 37, minSdk 30, targetSdk 36.
- JUnit 4 and kotlinx-coroutines-test; SQLCipher for Android (`net.zetetic:sqlcipher-android`); a 7-Zip library chosen in Task 11.

**Spec:** `SPEC.md`. Rules built here: R10, R15, R16, R18, R19, R22, R23, R99 (CI part), R109, R112, and the R12 check that no network permission exists.

**Carry out with:** `dev-engineering-suite:plan-runner`. Tick each step as it is done.

## Applies to every task

- **Packages:** `com.hazia.medicall.core` (`:core`, pure Kotlin, no Android imports), `com.hazia.medicall.data` (`:data`), `com.hazia.medicall` (`:android`).
- **Citations:** code and tests that depend on a rule cite it as `SPEC:Rnn` in a comment or the test name, so `speccheck --base` can block a rule change that leaves code behind.
- **Ported code:** code taken from Factotum keeps its structure and its tests. The copy is recorded in `docs/ported-from.md` with the Factotum commit (`6652e09` or later, written down when copied), and the package is renamed. Factotum's code is the owner's own, so it relicenses under GPL-3.0. The test files copied from Factotum's `core/src/commonTest/.../sync/` and `data/src/desktopTest/.../sync/` keep their names.
- **Ids:** UUIDs from `kotlin.uuid.Uuid.random()` (R15), in place of Factotum's ULIDs. `Merger` takes ids as plain strings, so nothing else changes.
- **Test data:** neutral only ("Profile A", "Lab 1", invented bytes). Nothing from the owner's own files (R06).
- **No network:** no task adds the `INTERNET` permission. The medicine-list download arrives in phase 3 (R12).
- **Commits:**
  - Work on branch `phase-1`, with one commit per task. Every message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Open pull requests after Task 2 (skeleton and CI), then after Tasks 5, 9, 14 and 16. The owner merges each one (R07).
  - The pre-push hook runs the publish check. Never bypass it.
- **Running Gradle:**
  - Use `./gradlew` from Git Bash in the repository root.
  - Never pipe a Gradle run into `tail` or `head`. Write it to a log file and read the exit code: `./gradlew <tasks> > build.log 2>&1; echo "EXIT=$?"`.
  - Clear `build/test-results` before quoting test counts.
- **Device runs:** set `ANDROID_SERIAL` to the phone's serial. The connected suite runs only with invented data, and no reminder or alarm is ever set.

## Already failing before this plan

Nothing: the repository holds no code yet. Factotum's offline `./gradlew build` fails on an uncached atomicfu jar, so this plan's gate runs the per-target test tasks, never a bare `build`.

## Cases to watch

| Case | Behaviour | Test |
|---|---|---|
| Two devices first started offline, each creating its own key file | the file with the lower key id wins; the losing key is retired, not deleted, and its lines stay readable | `KeyFolderTest.racingFirstRunsSettleOnLowerKeyIdAndKeepBothKeys_SPEC_R16` and `foldAndChangeMadeApartLoseNoKey_SPEC_R16`, Task 5 |
| Wrong passphrase on a second device | refused with "Wrong passphrase"; nothing is written to the folder | `KeyFolderTest.wrongPassphraseIsRefused`, Task 5 |
| Passphrase changed while another device is offline | the offline device asks once for the new passphrase and still reads lines written under the old key | `KeyFolderTest.oldLinesReadAfterChange`, Task 5 |
| A line or file altered in the folder | the line is skipped and counted, and the file is reported as damaged; nothing crashes and nothing is applied | `EncryptedFolderSyncTest.tamperedLineIsSkipped` (Task 8), `BlobSyncTest.tamperedBlobIsNotStored` (Task 10) |
| A line stamped more than a day ahead | set aside and counted, then applied once the clock catches up | `EncryptedFolderSyncTest.futureLineWaitsUntilPlausible`, Task 8 |
| A crash between writing the temporary file and replacing the target | the next write finishes or clears it; the folder never shows a half file | `SafSyncFolderTest.interruptedReplaceLeavesOldOrNew`, Task 15 |
| Backup folder unreachable or the disk full during a backup | the previous backup is untouched; the failure is reported, never thrown at the user | `BackupWriterTest.failedWriteKeepsPreviousBackup`, Task 12 |
| An old backup restored over newer data | the newer field groups stay, because restore merges with stamps | `BackupRestoreTest.oldBackupNeverOverwritesNewer`, Task 13 |
| A purge arriving at a device that edited the record afterwards | the edit wins and the record survives (Factotum's ADR 01 rule) | ported `SyncCasesTest`, Task 3 |
| Day 29 and day 31 in the bin | restorable on day 29; purged on day 31, and the purge travels to peers | `BinTest`, Task 9 |

## Questions for you

1. **How often is the automatic backup rewritten?** Planned as after changes, at most once an hour, plus at each app start (provisional, Task 12). Once a day instead would mean fewer writes, but up to a day of changes not yet backed up.
2. **How many weekly dated backups are kept?** Planned as all of them, with none deleted (provisional, Task 12). A limit such as the last 12 would add a pruning step, and the folder would stop growing without end.
3. **What is the shortest passphrase allowed?** Planned as 12 characters (provisional, Task 5). A shorter minimum is easier to type, but weaker against someone who copies the folder.

---

### Task 1: Build skeleton

**Files:**
- create `settings.gradle.kts`, `build.gradle.kts` and `gradle.properties`;
- create `gradle/libs.versions.toml` and `gradle/wrapper/*`, plus `gradlew` and `gradlew.bat`;
- create `core/build.gradle.kts`, `data/build.gradle.kts` and `android/build.gradle.kts`, and `android/src/main/AndroidManifest.xml`;
- tests in `core/src/commonTest/kotlin/com/hazia/medicall/core/SmokeTest.kt` and `android/src/test/kotlin/com/hazia/medicall/ManifestTest.kt`.

**Depends on:** none

**Provides:**
- **Modules:**
  - `:core`: multiplatform, with targets `android` and `jvm("desktop")`;
  - `:data`: multiplatform, with the same targets, Room and KSP;
  - `:android`: the application, with `applicationId = "com.hazia.medicall"` and `minSdk = 30`.
- **Versions:** taken from Factotum's `gradle/libs.versions.toml`, plus `sqlcipher-android`.

- [ ] Copy Factotum's wrapper (Gradle 9.7.1) and the shape of its version catalogue and module builds. Rename the project to `medicall` and the namespaces to `com.hazia.medicall.*`. Leave out Factotum's `:windows` module.
- [ ] Write `SmokeTest.coreLoads`: `assertEquals("medicall", CoreInfo.NAME)`. Write `ManifestTest.noInternetPermission_SPEC_R12`: under Robolectric, the merged manifest's requested permissions do not contain `android.permission.INTERNET`.
- [ ] Run `./gradlew :core:desktopTest > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails on `CoreInfo`.
- [ ] Add `object CoreInfo { const val NAME = "medicall" }` in `:core`.
- [ ] Run `./gradlew :core:desktopTest :android:testDebugUnitTest :android:assembleDebug > build.log 2>&1; echo "EXIT=$?"`. Expected: `EXIT=0`, with the two tests passing.
- [ ] Commit: "Add the Kotlin Multiplatform build: core, data and the Android app".

### Task 2: CI gate and CodeQL

**Files:** create `.github/workflows/gate.yml`, `.github/workflows/codeql.yml` and `tools/speccheck.py` (copied from the project-spec-writer skill's `scripts/speccheck.py`, with its source named in a header comment).

**Depends on:** Task 1

**Provides:**
- **`gate.yml`, on pull requests and pushes to `main`:** JDK 21, then `./gradlew :core:desktopTest :data:desktopTest :android:testDebugUnitTest`, then `python tools/speccheck.py SPEC.md`. The job is named `Gate`.
- **`codeql.yml`:** CodeQL for `java-kotlin`, with build mode `manual` running `./gradlew :android:assembleDebug`. It satisfies the repository ruleset's code-scanning rule.

- [ ] Write both workflows. `speccheck` exits 1 on an ASK, so the step fails on exit 2 only (BLOCK) and prints the ASK findings for the reviewer.
- [ ] Push branch `phase-1` and open the first pull request (Tasks 1–2). Expected: the `Gate` and CodeQL checks both finish green on it. Paste the run URLs into the pull request.
- [ ] Commit: "Add the CI gate and CodeQL workflows".

### Task 3: Port the sync core

**Files:** create `core/src/commonMain/kotlin/com/hazia/medicall/core/sync/{Stamp,Row,Merge}.kt`; tests `core/src/commonTest/kotlin/com/hazia/medicall/core/sync/{ClockAndIdTest,Device,KeepLoserMergeTest,ResolutionTest,SyncCasesTest}.kt` (ported) and `docs/ported-from.md`.

**Depends on:** Task 1

**Provides:**
- `data class Stamp(val hlc: Long, val device: String) : Comparable<Stamp>`.
- `class HybridClock(device: String, wallMillis: () -> Long, last: Long = 0)`, with `tick(): Stamp` and `observe(stamp: Stamp)`.
- `data class Group(val stamp: Stamp, val values: Map<String, Any?>, val settles: Stamp? = null)`.
- `data class Row(val table: String, val id: String, val groups: Map<String, Group>)`, with `edit()` and `set()`.
- `interface SyncStore`.
- `class Merger(clock: HybridClock, askGroups: Set<String>, keepLoserGroups: Set<String> = emptySet())`, with `created`, `added`, `purge` and `import`. All exactly as in Factotum.
- `object Ids { fun new(): String }`, returning `Uuid.random().toString()`.

- [ ] Copy the five Factotum test files and rename their packages. Replace the ULID assertions in `ClockAndIdTest` with `Ids.new()`: a 36-character UUID string, and 1,000 calls give 1,000 distinct ids (`idsAreDistinct_SPEC_R15`).
- [ ] Run `./gradlew :core:desktopTest > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails on `Merger` and `Ids`.
- [ ] Copy `Stamp.kt`, `Row.kt` and `Merge.kt` and rename the package. Add `Ids`.
- [ ] Run the same command. Expected: `EXIT=0`, with every ported test passing. Record the test count in `docs/ported-from.md`.
- [ ] Commit: "Port Factotum's field-group merge into core (SPEC:R16)".

### Task 4: Key derivation and line cipher

**Files:** create `core/src/commonMain/kotlin/com/hazia/medicall/core/crypto/{KeyDerivation,LineCipher}.kt`, with `expect` declarations; `actual` implementations in `core/src/jvmShared/...` (a source set shared by `android` and `desktop`, both of which have `javax.crypto`); test `core/src/desktopTest/kotlin/com/hazia/medicall/core/crypto/CryptoTest.kt`.

**Depends on:** Task 1

**Provides:**
- `object KeyDerivation { const val ITERATIONS = 600_000; fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray }`: PBKDF2-HMAC-SHA256, 32 bytes.
- `class LineCipher(keys: Map<String, ByteArray>, currentKeyId: String)`, with:
  - `fun seal(plain: ByteArray): String`: returns `"<keyId>.<base64url(nonce12 + ciphertext + tag16)>"`, AES-256-GCM, a fresh random nonce each time, no newline;
  - `fun open(line: String): ByteArray?`: null when the key id is unknown or the tag fails, and never throws on bad input.

- [ ] Write the tests:
  - **`pbkdf2MatchesRfc7914Vector`:** `derive("passwd", "salt", iterations = 1)`, taken to 64 bytes through an internal `deriveBytes(…, length = 64)`, equals the RFC 7914 section 11 vector, which starts `55ac046e56e3089fec1691c22544b605`. Copy the full 64-byte hex from the RFC.
  - **`sealedLineRoundTrips`:** seal then open returns the same bytes.
  - **`sameInputSealsDifferently`:** two seals of one input differ.
  - **`tamperedLineOpensToNull`:** flip one character and `open` returns null.
  - **`unknownKeyIdOpensToNull`.**
  - **`sealedLineHasNoNewline`.**
- [ ] Run `./gradlew :core:desktopTest --tests '*CryptoTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement both, using `SecretKeyFactory("PBKDF2WithHmacSHA256")`, `Cipher("AES/GCM/NoPadding")` and `SecureRandom`.
- [ ] Run the same command. Expected: 6 tests pass.
- [ ] Commit: "Add passphrase key derivation and an encrypted line format (SPEC:R16, SPEC:R109)".

### Task 5: The key file in the sync folder, and the passphrase

**Files:** create `core/src/commonMain/kotlin/com/hazia/medicall/core/crypto/KeyFolder.kt`; test `core/src/desktopTest/kotlin/com/hazia/medicall/core/crypto/KeyFolderTest.kt`.

**Depends on:** Task 4

**Provides:**
- **`keys/keyinfo.json` in the sync folder, readable by every device:** the key id, the salt, the iteration count, a check value (HMAC-SHA256 of the key over `"medicall-key-check"`), and the older keys, each sealed with the current key.
- **`interface KeyFiles { fun read(path: String): ByteArray?; fun replace(path: String, bytes: ByteArray); fun list(dir: String): List<String>; fun delete(path: String) }`:** the subset of `SyncFolder` this needs. `SyncFolder` arrives in Task 8 and implements it.
- **`class KeyFolder(files: KeyFiles)`** (revised after the Tasks 3–5 review, so that no key is ever lost):
  - `fun create(passphrase: CharArray): LineCipher`;
  - `fun unlock(passphrase: CharArray): Unlock`, one of `Unlocked(cipher)`, `WrongPassphrase`, `NoKeyFile` and `Damaged`. The file is validated first (version, ids, salt and check sizes, 100,000 to 10,000,000 rounds), and it never throws on a bad file;
  - `fun change(old: CharArray, new: CharArray): LineCipher`: settles conflict copies first, then writes a new key id and salt with every known key sealed into the chain;
  - `fun settleConflicts(): Boolean`: the lowest key id stays the folder's key, with the union of every same-id copy's chain. Every other key file moves to `keys/retired/`, never deleted, and a copy that can't be read moves to `keys/unreadable/`;
  - `fun fold(passphrase: CharArray, retiredPassphrase: CharArray): Boolean` and `fun unfoldedKeyIds(passphrase: CharArray): List<String>`: a retired key made with another passphrase joins the chain when that passphrase is given.
- **`const val MIN_PASSPHRASE = 12`:** provisional, see Questions for you, item 3. `create` and `change` reject anything shorter with `IllegalArgumentException("Passphrase too short")`.

- [ ] Write the tests: `secondDeviceUnlocksWithSamePassphrase_SPEC_R109`, `wrongPassphraseIsRefused`, `oldLinesReadAfterChange`, `shortPassphraseRejected`, `racingFirstRunsSettleOnLowerKeyId`. All use an in-memory `KeyFiles`.
- [ ] Run `./gradlew :core:desktopTest --tests '*KeyFolderTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement `KeyFolder`. Use `ITERATIONS` in production. Tests may pass a lower count through an internal constructor parameter, to stay fast.
- [ ] Run the same command. Expected: 5 tests pass.
- [ ] Commit, then open the pull request for Tasks 3–5: "One passphrase for every device, with a key chain across changes (SPEC:R109)".

### Task 6: Database version 1 and safe opening

**Files:** create `data/src/commonMain/kotlin/com/hazia/medicall/data/{MedicAllDatabase,DatabaseRecovery}.kt` and `data/src/commonMain/kotlin/com/hazia/medicall/data/sync/SyncDao.kt` (ported), plus `data/schemas/` (Room's exported schema); test `data/src/desktopTest/kotlin/com/hazia/medicall/data/DatabaseTest.kt`.

**Depends on:** Task 3

**Provides:**
- **`@Database(version = 1) abstract class MedicAllDatabase : RoomDatabase`**, with:
  - Factotum's sync tables: purge, clock, base, ask, read, outbox, known tables, waiting, lost;
  - `profile` (`id` text primary key, `name` text, `birth_date` text or null, `kind` text, `removed_at` integer or null, plus a stamp per group);
  - `blob_ref` (`id`, `profile_id` with a foreign key to `profile`, `sha256`, `size`, `name`, `mime`, `removed_at`).
- **Field groups:**
  - `profile`: `name` (name), `details` (birth date, kind) and `removed` (removed at), so a rename and a change of kind made on two devices are different groups and both survive;
  - `blob_ref`: `main` and `removed`.
- **`fun <T> openOrRecover(...)`:** ported unchanged, with `expect fun isCorruptDatabase(failure: Throwable): Boolean`.
- **`PRAGMA foreign_keys = ON`** is set on open.

- [ ] Write the tests:
  - **`blobRefWithoutProfileIsRefused_SPEC_R15`:** the insert throws a constraint error.
  - **`unopenableFileIsSetAsideNotDeleted`:** ported from Factotum.
  - **`schemaFileMatchesVersion`:** `data/schemas/.../1.json` exists, and its version is 1.
- [ ] Run `./gradlew :data:desktopTest --tests '*DatabaseTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Add the entities and DAOs, and port `SyncDao` and `DatabaseRecovery`. The desktop builder uses `BundledSQLiteDriver` with an in-memory or temporary file.
- [ ] Run the same command. Expected: 3 tests pass.
- [ ] Commit: "Add database version 1: profiles, file references and the sync tables (SPEC:R15)".

### Task 7: SQLCipher on Android, with the key kept in the key store

**Files:** create `data/src/androidMain/kotlin/com/hazia/medicall/data/AndroidDatabase.kt` and `android/src/main/kotlin/com/hazia/medicall/KeyVault.kt`; test `android/src/androidTest/kotlin/com/hazia/medicall/EncryptedDatabaseTest.kt`.

**Depends on:** Task 5, Task 6

**Provides:**
- **`fun openAndroidDatabase(context: Context, key: ByteArray): DatabaseOpen<MedicAllDatabase>`:** Room's Android builder with `openHelperFactory(SupportOpenHelperFactory(key))` from `sqlcipher-android`, wrapped in `openOrRecover`. The corruption callback keeps the file and never deletes it (`KeepFileOnCorruption`, as in Emivita).
- **`class KeyVault(context: Context)`:** `fun store(key: ByteArray)` and `fun load(): ByteArray?`. The derived key is sealed with an AES-GCM key kept in `AndroidKeyStore`, and the sealed bytes live in app-private storage, outside Auto Backup.

- [ ] Write the instrumented tests:
  - **`fileIsNotPlainSqlite_SPEC_R10`:** the database file's first 16 bytes are not `"SQLite format 3\u0000"`.
  - **`wrongKeyCannotRead`:** opening with another key fails on the first query.
  - **`keySurvivesRestart`:** `store`, then a new `KeyVault`, then `load` returns the same bytes.
  - **`corruptFileIsKept`:** write junk over the file, open, and the junk file still exists beside the new database with the `.unopenable-` suffix.
- [ ] Run `ANDROID_SERIAL=<6T serial> ./gradlew :android:connectedDebugAndroidTest --tests '*EncryptedDatabaseTest*' > build.log 2>&1; echo "EXIT=$?"` (the `--tests` filter is given through `-Pandroid.testInstrumentationRunnerArguments.class=com.hazia.medicall.EncryptedDatabaseTest`). Expected: the compile fails.
- [ ] Implement both. Add `sqlcipher-android` to the catalogue. Exclude the vault file and the database from Auto Backup in `data_extraction_rules.xml`.
- [ ] Run the same command. Expected: 4 tests pass on the 6T.
- [ ] Commit: "Encrypt the Android database with SQLCipher and keep its key in the key store (SPEC:R10)".

### Task 8: Folder sync with encrypted lines

**Files:**
- create `data/src/commonMain/kotlin/com/hazia/medicall/data/sync/{SyncFolder,FolderSync,StagedStore,Records,RowTable}.kt` (ported) and `data/src/commonMain/kotlin/com/hazia/medicall/data/sync/LineGuard.kt`;
- tests `data/src/desktopTest/kotlin/com/hazia/medicall/data/sync/{FolderSyncTest,DirectorySyncFolderTest,SyncthingDouble,World}.kt` (ported) and `EncryptedFolderSyncTest.kt`.

**Depends on:** Task 3, Task 4, Task 6

**Provides:**
- **`interface SyncFolder : KeyFiles`:** as in Factotum (`list`, `size`, `read`, `replace`, `delete`), with `class DirectorySyncFolder(root: java.nio.file.Path)` for the desktop.
- **`internal class FolderSync(...)`:** Factotum's constructor plus `cipher: LineCipher`. `RecordCodec` lines pass through `cipher.seal` on export and `cipher.open` on import. A line that opens to null is skipped and counted in `ImportReport.skipped`.
- **`object LineGuard { const val FUTURE_LIMIT_MILLIS = 86_400_000L }`:** on import, a record whose newest stamp is more than a day past this device's wall clock is set aside, not applied, and is counted in `ImportReport.waiting`, a new field. Because its byte position is not advanced, it is read again on the next import.

- [ ] Copy Factotum's four test files, renamed, and give each `World` device a `LineCipher` made from one shared test key. Write `EncryptedFolderSyncTest`:
  - **`twoDevicesConverge_SPEC_R16`:** offline, device A renames a profile (group `name`) and device B changes its kind (group `details`). They import in both orders, and both end with both edits.
  - **`laterEditWins_SPEC_R16`:** the same group is edited on both, and the later stamp wins on both.
  - **`folderHoldsNoPlaintext`:** after export, no file under `devices/` contains the profile's name bytes.
  - **`tamperedLineIsSkipped`.**
  - **`futureLineWaitsUntilPlausible`:** the fake clock is 2 days behind and the line waits; advance the clock 2 days and the line is applied.
  - **`conflictCopyIsMergedThenDeleted`:** ported.
- [ ] Run `./gradlew :data:desktopTest --tests '*FolderSync*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Port the five files, then add the cipher and `LineGuard`.
- [ ] Run `./gradlew :data:desktopTest > build.log 2>&1; echo "EXIT=$?"`. Expected: `EXIT=0`, every ported test passes, and the 6 new tests pass.
- [ ] Commit: "Port folder sync and encrypt every line it writes (SPEC:R16)".

### Task 9: Bin, purge and the typed DELETE

**Files:** create `data/src/commonMain/kotlin/com/hazia/medicall/data/bin/Bin.kt`; test `data/src/desktopTest/kotlin/com/hazia/medicall/data/bin/BinTest.kt`.

**Depends on:** Task 8

**Provides:**
- **`class Bin(db: MedicAllDatabase, merger: Merger, clock: HybridClock, wallMillis: () -> Long)`**, with:
  - `suspend fun remove(table: String, id: String)`: sets the `removed` group;
  - `suspend fun restore(table: String, id: String)`: clears it;
  - `suspend fun contents(): List<BinEntry>`;
  - `suspend fun purgeExpired()`: for rows removed more than `RETENTION_DAYS = 30` days ago, calls `Merger.purge`;
  - `suspend fun empty(confirmation: String)`: purges everything in the bin, and throws `IllegalArgumentException` unless `confirmation == "DELETE"`.
- **`data class BinEntry(val table: String, val id: String, val removedAt: Long)`.**
- **Profiles:** removing a profile also removes its `blob_ref` rows. Restoring the profile restores the ones removed at the same moment.

- [ ] Write the tests:
  - **`removedRowRestoresOnDay29_SPEC_R18`.**
  - **`removedRowIsPurgedOnDay31_SPEC_R18`:** after `purgeExpired`, the row is gone and the purge is in the purge registry.
  - **`purgeTravelsToPeer`:** using Task 8's `World`.
  - **`emptyNeedsTypedDelete_SPEC_R19`:** `empty("delete")` throws, and `empty("DELETE")` purges.
  - **`removingProfileTakesItsFiles`.**
- [ ] Run `./gradlew :data:desktopTest --tests '*BinTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement `Bin`.
- [ ] Run the same command. Expected: 5 tests pass.
- [ ] Commit, then open the pull request for Tasks 6–9: "Add the 30-day bin and the typed DELETE for emptying it (SPEC:R18, SPEC:R19)".

### Task 10: Encrypted files by SHA-256

**Files:** create `data/src/commonMain/kotlin/com/hazia/medicall/data/blob/{BlobStore,BlobSync,Blobs}.kt` (`BlobSync` ported from Factotum's `image/Images.kt`); test `data/src/desktopTest/kotlin/com/hazia/medicall/data/blob/BlobSyncTest.kt`.

**Depends on:** Task 4, Task 8

**Provides:**
- **`interface BlobStore`:** `read`, `write`, `delete` and `list`, as in Factotum, plus `class DirectoryBlobStore(root: Path)`.
- **`class Blobs(db, store: BlobStore, cipher: LineCipher)`**, with:
  - `suspend fun add(profileId: String, name: String, mime: String, bytes: ByteArray): String`: computes the SHA-256 of the plain bytes, stores `cipher`-sealed bytes under that hash, writes a `blob_ref` row and returns its id;
  - `suspend fun read(id: String): ByteArray?`: null when the stored bytes fail to open or their hash does not match.
- **`internal class BlobSync(db, folder: SyncFolder, store: BlobStore, wallMillis)`:** `suspend fun exchange()` moves sealed bytes through `blobs/<sha256>` in the folder. Conflict copies are deleted, and a file whose opened bytes do not match its name is never stored.
- **Encryption for whole files:** `LineCipher.seal` works on bytes, and its output is written as UTF-8 text.

- [ ] Write the tests:
  - **`addedFileReachesPeer_SPEC_R16`.**
  - **`storedBytesAreNotPlain_SPEC_R10`:** the stored file does not contain the plain bytes.
  - **`tamperedBlobIsNotStored`.**
  - **`sameFileTwiceIsStoredOnce`.**
- [ ] Run `./gradlew :data:desktopTest --tests '*BlobSyncTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement it, porting `BlobSync` and keeping its 30-day collection of folder copies.
- [ ] Run the same command. Expected: 4 tests pass.
- [ ] Commit: "Store and sync encrypted files by their SHA-256 (SPEC:R16, SPEC:R23)".

### Task 11: Trial of a 7-Zip library (a decision gate)

**Files:** create `docs/decisions/2026-11-22-7zip-library.md`; a throwaway trial in `spikes/sevenzip/`, which is deleted once the decision is written.

**Depends on:** Task 1

**Provides:** the chosen library's coordinates and licence, recorded in the decision file, and its interface for Task 12.
- `interface ArchiveWriter { fun write(target: java.io.OutputStream, password: CharArray, entries: Sequence<Pair<String, ByteArray>>) }`
- `interface ArchiveReader { fun list(source: ByteArray, password: CharArray?): List<String>?; fun read(source: ByteArray, password: CharArray, name: String): ByteArray? }`

- [ ] Try 7-Zip-JBinding (its desktop build) and an Android build of it, such as 7-Zip-JBinding-4Android. Write a 7z archive with AES-256 and header encryption, holding two entries.
- [ ] Prove it on the desktop: `"/c/Program Files/7-Zip/7z.exe" l -p<pw> trial.7z` lists both entries; `7z l trial.7z` without the password shows no names and asks for one.
- [ ] Prove it on the 6T: an instrumented trial writes the archive, `adb pull` brings it over, and the same two commands give the same results.
- [ ] Check the licence. LGPL is compatible with linking from GPL-3.0. Record its unRAR restriction, if any, and confirm the RAR code is not built in.
- [ ] If either platform fails, stop and ask the owner. The fallback is R112's ruled-out alternative, an AES ZIP holding one inner archive, and only the owner can bring it back.
- [ ] Commit the decision file: "Choose the 7-Zip library for backups (SPEC:R112)".

### Task 12: Backup writer

**Files:** create `data/src/commonMain/kotlin/com/hazia/medicall/data/backup/{BackupWriter,BackupContent,BackupSchedule}.kt` and `data/src/{androidMain,desktopMain}/.../backup/SevenZip.kt` (the Task 11 library behind `ArchiveWriter` and `ArchiveReader`); test `data/src/desktopTest/kotlin/com/hazia/medicall/data/backup/BackupWriterTest.kt`.

**Depends on:** Task 6, Task 10, Task 11

**Provides:**
- **`class BackupWriter(db, blobs: Blobs, writer: ArchiveWriter, target: BackupTarget)`**, with `suspend fun write(kind: BackupKind, password: CharArray): BackupResult`:
  - `enum class BackupKind { LATEST, WEEKLY }`;
  - `sealed interface BackupResult { data class Written(val name: String, val files: Int); data class Failed(val reason: String) }`.
- **File names:** `MedicAll-backup-latest.7z` and `MedicAll-backup-YYYY-MM-DD.7z`. Each is written to a temporary name first and replaces its target only after it is read back whole.
- **`interface BackupTarget`:** `write(name: String, bytes: ByteArray)`, `read(name)`, `list()` and `reachable(): Boolean`.
- **Archive entries, from `BackupContent`:**

| Entry | Content |
|---|---|
| `HOW-TO-OPEN.txt` | the plain English text below |
| `records/records.json` | every synced row with its groups and stamps, as `RecordCodec` lines |
| `records/<table>.csv` | one CSV per table, with a header row, values only |
| `files/<profile name>/<YYYY-MM-DD> <file name>` | each file's plain bytes; a clashing name gets " (2)" |

- **The `HOW-TO-OPEN.txt` text:** "This is a MedicAll backup. Open it with 7-Zip (7-zip.org) or another program that reads 7z archives, using your MedicAll passphrase. The files folder holds your attachments under readable names; the records folder holds your records as CSV and JSON."
- **`class BackupSchedule`:** `fun due(now, lastLatest, lastWeekly, changedSince): Set<BackupKind>`. LATEST is due when something changed and at least an hour has passed, and at each app start. WEEKLY is due 7 days after the last weekly backup. All weekly files are kept. Both rules are provisional: see Questions for you, items 1 and 2.

- [ ] Write the tests:
  - **`archiveHoldsEveryRecordAndFile_SPEC_R22`:** read the archive back with `ArchiveReader` and the password; each table's CSV has one line per row plus the header.
  - **`namesHiddenWithoutPassword_SPEC_R22`:** `ArchiveReader.list(bytes, null)` returns null.
  - **`failedWriteKeepsPreviousBackup`:** the target throws mid-write, and the previous backup is unchanged.
  - **`scheduleRules`:** one hour after a change, LATEST is due; 6 days after a weekly, WEEKLY is not due; 7 days after, it is.
- [ ] Run `./gradlew :data:desktopTest --tests '*BackupWriterTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement it.
- [ ] Run the same command. Expected: 4 tests pass. Then open the archive by hand: `"/c/Program Files/7-Zip/7z.exe" l -p<pw> <test output>` lists `HOW-TO-OPEN.txt`.
- [ ] Commit: "Write 7-Zip backups that open without MedicAll (SPEC:R22, SPEC:R112)".

### Task 13: Restore from a backup

**Files:** create `data/src/commonMain/kotlin/com/hazia/medicall/data/backup/BackupRestore.kt`; test `data/src/desktopTest/kotlin/com/hazia/medicall/data/backup/BackupRestoreTest.kt`.

**Depends on:** Task 12

**Provides:** `class BackupRestore(db, merger: Merger, blobs: Blobs, reader: ArchiveReader)`, with `suspend fun restore(archive: ByteArray, password: CharArray): RestoreReport`. It reads `records/records.json`, imports every row through `Merger.import` with its stamps, and re-adds every file under `files/` to `Blobs` by hash. The report is `data class RestoreReport(val rows: Int, val files: Int, val skipped: Int)`.

- [ ] Write the tests:
  - **`freshDeviceRestoresEverything_SPEC_R22`:** back up device A, restore it on an empty device B, and both hold the same rows and the same file hashes.
  - **`oldBackupNeverOverwritesNewer`:** back up, edit a profile, restore the old backup, and the edit stays.
  - **`wrongPasswordRestoresNothing`.**
- [ ] Run `./gradlew :data:desktopTest --tests '*BackupRestoreTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement it.
- [ ] Run the same command. Expected: 3 tests pass.
- [ ] Commit: "Restore a backup by merging it, so newer data is never lost (SPEC:R22)".

### Task 14: Integrity check and repair

**Files:** create `data/src/commonMain/kotlin/com/hazia/medicall/data/backup/IntegrityCheck.kt`; test `data/src/desktopTest/kotlin/com/hazia/medicall/data/backup/IntegrityCheckTest.kt`.

**Depends on:** Task 10, Task 12

**Provides:**
- **`class IntegrityCheck(blobs: Blobs, target: BackupTarget, reader: ArchiveReader)`**, with `suspend fun run(password: CharArray): IntegrityReport`:
  - it verifies every stored file's hash;
  - for each damaged file, it searches the backups, newest first, for an entry with the right hash, and restores the first one found.
- **`data class IntegrityReport(val checked: Int, val damaged: List<String>, val repaired: List<String>, val unrepaired: List<String>)`.** An unrepaired file is what phase 7's "Backup check failed" notification (R73) will report.
- **Schedule:** the check runs before each backup write, and on its own once a week.

- [ ] Write the tests:
  - **`alteredByteIsFoundAndRepaired_SPEC_R23`:** flip one byte of a stored file, `run`, and the file is reported damaged, then repaired from the newest backup.
  - **`skipsBackupWithDamagedCopy`:** the newest backup's copy is altered too, and the file is repaired from the one before it.
  - **`unrepairableIsReported`.**
- [ ] Run `./gradlew :data:desktopTest --tests '*IntegrityCheckTest*' > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement it.
- [ ] Run the same command. Expected: 3 tests pass.
- [ ] Commit, then open the pull request for Tasks 10–14: "Check every file's hash and repair it from the newest intact backup (SPEC:R23)".

### Task 15: The sync folder on Android

**Files:** create `android/src/main/kotlin/com/hazia/medicall/SafSyncFolder.kt`; test `android/src/androidTest/kotlin/com/hazia/medicall/SafSyncFolderTest.kt`.

**Depends on:** Task 8

**Provides:** `class SafSyncFolder(context: Context, tree: Uri) : SyncFolder`, with `fun reachable(): Boolean` (the persisted read-and-write grant exists, and the tree exists and can be written). Since SAF has no atomic rename, `replace` writes `.tmp-<name>` and reads it back byte for byte. It then rewrites the target the same way and deletes the temporary file. The next `replace` of the same target finishes a torn write from the temporary file when that file is complete.

- [ ] Write the instrumented tests, using a tree inside the test app's own `DocumentsProvider` stub:
  - **`replaceThenReadReturnsNewBytes`.**
  - **`interruptedReplaceLeavesOldOrNew`:** inject a failure after the temporary write, then the next `replace` mends it.
  - **`revokedGrantIsUnreachable`.**
  - **`listSkipsTempFiles`.**
- [ ] Run `ANDROID_SERIAL=<6T serial> ./gradlew :android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hazia.medicall.SafSyncFolderTest > build.log 2>&1; echo "EXIT=$?"`. Expected: the compile fails.
- [ ] Implement it.
- [ ] Run the same command. Expected: 4 tests pass.
- [ ] Commit: "Add the Android sync folder through the Storage Access Framework (SPEC:R16)".

### Task 16: Debug harness and the two-device walk (the phase gate)

**Files:** create `android/src/debug/kotlin/com/hazia/medicall/HarnessActivity.kt` (debug builds only; it is never in a release) and `docs/walks/2026-phase-1.md`.

**Depends on:** Tasks 5, 7, 8, 9, 10, 12, 13, 14, 15

**Provides:** a plain debug screen with the buttons "Set passphrase", "Pick sync folder", "Add test profile", "Edit name", "Add test file", "Remove", "Restore", "Empty bin", "Sync now", "Back up now", "Restore backup" and "Check files". A status line shows the last `ImportReport`, `BackupResult` and `IntegrityReport`. This screen is a test tool, not part of R26's or any later screen design.

- [ ] Build the harness on the composition already made in Tasks 5–15. It holds no logic of its own.
- [ ] Install the debug build on the 6T and the 9 Pro. Both phones run a Syncthing app sharing one test folder that holds only invented data.
- [ ] Walk it, logging each step in `docs/walks/2026-phase-1.md`:
  1. **Passphrase:** set it on the 6T, then unlock on the 9 Pro with the same passphrase.
  2. **Converge (R16):** add "Profile A" on the 6T and sync; it appears on the 9 Pro. Put both phones in airplane mode, rename the profile on the 6T and change its kind on the 9 Pro, then reconnect and sync both. Both phones show both changes.
  3. **Files:** add a test file on the 9 Pro and sync. "Check files" on the 6T reports it intact.
  4. **Backup (R22):** "Back up now" on the 6T, then copy the archive to the PC. `7z l` without the password shows no names; with it, it lists `HOW-TO-OPEN.txt`, `records/` and `files/`.
  5. **Restore (R22):** clear the 9 Pro's app data, set the passphrase, "Restore backup" from the archive, and the profile and file are back.
  6. **Bin:** "Remove" the profile, then "Restore", and it is back. "Empty bin" refuses until "DELETE" is typed.
- [ ] Run `./gradlew :core:desktopTest :data:desktopTest :android:testDebugUnitTest > build.log 2>&1; echo "EXIT=$?"`. Expected: `EXIT=0`.
- [ ] Clear the test data from both phones and remove the test folder from the Syncthing apps. Leave the debug build installed.
- [ ] Commit the walk record, then open the pull request for Tasks 15–16: "Phase 1 gate: sync converges and a backup restores on two phones (SPEC:R100)".

## Proof for the whole plan

- `./gradlew :core:desktopTest :data:desktopTest :android:testDebugUnitTest > build.log 2>&1; echo "EXIT=$?"` prints `EXIT=0`.
- The instrumented suites `EncryptedDatabaseTest` and `SafSyncFolderTest` pass on the 6T.
- `python tools/speccheck.py SPEC.md` reports 0 BLOCK, and the `Gate` and CodeQL checks are green on the last pull request.
- `docs/walks/2026-phase-1.md` records all six walk steps passing on the 6T and the 9 Pro, which is R100's phase 1 gate.

## Progress

- Task 1: done — commit fe71b86 — `./gradlew :core:desktopTest :android:testDebugUnitTest :android:assembleDebug` → BUILD SUCCESSFUL, 2 tests; the R12 test fails when INTERNET is added (checked by hand)
- Task 2: done — commit e16849b — pushed `phase-1`, PR #1 opened; speccheck left out of CI (the script is in a private plugin with no licence), owner to decide
- Task 3: done — `./gradlew :core:desktopTest` → 28 tests, 0 failures
- Task 4: done — `./gradlew :core:desktopTest :core:compileAndroidMain` → CryptoTest 6 passed; the RFC 7914 vector was also checked with Python hashlib. Plan deviation: `expect` declarations are primitives (`pbkdf2Sha256`, `aesGcmSeal`/`Open`, `hmacSha256`, `sha256`, `secureRandomBytes`), with `KeyDerivation` and `LineCipher` in common code
- Task 5: done — `./gradlew :core:desktopTest` → 39 tests, 0 failures (KeyFolderTest 5). `KeyFiles` also has `delete` (settleConflicts needs it)
- Tasks 3-5 review: needs fixes (H1 a losing key file was destroyed; H2 a damaged key file could hang or crash unlock; M1-M3; Lows). Fix round 1 — commit 9a62a67 — `./gradlew :core:desktopTest :core:compileAndroidMain` → CryptoTest 7, KeyFolderTest 12, all core tests pass; scoped re-review started. Left open: L2 (separate subkeys for HMAC and GCM), L7
- PR #1: CodeQL "Analyze" failed because the repository has CodeQL default setup on; the custom workflow was removed (08ef94a). Default setup lists no languages yet: the owner may need to re-run it once Kotlin is on main
- Tasks 3-5 re-review: needs another round (retired files deleted by key id alone; same-id copies dropped; unreadable copies destroyed; every unlock paid for every retired file). Fix round 2 — retired files are never deleted, same-id chains merge, unreadable copies go to keys/unreadable/, at most 8 unknown retired files are derived per unlock — `./gradlew :core:desktopTest :core:compileAndroidMain` → KeyFolderTest 15 passed, all core tests pass. Left open: L2 (separate subkeys), a test of the 8-file cap
- Tasks 3-5 re-review 2: needs another round (a bad same-id copy could make a good folder Damaged and discard the original; planted retired files could starve real ones; unfoldedKeyIds used another rule). Fix round 3 — a bad chain entry is reported in `Unlocked.unreadableKeyIds`, not a lockout; the folder file wins entry collisions and only same-salt same-check copies merge; every differing variant is kept in retired/; unreadable files keep a digest in their name; fold and unfoldedKeyIds try every retired file, unlock at most 8; `unreadableKeyFiles()` — `./gradlew :core:desktopTest :core:compileAndroidMain` → 51 tests, KeyFolderTest 16
- Tasks 3-5 re-review 3: approved (02f0a8f). Documented limits, not fixed: the cap of 8 retired files per unlock; fold has no cost limit; a planted lower-id key file locks devices out (WrongPassphrase), with the real file kept in retired/ and no restore call yet; change drops the unreadable-id warning; L2; no test of the cap. Trust comment corrected
- Task 6: done — commit fc867d4 (implementer subagent, rebased onto 02f0a8f..fa3a87b) — `:data:desktopTest --tests *DatabaseTest*` → 3 passed; departures in its report: entities in Profile.kt/BlobRef.kt, deferred FK on blob_ref, row mapping landed in Task 8
- Task 8: done — commit e7c13a5 — `./gradlew :core:desktopTest :data:desktopTest :data:testAndroidHostTest :android:testDebugUnitTest :android:assembleDebug` → core 51, data desktop 47, data Android host 14, android 1; FolderSync takes `wallMillis`; LineGuard and sealing each proven to fail when disabled. Open doubts: ImportReport.waiting may double-count after an undone purge; foreign_keys pragma unchecked on reader connections; profile.kind is free text
- Task 9: done — `./gradlew :data:desktopTest --tests *BinTest*` → 5 passed. `Bin(writes: LocalWrites, db, wallMillis)` in place of the plan's `(db, merger, clock, wallMillis)`: LocalWrites holds the merger and clock
