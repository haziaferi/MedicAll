package com.hazia.medicall.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KeyFolderTest {

    private class MemoryFiles : KeyFiles {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(path: String) = files[path]
        override fun replace(path: String, bytes: ByteArray) { files[path] = bytes }
        override fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") && '/' !in it.removePrefix("$dir/") }.map { it.removePrefix("$dir/") }
        override fun delete(path: String) { files.remove(path) }
        fun snapshot() = files.mapValues { it.value.decodeToString() }
    }

    private val passphrase = "correct horse battery".toCharArray()
    private val other = "a brand new passphrase".toCharArray()

    // A low count keeps the tests fast; production uses KeyDerivation.ITERATIONS and refuses fewer than MIN_FILE_ITERATIONS.
    private fun folder(files: KeyFiles) = KeyFolder(files, iterations = 1_000, minIterations = 1)

    private fun KeyFolder.open(p: CharArray): LineCipher = assertIs<Unlock.Unlocked>(unlock(p)).cipher

    private fun LineCipher.reads(line: String, text: String) = assertContentEquals(text.encodeToByteArray(), open(line))

    // SPEC:R109 one passphrase, entered once on each device that syncs.
    @Test
    fun secondDeviceUnlocksWithSamePassphrase_SPEC_R109() {
        val shared = MemoryFiles()
        val line = folder(shared).create(passphrase).seal("Profile A".encodeToByteArray())
        folder(shared).open(passphrase.copyOf()).reads(line, "Profile A")
    }

    @Test
    fun wrongPassphraseIsRefused() {
        val shared = MemoryFiles()
        folder(shared).create(passphrase)
        val before = shared.snapshot()

        assertEquals(Unlock.WrongPassphrase, folder(shared).unlock("wrong horse battery".toCharArray()))
        assertEquals(before, shared.snapshot())
    }

    @Test
    fun missingKeyFileIsNotAWrongPassphrase() {
        assertEquals(Unlock.NoKeyFile, folder(MemoryFiles()).unlock(passphrase))
    }

    @Test
    fun damagedKeyFileIsReportedNotThrown() {
        val shared = MemoryFiles()
        folder(shared).create(passphrase)
        val good = shared.files.getValue(KeyFolder.PATH).decodeToString()
        val damaged = listOf(
            "not json at all",
            good.replace(Regex("\"iterations\":\\d+"), "\"iterations\":0"),
            good.replace(Regex("\"iterations\":\\d+"), "\"iterations\":2147483647"),
            good.replace(Regex("\"salt\":\"[^\"]*\""), "\"salt\":\"\""),
            good.replace("\"version\":1", "\"version\":2"),
        )
        for (text in damaged) {
            shared.files[KeyFolder.PATH] = text.encodeToByteArray()
            assertEquals(Unlock.Damaged, folder(shared).unlock(passphrase), text)
        }
        // The production floor refuses a file that asks for too few rounds.
        shared.files[KeyFolder.PATH] = good.encodeToByteArray()
        assertEquals(Unlock.Damaged, KeyFolder(shared).unlock(passphrase), "1,000 rounds is under MIN_FILE_ITERATIONS")
    }

    @Test
    fun tamperedChainEntryIsDamagedNotWrong() {
        val shared = MemoryFiles()
        folder(shared).create(passphrase)
        folder(shared).change(passphrase, other)
        val text = shared.files.getValue(KeyFolder.PATH).decodeToString()
        val tampered = Regex("(\"older\":\\{\"[0-9a-f]+\":\"[0-9a-f]+\\.)(.)").replace(text) { it.groupValues[1] + if (it.groupValues[2] == "A") "B" else "A" }
        shared.files[KeyFolder.PATH] = tampered.encodeToByteArray()

        assertEquals(Unlock.Damaged, folder(shared).unlock(other))
    }

    // SPEC:R109 the person can change the passphrase; each other device then asks for the new one once.
    @Test
    fun oldLinesReadAfterChange_SPEC_R109() {
        val shared = MemoryFiles()
        val old = folder(shared).create(passphrase)
        val oldLine = old.seal("written before".encodeToByteArray())

        val changed = folder(shared).change(passphrase, other)
        assertNotEquals(old.currentKeyId, changed.currentKeyId)
        assertEquals(Unlock.WrongPassphrase, folder(shared).unlock(passphrase), "the old passphrase no longer opens the folder")

        val offlineDevice = folder(shared).open(other)
        offlineDevice.reads(oldLine, "written before")
        offlineDevice.reads(changed.seal("written after".encodeToByteArray()), "written after")
    }

    @Test
    fun twoChangesKeepTheWholeChain() {
        val shared = MemoryFiles()
        val first = folder(shared).create(passphrase).seal("first".encodeToByteArray())
        val second = folder(shared).change(passphrase, other).seal("second".encodeToByteArray())
        val third = "a third passphrase here".toCharArray()
        folder(shared).change(other, third)

        val device = folder(shared).open(third)
        device.reads(first, "first")
        device.reads(second, "second")
    }

    @Test
    fun changeWithWrongOldPassphraseLeavesTheFile() {
        val shared = MemoryFiles()
        folder(shared).create(passphrase)
        val before = shared.snapshot()

        assertEquals("Wrong passphrase", assertFailsWith<IllegalArgumentException> { folder(shared).change("wrong horse battery".toCharArray(), other) }.message)
        assertEquals(before, shared.snapshot())
    }

    @Test
    fun shortPassphraseRejected() {
        val shared = MemoryFiles()
        assertEquals("Passphrase too short", assertFailsWith<IllegalArgumentException> { folder(shared).create("eleven char".toCharArray()) }.message)
        folder(shared).create(passphrase)
        assertEquals("Passphrase too short", assertFailsWith<IllegalArgumentException> { folder(shared).change(passphrase, "eleven char".toCharArray()) }.message)
        assertEquals(12, MIN_PASSPHRASE)
    }

    /** Two devices started offline and each made a key file; Syncthing kept one and renamed the other. */
    private fun racingFolders(passA: CharArray, passB: CharArray): Triple<MemoryFiles, String, String> {
        val a = MemoryFiles()
        val b = MemoryFiles()
        val lineA = folder(a).create(passA).seal("written on A".encodeToByteArray())
        val lineB = folder(b).create(passB).seal("written on B".encodeToByteArray())
        val shared = MemoryFiles()
        shared.files[KeyFolder.PATH] = b.files.getValue(KeyFolder.PATH)
        shared.files["keys/keyinfo.sync-conflict-20261122-101500-ABCDEFG.json"] = a.files.getValue(KeyFolder.PATH)
        return Triple(shared, lineA, lineB)
    }

    // SPEC:R16 a conflict copy is merged, then deleted: the losing key is kept, never destroyed.
    @Test
    fun racingFirstRunsSettleOnLowerKeyIdAndKeepBothKeys_SPEC_R16() {
        val (shared, lineA, lineB) = racingFolders(passphrase, passphrase.copyOf())

        // A third device settles, while both A and B are closed.
        folder(shared).settleConflicts()

        assertTrue(shared.files.keys.none { "sync-conflict" in it }, "the conflict copy is gone")
        val device = folder(shared).open(passphrase)
        device.reads(lineA, "written on A")
        device.reads(lineB, "written on B")
    }

    @Test
    fun losingKeyWithAnotherPassphraseIsFoldedInWhenGiven() {
        val (shared, lineA, lineB) = racingFolders(passphrase, other)
        folder(shared).settleConflicts()
        val winner = folder(shared)
        val winningPass = if (winner.unlock(passphrase) is Unlock.Unlocked) passphrase else other
        val losingPass = if (winningPass === passphrase) other else passphrase

        assertTrue(winner.retiredKeyIds().isNotEmpty(), "the losing key waits, retired, until someone folds it in")
        assertTrue(winner.fold(winningPass, losingPass))

        val device = folder(shared).open(winningPass)
        device.reads(lineA, "written on A")
        device.reads(lineB, "written on B")
        assertTrue(folder(shared).retiredKeyIds().isEmpty())
    }

    @Test
    fun changeSettlesConflictsFirst() {
        val (shared, lineA, lineB) = racingFolders(passphrase, passphrase.copyOf())
        val third = "a third passphrase here".toCharArray()

        folder(shared).change(passphrase, third)
        folder(shared).settleConflicts()

        val device = folder(shared).open(third)
        device.reads(lineA, "written on A")
        device.reads(lineB, "written on B")
    }
}
