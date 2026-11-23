package com.hazia.medicall.data

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
    private val testFile = "An invented test file, no real data".encodeToByteArray()

    private val syncRoot by lazy { tmp.newFolder("sync").toPath() }
    private val backupRoot by lazy { tmp.newFolder("backups").toPath() }
    private val backups by lazy { DirectoryBackupTarget(backupRoot) }

    /** A device as the harness makes one: the passphrase unlocks (or makes) the key file, then the core is built. */
    private fun device(name: String): MedicAllCore = Phone(name, DirectorySyncFolder(syncRoot)).unlock()

    /** A device's database and stored files, kept across [unlock]s, as a shell keeps them when it unlocks again. */
    private inner class Phone(val name: String, val folder: SyncFolder) {
        private val dir = tmp.newFolder(name)
        private val db = openMedicAllDatabase(File(dir, "medicall.db")).database.also { opened += it }

        /** Unlocks (or makes) the key file with the passphrase and builds the core over this device's database. */
        fun unlock(): MedicAllCore = runBlocking {
            val unlocked = assertIs<Unlock.Unlocked>(MedicAllCore.unlockOrCreate(folder, passphrase, name))
            MedicAllCore.open(db, folder, name, unlocked.cipher, DirectoryBlobStore(dir.toPath().resolve("blobs")), File(dir, "work"), zone = ZoneOffset.UTC)
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

    // SPEC:R16, SPEC:R109 two devices that each made a key apart, with one passphrase, settle the clash and read each other.
    @Test
    fun keyFilesMadeApartAreSettled_SPEC_R16() = runBlocking {
        val folderA = MovableFolder(tmp.newFolder("syncA").toPath())
        val folderB = MovableFolder(tmp.newFolder("syncB").toPath())
        val phoneA = Phone("A", folderA)
        val phoneB = Phone("B", folderB)
        var a = phoneA.unlock()
        var b = phoneB.unlock()
        val fileA = "An invented test file from A".encodeToByteArray()
        val fileB = "An invented test file from B, longer".encodeToByteArray()
        val profileA = a.addProfile("Profile A")
        val refA = a.addFile(profileA, "test-a.txt", "text/plain", fileA)
        val profileB = b.addProfile("Profile B")
        val refB = b.addFile(profileB, "test-b.txt", "text/plain", fileB)
        a.syncNow()
        b.syncNow()

        val shared = tmp.newFolder("shared").toPath()
        meet(kept = folderA.root, renamed = folderB.root, into = shared)
        folderA.root = shared
        folderB.root = shared
        assertTrue(a.syncNow().unlockAgain, "A's cipher misses a key the folder now holds")
        assertTrue(b.syncNow().unlockAgain, "B's cipher misses a key the folder now holds")
        assertEquals(listOf("keyinfo.json", "retired"), DirectorySyncFolder(shared).list("keys").sorted(), "the conflict copy is settled")

        a = phoneA.unlock()
        b = phoneB.unlock()
        repeat(2) {
            assertFalse(a.syncNow().unlockAgain)
            assertFalse(b.syncNow().unlockAgain)
        }
        val expected = listOf(profileA to "Profile A", profileB to "Profile B")
        for ((name, core) in listOf("A" to a, "B" to b)) {
            assertEquals(expected, core.profiles().map { it.id to it.name }, name)
            assertContentEquals(fileA, core.readFile(refA), name)
            assertContentEquals(fileB, core.readFile(refB), name)
        }
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
        val expected = listOf(ProfileInfo(profile, "Profile A renamed", MedicAllCore.PET, null))
        assertEquals(expected, a.profiles())
        assertEquals(expected, b.profiles())

        // Files (SPEC:R23): added on B, intact on A.
        val ref = b.addFile(profile, "test-1.txt", "text/plain", testFile)
        b.syncNow()
        a.syncNow()
        assertContentEquals(testFile, a.readFile(ref))
        val check = a.checkFiles(backups, passphrase)
        assertEquals(1, check.checked)
        assertTrue(check.damaged.isEmpty(), "$check")

        // Backup and restore (SPEC:R22) on a fresh device.
        val written = assertIs<BackupResult.Written>(a.backUp(backups, passphrase))
        assertEquals(BackupWriter.LATEST_NAME, written.name)
        assertEquals(1, written.files)
        val c = device("C")
        val restored = assertNotNull(c.restoreBackup(backups, passphrase))
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

    // SPEC:R10 each device's database key comes from the passphrase and differs between devices.
    @Test
    fun databaseKeyIsPerDevice_SPEC_R10() {
        val one = MedicAllCore.databaseKey(passphrase, "device-1")
        assertEquals(32, one.size)
        assertContentEquals(one, MedicAllCore.databaseKey(passphrase, "device-1"))
        assertTrue(!one.contentEquals(MedicAllCore.databaseKey(passphrase, "device-2")))
    }

    // An empty backup folder is reported, not restored from.
    @Test
    fun restoreFromAnEmptyFolderIsRefused() = runBlocking {
        val a = device("A")
        assertFailsWith<IllegalStateException> { a.restoreBackup(backups, passphrase) }
        Unit
    }
}
