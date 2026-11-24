package com.hazia.medicall

import android.content.Context
import android.net.Uri
import com.hazia.medicall.SafTree.Companion.TEMP_PREFIX
import com.hazia.medicall.data.backup.BackupTarget
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The backup folder (SPEC:R22) on a tree the person picked through the Storage Access Framework, with the
 * contract of [BackupTarget] as [com.hazia.medicall.data.backup.DirectoryBackupTarget] keeps it. SAF has no
 * atomic move and no fsync, so a write goes:
 * 1. the source's SHA-256 and size recorded in a hidden document, then the source to a temporary document;
 * 2. that read back, its streamed SHA-256 compared with the source's;
 * 3. the previous target renamed to `<name>.prev` when no `.prev` exists, or else to a hidden temporary name
 *    (an existing `.prev` is never overwritten);
 * 4. the temporary document renamed onto the now free name, and read back and compared; a provider that
 *    cannot rename gets a copy, checked the same way;
 * 5. the old copy waiting under the temporary name deleted, the `.prev` unless kept, then the record.
 *
 * A failure in step 4 deletes the new target and puts the old one back. A crash cannot leave a torn target
 * while the rename works. A target found beside a waiting old copy was put in place by a write a crash cut
 * short: the next write keeps it when it matches the record, and deletes it otherwise (a copy torn where the
 * provider cannot rename), so a good target is never deleted and a torn one never moved over a whole copy.
 *
 * Blocking: call it off the main thread.
 */
class SafBackupTarget internal constructor(context: Context, tree: Uri, private val io: Io) : BackupTarget {

    constructor(context: Context, tree: Uri) : this(context, tree, Io())

    /** How bytes reach a document, and a hook after each rename; a test replaces them to corrupt or interrupt a write. */
    internal open class Io {
        open fun copy(name: String, from: InputStream, to: OutputStream) {
            from.copyTo(to, 64 * 1024)
        }

        /** Called once the document named [from] has been renamed [to]. */
        open fun moved(from: String, to: String) {}

        /** Called once [name] is in place and has read back whole, before the old copies are cleared. */
        open fun landed(name: String) {}
    }

    private val docs = SafTree(context.applicationContext, tree)
    private val root = docs.rootId

    override fun write(name: String, source: File, keepPrevious: Boolean) {
        check(name)
        val prev = BackupTarget.prev(name)
        val tmpName = TEMP_PREFIX + name
        val oldName = TEMP_PREFIX + name + ".old"
        val hash = source.inputStream().use(::sha256Hex)
        val recordName = TEMP_PREFIX + name + ".sha256"
        val size = source.length()
        val children = docs.children(root).associateBy { it.name }
        // A kept .prev is never overwritten: the old target then waits under a temporary name instead.
        val aside = if (prev in children) oldName else prev
        var target = children[name]
        if (target != null && aside == oldName && oldName in children) {
            // An old copy already waits aside, so this target was put in place by a write a crash cut short. The
            // record that write left says what the target should hold: if it does, the write had finished and the
            // waiting copy is superseded; if not (a copy torn where the provider cannot rename), it goes.
            val recorded = children[recordName]?.let { readRecord(it.id) }
            val good = recorded != null && (target.size == null || target.size == recorded.second) && docs.sha256(target.id) == recorded.first
            if (good) {
                docs.delete(children.getValue(oldName).id)
            } else {
                docs.delete(target.id)
                target = null
            }
        }
        try {
            children[recordName]?.let { docs.delete(it.id) }
            docs.create(root, recordName).let { record -> docs.openOut(record.id, "w").use { it.write("$hash $size".toByteArray()) } }
            children[tmpName]?.let { docs.delete(it.id) }
            val tmp = docs.create(root, tmpName)
            put(tmpName, tmp.id, source)
            if (docs.sha256(tmp.id) != hash) throw IOException(NOT_KEPT)

            val hadOld = target != null
            if (target != null) move(target.id, name, aside)
            try {
                val landed = land(tmp.id, name)
                if (docs.sha256(landed) != hash) throw IOException(NOT_KEPT)
            } catch (e: Exception) {
                try {
                    docs.child(root, name)?.let { docs.delete(it.id) }
                    if (hadOld) move(docs.child(root, aside)?.id ?: throw IOException("The old backup is gone."), aside, name)
                } catch (again: Exception) {
                    e.addSuppressed(again)
                }
                throw e
            }
            io.landed(name)
            docs.child(root, oldName)?.let { docs.delete(it.id) }
            if (!keepPrevious) docs.child(root, prev)?.let { docs.delete(it.id) }
            docs.child(root, recordName)?.let { docs.delete(it.id) }
        } catch (e: Exception) {
            // A write that failed has put the old target back, so its record has nothing left to vouch for.
            // (A crash leaves it, for the next write's check.)
            runCatching { docs.child(root, recordName)?.let { docs.delete(it.id) } }.exceptionOrNull()?.let(e::addSuppressed)
            throw e
        } finally {
            // Best effort: a temporary document left behind is hidden from [list] and replaced by the next write.
            runCatching { docs.child(root, tmpName)?.let { docs.delete(it.id) } }
        }
    }

    override fun openRead(name: String): InputStream? {
        check(name)
        return docs.child(root, name)?.takeIf { !it.isDir }?.let { docs.openIn(it.id) }
    }

    override fun list(): List<String> {
        if (docs.self(root)?.isDir != true) return emptyList()
        return docs.children(root).filter { !it.isDir && !it.name.startsWith(TEMP_PREFIX) }.map { it.name }
    }

    override fun reachable(): Boolean = docs.reachable()

    private fun put(name: String, id: String, source: File) {
        docs.openOut(id, "w").use { out -> source.inputStream().use { io.copy(name, it, out) } }
    }

    /**
     * Puts the checked temporary document [tmpId] in place as [name], which is free, and returns its id: by a
     * rename, so no torn copy can stand under [name]; a provider that cannot rename gets a copy instead, which
     * the caller checks (the temporary document then goes in the cleanup).
     */
    private fun land(tmpId: String, name: String): String = try {
        docs.rename(tmpId, name)
    } catch (_: UnsupportedOperationException) {
        val fresh = docs.create(root, name)
        docs.openIn(tmpId).use { input -> docs.openOut(fresh.id, "w").use { io.copy(name, input, it) } }
        fresh.id
    }

    /** The hash and size a write recorded for the target it was putting in place, or null when unreadable. */
    private fun readRecord(id: String): Pair<String, Long>? = runCatching {
        val (hash, size) = docs.openIn(id).use { it.readBytes() }.decodeToString().split(' ')
        hash to size.toLong()
    }.getOrNull()

    /** Renames [id] from [from] to [to], which is free; a provider that cannot rename gets a checked copy instead. */
    private fun move(id: String, from: String, to: String) {
        try {
            docs.rename(id, to)
        } catch (_: UnsupportedOperationException) {
            val copy = docs.create(root, to)
            docs.openIn(id).use { input -> docs.openOut(copy.id, "w").use { input.copyTo(it, 64 * 1024) } }
            if (docs.sha256(copy.id) != docs.sha256(id)) {
                docs.delete(copy.id)
                throw IOException(NOT_KEPT)
            }
            docs.delete(id)
        }
        io.moved(from, to)
    }

    /** Only a plain file name reaches the folder. */
    private fun check(name: String) {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "not a backup name: $name" }
    }

    private companion object {
        const val NOT_KEPT = "The backup folder did not keep the backup whole."
    }
}
