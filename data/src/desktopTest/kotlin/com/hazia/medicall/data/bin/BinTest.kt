package com.hazia.medicall.data.bin

import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.REMOVED
import com.hazia.medicall.data.sync.World
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()
    @After fun close() = worlds.forEach { it.close() }

    private val day = 86_400_000L

    private fun world() = World(tmp.newFolder(), seed = 7).also { worlds += it }

    private fun bin(w: World, d: World.Device) = Bin(d.writes, d.db) { w.syncthing.now }

    private fun World.Device.removedAt(table: String, id: String): Any? {
        val rows = if (table == PROFILE) rows() else blobRefs()
        return rows.getValue(id).groups.getValue(REMOVED).values["removed_at"]
    }

    // SPEC:R18 a removed record can be restored for 30 days.
    @Test
    fun removedRowRestoresOnDay29_SPEC_R18() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)
        assertEquals(listOf(BinEntry(PROFILE, id, 1_000)), bin(w, a).contents())

        w.syncthing.now = 1_000 + 29 * day
        bin(w, a).purgeExpired()
        bin(w, a).restore(PROFILE, id)

        assertNull(a.removedAt(PROFILE, id))
        assertTrue(bin(w, a).contents().isEmpty())
    }

    // SPEC:R18 after 30 days in the bin the record is purged, and the purge is kept so no peer brings it back.
    @Test
    fun removedRowIsPurgedOnDay31_SPEC_R18() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)

        w.syncthing.now = 1_000 + 31 * day
        bin(w, a).purgeExpired()

        assertNull(a.rows()[id])
        assertNotNull(a.db.syncDao().purges(listOf(id)).singleOrNull(), "the purge is in the registry")
        assertTrue(bin(w, a).contents().isEmpty())
    }

    @Test
    fun purgeTravelsToPeer() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val id = a.create("Profile A")
        a.export(); w.syncthing.session("A", "B"); b.import()
        assertNotNull(b.rows()[id])

        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)
        w.syncthing.now = 1_000 + 31 * day
        bin(w, a).purgeExpired()
        a.export(); w.syncthing.session("A", "B"); b.import()

        assertNull(b.rows()[id])
    }

    // SPEC:R19 emptying the bin cannot be undone, so it asks for "DELETE" typed out.
    @Test
    fun emptyNeedsTypedDelete_SPEC_R19() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        bin(w, a).remove(PROFILE, id)

        assertFailsWith<IllegalArgumentException> { bin(w, a).empty("delete") }
        assertNotNull(a.rows()[id], "a wrong confirmation empties nothing")

        bin(w, a).empty("DELETE")
        assertNull(a.rows()[id])
    }

    @Test
    fun removingProfileTakesItsFiles() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        val file = a.addBlobRef(id)
        val removedEarlier = a.addBlobRef(id)
        w.syncthing.now = 500
        bin(w, a).remove(BLOB_REF, removedEarlier)

        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)
        assertEquals(1_000L, a.removedAt(BLOB_REF, file))

        bin(w, a).restore(PROFILE, id)
        assertNull(a.removedAt(BLOB_REF, file), "removed with the profile, restored with it")
        assertEquals(500L, a.removedAt(BLOB_REF, removedEarlier), "removed on its own earlier, so it stays in the bin")

        bin(w, a).remove(PROFILE, id)
        w.syncthing.now = 1_000 + 31 * day
        bin(w, a).purgeExpired()
        assertTrue(a.rows().isEmpty() && a.blobRefs().isEmpty(), "a purged profile takes its files")
    }
}
