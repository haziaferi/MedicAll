package com.hazia.medicall

import android.content.Context
import android.net.Uri
import com.hazia.medicall.SafTree.Companion.TEMP_PREFIX
import com.hazia.medicall.data.sync.SyncFolder
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

/**
 * The sync folder (SPEC:R16) on a tree the person picked through the Storage Access Framework, which has no
 * atomic rename. So [replace] writes `.tmp-<name>` first, as the bytes followed by a seal (a marker and
 * their SHA-256), and reads it back byte for byte; it then rewrites the target in place, reads that back,
 * and deletes the temporary file. A crash halfway through the target leaves a sealed temporary file: the
 * next [read], [size] or [replace] of that target finishes the write from it, so a reader sees the old
 * bytes or the new ones, never a mix. A temporary file without a whole seal was cut short before the target
 * was touched, and is dropped. [list] leaves temporary files out.
 *
 * Blocking: call it off the main thread.
 */
class SafSyncFolder internal constructor(context: Context, tree: Uri, private val io: Io) : SyncFolder {

    constructor(context: Context, tree: Uri) : this(context, tree, Io())

    /** How bytes reach a document; a test replaces it to interrupt a write. */
    internal open class Io {
        open fun write(name: String, bytes: ByteArray, to: OutputStream) = to.write(bytes)
    }

    private val docs = SafTree(context.applicationContext, tree)

    /** The persisted read-and-write grant exists, and the tree exists and can be written (SPEC:R16, folder unreachable). */
    fun reachable(): Boolean = docs.reachable()

    override fun list(dir: String): List<String> {
        val id = docs.dir(segments(dir), create = false) ?: return emptyList()
        return docs.children(id).map { it.name }.filter { !it.startsWith(TEMP_PREFIX) }
    }

    override fun size(path: String): Long? {
        val (dirId, name) = locate(path) ?: return null
        val doc = mend(dirId, name)?.takeIf { !it.isDir } ?: return null
        return doc.size ?: docs.openIn(doc.id).use { it.readBytes().size.toLong() }
    }

    override fun read(path: String): ByteArray? {
        val (dirId, name) = locate(path) ?: return null
        val doc = mend(dirId, name)?.takeIf { !it.isDir } ?: return null
        return docs.openIn(doc.id).use { it.readBytes() }
    }

    override fun replace(path: String, bytes: ByteArray) {
        val parts = segments(path)
        require(parts.isNotEmpty()) { "no file in $path" }
        val name = parts.last()
        val dirId = docs.dir(parts.dropLast(1), create = true) ?: throw IOException("A file stands where the folder of $path should be.")
        val tmpName = TEMP_PREFIX + name
        docs.child(dirId, tmpName)?.let { docs.delete(it.id) } // the new bytes supersede whatever it held
        val sealed = bytes + seal(bytes)
        val tmp = docs.create(dirId, tmpName)
        docs.openOut(tmp.id, "w").use { io.write(tmpName, sealed, it) }
        if (!docs.openIn(tmp.id).use { it.readBytes() }.contentEquals(sealed)) {
            docs.delete(tmp.id)
            throw IOException(NOT_KEPT)
        }
        land(dirId, name, bytes, docs.child(dirId, name))
        docs.delete(tmp.id)
    }

    override fun delete(path: String) {
        val (dirId, name) = locate(path) ?: return
        val children = docs.children(dirId)
        children.firstOrNull { it.name == TEMP_PREFIX + name }?.let { docs.delete(it.id) }
        children.firstOrNull { it.name == name }?.let { docs.delete(it.id) }
    }

    /** The directory id and name of [path], or null when its directory is missing. */
    private fun locate(path: String): Pair<String, String>? {
        val parts = segments(path)
        if (parts.isEmpty()) return null
        return docs.dir(parts.dropLast(1), create = false)?.let { it to parts.last() }
    }

    /**
     * Finishes a write a crash cut short beside [name], and returns [name]'s document as it now stands. A
     * sealed temporary file holds the newer bytes, so they land; an unsealed one never reached the target.
     */
    private fun mend(dirId: String, name: String): SafTree.Doc? {
        val children = docs.children(dirId)
        val target = children.firstOrNull { it.name == name }
        val tmp = children.firstOrNull { it.name == TEMP_PREFIX + name } ?: return target
        val bytes = unseal(docs.openIn(tmp.id).use { it.readBytes() })
        if (bytes != null) land(dirId, name, bytes, target)
        docs.delete(tmp.id)
        return docs.child(dirId, name)
    }

    /** Rewrites [name] with [bytes] and reads it back. */
    private fun land(dirId: String, name: String, bytes: ByteArray, target: SafTree.Doc?) {
        if (target?.isDir == true) throw IOException("A folder stands where $name should be.")
        val id = target?.id ?: docs.create(dirId, name).id
        docs.openOut(id, if (target == null) "w" else "wt").use { io.write(name, bytes, it) }
        if (!docs.openIn(id).use { it.readBytes() }.contentEquals(bytes)) throw IOException(NOT_KEPT)
    }

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    private companion object {
        const val NOT_KEPT = "The sync folder did not keep the file whole."
        val MARK = "medicall-sealed1".toByteArray()
        const val DIGEST = 32

        fun seal(bytes: ByteArray) = MARK + MessageDigest.getInstance("SHA-256").digest(bytes)

        /** The bytes a sealed temporary file holds, or null when its seal is missing or does not match. */
        fun unseal(file: ByteArray): ByteArray? {
            val tail = MARK.size + DIGEST
            if (file.size < tail) return null
            val bytes = file.copyOfRange(0, file.size - tail)
            return bytes.takeIf { seal(it).contentEquals(file.copyOfRange(file.size - tail, file.size)) }
        }
    }
}
