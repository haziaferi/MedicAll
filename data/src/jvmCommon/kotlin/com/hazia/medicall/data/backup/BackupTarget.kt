package com.hazia.medicall.data.backup

import com.hazia.medicall.data.sync.TEMP_PREFIX
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The folder the person chose for backups (SPEC:R22). Blocking: call it off the main thread.
 */
interface BackupTarget {
    /**
     * Writes [source]'s bytes as [name], whole or not at all:
     * 1. to a temporary name, forced to the device (fsync);
     * 2. read back, its streamed SHA-256 compared with [source]'s;
     * 3. the previous [name], if there is one, kept as `<name>.prev` ([prev]) when no `.prev` exists;
     *    an existing `.prev` is never overwritten, and the previous [name] waits under a temporary
     *    name instead, deleted once the new one is in place;
     * 4. moved over [name], and read back and compared again.
     *
     * Then `<name>.prev` goes, unless [keepPrevious]: the caller keeps it until a write whose backup
     * is complete. A failure at any step throws, and leaves the previous [name] as it was (with none,
     * no bad [name] is left); a write interrupted between steps 3 and 4 leaves the old copy as
     * `<name>.prev`, or beside a kept `.prev`. So an old copy or the new one is always there.
     */
    fun write(name: String, source: File, keepPrevious: Boolean = false)

    /** The bytes of [name], streamed, or null when there is no such file. */
    fun openRead(name: String): InputStream?

    /** The names of the files in the folder, `.prev` copies included; temporary ones are left out. */
    fun list(): List<String>

    /** Whether the folder is there to be written: a removed card or an unmounted drive is not. */
    fun reachable(): Boolean

    companion object {
        /** The name the previous copy of [name] is kept under while [name] is replaced, or after. */
        fun prev(name: String) = "$name.prev"
    }
}

/** A [BackupTarget] in a directory of the file system, as the desktop has it. */
class DirectoryBackupTarget internal constructor(private val root: Path, private val disk: Disk) : BackupTarget {

    constructor(root: Path) : this(root, Disk())

    /** The file operations a write is made of; a test replaces one to corrupt or interrupt it. */
    internal open class Disk {
        /** Copies [source] to [to], forced to the device before it returns. */
        open fun copy(source: File, to: Path) {
            FileChannel.open(to, CREATE, TRUNCATE_EXISTING, WRITE).use { channel ->
                source.inputStream().use { it.copyTo(Channels.newOutputStream(channel)) }
                channel.force(true)
            }
        }

        open fun move(from: Path, to: Path) {
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }

    override fun write(name: String, source: File, keepPrevious: Boolean) {
        val target = file(name)
        val prev = file(BackupTarget.prev(name))
        val tmp = root.resolve(TEMP_PREFIX + name)
        val hash = sha256Hex(source)
        // A kept .prev is never overwritten: the old target then waits under a temporary name instead.
        val aside = if (prev.exists()) root.resolve(TEMP_PREFIX + name + ".old") else prev
        try {
            disk.copy(source, tmp)
            if (sha256Hex(tmp.toFile()) != hash) throw IOException(NOT_KEPT)
            val hadOld = target.exists()
            if (hadOld) disk.move(target, aside)
            try {
                disk.move(tmp, target)
                if (sha256Hex(target.toFile()) != hash) throw IOException(NOT_KEPT)
            } catch (e: IOException) {
                try {
                    if (hadOld) disk.move(aside, target) else Files.deleteIfExists(target)
                } catch (again: IOException) {
                    e.addSuppressed(again)
                }
                throw e
            }
            if (aside != prev) Files.deleteIfExists(aside)
            if (!keepPrevious) Files.deleteIfExists(prev)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    override fun openRead(name: String): InputStream? = file(name).takeIf { it.isRegularFile() }?.let { Files.newInputStream(it) }

    override fun list(): List<String> =
        root.takeIf { it.isDirectory() }?.listDirectoryEntries()?.filter { it.isRegularFile() }?.map { it.name }
            ?.filter { !it.startsWith(TEMP_PREFIX) }.orEmpty()

    override fun reachable(): Boolean = root.isDirectory() && Files.isWritable(root)

    /** Only a plain file name reaches the disk, so no name walks out of [root]. */
    private fun file(name: String): Path {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "not a backup name: $name" }
        return root.resolve(name)
    }

    private companion object {
        const val NOT_KEPT = "The backup folder did not keep the backup whole."
    }
}
