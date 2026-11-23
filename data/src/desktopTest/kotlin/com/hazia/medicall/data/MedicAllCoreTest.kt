package com.hazia.medicall.data

import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.data.backup.BackupResult
import com.hazia.medicall.data.backup.BackupWriter
import com.hazia.medicall.data.backup.DirectoryBackupTarget
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.sync.DirectorySyncFolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    private fun device(name: String): MedicAllCore = runBlocking {
        val folder = DirectorySyncFolder(syncRoot)
        val unlocked = assertIs<Unlock.Unlocked>(MedicAllCore.unlockOrCreate(folder, passphrase))
        val dir = tmp.newFolder(name)
        val db = openMedicAllDatabase(File(dir, "medicall.db")).database.also { opened += it }
        MedicAllCore.open(db, folder, name, unlocked.cipher, DirectoryBlobStore(dir.toPath().resolve("blobs")), File(dir, "work"), zone = ZoneOffset.UTC)
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
        assertEquals(1, b.syncNow().changed)
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
        assertIs<Unlock.Unlocked>(MedicAllCore.unlockOrCreate(folder, passphrase))
        val before = syncRoot.resolve("keys/keyinfo.json").toFile().readBytes()
        assertEquals(Unlock.WrongPassphrase, MedicAllCore.unlockOrCreate(folder, "another-invented-phrase".toCharArray()))
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
