package com.hazia.medicall.data.backup

import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.sync.RecordCodec
import com.hazia.medicall.data.sync.RowRecord
import com.hazia.medicall.data.sync.World
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
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

/** 7-Zip backups that open without MedicAll (SPEC:R22, SPEC:R112). */
class BackupWriterTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()
    @After fun close() = worlds.forEach { it.close() }

    private val password = "invented-Passphrase-7".toCharArray()
    private val start = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private val day = 24 * hour

    private val lab1 = "Lab 1 result, invented for a test: 42 units".encodeToByteArray()
    private val lab1Again = "Lab 1 repeated, invented for a test: 43 units".encodeToByteArray()
    private val note = "An invented note".encodeToByteArray()

    private fun device(): World.Device {
        val w = World(tmp.newFolder(), 1).also { worlds += it }
        w.syncthing.now = start
        return w.Device("A")
    }

    private fun writer(d: World.Device, target: BackupTarget) =
        BackupWriter(d.db, d.blobs, SevenZip, target, wallMillis = { start }, zone = ZoneOffset.UTC, workDir = tmp.newFolder())

    @Test
    fun archiveHoldsEveryRecordAndFile_SPEC_R22() {
        val a = device()
        val profileA = a.create("Profile A")
        val profileB = a.create("Profile B")
        runBlocking {
            a.blobs.add(profileA, "Lab 1.pdf", "application/pdf", lab1)
            a.blobs.add(profileA, "Lab 1.pdf", "application/pdf", lab1Again)
            a.blobs.add(profileB, "Note.txt", "text/plain", note)
        }
        val target = MemoryBackupTarget()

        val result = runBlocking { writer(a, target).write(BackupKind.LATEST, password) }

        assertEquals(BackupResult.Written("MedicAll-backup-latest.7z", files = 3), result)
        val archive = archiveFile(tmp.root, assertNotNull(target.read("MedicAll-backup-latest.7z")))
        val names = assertNotNull(SevenZip.list(archive, password))
        val files = mapOf(
            "files/Profile A/2026-11-20 Lab 1.pdf" to lab1,
            "files/Profile A/2026-11-20 Lab 1 (2).pdf" to lab1Again,
            "files/Profile B/2026-11-20 Note.txt" to note,
        )
        assertEquals(
            setOf("HOW-TO-OPEN.txt", "records/records.json", "records/profile.csv", "records/blob_ref.csv", "records/files.csv") + files.keys,
            names.toSet(),
        )
        for ((name, bytes) in files) assertContentEquals(bytes, SevenZip.read(archive, password, name), name)
        assertEquals(BackupContent.HOW_TO_OPEN, SevenZip.read(archive, password, "HOW-TO-OPEN.txt")!!.decodeToString())

        fun lines(name: String) = SevenZip.read(archive, password, name)!!.decodeToString().split("\r\n").filter { it.isNotEmpty() }
        assertEquals(2 + 1, lines("records/profile.csv").size)
        assertEquals(3 + 1, lines("records/blob_ref.csv").size)
        assertTrue(lines("records/profile.csv").any { "Profile B" in it })

        val records = SevenZip.read(archive, password, "records/records.json")!!.decodeToString().lines().filter { it.isNotEmpty() }
            .map { RecordCodec.decode(it) as RowRecord }.map { it.row }
        assertEquals(a.rows().values.toSet() + a.blobRefs().values.toSet(), records.toSet())
        assertEquals(listOf(PROFILE, PROFILE, BLOB_REF, BLOB_REF, BLOB_REF), records.map { it.table })
    }

    @Test
    fun namesHiddenWithoutPassword_SPEC_R22() {
        val a = device()
        val profile = a.create("Profile A")
        runBlocking { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1) }
        val target = MemoryBackupTarget()
        runBlocking { writer(a, target).write(BackupKind.LATEST, password) }
        val bytes = assertNotNull(target.read("MedicAll-backup-latest.7z"))
        val archive = archiveFile(tmp.root, bytes)

        assertNull(SevenZip.list(archive, null))
        assertNull(SevenZip.list(archive, "wrong-Passphrase-7".toCharArray()))
        assertNull(SevenZip.read(archive, "wrong-Passphrase-7".toCharArray(), "HOW-TO-OPEN.txt"))
        // 7z keeps names as UTF-16LE: neither form of any name is in the file.
        for (name in listOf("HOW-TO-OPEN.txt", "Lab 1.pdf", "Profile A")) {
            assertFalse(bytes.contains(name.toByteArray(Charsets.UTF_16LE)), name)
            assertFalse(bytes.contains(name.encodeToByteArray()), name)
        }
    }

    @Test
    fun failedWriteKeepsPreviousBackup() {
        val a = device()
        a.create("Profile A")
        val target = MemoryBackupTarget()
        val writer = writer(a, target)
        assertIs<BackupResult.Written>(runBlocking { writer.write(BackupKind.LATEST, password) })
        val previous = target.read("MedicAll-backup-latest.7z")!!

        a.create("Profile B")
        target.failWrites = true
        val failed = runBlocking { writer.write(BackupKind.LATEST, password) }

        assertIs<BackupResult.Failed>(failed)
        assertContentEquals(previous, target.read("MedicAll-backup-latest.7z"))
        target.failWrites = false
        target.isReachable = false
        assertIs<BackupResult.Failed>(runBlocking { writer.write(BackupKind.WEEKLY, password) })
        assertEquals(listOf("MedicAll-backup-latest.7z"), target.list())
        assertEquals(emptyList(), tmp.root.walk().filter { it.name.startsWith("medicall-backup-") }.toList(), "temporary files left behind")
    }

    @Test
    fun scheduleRules() {
        val s = BackupSchedule()
        val t = start
        // LATEST: an hour after a change, and at each start; WEEKLY: 7 days after the last one.
        assertEquals(setOf(BackupKind.LATEST), s.due(t + hour, lastLatest = t, lastWeekly = t, changedSince = true))
        assertEquals(emptySet(), s.due(t + hour - 1, lastLatest = t, lastWeekly = t, changedSince = true))
        assertEquals(emptySet(), s.due(t + 2 * hour, lastLatest = t, lastWeekly = t, changedSince = false))
        assertEquals(setOf(BackupKind.LATEST), s.due(t + 1, lastLatest = t, lastWeekly = t, changedSince = false, starting = true))
        assertEquals(emptySet(), s.due(t + 6 * day, lastLatest = t + 6 * day, lastWeekly = t, changedSince = false))
        assertEquals(setOf(BackupKind.WEEKLY), s.due(t + 7 * day, lastLatest = t + 7 * day, lastWeekly = t, changedSince = false))
        assertEquals(setOf(BackupKind.LATEST, BackupKind.WEEKLY), s.due(t, lastLatest = null, lastWeekly = null, changedSince = false))
    }

    @Test
    fun weeklyNameCarriesTheDate() {
        val a = device()
        a.create("Profile A")
        val target = MemoryBackupTarget()
        assertEquals(BackupResult.Written("MedicAll-backup-2026-11-20.7z", 0), runBlocking { writer(a, target).write(BackupKind.WEEKLY, password) })
    }

    @Test
    fun integrityCheckRunsFirstAndRepairedFilesAreIncluded_M2() {
        val a = device()
        val profile = a.create("Profile A")
        runBlocking {
            a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1)
            a.blobs.add(profile, "Note.txt", "text/plain", note)
        }
        val target = MemoryBackupTarget()
        assertEquals(2, (runBlocking { writer(a, target).write(BackupKind.LATEST, password) } as BackupResult.Written).files)
        val hash = a.blobStore.files.keys.first()
        val sealed = a.blobStore.files.getValue(hash)
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 0x01).toByte()

        val result = runBlocking { writer(a, target).write(BackupKind.LATEST, password) }

        assertEquals(2, (result as BackupResult.Written).files, "the damaged file was repaired from the backup first")
        assertEquals(BackupResult.Written(BackupWriter.LATEST_NAME, files = 2), result)
        assertNotNull(a.blobs.intact(hash))

        // Damaged with no backup to repair it from: left out, and said so.
        val again = a.blobStore.files.getValue(hash)
        again[again.size / 2] = (again[again.size / 2].toInt() xor 0x01).toByte()
        assertEquals(BackupResult.Written(BackupWriter.LATEST_NAME, files = 1, missing = listOf(hash)), runBlocking { writer(a, MemoryBackupTarget()).write(BackupKind.LATEST, password) })
    }

    @Test
    fun keepsPreviousLatestWhenLiveFilesAreMissing_M2() {
        val a = device()
        val profile = a.create("Profile A")
        runBlocking { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1) }
        val target = MemoryBackupTarget()
        assertIs<BackupResult.Written>(runBlocking { writer(a, target).write(BackupKind.LATEST, password) })
        val first = target.read(BackupWriter.LATEST_NAME)!!
        // A reference whose file has not arrived here: the new LATEST holds fewer files than the live references name.
        a.addBlobRef(profile)
        val absent = "ab".repeat(32)

        val result = runBlocking { writer(a, target).write(BackupKind.LATEST, password) }

        assertEquals(BackupResult.Written(BackupWriter.LATEST_NAME, files = 1, missing = listOf(absent), keptPrevious = true), result)
        assertTrue(BackupWriter.LATEST_NAME + ".prev" in target.list(), "${target.list()}")
        assertContentEquals(first, target.read(BackupWriter.LATEST_NAME + ".prev"))
        // A weekly backup is dated, the old ones stay anyway: nothing is kept beside it.
        val weekly = runBlocking { writer(a, target).write(BackupKind.WEEKLY, password) }
        assertEquals(BackupResult.Written("MedicAll-backup-2026-11-20.7z", files = 1, missing = listOf(absent), keptPrevious = false), weekly)

        // Once every live file is held again, the next LATEST drops the kept copy.
        runBlocking { a.db.blobRefDao().deleteBlobRefs(a.blobRefs().filterValues { it.groups.getValue("main").values["sha256"] == absent }.keys.toList()) }
        assertEquals(BackupResult.Written(BackupWriter.LATEST_NAME, files = 1), runBlocking { writer(a, target).write(BackupKind.LATEST, password) })
        assertFalse(BackupWriter.LATEST_NAME + ".prev" in target.list(), "${target.list()}")
    }

    @Test
    fun targetThatCorruptsBytesKeepsPreviousLatest_H2() {
        val a = device()
        a.create("Profile A")
        val folder = tmp.newFolder("backups").toPath()
        assertIs<BackupResult.Written>(runBlocking { writer(a, DirectoryBackupTarget(folder)).write(BackupKind.LATEST, password) })
        val previous = folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes()
        a.create("Profile B")

        val result = runBlocking { writer(a, DirectoryBackupTarget(folder, CorruptingDisk())).write(BackupKind.LATEST, password) }

        assertIs<BackupResult.Failed>(result)
        assertContentEquals(previous, folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes())
        assertEquals(listOf(BackupWriter.LATEST_NAME), DirectoryBackupTarget(folder).list())
    }

    /** Flips one byte of every copy it lands, as a failing card might. */
    private class CorruptingDisk : DirectoryBackupTarget.Disk() {
        override fun copy(source: File, to: Path) {
            super.copy(source, to)
            val bytes = to.toFile().readBytes()
            bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
            to.toFile().writeBytes(bytes)
        }
    }

    /** Lands the copy whole, then corrupts it once it is in place: the check after the move catches it and puts the old one back. */
    private class CorruptingAfterMoveDisk(private val name: String) : DirectoryBackupTarget.Disk() {
        override fun move(from: Path, to: Path) {
            super.move(from, to)
            if (to.fileName.toString() == name && from.fileName.toString() != BackupTarget.prev(name)) {
                val bytes = to.toFile().readBytes()
                bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
                to.toFile().writeBytes(bytes)
            }
        }
    }

    @Test
    fun badBytesAfterTheMovePutThePreviousBack_H2() {
        val a = device()
        a.create("Profile A")
        val folder = tmp.newFolder("backups").toPath()
        assertIs<BackupResult.Written>(runBlocking { writer(a, DirectoryBackupTarget(folder)).write(BackupKind.LATEST, password) })
        val previous = folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes()
        a.create("Profile B")

        val result = runBlocking { writer(a, DirectoryBackupTarget(folder, CorruptingAfterMoveDisk(BackupWriter.LATEST_NAME))).write(BackupKind.LATEST, password) }

        assertIs<BackupResult.Failed>(result)
        assertContentEquals(previous, folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes())
    }

    /** Stops the process, as a crash would, right after the old copy was moved aside: nothing that follows runs. */
    private class Crash : Error("invented crash")

    private class CrashingDisk(private val name: String) : DirectoryBackupTarget.Disk() {
        override fun move(from: Path, to: Path) {
            if (to.fileName.toString() == name) throw Crash()
            super.move(from, to)
        }
    }

    @Test
    fun interruptedMoveLeavesTheOldCopyOrTheNew_H2() {
        val a = device()
        a.create("Profile A")
        val folder = tmp.newFolder("backups").toPath()
        assertIs<BackupResult.Written>(runBlocking { writer(a, DirectoryBackupTarget(folder)).write(BackupKind.LATEST, password) })
        val previous = folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes()
        a.create("Profile B")

        assertFailsWith<Crash> { runBlocking { writer(a, DirectoryBackupTarget(folder, CrashingDisk(BackupWriter.LATEST_NAME))).write(BackupKind.LATEST, password) } }

        // The old copy is there under its .prev name, which the integrity check and a restore look in.
        val target = DirectoryBackupTarget(folder)
        assertEquals(listOf(BackupTarget.prev(BackupWriter.LATEST_NAME)), target.list())
        assertEquals(listOf(BackupTarget.prev(BackupWriter.LATEST_NAME)), BackupWriter.newestFirst(target.list()))
        assertContentEquals(previous, target.openRead(BackupTarget.prev(BackupWriter.LATEST_NAME))!!.use { it.readBytes() })

        // The next write lands the new one and drops the old.
        assertIs<BackupResult.Written>(runBlocking { writer(a, target).write(BackupKind.LATEST, password) })
        assertEquals(listOf(BackupWriter.LATEST_NAME), target.list())
    }

    @Test
    fun ioFailureOnTheMovePutsThePreviousBack_H2() {
        val a = device()
        a.create("Profile A")
        val folder = tmp.newFolder("backups").toPath()
        assertIs<BackupResult.Written>(runBlocking { writer(a, DirectoryBackupTarget(folder)).write(BackupKind.LATEST, password) })
        val previous = folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes()
        val failing = object : DirectoryBackupTarget.Disk() {
            override fun move(from: Path, to: Path) {
                if (to.fileName.toString() == BackupWriter.LATEST_NAME && from.fileName.toString() != BackupTarget.prev(BackupWriter.LATEST_NAME)) {
                    throw IOException("invented failure")
                }
                super.move(from, to)
            }
        }

        assertIs<BackupResult.Failed>(runBlocking { writer(a, DirectoryBackupTarget(folder, failing)).write(BackupKind.LATEST, password) })
        assertContentEquals(previous, folder.resolve(BackupWriter.LATEST_NAME).toFile().readBytes())
        assertEquals(listOf(BackupWriter.LATEST_NAME), DirectoryBackupTarget(folder).list())
    }

    /**
     * A backup of 300 small files, written and read back (M4). The writer derives the key once; the
     * read-back, through Commons Compress's own reader, derives it once per entry and once for the
     * header, a library limit. So it must take less than 400 derivations and 3 s more, measured on the
     * machine running the test: about 12.9 s on the desktop, where one derivation is some 38 ms. Before
     * the fix, writing derived it once per entry too: 23.2 s, past the limit.
     */
    @Test
    fun threeHundredFilesBackUpInTime_M4() {
        val a = device()
        val profile = a.create("Profile A")
        runBlocking { repeat(300) { a.blobs.add(profile, "Lab $it.pdf", "application/pdf", "Invented result $it".encodeToByteArray()) } }
        val derivation = keyDerivationMillis(tmp.root, password)
        val started = System.nanoTime()
        val result = runBlocking { writer(a, MemoryBackupTarget()).write(BackupKind.LATEST, password) }
        val ms = (System.nanoTime() - started) / 1_000_000
        val limit = 400 * derivation + 3_000
        println("backup with 300 files written and read back in $ms ms; one key derivation $derivation ms; limit $limit ms")
        assertEquals(300, (result as BackupResult.Written).files)
        assertTrue(ms < limit, "the backup took $ms ms, more than $limit")
    }

    @Test
    fun namesAreSafeOnWindows_L7() {
        val a = device()
        val smith = a.create("Dr. Smith")
        val smith2 = a.create("Dr. Smith")
        val con = a.create("CON")
        val nul = a.create("nul.txt")
        runBlocking {
            a.blobs.add(smith, "Lab 1.pdf", "application/pdf", lab1)
            a.blobs.add(smith, "Lab 1.pdf", "application/pdf", lab1Again)
            a.blobs.add(smith2, "Lab 1.pdf", "application/pdf", lab1)
            a.blobs.add(con, "Note", "text/plain", note)
            a.blobs.add(nul, "Note", "text/plain", note)
        }
        val target = MemoryBackupTarget()
        assertIs<BackupResult.Written>(runBlocking { writer(a, target).write(BackupKind.LATEST, password) })
        val names = SevenZip.list(archiveFile(tmp.root, target.read(BackupWriter.LATEST_NAME)!!), password)!!.toSet()

        assertTrue("files/Dr. Smith/2026-11-20 Lab 1.pdf" in names, "$names")
        assertTrue("files/Dr. Smith/2026-11-20 Lab 1 (2).pdf" in names, "$names")
        assertTrue("files/Dr. Smith (2)/2026-11-20 Lab 1.pdf" in names, "$names")
        assertTrue("files/_CON/2026-11-20 Note" in names, "$names")
        assertTrue("files/_nul.txt/2026-11-20 Note" in names, "$names")
        val csv = SevenZip.read(archiveFile(tmp.root, target.read(BackupWriter.LATEST_NAME)!!), password, "records/profile.csv")!!.decodeToString()
        assertTrue("Dr. Smith" in csv)

        assertEquals("_CON", BackupContent.safe("CON", "Profile"))
        assertEquals("_nul.txt", BackupContent.safe("nul.txt", "Profile"))
        assertEquals("_COM1.log", BackupContent.safe("COM1.log", "Profile"))
        assertEquals("_lpt9", BackupContent.safe("lpt9 ", "Profile"))
        assertEquals("_Aux .tar.gz", BackupContent.safe("Aux .tar.gz", "Profile"))
        assertEquals("COM10", BackupContent.safe("COM10", "Profile"))
        assertEquals("CONSOLE", BackupContent.safe("CONSOLE", "Profile"))
        assertEquals("Lab", BackupContent.safe("Lab. . ", "Profile"))
        assertEquals("Profile", BackupContent.safe(" . ", "Profile"))
    }

    @Test
    fun contentOpensEachFileOnce_L6() {
        val a = device()
        val profile = a.create("Profile A")
        runBlocking {
            a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1)
            a.blobs.add(profile, "Note.txt", "text/plain", note)
        }
        a.blobStore.reads.clear()

        val content = runBlocking { BackupContent.of(a.db, a.blobs, ZoneOffset.UTC) }
        content.entries.forEach { e -> e.open().use { it.readBytes() } }

        assertEquals(a.blobStore.files.keys.associateWith { 1 }, a.blobStore.reads.toMap())
    }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { i -> part.indices.all { this[i + it] == part[it] } }
}
