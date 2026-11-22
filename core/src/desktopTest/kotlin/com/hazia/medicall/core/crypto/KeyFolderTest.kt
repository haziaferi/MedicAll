package com.hazia.medicall.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyFolderTest {

    private class MemoryFiles : KeyFiles {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(path: String) = files[path]
        override fun replace(path: String, bytes: ByteArray) { files[path] = bytes }
        override fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }
        override fun delete(path: String) { files.remove(path) }
    }

    private val passphrase = "correct horse battery".toCharArray()

    // A low count keeps the tests fast; production uses KeyDerivation.ITERATIONS.
    private fun folder(files: KeyFiles) = KeyFolder(files, iterations = 1_000)

    // SPEC:R109 one passphrase, entered once on each device that syncs.
    @Test
    fun secondDeviceUnlocksWithSamePassphrase_SPEC_R109() {
        val shared = MemoryFiles()
        val first = folder(shared).create(passphrase)
        val second = assertNotNull(folder(shared).unlock(passphrase.copyOf()))

        val line = first.seal("Profile A".encodeToByteArray())
        assertContentEquals("Profile A".encodeToByteArray(), second.open(line))
    }

    @Test
    fun wrongPassphraseIsRefused() {
        val shared = MemoryFiles()
        folder(shared).create(passphrase)
        val before = shared.files.mapValues { it.value.decodeToString() }

        assertNull(folder(shared).unlock("wrong horse battery".toCharArray()))
        assertEquals(before, shared.files.mapValues { it.value.decodeToString() })
    }

    // SPEC:R109 the person can change the passphrase; each other device then asks for the new one once.
    @Test
    fun oldLinesReadAfterChange() {
        val shared = MemoryFiles()
        val old = folder(shared).create(passphrase)
        val oldLine = old.seal("written before".encodeToByteArray())

        val changed = folder(shared).change(passphrase, "a brand new passphrase".toCharArray())
        assertNotEquals(old.currentKeyId, changed.currentKeyId)
        assertNull(folder(shared).unlock(passphrase), "the old passphrase no longer opens the folder")

        val offlineDevice = assertNotNull(folder(shared).unlock("a brand new passphrase".toCharArray()))
        assertContentEquals("written before".encodeToByteArray(), offlineDevice.open(oldLine))
        assertContentEquals("written after".encodeToByteArray(), offlineDevice.open(changed.seal("written after".encodeToByteArray())))
    }

    @Test
    fun shortPassphraseRejected() {
        val e = assertFailsWith<IllegalArgumentException> { folder(MemoryFiles()).create("eleven char".toCharArray()) }
        assertEquals("Passphrase too short", e.message)
        assertEquals(12, MIN_PASSPHRASE)
    }

    @Test
    fun racingFirstRunsSettleOnLowerKeyId() {
        // Two devices started offline, each made its own key file; Syncthing kept one and renamed the other.
        val a = MemoryFiles()
        val b = MemoryFiles()
        val keyA = folder(a).create(passphrase)
        val keyB = folder(b).create(passphrase)
        val shared = MemoryFiles()
        shared.files["keys/keyinfo.json"] = b.files.getValue("keys/keyinfo.json")
        shared.files["keys/keyinfo.sync-conflict-20261122-101500-ABCDEFG.json"] = a.files.getValue("keys/keyinfo.json")

        val lower = minOf(keyA.currentKeyId, keyB.currentKeyId)
        val changed = folder(shared).settleConflicts()

        assertTrue(shared.files.keys.none { "sync-conflict" in it }, "the conflict copy is gone")
        assertEquals(lower, assertNotNull(folder(shared).unlock(passphrase)).currentKeyId)
        assertEquals(keyB.currentKeyId != lower, changed, "true when the file this device read lost")
    }
}
