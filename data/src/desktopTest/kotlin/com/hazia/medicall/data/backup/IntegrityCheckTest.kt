package com.hazia.medicall.data.backup

import com.hazia.medicall.data.sync.World
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Every file's hash is checked, and a damaged one repaired from the newest backup holding it intact (SPEC:R23). */
class IntegrityCheckTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()
    @After fun close() = worlds.forEach { it.close() }

    private val password = "invented-Passphrase-7".toCharArray()
    private val day = 86_400_000L
    private val start = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli()
    private val lab1 = "Lab 1 result, invented for a test: 42 units".encodeToByteArray()
    private val note = "An invented note".encodeToByteArray()

    private class Setup(val a: World.Device, val lab: String, val hash: String, val noteHash: String)

    private fun setup(): Setup {
        val w = World(tmp.newFolder(), 1).also { worlds += it }
        w.syncthing.now = start
        val a = w.Device("A")
        val profile = a.create("Profile A")
        val lab = runBlocking { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1) }
        runBlocking { a.blobs.add(profile, "Note.txt", "text/plain", note) }
        val hash = a.blobRefs().getValue(lab).groups.getValue("main").values["sha256"] as String
        return Setup(a, lab, hash, a.blobStore.files.keys.single { it != hash })
    }

    private fun backup(d: World.Device, target: BackupTarget, kind: BackupKind, at: Long) {
        val result = runBlocking { BackupWriter(d.db, d.blobs, SevenZip, target, wallMillis = { at }, zone = ZoneOffset.UTC, workDir = tmp.newFolder()).write(kind, password) }
        assertIs<BackupResult.Written>(result)
    }

    private fun check(d: World.Device, target: BackupTarget) = runBlocking { IntegrityCheck(d.blobs, target, SevenZip, workDir = tmp.newFolder()).run(password) }

    /** Flips one byte of the stored copy, which is sealed text: it no longer opens, or opens to other bytes. */
    private fun World.Device.alter(hash: String) {
        val bytes = blobStore.files.getValue(hash)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
    }

    @Test
    fun alteredByteIsFoundAndRepaired_SPEC_R23() {
        val s = setup()
        val target = MemoryBackupTarget()
        backup(s.a, target, BackupKind.LATEST, start)
        s.a.alter(s.hash)

        val report = check(s.a, target)

        assertEquals(IntegrityReport(checked = 2, damaged = listOf(s.hash), repaired = listOf(s.hash), unrepaired = emptyList()), report)
        assertContentEquals(lab1, runBlocking { s.a.blobs.read(s.lab) })
        assertEquals(IntegrityReport(2, emptyList(), emptyList(), emptyList()), check(s.a, target))
    }

    @Test
    fun missingFileIsRepairedToo_SPEC_R23() {
        val s = setup()
        val target = MemoryBackupTarget()
        backup(s.a, target, BackupKind.LATEST, start)
        s.a.blobStore.files.remove(s.hash)

        assertEquals(IntegrityReport(2, listOf(s.hash), listOf(s.hash), emptyList()), check(s.a, target))
        assertContentEquals(lab1, runBlocking { s.a.blobs.read(s.lab) })
    }

    @Test
    fun skipsBackupWithDamagedCopy() {
        val s = setup()
        val target = MemoryBackupTarget()
        backup(s.a, target, BackupKind.WEEKLY, start - 7 * day)
        backup(s.a, target, BackupKind.WEEKLY, start - 14 * day)
        backup(s.a, target, BackupKind.LATEST, start)
        // The newest backup's copy of the file holds other bytes; the archive itself still opens.
        alterEntry(target, BackupWriter.LATEST_NAME, "files/Profile A/2026-11-20 Lab 1.pdf")
        s.a.alter(s.hash)

        val report = check(s.a, target)

        assertEquals(IntegrityReport(2, listOf(s.hash), listOf(s.hash), emptyList()), report)
        assertContentEquals(lab1, runBlocking { s.a.blobs.read(s.lab) })
        assertEquals(listOf(BackupWriter.LATEST_NAME, "MedicAll-backup-2026-11-13.7z", "MedicAll-backup-2026-11-06.7z"), BackupWriter.newestFirst(target.list()))
    }

    @Test
    fun unrepairableIsReported() {
        val s = setup()
        val target = MemoryBackupTarget()
        s.a.alter(s.hash)
        s.a.alter(s.noteHash)
        val both = listOf(s.hash, s.noteHash).sorted()

        // No backup in the folder holds them.
        assertEquals(IntegrityReport(2, both, emptyList(), both), check(s.a, target))

        // A backup that does, in a folder that cannot be reached.
        val unreachable = MemoryBackupTarget()
        val good = setup()
        backup(good.a, unreachable, BackupKind.LATEST, start)
        unreachable.isReachable = false
        good.a.alter(good.hash)
        assertEquals(listOf(good.hash), check(good.a, unreachable).unrepaired)
    }

    @Test
    fun fileNotArrivedYetIsNotDamaged_M3() {
        val s = setup()
        val profile = s.a.rows().keys.single()
        // A reference from another device whose file has not reached this one.
        s.a.addBlobRef(profile)

        val report = check(s.a, MemoryBackupTarget())

        assertEquals(emptyList(), report.damaged)
        assertEquals(emptyList(), report.unrepaired)
        assertEquals(IntegrityReport(checked = 2, damaged = emptyList(), repaired = emptyList(), unrepaired = emptyList(), notYetArrived = listOf("ab".repeat(32))), report)

        // A file this device held, then lost, is damaged, not on its way.
        s.a.blobStore.files.remove(s.noteHash)
        val lost = check(s.a, MemoryBackupTarget())
        assertEquals(listOf(s.noteHash), lost.damaged)
        assertEquals(listOf(s.noteHash), lost.unrepaired)
        assertEquals(listOf("ab".repeat(32)), lost.notYetArrived)
    }

    @Test
    fun malformedHashIsPassedOver_L4() {
        val s = setup()
        val ref = s.a.db.blobRefDao().let { dao -> runBlocking { dao.allBlobRefs() } }.first()
        runBlocking { s.a.db.blobRefDao().putBlobRefs(listOf(ref.copy(id = "malformed", sha256 = "not-a-hash"))) }

        val report = check(s.a, MemoryBackupTarget())

        assertEquals(IntegrityReport(2, emptyList(), emptyList(), emptyList()), report)
        val written = runBlocking { BackupWriter(s.a.db, s.a.blobs, SevenZip, MemoryBackupTarget(), wallMillis = { start }, zone = ZoneOffset.UTC, workDir = tmp.newFolder()).write(BackupKind.LATEST, password) }
        assertEquals(2, (written as BackupResult.Written).files)
    }

    /** Rewrites [name] in [target] with [entry]'s bytes changed, every other entry as it was. */
    private fun alterEntry(target: MemoryBackupTarget, name: String, entry: String) {
        val source = archiveFile(tmp.root, target.read(name)!!)
        val entries = mutableListOf<ArchiveEntry>()
        check(SevenZip.readEach(source, password) { e ->
            val b = if (e.name == entry) "Other invented bytes".encodeToByteArray() else e.bytesOrNull()!!
            entries += ArchiveEntry(e.name, b.size.toLong()) { b.inputStream() }
        })
        check(entries.any { it.name == entry }) { "no entry $entry" }
        val out = File(tmp.root, "altered.7z")
        SevenZip.write(out, password, entries)
        target.files[name] = out.readBytes()
    }
}
