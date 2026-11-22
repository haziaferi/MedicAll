package com.hazia.medicall.data.backup

import java.io.File
import java.io.IOException

/** A backup folder in memory. [failWrites] makes every write throw, as a full disk or a removed card would. */
internal class MemoryBackupTarget : BackupTarget {
    val files = mutableMapOf<String, ByteArray>()
    var failWrites = false
    var isReachable = true

    override fun write(name: String, bytes: ByteArray) {
        if (failWrites) throw IOException("disk full")
        files[name] = bytes.copyOf()
    }

    override fun read(name: String) = files[name]?.copyOf()
    override fun list() = files.keys.sorted()
    override fun reachable() = isReachable
}

/** [bytes] in a file under [dir], for the archive reader, which reads files. */
internal fun archiveFile(dir: File, bytes: ByteArray): File = File.createTempFile("backup-", ".7z", dir).apply { writeBytes(bytes) }
