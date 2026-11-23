package com.hazia.medicall.data.backup

import org.apache.commons.compress.archivers.sevenz.SealedSevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        val read = assertNotNull(SevenZip.readAll(file, password))
        assertEquals(null, read.getValue("a.txt"))
        assertContentEquals(same, read.getValue("b.txt"))
        assertEquals(null, SevenZip.read(file, password, "a.txt"))
    }

    /** An entry whose header declares more bytes than the cap is refused before it is read; the others still read (M1). */
    @Test
    fun entryLargerThanTheCapIsRefusedUnread_M1() {
        val big = ByteArray(1000) { (it % 7).toByte() }
        val file = tmp.newFile("capped.7z")
        SevenZip.write(file, password, listOf(
            ArchiveEntry("big.bin", big.size.toLong()) { big.inputStream() },
            ArchiveEntry("small.txt", same.size.toLong()) { same.inputStream() },
        ))
        val capped = SevenZipArchives(maxEntryBytes = 999)

        var refused: EntryTooLargeException? = null
        val read = mutableMapOf<String, ByteArray?>()
        assertTrue(capped.readEach(file, password) { e ->
            assertEquals(if (e.name == "big.bin") 1000L else same.size.toLong(), e.size)
            try {
                read[e.name] = e.open().use { it.readBytes() }
            } catch (x: EntryTooLargeException) {
                refused = x
                read[e.name] = null
            }
        })
        assertEquals("big.bin", refused?.name)
        assertEquals(null, read.getValue("big.bin"))
        assertContentEquals(same, read.getValue("small.txt"))
        assertContentEquals(big, SevenZipArchives(maxEntryBytes = 1000).read(file, password, "big.bin"))
    }

    /** With a password, an archive that opens without one is refused: no MedicAll backup does (M1). */
    @Test
    fun unsealedArchiveIsRefused_M1() {
        val file = tmp.newFile("plain.7z")
        SevenZOutputFile(file).use { z -> z.putArchiveEntry(SevenZArchiveEntry().apply { name = "a.txt" }); z.write(same); z.closeArchiveEntry() }

        assertEquals(listOf("a.txt"), SevenZip.list(file, null))
        assertNull(SevenZip.list(file, password))
        assertFalse(SevenZip.readEach(file, password) { error("read an unsealed entry") })
    }

    /**
     * The writer derives the key (2^19 SHA-256 rounds, some 38 ms on the desktop) once per archive, not
     * once per entry (M4): 300 entries must take less than 100 derivations and 3 s more, measured on
     * the machine running the test. Before the fix they took about 300 derivations.
     */
    @Test
    fun keyIsDerivedOncePerArchive_M4() {
        val derivation = keyDerivationMillis(tmp.root, password)
        val entries = (0 until 300).map { i ->
            val b = "Invented entry $i".encodeToByteArray()
            ArchiveEntry("e$i.txt", b.size.toLong()) { b.inputStream() }
        }
        val file = tmp.newFile("many.7z")
        val started = System.nanoTime()
        SevenZip.write(file, password, entries)
        val ms = (System.nanoTime() - started) / 1_000_000
        val limit = 100 * derivation + 3_000
        println("300 entries written in $ms ms; one key derivation $derivation ms; limit $limit ms")
        assertTrue(ms < limit, "300 entries took $ms ms to write, more than $limit")
        assertEquals(300, SevenZip.list(file, password)!!.size)
    }

    /** No password copy, nor any key made from it, is left in the writer once it is closed, even when finishing fails (L2). */
    @Test
    fun writerClearsThePasswordEvenWhenFinishingFails_L2() {
        class Failing : SeekableInMemoryByteChannel() {
            var fail = false
            override fun position(): Long = if (fail) throw IOException("invented failure") else super.position()
        }
        val channel = Failing()
        val z = SealedSevenZOutputFile(channel, password)
        z.putArchiveEntry(SevenZArchiveEntry().apply { name = "a.txt" })
        z.write(same)
        z.closeArchiveEntry()
        channel.fail = true
        assertFailsWith<IOException> { z.close() }

        for (f in SealedSevenZOutputFile::class.java.declaredFields) {
            f.isAccessible = true
            when (val v = f.get(z)) {
                is CharArray -> assertTrue(v.all { it == '\u0000' }, "${f.name} still holds the password")
                is ByteArray -> assertTrue(v.all { it == 0.toByte() }, "${f.name} still holds key bytes")
            }
        }
    }

    /** A file that cannot be read is an I/O error, not a wrong password (L3). */
    @Test
    fun ioErrorIsNotAWrongPassword_L3() {
        val missing = tmp.root.resolve("missing.7z")
        assertFailsWith<IOException> { SevenZip.list(missing, password) }
        assertFailsWith<IOException> { SevenZip.readEach(missing, password) {} }

        // A wrong password, and a file that is not an archive, read as null.
        val file = tmp.newFile("ok.7z")
        SevenZip.write(file, password, listOf(ArchiveEntry("a.txt", same.size.toLong()) { same.inputStream() }))
        assertNull(SevenZip.list(file, "wrong-Passphrase-7".toCharArray()))
        assertNull(SevenZip.list(tmp.newFile("empty.7z"), password))
        assertNull(SevenZip.list(tmp.newFile("text.7z").apply { writeText("not an archive") }, password))
    }

    private companion object {
        /** The 7z signature header; the first entry's packed stream starts right after it. */
        const val SIGNATURE_HEADER = 32
    }
}
