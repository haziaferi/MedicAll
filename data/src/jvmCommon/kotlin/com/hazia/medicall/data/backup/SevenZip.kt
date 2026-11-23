package com.hazia.medicall.data.backup

import org.apache.commons.compress.archivers.sevenz.SealedSevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.READ

/**
 * 7z archives through Apache Commons Compress 1.28.0 (SPEC:R112), the same on Android and the
 * desktop: written by the patched [SealedSevenZOutputFile], so the content is AES-256 under one key
 * derived once per archive, with a fresh IV per entry, and the header, which holds the names, is
 * sealed too (SPEC:R22); read by the library's own [SevenZFile], which derives that key again for
 * the header and for each entry it opens (2^19 SHA-256 rounds each, a library limit), so every
 * caller reads an archive in one pass. Blocking: call it off the main thread.
 *
 * [maxEntryBytes] caps the size an entry may declare, checked before it is read: a crafted header
 * cannot make a reader hold more than that. [maxMemoryKiB] is Commons Compress's own limit on what
 * the decoders and the header may take.
 */
open class SevenZipArchives(
    private val maxEntryBytes: Long = Runtime.getRuntime().maxMemory() / 4,
    private val maxMemoryKiB: Int = MAX_MEMORY_KIB,
) : ArchiveWriter, ArchiveReader {

    override fun write(target: File, password: CharArray, entries: List<ArchiveEntry>) {
        SealedSevenZOutputFile(target, password).use { out ->
            for (entry in entries) {
                out.putArchiveEntry(SevenZArchiveEntry().apply { name = entry.name })
                entry.open().use(out::write)
                out.closeArchiveEntry()
            }
        }
    }

    override fun list(source: File, password: CharArray?): List<String>? = opened(source, password) { z -> z.entries.map { it.name } }

    override fun readEach(source: File, password: CharArray, block: (ArchiveEntry) -> Unit): Boolean =
        opened(source, password) { z ->
            // Each entry is its own folder, so a damaged one fails alone and the next still reads.
            for (e in z.entries) {
                block(ArchiveEntry(e.name, e.size) {
                    if (e.size > maxEntryBytes) throw EntryTooLargeException(e.name, e.size, maxEntryBytes)
                    z.getInputStream(e)
                })
            }
            true
        } ?: false

    /**
     * [use] run on [source] opened with [password], or null when it does not open with it; with a
     * [password], also null when it opens without one. What [use] throws is the caller's, and passes.
     */
    private fun <T> opened(source: File, password: CharArray?, use: (SevenZFile) -> T): T? {
        if (password != null) open(source, null)?.use { return null }
        val archive = open(source, password) ?: return null
        return archive.use(use)
    }

    /**
     * [source] opened with [password], or null when it does not open: a wrong or missing password (the
     * header is sealed), a damaged header, or not a 7z archive. A failure of the file itself, which the
     * channel saw, is thrown. A copy of the password is cleared once the archive is open; Commons
     * Compress keeps its own UTF-16 copy inside the [SevenZFile] until it is collected, a library limit.
     */
    private fun open(source: File, password: CharArray?): SevenZFile? {
        val channel = WatchedChannel(Files.newByteChannel(source.toPath(), READ))
        val pw = password?.copyOf()
        try {
            val builder = SevenZFile.builder().setSeekableByteChannel(channel).setMaxMemoryLimitKiB(maxMemoryKiB)
            if (pw != null) builder.setPassword(pw)
            return builder.get()
        } catch (e: IOException) {
            channel.close()
            channel.failure?.let { throw it }
            return null
        } catch (_: RuntimeException) {
            // A malformed header Commons Compress trips over rather than refuses.
            channel.close()
            return null
        } finally {
            pw?.fill('\u0000')
        }
    }

    /** A channel that remembers an [IOException] of its own, so a failing file is told apart from a refused archive. */
    private class WatchedChannel(private val inner: SeekableByteChannel) : SeekableByteChannel by inner {
        var failure: IOException? = null

        private inline fun <T> watched(block: () -> T): T =
            try {
                block()
            } catch (e: IOException) {
                failure = e
                throw e
            }

        override fun read(dst: ByteBuffer): Int = watched { inner.read(dst) }
        override fun position(): Long = watched { inner.position() }
        override fun position(newPosition: Long): SeekableByteChannel = watched { inner.position(newPosition); this }
        override fun size(): Long = watched { inner.size() }
    }

    private companion object {
        /** 64 MiB: LZMA2 at the writer's dictionary (8 MiB) needs about 9 MiB to decode. */
        const val MAX_MEMORY_KIB = 64 * 1024
    }
}

/** The archives MedicAll writes and reads, with the default caps. */
object SevenZip : SevenZipArchives()
