package com.hazia.medicall.data.blob

import com.hazia.medicall.core.crypto.sha256
import com.hazia.medicall.data.sync.World
import com.hazia.medicall.data.sync.twoKeyCipher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Files kept encrypted and named by the SHA-256 of their plain bytes, carried through the sync folder (SPEC:R16, SPEC:R23). */
class BlobSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()
    @After fun close() = worlds.forEach { it.close() }

    private val day = 86_400_000L

    private fun <T> two(seed: Int, body: World.(World.Device, World.Device) -> T): T {
        val w = World(tmp.newFolder(), seed).also { worlds += it }
        return w.body(w.Device("A"), w.Device("B"))
    }

    private fun <T> go(block: suspend () -> T): T = runBlocking { block() }

    private val plain = "Lab 1 result, invented for a test: 42 units".encodeToByteArray()
    private val hash = sha256(plain).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun World.blobPaths(device: String) = syncthing.paths(device).filter { it.startsWith("blobs/") }.toSet()

    @Test
    fun addedFileReachesPeer_SPEC_R16() = two(1) { a, b ->
        syncthing.now = day
        val profile = a.create("Profile A")
        val id = go { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        settle(listOf(a, b))

        assertEquals(hash, b.blobRefs().getValue(id).groups.getValue("main").values["sha256"])
        assertContentEquals(plain, go { b.blobs.read(id) })
        assertEquals(setOf("blobs/$hash"), blobPaths("B"))
        // B has held it now: lost later, it is damaged, not on its way (SPEC:R23).
        assertTrue(hash in go { b.blobs.heldOnce() })
    }

    @Test
    fun collectedFileIsNoLongerHeld_SPEC_R23() = two(5) { a, _ ->
        syncthing.now = day
        val profile = a.create("Profile A")
        go { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        assertTrue(hash in go { a.blobs.heldOnce() })

        a.purge(profile)
        a.export()
        syncthing.now += 31 * day
        a.export()

        assertEquals(emptyList(), a.blobStore.list())
        assertFalse(hash in go { a.blobs.heldOnce() }, "a file collected on purpose would later count as damaged")
    }

    @Test
    fun storedBytesAreNotPlain_SPEC_R10() = two(2) { a, b ->
        syncthing.now = day
        val profile = a.create("Profile A")
        go { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        settle(listOf(a, b))

        val copies = listOf(
            "A's own copy" to a.blobStore.read(hash),
            "B's own copy" to b.blobStore.read(hash),
            "the folder's copy" to syncthing.folder("B").read("blobs/$hash"),
        )
        for ((what, bytes) in copies) {
            assertNotNull(bytes, what)
            assertFalse(bytes.contains(plain), "$what holds the plain bytes")
            assertFalse(bytes.contains(plain.copyOfRange(0, 8)), "$what holds part of the plain bytes")
        }
    }

    @Test
    fun tamperedBlobIsNotStored() = two(3) { a, b ->
        syncthing.now = day
        val profile = a.create("Profile A")
        val id = go { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        a.export()

        // Sealed with the right key, but other bytes than its name promises: it opens, and is still refused.
        syncthing.folder("A").replace("blobs/$hash", sealed("Other invented bytes, a different length").trimEnd().encodeToByteArray())
        syncthing.session("A", "B")
        b.import()
        assertNull(b.blobStore.read(hash))
        assertNull(go { b.blobs.read(id) })

        // Bytes that do not open at all.
        syncthing.folder("A").replace("blobs/$hash", "garbage".encodeToByteArray())
        syncthing.session("A", "B")
        b.import()
        assertNull(b.blobStore.read(hash))

        // The device holding it undamaged puts it back over the damaged copy, and then it arrives.
        settle(listOf(a, b))
        assertContentEquals(plain, go { b.blobs.read(id) })

        // A copy damaged here reads as missing, and the next sync fetches it again.
        b.blobStore.write(hash, "garbage".encodeToByteArray())
        assertNull(go { b.blobs.read(id) })
        settle(listOf(a, b))
        assertContentEquals(plain, go { b.blobs.read(id) })
    }

    // SPEC:R109, SPEC:R23 a copy sealed under a key this device does not hold yet is kept, not taken for damage.
    @Test
    fun copyUnderAKeyNotHeldYetIsKept_SPEC_R109() = two(6) { a, b ->
        syncthing.now = day
        val profile = a.create("Profile A")
        settle(listOf(a, b))
        val id = go { b.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        // The copy here sealed under k2, which B's cipher (k1 only) lacks: as after a key-file clash, before B unlocks again.
        val underK2 = twoKeyCipher("k2").seal(plain).encodeToByteArray()
        b.blobStore.write(hash, underK2)

        assertNull(go { b.blobs.read(id) }, "it does not open here yet")
        assertContentEquals(underK2, b.blobStore.read(hash), "read kept it")
        b.export()
        b.import()
        assertContentEquals(underK2, b.blobStore.read(hash), "the exchange kept it")
        assertFalse("blobs/$hash" in syncthing.paths("B"), "a copy this device cannot check is not published")

        // Once the cipher holds k2, the same copy opens.
        val later = Blobs(b.writes, b.db, b.blobStore, twoKeyCipher("k1"))
        assertContentEquals(plain, go { later.read(id) })
    }

    // SPEC:R23 a copy altered under a key this device holds is still damage: dropped, and fetched again.
    @Test
    fun copyAlteredUnderAKnownKeyIsDropped_SPEC_R23() = two(7) { a, b ->
        syncthing.now = day
        val profile = a.create("Profile A")
        val id = go { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        settle(listOf(a, b))
        val good = assertNotNull(b.blobStore.read(hash)).decodeToString()

        val i = good.length / 2
        val altered = good.substring(0, i) + (if (good[i] == 'A') 'B' else 'A') + good.substring(i + 1)
        val otherBytes = sealed("Other invented bytes").trimEnd()
        for (damaged in listOf(altered, otherBytes)) {
            b.blobStore.write(hash, damaged.encodeToByteArray())
            assertNull(go { b.blobs.read(id) })
            assertNull(b.blobStore.read(hash), "a copy that fails under k1, which B holds, is dropped")
            settle(listOf(a, b))
            assertContentEquals(plain, go { b.blobs.read(id) })
        }
    }

    @Test
    fun sameFileTwiceIsStoredOnce() = two(4) { a, b ->
        syncthing.now = day
        val profile = a.create("Profile A")
        settle(listOf(a, b))
        val first = go { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        val second = go { a.blobs.add(profile, "Lab 1 again.pdf", "application/pdf", plain) }
        assertTrue(first != second)
        assertEquals(listOf(hash), a.blobStore.list())

        // B stores the same file before it hears of A's: both publish it, Syncthing keeps one and makes a conflict copy.
        val third = go { b.blobs.add(profile, "Lab 1.pdf", "application/pdf", plain) }
        settle(listOf(a, b))
        assertTrue(syncthing.conflictCopies > 0, "the two devices' copies clashed")

        for (d in listOf(a, b)) {
            assertEquals(listOf(hash), d.blobStore.list(), d.name)
            assertEquals(setOf("blobs/$hash"), blobPaths(d.name), d.name)
            for (id in listOf(first, second, third)) assertContentEquals(plain, go { d.blobs.read(id) }, d.name)
        }
    }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { at -> part.indices.all { this[at + it] == part[it] } }
}
