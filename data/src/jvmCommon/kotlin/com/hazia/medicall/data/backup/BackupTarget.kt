package com.hazia.medicall.data.backup

import com.hazia.medicall.data.sync.TEMP_PREFIX
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The folder the person chose for backups (SPEC:R22). Blocking: call it off the main thread.
 */
interface BackupTarget {
    /**
     * Writes [bytes] as [name], whole or not at all: to a temporary name first, moved over [name]
     * only once written, so a failure part way leaves the previous [name] as it was. Throws on failure.
     */
    fun write(name: String, bytes: ByteArray)

    /** The bytes of [name], or null when there is no such file. */
    fun read(name: String): ByteArray?

    /** The names of the files in the folder; temporary ones are left out. */
    fun list(): List<String>

    /** Whether the folder is there to be written: a removed card or an unmounted drive is not. */
    fun reachable(): Boolean
}

/** A [BackupTarget] in a directory of the file system, as the desktop has it. */
class DirectoryBackupTarget(private val root: Path) : BackupTarget {

    override fun write(name: String, bytes: ByteArray) {
        val target = file(name)
        val tmp = root.resolve(TEMP_PREFIX + name)
        try {
            Files.write(tmp, bytes)
            Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    override fun read(name: String): ByteArray? = file(name).takeIf { it.isRegularFile() }?.let(Files::readAllBytes)

    override fun list(): List<String> =
        root.takeIf { it.isDirectory() }?.listDirectoryEntries()?.filter { it.isRegularFile() }?.map { it.name }
            ?.filter { !it.startsWith(TEMP_PREFIX) }.orEmpty()

    override fun reachable(): Boolean = root.isDirectory() && Files.isWritable(root)

    /** Only a plain file name reaches the disk, so no name walks out of [root]. */
    private fun file(name: String): Path {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "not a backup name: $name" }
        return root.resolve(name)
    }
}
