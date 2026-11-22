package com.hazia.medicall.data.backup

import org.apache.commons.compress.archivers.sevenz.SealedSevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File

/**
 * 7z archives through Apache Commons Compress 1.28.0 (SPEC:R112), the same on Android and the
 * desktop: written by the patched [SealedSevenZOutputFile], so the content is AES-256 under a fresh IV
 * per entry and the header, which holds the names, is sealed too (SPEC:R22); read by the library's
 * own [SevenZFile]. Blocking: call it off the main thread.
 */
object SevenZip : ArchiveWriter, ArchiveReader {

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

    override fun read(source: File, password: CharArray, name: String): ByteArray? = opened(source, password) { z ->
        z.entries.firstOrNull { it.name == name }?.let { e -> runCatching { z.getInputStream(e).use { it.readBytes() } }.getOrNull() }
    }

    override fun readEach(source: File, password: CharArray, block: (name: String, bytes: ByteArray?) -> Unit): Boolean =
        opened(source, password) { z ->
            // Each entry is its own folder, so a damaged one reads as null and the next still reads.
            for (e in z.entries) block(e.name, runCatching { z.getInputStream(e).use { it.readBytes() } }.getOrNull())
            true
        } ?: false

    /**
     * [use] run on [source] opened with [password], or null when it does not open: a wrong or missing
     * password (the header is sealed), or not a 7z archive. What [use] throws is the caller's, and
     * passes. A copy of the password is cleared once the archive is open.
     */
    private fun <T> opened(source: File, password: CharArray?, use: (SevenZFile) -> T): T? {
        val pw = password?.copyOf()
        val archive = try {
            val builder = SevenZFile.builder().setFile(source)
            if (pw != null) builder.setPassword(pw)
            builder.get()
        } catch (_: Exception) {
            return null
        } finally {
            pw?.fill('\u0000')
        }
        return archive.use(use)
    }
}
