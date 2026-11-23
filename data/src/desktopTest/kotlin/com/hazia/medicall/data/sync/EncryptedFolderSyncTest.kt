package com.hazia.medicall.data.sync

import com.hazia.medicall.data.DETAILS
import com.hazia.medicall.data.NAME
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Folder sync with every line sealed (SPEC:R16): convergence, no plain text, tampering and clocks set ahead. */
class EncryptedFolderSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()

    private fun world(segmentBytes: Int = 16 * 1024, snapshotEvery: Int = 64) =
        World(tmp.newFolder(), seed = 1, segmentBytes, snapshotEvery).also { worlds += it }

    @After fun close() = worlds.forEach { it.close() }

    private val day = 86_400_000L

    private fun World.Device.profileName(id: String) = rows().getValue(id).groups.getValue(NAME).values["name"]
    private fun World.Device.kind(id: String) = rows().getValue(id).groups.getValue(DETAILS).values["kind"]

    /** A and B both hold [World.Device.create]'s profile, made on A; returns it. */
    private fun shared(w: World, a: World.Device, b: World.Device): String {
        val id = a.create("Profile A")
        a.export()
        w.syncthing.session("A", "B")
        b.import()
        assertNotNull(b.rows()[id])
        return id
    }

    @Test
    fun twoDevicesConverge_SPEC_R16() {
        for (aFirst in listOf(true, false)) {
            val w = world()
            val a = w.Device("A")
            val b = w.Device("B")
            val id = shared(w, a, b)

            // Offline: A renames the profile, B changes its kind; different groups.
            w.syncthing.now = 10
            a.edit(id, NAME, "Profile A renamed")
            w.syncthing.now = 20
            b.edit(id, DETAILS, "CHILD")
            a.export()
            b.export()
            w.syncthing.session("A", "B")
            (if (aFirst) listOf(a, b) else listOf(b, a)).forEach { it.import() }

            for (d in listOf(a, b)) {
                assertEquals("Profile A renamed", d.profileName(id), "${d.name}, A first: $aFirst")
                assertEquals("CHILD", d.kind(id), "${d.name}, A first: $aFirst")
            }
        }
    }

    @Test
    fun laterEditWins_SPEC_R16() {
        for (aFirst in listOf(true, false)) {
            val w = world()
            val a = w.Device("A")
            val b = w.Device("B")
            val id = shared(w, a, b)

            w.syncthing.now = 20
            b.edit(id, NAME, "Profile B's name")
            w.syncthing.now = 10
            a.edit(id, NAME, "Profile A's name")
            a.export()
            b.export()
            w.syncthing.session("A", "B")
            (if (aFirst) listOf(a, b) else listOf(b, a)).forEach { it.import() }

            for (d in listOf(a, b)) assertEquals("Profile B's name", d.profileName(id), "${d.name}, A first: $aFirst")
        }
    }

    @Test
    fun folderHoldsNoPlaintext() {
        // Small segments and early compaction, so the folder holds log segments and a snapshot.
        val w = world(segmentBytes = 200, snapshotEvery = 2)
        val a = w.Device("A")
        val id = a.create("Profile A")
        repeat(3) { a.edit(id, DETAILS, "ADULT$it"); a.export() }

        val paths = w.syncthing.paths("A").filter { it.startsWith("${FolderLayout.DEVICES}/") }
        assertTrue(paths.any { "/snapshot-" in it }, "no snapshot to check: $paths")
        val needle = "Profile A".encodeToByteArray()
        for (path in paths) {
            val bytes = w.syncthing.folder("A").read(path)!!
            assertFalse(bytes.contains(needle), "$path holds the profile's name in plain text")
        }
        // The control: the same lines, opened, do hold it.
        assertTrue(paths.any { p -> w.plainLines("A", p).any { "Profile A" in it } })
    }

    @Test
    fun tamperedLineIsSkipped() {
        val w = world()
        val a = w.Device("A")
        val altered = a.create("Profile A")
        val intact = a.create("Profile B")
        val b = w.Device("B")
        a.export()
        w.syncthing.session("A", "B")

        // The first line, A's first profile, has one character of its sealed text changed in B's copy.
        val path = FolderLayout.segment("A", 0)
        val folder = w.syncthing.folder("B")
        val lines = folder.read(path)!!.decodeToString().lines().filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        val first = lines.first()
        val at = first.length / 2
        val tampered = first.substring(0, at) + (if (first[at] == 'A') 'B' else 'A') + first.substring(at + 1)
        folder.replace(path, (listOf(tampered) + lines.drop(1)).joinToString("") { "$it\n" }.encodeToByteArray())

        val report = b.import()

        assertEquals(1, report.skipped)
        assertEquals(1, report.changed)
        assertEquals(setOf(intact), b.rows().keys)
        assertFalse(altered in b.rows())
    }

    @Test
    fun futureLineWaitsUntilPlausible() {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        w.syncthing.now = 3 * day
        b.skew = -2 * day
        val id = a.create("Profile A")
        val stamp = a.rows().getValue(id).newest
        a.export()
        w.syncthing.session("A", "B")

        val early = b.import()
        assertEquals(ImportReport(changed = 0, skipped = 0, waiting = 1), early)
        assertTrue(b.rows().isEmpty())
        assertTrue(b.clock.last < stamp.hlc, "a line set aside must not move this device's clock")

        b.skew = 0
        val later = b.import()
        assertEquals(ImportReport(changed = 1, skipped = 0, waiting = 0), later)
        assertEquals("Profile A", b.profileName(id))
    }

    @Test
    fun conflictCopyIsMergedThenDeleted() {
        val w = world()
        val a = w.Device("A")
        val clone = w.Device("A'", id = "A")
        val mine = a.create("Profile A")
        w.syncthing.now = 5
        val theirs = clone.create("Profile B")
        a.export()
        clone.export()
        w.syncthing.session("A", "A'")
        assertEquals(1, w.syncthing.conflictCopies)
        val copy = w.syncthing.paths("A").single { ".sync-conflict-" in it }
        assertFalse(w.syncthing.folder("A").read(copy)!!.contains("Profile".encodeToByteArray()), "the conflict copy is sealed too")

        a.import()
        w.syncthing.forget("A'")

        assertEquals("Profile A", a.profileName(mine))
        assertEquals("Profile B", a.profileName(theirs))
        assertTrue(w.syncthing.paths("A").none { ".sync-conflict-" in it })
    }

    // SPEC:R16 a line sealed under a key this device does not hold yet waits for the key, as a line stamped ahead waits.
    @Test
    fun aLineUnderAKeyNotHeldYetIsReadOnceTheKeyArrives_SPEC_R16() {
        val w = world()
        val a = w.Device("A", cipher = twoKeyCipher("k2"))
        val d = w.Device("D")
        val id = a.create("Profile A")
        a.export()
        w.syncthing.session("A", "D")

        val early = d.import()
        assertTrue(d.rows().isEmpty(), "D holds only k1")
        d.db.close()

        // The same database, now with a FolderSync that holds k2 as well.
        val later = w.Device("D", cipher = twoKeyCipher("k1"))
        later.import()
        assertNotNull(later.rows()[id], "the line under k2 is applied once the key is here")
        assertEquals("Profile A", later.profileName(id))
        assertEquals(ImportReport(changed = 0, skipped = 0, waiting = 1), early, "held, not skipped")
    }

    // SPEC:R16 a conflict copy is merged, then deleted, but not while it holds a line no key here opens yet.
    @Test
    fun aConflictCopyHoldingALineUnderAnUnknownKeyIsKept_SPEC_R16() {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        a.export()
        val record = w.plainLines("A", FolderLayout.segment("A", 0)).single()
        val d = w.Device("D")
        val copy = "${FolderLayout.dir("X")}/log-000000.sync-conflict-5-X.jsonl"
        w.syncthing.folder("D").replace(copy, (twoKeyCipher("k2").seal(record.encodeToByteArray()) + "\n").encodeToByteArray())

        d.import()
        assertTrue(copy in w.syncthing.paths("D"), "the copy holds a line no key on D opens yet")
        d.db.close()

        val later = w.Device("D", cipher = twoKeyCipher("k1"))
        later.import()
        assertNotNull(later.rows()[id])
        assertFalse(copy in w.syncthing.paths("D"), "applied, the copy goes")
    }

    @Test
    fun aPlausibleLineAfterAHeldOneAppliesOnTheSameImport() {
        val w = world()
        val a = w.Device("A")
        val plausible = a.create("Profile A")
        w.syncthing.now = 3 * day
        val ahead = a.create("Profile B")
        a.export()
        val lines = w.plainLines("A", FolderLayout.segment("A", 0))
        val (p, q) = listOf(plausible, ahead).map { id -> lines.single { "\"id\":\"$id\"" in it } }
        val b = w.Device("B")
        b.skew = -2 * day
        // The line stamped ahead comes first in the file.
        w.syncthing.folder("B").replace(FolderLayout.segment("X", 0), (w.sealed(q) + w.sealed(p)).encodeToByteArray())

        assertEquals(ImportReport(changed = 1, skipped = 0, waiting = 1), b.import())
        assertEquals(setOf(plausible), b.rows().keys)

        b.skew = 0
        assertEquals(ImportReport(changed = 1, skipped = 0, waiting = 0), b.import())
        assertEquals(setOf(plausible, ahead), b.rows().keys)
    }

    @Test
    fun aHeldLineSurvivesTheWritersCompaction() {
        // Small segments and early compaction, so A's next exports replace the segment B holds a position in.
        val w = world(segmentBytes = 200, snapshotEvery = 2)
        val a = w.Device("A")
        val b = w.Device("B")
        w.syncthing.now = 3 * day
        b.skew = -2 * day
        val id = a.create("Profile A")
        a.export()
        w.syncthing.session("A", "B")
        assertEquals(1, b.import().waiting)

        repeat(3) { a.edit(id, DETAILS, "ADULT$it"); a.export() }
        val paths = w.syncthing.paths("A")
        assertFalse(FolderLayout.segment("A", 0) in paths, "the segment B read was deleted: $paths")
        assertTrue(paths.any { "/snapshot-" in it }, "no snapshot: $paths")
        w.syncthing.session("A", "B")
        b.import()
        assertTrue(b.rows().isEmpty(), "still ahead of B's clock")

        b.skew = 0
        b.import()
        assertEquals("Profile A", b.profileName(id))
        assertEquals("ADULT2", b.kind(id))
    }

    @Test
    fun aConflictCopyHoldingAHeldLineIsKept() {
        val w = world()
        val a = w.Device("A")
        w.syncthing.now = 3 * day
        val id = a.create("Profile A")
        a.export()
        val record = w.plainLines("A", FolderLayout.segment("A", 0)).single()
        val b = w.Device("B")
        b.skew = -2 * day
        val copy = "${FolderLayout.dir("X")}/log-000000.sync-conflict-5-X.jsonl"
        w.syncthing.folder("B").replace(copy, w.sealed(record).encodeToByteArray())

        assertEquals(1, b.import().waiting)
        assertTrue(copy in w.syncthing.paths("B"), "the copy holds a line set aside")

        b.skew = 0
        b.import()
        assertEquals("Profile A", b.profileName(id))
        assertFalse(copy in w.syncthing.paths("B"), "applied, the copy goes")
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        (0..size - needle.size).any { i -> needle.indices.all { this[i + it] == needle[it] } }
}
