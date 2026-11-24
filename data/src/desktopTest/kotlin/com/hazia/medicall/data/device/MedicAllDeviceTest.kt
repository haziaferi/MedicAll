package com.hazia.medicall.data.device

import com.hazia.medicall.core.crypto.KeyFolder
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.core.lock.AppLock
import com.hazia.medicall.core.lock.Verdict
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.Opened
import com.hazia.medicall.data.backup.BackupResult
import com.hazia.medicall.data.backup.DirectoryBackupTarget
import com.hazia.medicall.data.backup.SevenZip
import com.hazia.medicall.data.backup.read
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.openMedicAllDatabase
import com.hazia.medicall.data.sync.DirectorySyncFolder
import com.hazia.medicall.data.sync.FolderLayout
import com.hazia.medicall.data.sync.completeLines
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneOffset
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Several sync folders on one device (SPEC:R110, SPEC:R109, SPEC:R22) and the app lock beside them (SPEC:R11). Every value is invented. */
class MedicAllDeviceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val opened = mutableListOf<MedicAllDatabase>()
    @After fun close() = opened.forEach { it.close() }

    private val passA = "invented-folder-a-passphrase"
    private val passB = "invented-folder-b-passphrase"
    private val folderA by lazy { tmp.newFolder("sync-a").toPath() }
    private val folderB by lazy { tmp.newFolder("sync-b").toPath() }
    private var elapsed = 5_000_000L

    /** One device: its own settings file and databases; folders are joined by their path as the URI. */
    private inner class Phone(val name: String) {
        val dir: File = tmp.newFolder(name)
        val settings = MemorySealedFile()
        val leftAside = mutableListOf<String>()
        private val databases = mutableMapOf<String, MedicAllDatabase>()

        fun device(): MedicAllDevice {
            val registry = FolderRegistry(settings)
            val lock = AppLock(registry.lock(), { elapsed }, registry::saveLock)
            return MedicAllDevice(registry, ::open, lock) { f ->
                // As a shell does: the database closes first, then its file is moved aside.
                databases.remove(f.id)?.close()
                val db = File(dir, f.databaseName)
                assertTrue(db.renameTo(File(dir, f.databaseName + ".left")))
                leftAside += f.id
            }
        }

        private suspend fun open(f: JoinedFolder, passphrase: CharArray): Opened {
            val db = openMedicAllDatabase(File(dir, f.databaseName)).database.also { opened += it; databases[f.id] = it }
            val work = File(dir, f.id)
            return com.hazia.medicall.data.MedicAllCore.open(
                db, DirectorySyncFolder(Paths.get(f.folderUri)), name, passphrase,
                DirectoryBlobStore(work.toPath().resolve("blobs")), File(work, "work"), System::currentTimeMillis, ZoneOffset.UTC,
            )
        }
    }

    private fun snapshot(vararg roots: Path): Map<String, List<Byte>> = roots.flatMap { root ->
        Files.walk(root).use { s -> s.filter { it.isRegularFile() }.toList() }.map { it.toString() to it.readBytes().toList() }
    }.toMap()

    @Test
    fun aDeviceInOnlyFolderBHoldsNothingOfFolderA_SPEC_R110() = runBlocking {
        val one = Phone("phone-1").device()
        val a = assertIs<Opened.Ready>(one.join("Folder A", folderA.toString(), passA.toCharArray()))
        val b = assertIs<Opened.Ready>(one.join("Folder B", folderB.toString(), passB.toCharArray()))
        val idA = a.core.addProfile("Profile A")
        val idB = b.core.addProfile("Profile B")
        a.core.syncNow(); b.core.syncNow()
        assertEquals(setOf("Profile A", "Profile B"), one.profiles().map { it.second.name }.toSet())

        val two = Phone("phone-2").device()
        val onlyB = assertIs<Opened.Ready>(two.join("Folder B", folderB.toString(), passB.toCharArray()))
        onlyB.core.syncNow()
        assertEquals(listOf(idB), two.profiles().map { it.second.id })

        // No line of folder B, opened with B's key, names Profile A.
        val cipher = assertIs<Unlock.Unlocked>(KeyFolder(DirectorySyncFolder(folderB)).unlock(passB.toCharArray())).cipher
        var lines = 0
        Files.walk(folderB.resolve(FolderLayout.DEVICES)).use { s -> s.filter { it.isRegularFile() }.toList() }.forEach { f ->
            for (line in completeLines(f.readBytes(), 0)) {
                val text = assertNotNull(cipher.open(line.text)).decodeToString()
                assertFalse(idA in text, "folder B holds a line naming Profile A")
                lines++
            }
        }
        assertTrue(lines > 0)
    }

    @Test
    fun eachFolderHasItsOwnPassphrase_SPEC_R109() = runBlocking {
        val one = Phone("phone-1").device()
        val a = assertIs<Opened.Ready>(one.join("Folder A", folderA.toString(), passA.toCharArray()))
        assertIs<Opened.Ready>(one.join("Folder B", folderB.toString(), passB.toCharArray()))
        assertFalse(a.core.passphraseOpens(passB.toCharArray()))
        assertTrue(a.core.passphraseOpens(passA.toCharArray()))

        val phone2 = Phone("phone-2")
        val two = phone2.device()
        assertIs<Opened.Refused>(two.join("Folder A", folderA.toString(), passB.toCharArray()))
        assertTrue(two.folders().isEmpty())
        assertTrue(FolderRegistry(phone2.settings).folders().isEmpty())
        assertFailsWith<IllegalStateException> { one.join("Folder A again", folderA.toString(), passA.toCharArray()) }
        Unit
    }

    @Test
    fun eachFolderBacksUpApart_SPEC_R22() = runBlocking {
        val one = Phone("phone-1").device()
        val a = assertIs<Opened.Ready>(one.join("Folder A", folderA.toString(), passA.toCharArray()))
        val b = assertIs<Opened.Ready>(one.join("Folder B", folderB.toString(), passB.toCharArray()))
        a.core.addProfile("Profile A")
        val idB = b.core.addProfile("Profile B")
        val backupA = tmp.newFolder("backup-a").toPath()
        assertIs<BackupResult.Written>(a.core.backUp(DirectoryBackupTarget(backupA)))
        val archive = Files.list(backupA).use { s -> s.toList().single { it.toString().endsWith(".7z") } }.toFile()
        assertNull(SevenZip.list(archive, passB.toCharArray()))
        val names = assertNotNull(SevenZip.list(archive, passA.toCharArray()))
        for (n in names) assertFalse(idB in (SevenZip.read(archive, passA.toCharArray(), n)?.decodeToString().orEmpty()), n)
    }

    @Test
    fun changingThePinTouchesNoFolderOrBackup_SPEC_R11() = runBlocking {
        val one = Phone("phone-1").device()
        val a = assertIs<Opened.Ready>(one.join("Folder A", folderA.toString(), passA.toCharArray()))
        val b = assertIs<Opened.Ready>(one.join("Folder B", folderB.toString(), passB.toCharArray()))
        a.core.addProfile("Profile A"); b.core.addProfile("Profile B")
        a.core.syncNow(); b.core.syncNow()
        val backupA = tmp.newFolder("backup-a").toPath()
        val backupB = tmp.newFolder("backup-b").toPath()
        a.core.backUp(DirectoryBackupTarget(backupA)); b.core.backUp(DirectoryBackupTarget(backupB))
        val before = snapshot(folderA, folderB, backupA, backupB)

        one.lock.setPin("2468".toCharArray())
        assertEquals(Verdict.Ok, one.lock.changePin("2468".toCharArray(), "1357".toCharArray()))
        assertEquals(Verdict.Ok, one.lock.turnOff("1357".toCharArray()))
        a.core.syncNow(); b.core.syncNow()

        assertEquals(before, snapshot(folderA, folderB, backupA, backupB))
    }

    @Test
    fun aForgottenPinGoesWithThePassphrase_SPEC_R11() = runBlocking {
        val phone = Phone("phone-1")
        val one = phone.device()
        one.join("Folder A", folderA.toString(), passA.toCharArray())
        one.join("Folder B", folderB.toString(), passB.toCharArray())
        one.lock.setPin("2468".toCharArray())
        assertEquals(Verdict.Wrong(4), one.removeForgottenPin("not-the-passphrase-at-all".toCharArray()))
        assertTrue(one.lock.isOn)
        assertEquals(Verdict.Ok, one.removeForgottenPin(passB.toCharArray()))
        assertFalse(one.lock.isOn)
        assertFalse(FolderRegistry(phone.settings).lock().pinHash != null)
    }

    @Test
    fun leavingAFolderKeepsItsDatabase_SPEC_R10() = runBlocking {
        val phone = Phone("phone-1")
        val one = phone.device()
        val a = assertIs<Opened.Ready>(one.join("Folder A", folderA.toString(), passA.toCharArray()))
        one.join("Folder B", folderB.toString(), passB.toCharArray())
        val idA = one.folders().first().id
        a.core.addProfile("Profile A")
        one.leave(idA)
        assertEquals(listOf("Folder B"), one.folders().map { it.label })
        assertNull(one.core(idA))
        assertEquals(listOf(idA), phone.leftAside)
        assertTrue(File(phone.dir, "medicall-$idA.db.left").isFile)
        assertFailsWith<IllegalStateException> { a.core.backUp(DirectoryBackupTarget(tmp.newFolder("late").toPath())) }
        Unit
    }

    @Test
    fun everyFolderOpensAgainAfterARestart_SPEC_R110() = runBlocking {
        val phone = Phone("phone-1")
        val one = phone.device()
        assertIs<Opened.Ready>(one.join("Folder A", folderA.toString(), passA.toCharArray())).core.addProfile("Profile A")
        assertIs<Opened.Ready>(one.join("Folder B", folderB.toString(), passB.toCharArray())).core.addProfile("Profile B")

        val again = phone.device()
        val result = again.openAll { f -> (if (f.label == "Folder A") passA else passB).toCharArray() }
        assertTrue(result.values.all { it is Opened.Ready })
        assertEquals(setOf("Profile A", "Profile B"), again.profiles().map { it.second.name }.toSet())
        assertEquals(listOf("Folder A", "Folder B"), again.profiles().map { it.first.label }.sorted())

        // A folder whose passphrase is not at hand stays shut, and the others open.
        val third = phone.device()
        val some = third.openAll { f -> if (f.label == "Folder B") passB.toCharArray() else null }
        assertEquals(1, some.size)
        assertEquals(listOf("Profile B"), third.profiles().map { it.second.name })
    }
}
