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
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { i -> part.indices.all { this[i + it] == part[it] } }
}
