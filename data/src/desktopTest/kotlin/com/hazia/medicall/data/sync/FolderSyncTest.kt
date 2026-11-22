package com.hazia.medicall.data.sync

import androidx.sqlite.SQLiteException
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.core.sync.Stamp
import com.hazia.medicall.data.DETAILS
import com.hazia.medicall.data.NAME
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.REMOVED
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Factotum's ADR 13 cases (`decisions/cases/13-folder.jsonl`) through the real importer, exporter
 * and profile table (SPEC:R16), every line sealed with the shared test key.
 */
class FolderSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()

    private fun world(seed: Int = 1, segmentBytes: Int = 16 * 1024, snapshotEvery: Int = 64) =
        World(tmp.root, seed, segmentBytes, snapshotEvery).also { worlds += it }

    @After fun close() = worlds.forEach { it.close() }

    private val hour = 3_600_000L

    /**
     * [days] of a phone and a laptop editing while online and offline: every [stepHours] a device is
     * online with some chance, and two online devices meet in a session that is cut 5% of the time.
     */
    private fun ordinaryUse(w: World, days: Int, editsPerStep: Int, random: Random, stepHours: Int = 1): List<World.Device> {
        val devices = listOf(w.Device("phone"), w.Device("laptop"))
        for (step in 0 until days * 24 / stepHours) {
            w.syncthing.now = step * stepHours * hour
            val online = devices.filter { random.nextDouble() < 0.6 }
            for (d in devices) repeat(random.nextInt(editsPerStep + 1)) {
                val rows = d.rows().keys.toList()
                if (rows.isEmpty() || random.nextDouble() < 0.1) d.create() else d.edit(rows.random(random), GROUPS.random(random))
            }
            online.forEach { it.import() }
            online.forEach { it.export() }
            if (online.size == 2) w.syncthing.session("phone", "laptop", cut = random.nextDouble() < 0.05)
        }
        return devices
    }

    @Test
    fun stNoConflictCopies_aMonthOfOrdinaryUseMakesNone() {
        val w = world()
        val devices = ordinaryUse(w, days = 30, editsPerStep = 2, Random(7))
        w.settle(devices)

        assertEquals(0, w.syncthing.conflictCopies)
    }

    @Test
    fun ffNothingLost_cutSessionsThenSettlingLeaveEveryDeviceWithTheJoin() {
        val w = world()
        val devices = ordinaryUse(w, days = 30, editsPerStep = 3, Random(11))
        w.settle(devices)

        w.assertJoined(devices)
        assertTrue(w.truth.size > 100, "the month wrote too little to test: ${w.truth.size}")
    }

    @Test
    fun stRetiredLoser_aWipedDevicesChangeSurvives() {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val row = a.create()
        a.export()
        w.syncthing.session("A", "B")
        b.import()

        w.syncthing.now = 10
        b.edit(row, DETAILS)
        w.syncthing.now = 20
        a.edit(row, NAME)
        b.export()
        a.export()
        w.syncthing.session("A", "B")
        w.syncthing.forget("B")
        val c = w.Device("C")
        w.settle(listOf(a, c))

        w.assertJoined(listOf(a, c))
    }

    @Test
    fun aVersionOutlivesTheLossOfItsAuthorsFiles() {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val row = b.create()
        b.edit(row, NAME)
        b.export()
        w.syncthing.session("A", "B")
        a.import()
        a.export()

        // B is wiped, and its files are lost from the folder too: only A's copy of B's versions is left.
        w.syncthing.forget("B")
        val folder = w.syncthing.folder("A")
        folder.list(FolderLayout.dir("B")).forEach { folder.delete("${FolderLayout.dir("B")}/$it") }
        val c = w.Device("C")
        w.settle(listOf(a, c))

        w.assertJoined(listOf(a, c))
    }

    @Test
    fun androidFiles1y_aYearKeepsEachDeviceToItsSegmentsAndOneSnapshot() {
        val w = world()
        // Steps of 4 hours: the file count follows how much is exported, not how often devices meet.
        val devices = ordinaryUse(w, days = 365, editsPerStep = 4, Random(3), stepHours = 4)
        w.settle(devices)

        val files = devices.flatMap { w.syncthing.paths(it.name) }.toSet()
        assertTrue(files.size < 10_000)
        for (d in devices) {
            val own = files.filter { it.startsWith("devices/${d.id}/") }
            assertTrue(own.size <= 64 + 1, "${d.id} holds ${own.size} files")
            assertTrue(own.any { "/snapshot-" in it }, "a year should have compacted ${d.id}'s log at least once")
        }
        w.assertJoined(devices)
    }

    @Test
    fun aClonedDeviceIdsClashIsMergedAndTheCopyRemoved() {
        val w = world()
        val a = w.Device("A")
        val clone = w.Device("A'", id = "A")
        val mine = a.create()
        w.syncthing.now = 5
        val theirs = clone.create()
        a.export()
        clone.export()
        w.syncthing.session("A", "A'")
        assertEquals(1, w.syncthing.conflictCopies)

        a.import()
        w.syncthing.forget("A'")

        assertNotNull(a.rows()[mine])
        assertNotNull(a.rows()[theirs])
        assertTrue(w.syncthing.paths("A").none { ".sync-conflict-" in it })
    }

    @Test
    fun aPurgeReachesAPeerAndOutlivesCompaction() {
        // Segments of one line, compacted every two, so three edits compact.
        val w = world(segmentBytes = 200, snapshotEvery = 2)
        val a = w.Device("A")
        val b = w.Device("B")
        val gone = a.create()
        val kept = a.create()
        a.export()
        w.syncthing.session("A", "B")
        b.import()
        a.purge(gone)
        repeat(3) { a.edit(kept, NAME); a.export() }

        val late = w.Device("C")
        w.settle(listOf(a, b, late))

        for (d in listOf(a, b, late)) {
            assertEquals(setOf(kept), d.rows().keys, d.name)
            assertEquals(listOf(gone), runBlocking { d.db.syncDao().purges() }.map { it.id }, d.name)
        }
        assertTrue(w.syncthing.paths("A").any { "/snapshot-" in it })
    }

    @Test
    fun aPurgeReachesAPeerThroughTheLog() {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val gone = a.create()
        a.export()
        w.syncthing.session("A", "B")
        b.import()

        a.purge(gone)
        a.export()
        w.syncthing.session("A", "B")
        // The row is live on B when the purge arrives: the import applies it all the same.
        assertEquals(setOf(gone), b.rows().keys)
        assertNull(b.rows().getValue(gone).groups.getValue(REMOVED).values["removed_at"])
        b.import()

        assertTrue(b.rows().isEmpty())
        assertTrue(w.syncthing.paths("A").none { "/snapshot-" in it })
    }

    @Test
    fun aPeerFileIsReadOnlyWhenItHasGrown() {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        a.create()
        a.export()
        w.syncthing.session("A", "B")
        b.import()
        val before = w.syncthing.bytesRead

        b.import()

        assertEquals(before, w.syncthing.bytesRead)
    }

    @Test
    fun aBigFileIsMergedInChunksEachWithItsReadPosition() {
        val w = world()
        val lines = (0 until 2_500).map { w.sealed(line("r$it")) }
        w.syncthing.folder("B").replace(FolderLayout.segment("A", 0), lines.joinToString("").encodeToByteArray())
        val memory = MemoryTable()
        val b = w.Device("B", extra = mapOf(PROFILE to memory))

        memory.savesLeft = 1
        assertFailsWith<IllegalStateException> { b.import() }
        assertEquals(2_000, memory.rows.size)
        val firstChunk = lines.take(2_000).sumOf { it.encodeToByteArray().size }.toLong()
        assertEquals(firstChunk, runBlocking { b.db.syncDao().reads() }.single().readBytes)

        memory.savesLeft = Int.MAX_VALUE
        b.import()
        assertEquals(2_500, memory.rows.size)
    }

    @Test
    fun aLineStillBeingWrittenWaitsForItsNewline() {
        val w = world()
        val b = w.Device("B", extra = mapOf(PROFILE to MemoryTable()))
        val path = FolderLayout.segment("A", 0)
        val folder = w.syncthing.folder("B")

        folder.replace(path, w.sealed(line("x")).removeSuffix("\n").encodeToByteArray())
        b.import()
        assertTrue(b.rows().isEmpty())

        folder.replace(path, w.sealed(line("x")).encodeToByteArray())
        b.import()
        assertNotNull(b.rows()["x"])
    }

    @Test
    fun aLineForATableThisVersionLacksIsReadAgainOnceItHasIt() {
        val w = world()
        val node = Row("future_table", "n", mapOf("a" to Group(Stamp(1, "A"), mapOf("title" to "notes"))))
        val lines = w.sealed(RecordCodec.encode(RowRecord(node))) + w.sealed("{not json")
        w.syncthing.folder("old").replace(FolderLayout.segment("A", 0), lines.encodeToByteArray())

        val old = w.Device("old")
        assertEquals(ImportReport(changed = 0, skipped = 2), old.import())
        old.db.close()

        val upgraded = w.Device("old", extra = mapOf("future_table" to MemoryTable()))
        assertEquals(ImportReport(changed = 1, skipped = 1), upgraded.import())
    }

    @Test
    fun aRowThatDoesNotFitItsTableIsSkipped() {
        val w = world()
        w.syncthing.folder("B").replace(FolderLayout.segment("A", 0), w.sealed(line("x")).encodeToByteArray())
        val b = w.Device("B")

        assertEquals(ImportReport(changed = 0, skipped = 1), b.import())
        assertTrue(b.rows().isEmpty())
    }

    /** Factotum's `aSubtaskThatArrivesBeforeItsParentWaitsForIt`, with a file reference and its profile. */
    @Test
    fun aFileReferenceThatArrivesBeforeItsProfileWaitsForIt_SPEC_R15() {
        val w = world()
        val a = w.Device("A")
        val profile = a.create()
        val ref = a.addBlobRef(profile)
        a.export()
        val lines = w.plainLines("A", FolderLayout.segment("A", 0))
        val (profileLine, refLine) = listOf(profile, ref).map { id -> lines.single { "\"id\":\"$id\"" in it } }

        // The reference reaches B in one device's file before the profile arrives in another's.
        val b = w.Device("B")
        val folder = w.syncthing.folder("B")
        folder.replace(FolderLayout.segment("X", 0), w.sealed(refLine).encodeToByteArray())
        b.import()
        assertEquals(emptySet(), b.blobRefs().keys)
        assertEquals(1, runBlocking { b.db.syncDao().waiting() }.size)

        folder.replace(FolderLayout.segment("Y", 0), w.sealed(profileLine).encodeToByteArray())
        b.import()
        assertEquals(setOf(profile), b.rows().keys)
        assertEquals(setOf(ref), b.blobRefs().keys)
        assertEquals(emptyList(), runBlocking { b.db.syncDao().waiting() })
    }

    /** Factotum's `aSubtaskWhoseParentWasPurgedIsDroppedNotKeptWaiting`. */
    @Test
    fun aFileReferenceWhoseProfileWasPurgedIsDroppedNotKeptWaiting() {
        val w = world()
        val a = w.Device("A")
        val profile = a.create()
        val ref = a.addBlobRef(profile)
        a.export()
        val refLine = w.plainLines("A", FolderLayout.segment("A", 0)).single { "\"id\":\"$ref\"" in it }
        a.purge(profile)
        a.export()
        val b = w.Device("B")
        w.syncthing.session("A", "B")
        b.import()

        w.syncthing.folder("B").replace(FolderLayout.segment("X", 0), w.sealed(refLine).encodeToByteArray())

        assertEquals(ImportReport(changed = 0, skipped = 1), b.import())
        assertEquals(emptyList(), runBlocking { b.db.syncDao().waiting() })
    }

    /** Factotum's `aLineWaitingForItsParentIsKeptOnceThoughTwoFilesHoldIt`. */
    @Test
    fun aLineWaitingForItsProfileIsKeptOnceThoughTwoFilesHoldIt() {
        val w = world()
        val a = w.Device("A")
        val profile = a.create()
        val ref = a.addBlobRef(profile)
        a.export()
        val refLine = w.plainLines("A", FolderLayout.segment("A", 0)).single { "\"id\":\"$ref\"" in it }
        val b = w.Device("B")
        val folder = w.syncthing.folder("B")

        folder.replace(FolderLayout.segment("X", 0), w.sealed(refLine).encodeToByteArray())
        folder.replace(FolderLayout.segment("Y", 0), w.sealed(refLine).encodeToByteArray())
        b.import()

        assertEquals(1, runBlocking { b.db.syncDao().waiting() }.size)
    }

    /** An undone purge reads every file again; the second pass's skipped and waiting lines are not added to the first's. */
    @Test
    fun anUndonePurgeCountsEachSkippedLineOnce() {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val row = a.create()
        a.export()
        w.syncthing.session("A", "B")
        b.import()

        w.syncthing.now = 10
        a.purge(row)
        a.export()
        // B edits the row after the purge, so B takes the purge back when it arrives (ADR 01).
        w.syncthing.now = 20
        b.edit(row, NAME)
        w.syncthing.session("A", "B")
        w.syncthing.folder("B").replace(FolderLayout.segment("X", 0), w.sealed("{not json").encodeToByteArray())

        val report = b.import()

        assertNotNull(b.rows()[row], "the edit after the purge keeps the row")
        assertEquals(1, report.skipped, "one unreadable line in the folder")
        assertEquals(0, report.waiting)
    }

    /** The constraint backstop: a chunk the database refuses is applied line by line, and the refused line is dropped. */
    @Test
    fun aLineAConstraintRefusesIsDroppedAndTheOthersApply() {
        val w = world()
        val table = RefusingTable(refused = "y")
        val b = w.Device("B", extra = mapOf(PROFILE to table))
        w.syncthing.folder("B").replace(FolderLayout.segment("A", 0), (w.sealed(line("x")) + w.sealed(line("y"))).encodeToByteArray())

        assertEquals(ImportReport(changed = 1, skipped = 1), b.import())
        assertEquals(setOf("x"), table.rows.keys)
        assertEquals(emptyList(), runBlocking { b.db.syncDao().waiting() }, "a refused line with no missing parent does not wait")
    }

    /** A table that refuses to save the row [refused] as a foreign key would, and saves nothing of that call. */
    private class RefusingTable(private val refused: String) : RowTable {
        val rows = mutableMapOf<String, Row>()
        override suspend fun load(ids: Collection<String>) = ids.mapNotNull { rows[it] }
        override suspend fun all() = rows.values.toList()
        override suspend fun save(rows: Collection<Row>) {
            if (rows.any { it.id == refused }) throw SQLiteException("Error code: 787, message: FOREIGN KEY constraint failed")
            rows.forEach { this.rows[it.id] = it }
        }
        override suspend fun delete(ids: Collection<String>) = ids.forEach { rows.remove(it) }
        override fun fits(row: Row) = true
        override fun parents(row: Row) = emptyList<Pair<String, String>>()
    }

    /** A record of a row in a table only [MemoryTable] would take: one group, no profile fields. */
    private fun line(id: String) =
        RecordCodec.encode(RowRecord(Row(PROFILE, id, mapOf("a" to Group(Stamp(1, "A"), mapOf("v" to id))))))
}
