package com.hazia.medicall.data.backup

import java.io.File
import java.io.IOException

/**
 * A backup folder in memory, keeping [BackupTarget.write]'s contract: the previous copy as `.prev`
 * unless a `.prev` is already kept, which is never overwritten; `.prev` dropped unless kept. [failWrites] makes every write throw, as a
 * full disk or a removed card would.
 */
internal class MemoryBackupTarget : BackupTarget {
    val files = mutableMapOf<String, ByteArray>()
    var failWrites = false
    var isReachable = true

    override fun write(name: String, source: File, keepPrevious: Boolean) {
        if (failWrites) throw IOException("disk full")
        val bytes = source.readBytes()
        // A kept .prev is never overwritten.
        if (BackupTarget.prev(name) !in files) files[name]?.let { files[BackupTarget.prev(name)] = it }
        files[name] = bytes
        if (!keepPrevious) files.remove(BackupTarget.prev(name))
    }

    override fun openRead(name: String) = files[name]?.inputStream()
    override fun list() = files.keys.sorted()
    override fun reachable() = isReachable

    /** The bytes of [name], or null. */
    fun read(name: String) = files[name]?.copyOf()
}

/** [bytes] in a file under [dir], for the archive reader, which reads files. */
internal fun archiveFile(dir: File, bytes: ByteArray): File = File.createTempFile("backup-", ".7z", dir).apply { writeBytes(bytes) }

/** The bytes of entry [name], or null: one opening of the archive per call, for a test's small entries. */
internal fun ArchiveReader.read(source: File, password: CharArray, name: String): ByteArray? {
    var bytes: ByteArray? = null
    readEach(source, password) { if (it.name == name) bytes = it.bytesOrNull() }
    return bytes
}

/**
 * About how long one key derivation (2^19 SHA-256 rounds) takes here, in milliseconds: the quickest of
 * five listings of a one-entry archive, each of which derives the header's key once.
 */
internal fun keyDerivationMillis(dir: File, password: CharArray): Long {
    val file = File.createTempFile("derivation-", ".7z", dir)
    val b = "Invented".encodeToByteArray()
    SevenZip.write(file, password, listOf(ArchiveEntry("a.txt", b.size.toLong()) { b.inputStream() }))
    return (1..5).minOf {
        val started = System.nanoTime()
        checkNotNull(SevenZip.list(file, password))
        (System.nanoTime() - started) / 1_000_000
    }
}

/** Every entry's bytes, by name, null for one that does not read back intact; null if the archive does not open. */
internal fun ArchiveReader.readAll(source: File, password: CharArray): Map<String, ByteArray?>? {
    val all = linkedMapOf<String, ByteArray?>()
    return if (readEach(source, password) { all[it.name] = it.bytesOrNull() }) all else null
}
