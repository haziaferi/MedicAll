package com.hazia.medicall.data.backup

import java.io.File
import java.io.InputStream

/** One file in an archive: its [name], with '/' separators, its [size] in bytes, and its bytes when [open]ed. */
class ArchiveEntry(val name: String, val size: Long, val open: () -> InputStream)

/** Writes backups (SPEC:R22, SPEC:R112); see docs/decisions/2026-11-22-7zip-library.md, section 5. */
interface ArchiveWriter {
    /** Writes a 7z archive, AES-256 with an encrypted header, to [target], replacing it. */
    fun write(target: File, password: CharArray, entries: List<ArchiveEntry>)
}

/** Reads backups (SPEC:R22, SPEC:R112). Nothing here throws for a damaged or foreign archive: it reads as null. */
interface ArchiveReader {
    /** Entry names with '/' separators, or null if the archive does not open with [password]. */
    fun list(source: File, password: CharArray?): List<String>?

    /** The bytes of [name], or null if the archive does not open or has no such entry. */
    fun read(source: File, password: CharArray, name: String): ByteArray?

    /**
     * Every entry, in one opening of the archive: [block] gets each entry's name with its bytes, or
     * with null when that entry does not read back intact. False if the archive does not open with
     * [password]. An addition to the decision's interface: reading entries one by one with [read]
     * opens the archive, and derives its key, once per entry.
     */
    fun readEach(source: File, password: CharArray, block: (name: String, bytes: ByteArray?) -> Unit): Boolean
}
