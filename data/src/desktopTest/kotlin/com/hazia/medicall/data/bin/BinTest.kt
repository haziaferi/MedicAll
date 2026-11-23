package com.hazia.medicall.data.bin

import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.NAME
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.ProfileDao
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

    /** [d]'s bin, measuring time by [d]'s own wall clock. */
    private fun bin(w: World, d: World.Device) = Bin(d.writes, d.db, wallMillis = { w.syncthing.now + d.skew })

    private fun World.Device.removedAt(table: String, id: String): Any? {
        val rows = if (table == PROFILE) rows() else blobRefs()
        return rows.getValue(id).groups.getValue(REMOVED).values["removed_at"]
    }

    private suspend fun World.Device.purged(vararg ids: String): Set<String> =
        db.syncDao().purges(ids.toList()).map { it.id }.toSet()

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

    // SPEC:R18 the purge travels: the peer deletes the profile too, and its own older copy cannot bring it back.
    @Test
    fun purgeTravelsToPeer_SPEC_R18() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val id = a.create("Profile A")
        a.export(); w.syncthing.session("A", "B"); b.import()
        assertNotNull(b.rows()[id])

        // B renames the profile before A removes it, and has not exported the rename yet.
        w.syncthing.now = 500
        b.edit(id, NAME, "Profile A renamed")
        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)
        w.syncthing.now = 1_000 + 31 * day
        bin(w, a).purgeExpired()
        a.export(); w.syncthing.session("A", "B")

        // B's stale copy reaches the folder after the purge.
        b.export(); b.import()
        assertNull(b.rows()[id])
        assertEquals(setOf(id), b.purged(id), "B keeps the purge in its registry")

        val c = w.Device("C")
        w.settle(listOf(a, b, c))
        for (d in listOf(a, b, c)) {
            assertNull(d.rows()[id], "${d.name} holds the purged profile again")
            assertEquals(setOf(id), d.purged(id), d.name)
        }
    }

    // SPEC:R19 emptying the bin cannot be undone, so it asks for "DELETE" typed out.
    @Test
    fun emptyNeedsTypedDelete_SPEC_R19() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        val file = a.addBlobRef(id)
        bin(w, a).remove(PROFILE, id)

        assertFailsWith<IllegalArgumentException> { bin(w, a).empty("delete") }
        assertNotNull(a.rows()[id], "a wrong confirmation empties nothing")
        assertEquals(emptySet(), a.purged(id, file))

        bin(w, a).empty("DELETE")
        assertNull(a.rows()[id])
        assertEquals(setOf(id, file), a.purged(id, file), "the profile and its file reference are in the registry")
    }

    @Test
    fun removingProfileTakesItsFiles_SPEC_R18() = runBlocking {
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
        assertEquals(setOf(id, file, removedEarlier), a.purged(id, file, removedEarlier))
    }

    // SPEC:R18 the 30 days count from the first removal: removing a record already in the bin changes nothing.
    @Test
    fun removingARemovedRowKeepsItsMoment_SPEC_R18() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        val file = a.addBlobRef(id)
        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)

        w.syncthing.now = 2_000
        bin(w, a).remove(PROFILE, id)
        bin(w, a).remove(BLOB_REF, file)
        assertEquals(1_000L, a.removedAt(PROFILE, id))
        assertEquals(1_000L, a.removedAt(BLOB_REF, file))

        bin(w, a).restore(PROFILE, id)
        assertNull(a.removedAt(BLOB_REF, file), "the file comes back with its profile")
    }

    // SPEC:R18 a remover whose clock runs behind cannot shorten the 30 days on its peers.
    @Test
    fun retentionCountsFromTheRemovalStampWhenTheClockRunsBehind_SPEC_R18() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        w.syncthing.now = 100 * day
        val id = b.create("Profile B")
        b.export(); w.syncthing.session("A", "B"); a.import()

        // A's clock runs ten days behind; its hybrid clock has seen B's stamp.
        a.skew = -10 * day
        bin(w, a).remove(PROFILE, id)
        assertEquals(90 * day, a.removedAt(PROFILE, id))
        a.export(); w.syncthing.session("A", "B"); b.import()

        w.syncthing.now = 125 * day
        bin(w, b).purgeExpired()
        assertNotNull(b.rows()[id], "25 days after the removal, it is still in the bin")

        w.syncthing.now = 131 * day
        bin(w, b).purgeExpired()
        assertNull(b.rows()[id], "31 days after, it is purged")
    }

    // SPEC:R18 a peer's restore that an import applies while the bin picks what to purge is not purged.
    @Test
    fun aRestoreImportedWhilePurgingIsKept_SPEC_R18() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val b = w.Device("B")
        val id = a.create("Profile A")
        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)
        a.export(); w.syncthing.session("A", "B"); b.import()

        w.syncthing.now = 1_000 + 30 * day
        bin(w, b).restore(PROFILE, id)
        b.export(); w.syncthing.session("A", "B")

        // A's import of B's restore runs right after the bin has read what is in it.
        w.syncthing.now = 1_000 + 31 * day
        val real = a.db.profileDao()
        var imported = false
        val profiles = object : ProfileDao by real {
            override suspend fun removedProfiles() = real.removedProfiles().also {
                if (!imported) {
                    imported = true
                    a.sync.import()
                }
            }
        }
        Bin(a.writes, a.db, { w.syncthing.now }, profiles = profiles).purgeExpired()

        assertTrue(imported)
        assertNotNull(a.rows()[id], "B restored it, so it is not purged")
        assertNull(a.removedAt(PROFILE, id))
        assertEquals(emptySet(), a.purged(id))
    }

    @Test
    fun restoringALiveFileReferenceChangesNothing() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        val file = a.addBlobRef(id)
        val before = a.blobRefs().getValue(file)

        w.syncthing.now = 1_000
        bin(w, a).restore(BLOB_REF, file)

        assertEquals(before, a.blobRefs().getValue(file))
    }

    @Test
    fun aFileReferenceWhoseProfileIsInTheBinIsNotRestoredAlone() = runBlocking {
        val w = world()
        val a = w.Device("A")
        val id = a.create("Profile A")
        val file = a.addBlobRef(id)
        w.syncthing.now = 1_000
        bin(w, a).remove(PROFILE, id)

        assertFailsWith<IllegalStateException> { bin(w, a).restore(BLOB_REF, file) }
        assertEquals(1_000L, a.removedAt(BLOB_REF, file))
    }
}
