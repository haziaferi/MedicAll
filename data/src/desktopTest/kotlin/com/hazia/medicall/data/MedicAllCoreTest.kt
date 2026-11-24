package com.hazia.medicall.data

import com.hazia.medicall.core.crypto.KeyFolder
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.data.backup.BackupResult
import com.hazia.medicall.data.backup.BackupWriter
import com.hazia.medicall.data.backup.DirectoryBackupTarget
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.sync.DirectorySyncFolder
import com.hazia.medicall.data.sync.SyncFolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phase 1 walk (SPEC:R100) on the desktop: two devices over one [DirectorySyncFolder] and one
 * [DirectoryBackupTarget], each built through [MedicAllCore] as the debug harness builds it. Every value is invented.
 */
class MedicAllCoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val opened = mutableListOf<MedicAllDatabase>()
    @After fun close() = opened.forEach { it.close() }

    private val passphrase = "invented-walk-passphrase".toCharArray()
    private val DAY = 86_400_000L
    private val HOUR = 3_600_000L
    private val testFile = "An invented test file, no real data".encodeToByteArray()

    private val syncRoot by lazy { tmp.newFolder("sync").toPath() }
    private val backupRoot by lazy { tmp.newFolder("backups").toPath() }
    private val backups by lazy { DirectoryBackupTarget(backupRoot) }

    /** A device as the harness makes one: the passphrase unlocks (or makes) the key file, then the core is built. */
    private fun device(name: String, phrase: CharArray = passphrase): MedicAllCore = Phone(name, DirectorySyncFolder(syncRoot)).unlock(phrase)

    /** A device's database and stored files, kept across [unlock]s, as a shell keeps them when it unlocks again. */
    private inner class Phone(
        val name: String,
        val folder: SyncFolder,
        dirName: String = name,
        private val wall: () -> Long = System::currentTimeMillis,
    ) {
        private val dir = tmp.newFolder(dirName)
        private val db = openMedicAllDatabase(File(dir, "medicall.db")).database.also { opened += it }

        /** Unlocks (or makes) the key file with [phrase] and builds the core over this device's database. */
        fun unlock(phrase: CharArray = passphrase): MedicAllCore = assertIs<Opened.Ready>(open(phrase)).core

        /** What [MedicAllCore.open] gives for [phrase]. */
        fun open(phrase: CharArray): Opened = runBlocking {
            MedicAllCore.open(db, folder, name, phrase, DirectoryBlobStore(dir.toPath().resolve("blobs")), File(dir, "work"), wall, ZoneOffset.UTC)
        }
    }

    /** A sync folder over [root], which the test moves, as Syncthing changes what a device's folder holds. */
    private class MovableFolder(var root: Path) : SyncFolder {
        private val at get() = DirectorySyncFolder(root)
        override fun list(dir: String) = at.list(dir)
        override fun size(path: String) = at.size(path)
        override fun read(path: String) = at.read(path)
        override fun replace(path: String, bytes: ByteArray) = at.replace(path, bytes)
        override fun delete(path: String) = at.delete(path)
    }

    /**
     * Two folders meeting as Syncthing would join them into [into]: every file of both, [kept]'s winning
     * where both have one, and [renamed]'s key file kept as a conflict copy.
     */
    private fun meet(kept: Path, renamed: Path, into: Path) {
        for (root in listOf(renamed, kept)) {
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { from ->
                    val to = into.resolve(root.relativize(from).toString())
                    Files.createDirectories(to.parent)
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        Files.copy(renamed.resolve("keys/keyinfo.json"), into.resolve("keys/keyinfo.sync-conflict-20261123-120000-ABCDEFG.json"))
    }

    // SPEC:R16, SPEC:R109 two devices that each made a key apart, with one passphrase, settle the clash and read
    // each other: once with A's key file kept by Syncthing and B's renamed, once the other way round.
    @Test
    fun keyFilesMadeApartAreSettled_SPEC_R16() {
        settleClash(round = 1, keptIsA = true)
        settleClash(round = 2, keptIsA = false)
    }

    private fun settleClash(round: Int, keptIsA: Boolean) = runBlocking {
        val folderA = MovableFolder(tmp.newFolder("syncA$round").toPath())
        val folderB = MovableFolder(tmp.newFolder("syncB$round").toPath())
        val phoneA = Phone("A", folderA, "A$round")
        val phoneB = Phone("B", folderB, "B$round")
        var a = phoneA.unlock()
        var b = phoneB.unlock()
        val keyA = assertNotNull(KeyFolder(folderA).currentKeyId())
        val keyB = assertNotNull(KeyFolder(folderB).currentKeyId())
        val fileA = "An invented test file from A".encodeToByteArray()
        val fileB = "An invented test file from B, longer".encodeToByteArray()
        val profileA = a.addProfile("Profile A")
        val refA = a.addFile(profileA, "test-a.txt", "text/plain", fileA)
        val profileB = b.addProfile("Profile B")
        val refB = b.addFile(profileB, "test-b.txt", "text/plain", fileB)
        a.syncNow()
        b.syncNow()

        val shared = tmp.newFolder("shared$round").toPath()
        if (keptIsA) meet(kept = folderA.root, renamed = folderB.root, into = shared)
        else meet(kept = folderB.root, renamed = folderA.root, into = shared)
        folderA.root = shared
        folderB.root = shared
        val what = "round $round, ${if (keptIsA) "A's" else "B's"} key file kept, ${if (keyA < keyB) "A's" else "B's"} key id lower"
        assertTrue(a.syncNow().unlockAgain, "A's cipher misses a key the folder now holds; $what")
        assertTrue(b.syncNow().unlockAgain, "B's cipher misses a key the folder now holds; $what")
        assertEquals(listOf("keyinfo.json", "retired"), DirectorySyncFolder(shared).list("keys").sorted(), "the conflict copy is settled; $what")
        assertEquals(minOf(keyA, keyB), KeyFolder(DirectorySyncFolder(shared)).currentKeyId(), "the lower key id wins; $what")
        assertEquals(setOf(maxOf(keyA, keyB)), KeyFolder(DirectorySyncFolder(shared)).retiredKeyIds(), "the other is retired; $what")

        a = phoneA.unlock()
        b = phoneB.unlock()
        repeat(2) {
            assertFalse(a.syncNow().unlockAgain, what)
            assertFalse(b.syncNow().unlockAgain, what)
        }
        val expected = listOf(profileA to "Profile A", profileB to "Profile B")
        for ((name, core) in listOf("A" to a, "B" to b)) {
            assertEquals(expected, core.profiles().map { it.id to it.name }, "$name; $what")
            assertContentEquals(fileA, core.readFile(refA), "$name; $what")
            assertContentEquals(fileB, core.readFile(refB), "$name; $what")
        }
    }

    // SPEC:R18 the bin's 30 days run out at a sync: on day 29 the profile is still in the bin, past day 30 it is
    // purged at A's sync, and the purge reaches B before B's own clock would purge it. B's wall clock runs 13 hours
    // behind A's, so on A's day 30.5 B is on day 29.96: only the purge A exported can take it there. (A
    // larger lag is held by LineGuard, more than a day ahead, so day 31 on A cannot tell travel from B's own purge.)
    @Test
    fun binExpiresAtSync_SPEC_R18() = runBlocking {
        val start = 1_800_000_000_000L
        var now = start
        val lag = 13 * HOUR
        val a = Phone("A", DirectorySyncFolder(syncRoot), wall = { now }).unlock()
        val b = Phone("B", DirectorySyncFolder(syncRoot), wall = { now - lag }).unlock()
        val profile = a.addProfile("Profile A")
        a.syncNow()
        b.syncNow()
        a.remove(PROFILE, profile)
        a.syncNow()
        b.syncNow()
        assertEquals(listOf(profile), b.binContents().map { it.id }, "the removal reached B")

        now = start + 29 * DAY
        a.syncNow()
        b.syncNow()
        assertEquals(listOf(profile), a.profiles().map { it.id }, "day 29: still in A's bin")
        assertEquals(listOf(profile), b.profiles().map { it.id }, "day 29: still in B's bin")

        now = start + 30 * DAY + 12 * HOUR
        a.syncNow()
        assertTrue(a.profiles().isEmpty(), "day 30.5: purged on A")
        a.syncNow() // the purge travels with A's next export
        b.syncNow()
        assertTrue(b.profiles().isEmpty(), "B, on day 29.96 by its own clock, has A's purge")

        now = start + 31 * DAY
        a.syncNow()
        b.syncNow()
        assertTrue(a.profiles().isEmpty() && b.profiles().isEmpty() && a.binContents().isEmpty() && b.binContents().isEmpty(), "day 31: gone everywhere")
    }

    // SPEC:R109 a device that finds another device's directory but no key file makes no key of its own.
    @Test
    fun noKeyIsMadeBeforeThePeersKeyFileArrives_SPEC_R109() {
        val folder = DirectorySyncFolder(syncRoot)
        folder.replace("devices/other-device/log-000001.jsonl", "an invented line\n".encodeToByteArray())
        assertEquals(Unlock.KeyFileNotArrived, MedicAllCore.unlockOrCreate(folder, passphrase, "this-device"))
        assertNull(folder.read("keys/keyinfo.json"), "no key file was made")
        // Its own directory alone is no peer: the device may make the key.
        val alone = DirectorySyncFolder(tmp.newFolder("alone").toPath())
        alone.replace("devices/this-device/log-000001.jsonl", "an invented line\n".encodeToByteArray())
        assertIs<Unlock.Unlocked>(MedicAllCore.unlockOrCreate(alone, passphrase, "this-device"))
    }

    // SPEC:R100 the phase 1 gate: passphrase, convergence, files, backup, restore and bin, through the one composition.
    @Test
    fun phaseOneWalk_SPEC_R100() = runBlocking {
        val a = device("A")
        val b = device("B")
        assertTrue(syncRoot.resolve("keys/keyinfo.json").toFile().isFile, "the key file is in the sync folder")

        // Converge (SPEC:R16): a rename on A and a change of kind on B both survive.
        val profile = a.addProfile("Profile A")
        a.syncNow()
        assertEquals(1, b.syncNow().imported.changed)
        a.rename(profile, "Profile A renamed")
        b.changeKind(profile, MedicAllCore.PET)
        a.syncNow()
        b.syncNow()
        a.syncNow()
        val expected = listOf(ProfileInfo(profile, "Profile A renamed", MedicAllCore.PET, null, Module.entries.toSet(), "A", null))
        assertEquals(expected, a.profiles())
        assertEquals(expected, b.profiles())

        // Files (SPEC:R23): added on B, intact on A.
        val ref = b.addFile(profile, "test-1.txt", "text/plain", testFile)
        b.syncNow()
        a.syncNow()
        assertContentEquals(testFile, a.readFile(ref))
        val check = a.checkFiles(backups)
        assertEquals(1, check.checked)
        assertTrue(check.damaged.isEmpty(), "$check")

        // Backup and restore (SPEC:R22) on a fresh device.
        val written = assertIs<BackupResult.Written>(a.backUp(backups))
        assertEquals(BackupWriter.LATEST_NAME, written.name)
        assertEquals(1, written.files)
        val c = device("C")
        val restored = assertNotNull(c.restoreBackup(backups))
        assertEquals(2, restored.rows)
        assertEquals(1, restored.files)
        assertEquals(expected, c.profiles())
        assertContentEquals(testFile, c.readFile(ref))
        assertEquals(null, c.restoreBackup(backups, "another-invented-phrase".toCharArray()), "a wrong passphrase restores nothing")

        // Bin (SPEC:R18, SPEC:R19): remove, restore, and an empty that needs "DELETE".
        a.remove(PROFILE, profile)
        assertEquals(setOf(PROFILE, BLOB_REF), a.binContents().map { it.table }.toSet(), "the profile's file goes with it")
        a.restore(PROFILE, profile)
        assertEquals(expected, a.profiles())
        assertTrue(a.binContents().isEmpty())
        a.remove(PROFILE, profile)
        assertFailsWith<IllegalArgumentException> { a.emptyBin("delete") }
        assertEquals(1, a.profiles().size)
        a.emptyBin("DELETE")
        assertTrue(a.profiles().isEmpty())
        assertTrue(a.files().isEmpty())
    }

    // SPEC:R109 a second device with another passphrase is told so, and makes no new key file.
    @Test
    fun wrongPassphraseDoesNotUnlock_SPEC_R109() {
        val folder = DirectorySyncFolder(syncRoot)
        assertIs<Unlock.Unlocked>(MedicAllCore.unlockOrCreate(folder, passphrase, "A"))
        val before = syncRoot.resolve("keys/keyinfo.json").toFile().readBytes()
        assertEquals(Unlock.WrongPassphrase, MedicAllCore.unlockOrCreate(folder, "another-invented-phrase".toCharArray(), "B"))
        assertContentEquals(before, syncRoot.resolve("keys/keyinfo.json").toFile().readBytes())
    }

    // SPEC:R10 the database key is random, apart from the passphrase: 32 bytes, never the same twice.
    @Test
    fun databaseKeyIsRandom_SPEC_R10() {
        val one = MedicAllCore.newDatabaseKey()
        assertEquals(32, one.size)
        assertFalse(one.contentEquals(MedicAllCore.newDatabaseKey()))
    }

    // An empty backup folder is reported, not restored from.
    @Test
    fun restoreFromAnEmptyFolderIsRefused() = runBlocking {
        val a = device("A")
        assertFailsWith<IllegalStateException> { a.restoreBackup(backups) }
        Unit
    }

    // SPEC:R109 the core is built from the passphrase: another passphrase builds nothing and changes no key file.
    @Test
    fun openRefusesAWrongPassphrase_SPEC_R109() {
        device("A")
        val before = syncRoot.resolve("keys/keyinfo.json").toFile().readBytes()
        val refused = assertIs<Opened.Refused>(Phone("B", DirectorySyncFolder(syncRoot)).open("another-invented-phrase".toCharArray()))
        assertEquals(Unlock.WrongPassphrase, refused.reason)
        assertContentEquals(before, syncRoot.resolve("keys/keyinfo.json").toFile().readBytes())
    }

    // SPEC:R109 a backup is sealed with the passphrase that unlocked sync, which the call no longer takes.
    @Test
    fun backupUsesThePassphraseThatUnlockedSync_SPEC_R109() = runBlocking {
        val a = device("A")
        a.addProfile("Profile A")
        assertIs<BackupResult.Written>(a.backUp(backups))
        val c = device("C")
        assertNull(c.restoreBackup(backups, "another-invented-phrase".toCharArray()), "another passphrase does not open it")
        assertEquals(1, assertNotNull(c.restoreBackup(backups), "the sync passphrase opens it").rows)
    }

    // A closed core has cleared its passphrase: it refuses to seal or open a backup with what is left.
    @Test
    fun aClosedCoreRefusesBackups() = runBlocking {
        val a = device("A")
        a.close()
        assertFailsWith<IllegalStateException> { a.backUp(backups) }
        assertFailsWith<IllegalStateException> { a.restoreBackup(backups) }
        assertFailsWith<IllegalStateException> { a.checkFiles(backups) }
        assertTrue(backupRoot.toFile().list()!!.isEmpty(), "nothing was written")
    }

    // SPEC:R109 a key file that cannot be read for a moment is not reported as a passphrase change.
    @Test
    fun anUnreadableKeyFileIsNotAPassphraseChange_SPEC_R109() = runBlocking {
        val a = device("A")
        val keyFile = syncRoot.resolve("keys/keyinfo.json").toFile()
        val bytes = keyFile.readBytes()
        keyFile.delete()
        assertEquals(MedicAllCore.KEY_FILE_UNREADABLE, assertIs<BackupResult.Failed>(a.backUp(backups)).reason)
        keyFile.writeBytes(bytes)
        assertIs<BackupResult.Written>(a.backUp(backups))
        Unit
    }

    // SPEC:R10 a database made anew reads its own log back at the first sync, and only then says so (the shell
    // then clears its lasting mark), so a restart before that sync reads it back still.
    @Test
    fun aRecoveredDatabaseSaysWhenItsOwnLogIsRead_SPEC_R10() = runBlocking {
        var said = 0
        val folder = DirectorySyncFolder(syncRoot)
        val dir = tmp.newFolder("R")
        val db = openMedicAllDatabase(File(dir, "medicall.db")).database.also { opened += it }
        val core = assertIs<Opened.Ready>(
            MedicAllCore.open(db, folder, "R", passphrase, DirectoryBlobStore(dir.toPath().resolve("blobs")), File(dir, "work"),
                recovered = true, ownLogRead = { said++ }),
        ).core
        assertEquals(0, said, "not before the first import")
        core.syncNow()
        assertEquals(0, said, "no own log in the folder yet: still waiting")
        core.addProfile("Profile R")
        core.syncNow() // exports the own log, then reads it back
        assertEquals(1, said)
        core.syncNow()
        assertEquals(1, said, "once")
    }

    // SPEC:R109 a passphrase change made on another device is noticed: this device writes no backup under the old
    // passphrase, the old one no longer opens, and the new one does and seals the next backup.
    @Test
    fun peerPassphraseChangeIsNoticed_SPEC_R109() = runBlocking {
        val newPhrase = "invented-new-passphrase".toCharArray()
        val phoneA = Phone("A", DirectorySyncFolder(syncRoot))
        val a = phoneA.unlock()
        val b = device("B")
        a.addProfile("Profile A")
        a.syncNow()
        b.syncNow()
        assertIs<BackupResult.Written>(a.backUp(backups))
        val before = backupRoot.resolve(BackupWriter.LATEST_NAME).toFile().readBytes()

        KeyFolder(DirectorySyncFolder(syncRoot)).change(passphrase, newPhrase) // B's change, as the owed flow will make it
        assertTrue(a.syncNow().unlockAgain, "A notices the folder's key moved on")
        val refused = assertIs<BackupResult.Failed>(a.backUp(backups))
        assertEquals(MedicAllCore.PASSPHRASE_CHANGED, refused.reason)
        assertContentEquals(before, backupRoot.resolve(BackupWriter.LATEST_NAME).toFile().readBytes(), "nothing was written")

        assertEquals(Unlock.WrongPassphrase, assertIs<Opened.Refused>(phoneA.open(passphrase)).reason)
        val again = phoneA.unlock(newPhrase)
        assertIs<BackupResult.Written>(again.backUp(backups))
        val c = device("C-new", newPhrase)
        assertNull(c.restoreBackup(backups, passphrase), "the old passphrase no longer opens the new backup")
        assertNotNull(c.restoreBackup(backups), "the new one does")
        Unit
    }
}
