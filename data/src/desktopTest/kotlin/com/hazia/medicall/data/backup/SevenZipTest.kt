package com.hazia.medicall.data.backup

import org.apache.commons.compress.archivers.sevenz.SealedSevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The patched 7z writer (SPEC:R112): what it changes from Commons Compress 1.28.0, and that it still reads back. */
class SevenZipTest {

    @get:Rule val tmp = TemporaryFolder()

    private val password = "invented-Passphrase-7".toCharArray()
    private val same = "Invented content, the same in both entries, longer than one AES block.".encodeToByteArray()

    /**
     * Two entries with the same plain bytes compress to the same bytes; sealed under one key and IV,
     * as upstream does, their first ciphertext blocks are then equal, which shows they start alike.
     * The patched writer gives each entry its own IV, so they differ.
     */
    @Test
    fun everyEntryHasItsOwnIv() {
        fun write(file: File, open: (File) -> Pair<Closeable, (SevenZArchiveEntry, ByteArray) -> Unit>): ByteArray {
            val (archive, add) = open(file)
            archive.use { listOf("a.txt", "b.txt").forEach { add(SevenZArchiveEntry().apply { name = it }, same) } }
            return file.readBytes()
        }

        val upstreamBytes = write(tmp.newFile("upstream.7z")) { f ->
            val z = SevenZOutputFile(f, password)
            z to { e, b -> z.putArchiveEntry(e); z.write(b); z.closeArchiveEntry() }
        }
        val patchedBytes = write(tmp.newFile("patched.7z")) { f ->
            val z = SealedSevenZOutputFile(f, password)
            z to { e, b -> z.putArchiveEntry(e); z.write(b); z.closeArchiveEntry() }
        }
        // Upstream's header follows the two packed streams, which are as long as each other: the start
        // header's "next header offset" (bytes 12..19, little-endian) is twice one stream's length. The
        // patched archive's streams are as long: the same bytes, compressed alike, padded alike.
        val nextHeader = (0 until 8).sumOf { (upstreamBytes[12 + it].toLong() and 0xFF) shl (8 * it) }
        val stream = (nextHeader / 2).toInt()
        fun firstBlocks(bytes: ByteArray) = listOf(SIGNATURE_HEADER, SIGNATURE_HEADER + stream).map { bytes.copyOfRange(it, it + 16) }
        val upstream = firstBlocks(upstreamBytes)
        val patched = firstBlocks(patchedBytes)

        assertContentEquals(upstream[0], upstream[1], "the control: upstream's entries share their first block")
        assertFalse(patched[0].contentEquals(patched[1]), "the patched entries share their first block")
        for (name in listOf("a.txt", "b.txt")) assertContentEquals(same, SevenZip.read(tmp.root.resolve("patched.7z"), password, name), name)
    }

    @Test
    fun damagedEntryReadsAsNullAndTheOthersStillRead() {
        val file = tmp.newFile("two.7z")
        val entries = listOf("a.txt", "b.txt").map { n -> ArchiveEntry(n, same.size.toLong()) { same.inputStream() } }
        SevenZip.write(file, password, entries)
        val bytes = file.readBytes()
        bytes[SIGNATURE_HEADER + 1] = (bytes[SIGNATURE_HEADER + 1].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)

        val read = mutableMapOf<String, ByteArray?>()
        assertTrue(SevenZip.readEach(file, password) { n, b -> read[n] = b })
        assertEquals(null, read.getValue("a.txt"))
        assertContentEquals(same, read.getValue("b.txt"))
        assertEquals(null, SevenZip.read(file, password, "a.txt"))
    }

    private companion object {
        /** The 7z signature header; the first entry's packed stream starts right after it. */
        const val SIGNATURE_HEADER = 32
    }
}
