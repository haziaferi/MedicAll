package com.hazia.medicall.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * One file in an archive: its [name], with '/' separators, its [size] in bytes, and its bytes when
 * [open]ed. The writer streams each entry from [open]; the reader hands one out per entry, its [size]
 * the size the header declares.
 */
class ArchiveEntry(val name: String, val size: Long, val open: () -> InputStream)

/** Writes backups (SPEC:R22, SPEC:R112); see docs/decisions/2026-11-22-7zip-library.md, section 5. */
interface ArchiveWriter {
    /** Writes a 7z archive, AES-256 with an encrypted header, to [target], replacing it; each entry streamed from [ArchiveEntry.open]. */
    fun write(target: File, password: CharArray, entries: List<ArchiveEntry>)
}

/**
 * Reads backups (SPEC:R22, SPEC:R112). A damaged or foreign archive reads as null or false, never as
 * an exception: a wrong or missing password, a header that does not decode, a file that is not a 7z
 * archive, and, given a password, an archive that opens without one, which no MedicAll backup does.
 * An [IOException] is thrown only when the file itself cannot be read (missing, unreadable, a failing
 * disk): that is not a wrong password.
 */
interface ArchiveReader {
    /** Entry names with '/' separators, or null if the archive does not open with [password]. */
    fun list(source: File, password: CharArray?): List<String>?

    /**
     * Every entry in archive order, in one opening of the archive: [block] gets each as an
     * [ArchiveEntry], to stream while [block] runs and not after. Its stream throws an [IOException]
     * when the entry does not read back intact; [ArchiveEntry.open] throws [EntryTooLargeException]
     * when the size the header declares passes the reader's cap, before anything is read. An entry not
     * opened costs nothing. False if the archive does not open with [password].
     */
    fun readEach(source: File, password: CharArray, block: (ArchiveEntry) -> Unit): Boolean
}

/** An entry whose declared [size] passes the reader's [cap]: refused before it is read (SPEC:R22). */
class EntryTooLargeException(val name: String, val size: Long, val cap: Long) : IOException("$name declares $size bytes, more than $cap")

/** This entry's bytes, for one small enough to hold in memory; null when it does not read back intact or passes the cap. */
internal fun ArchiveEntry.bytesOrNull(): ByteArray? =
    try {
        open().use { it.readBytes() }
    } catch (_: IOException) {
        null
    }

/** The SHA-256 of [input], streamed, in lower-case hex; [input] is read to its end, not closed. */
internal fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    return digest.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** The SHA-256 of [file]'s bytes, streamed. */
internal fun sha256Hex(file: File): String = file.inputStream().use(::sha256Hex)
