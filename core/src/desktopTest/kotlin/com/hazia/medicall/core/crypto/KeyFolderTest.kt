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

        assertTrue(winner.unfoldedKeyIds(winningPass).isNotEmpty(), "the losing key waits, retired, until someone folds it in")
        assertTrue(winner.fold(winningPass, losingPass))

        val device = folder(shared).open(winningPass)
        device.reads(lineA, "written on A")
        device.reads(lineB, "written on B")
        assertTrue(folder(shared).unfoldedKeyIds(winningPass).isEmpty())
        assertTrue(shared.files.keys.any { it.startsWith(KeyFolder.RETIRED + "/") }, "retired files are kept")
    }

    private fun MemoryFiles.copy() = MemoryFiles().also { c -> c.files.putAll(files.mapValues { it.value.copyOf() }) }

    /** Syncthing meets two edited copies of the folder: [kept]'s key file stays, [renamed]'s becomes a conflict copy, every other file is the union. */
    private fun meet(kept: MemoryFiles, renamed: MemoryFiles): MemoryFiles {
        val merged = MemoryFiles()
        merged.files.putAll(renamed.files)
        merged.files.putAll(kept.files)
        merged.files["keys/keyinfo.sync-conflict-20261122-120000-XYZ.json"] = renamed.files.getValue(KeyFolder.PATH)
        return merged
    }

    // SPEC:R16 a fold on one device and a passphrase change on another, made apart, lose no key.
    @Test
    fun foldAndChangeMadeApartLoseNoKey_SPEC_R16() {
        val p = passphrase
        val q = other
        val n = "the third passphrase".toCharArray()
        // A folder whose key X (passphrase p) has a retired key R (passphrase q) not yet folded in.
        val (start, lineA, lineB) = racingFolders(p, q)
        folder(start).settleConflicts()
        val winningPass = if (folder(start).unlock(p) is Unlock.Unlocked) p else q
        val losingPass = if (winningPass === p) q else p

        val deviceA = start.copy()
        folder(deviceA).fold(winningPass, losingPass)
        val deviceB = start.copy()
        val lineZ = folder(deviceB).change(winningPass, n).seal("written after the change".encodeToByteArray())

        for (shared in listOf(meet(deviceA, deviceB), meet(deviceB, deviceA))) {
            folder(shared).settleConflicts()
            val passes = listOf(p, q, n)
            val current = passes.first { folder(shared).unlock(it) is Unlock.Unlocked }
            passes.filter { it !== current }.forEach { folder(shared).fold(current, it) }

            val device = folder(shared).open(current)
            device.reads(lineA, "written on A")
            device.reads(lineB, "written on B")
            device.reads(lineZ, "written after the change")
        }
    }

    @Test
    fun sameIdCopiesMergeTheirChains() {
        val q2 = "yet another passphrase".toCharArray()
        val start = MemoryFiles()
        folder(start).create(passphrase)
        // Two retired keys with their own passphrases, folded in on two devices apart.
        val r1 = MemoryFiles().also { folder(it).create(other) }
        val r2 = MemoryFiles().also { folder(it).create(q2) }
        val line1 = folder(r1).open(other).seal("one".encodeToByteArray())
        val line2 = folder(r2).open(q2).seal("two".encodeToByteArray())
        start.files["${KeyFolder.RETIRED}/r1.json"] = r1.files.getValue(KeyFolder.PATH)
        start.files["${KeyFolder.RETIRED}/r2.json"] = r2.files.getValue(KeyFolder.PATH)
        val a = start.copy().also { folder(it).fold(passphrase, other) }
        val b = start.copy().also { folder(it).fold(passphrase, q2) }
        val shared = meet(a, b)
        shared.files.keys.filter { it.startsWith(KeyFolder.RETIRED) }.toList().forEach { shared.files.remove(it) } // the retired files are gone: only the chains hold the keys

        folder(shared).settleConflicts()

        val device = folder(shared).open(passphrase)
        device.reads(line1, "one")
        device.reads(line2, "two")
    }

    @Test
    fun unreadableCopyIsKeptAside() {
        val shared = MemoryFiles()
        folder(shared).create(passphrase)
        shared.files["keys/keyinfo.sync-conflict-20261122-130000-QRS.json"] = "{\"version\":2}".encodeToByteArray()

        folder(shared).settleConflicts()

        assertEquals("{\"version\":2}", shared.files["${KeyFolder.UNREADABLE}/keyinfo.sync-conflict-20261122-130000-QRS.json"]?.decodeToString())
        assertTrue(shared.files.keys.none { it.startsWith("keys/keyinfo.sync-conflict") })
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
