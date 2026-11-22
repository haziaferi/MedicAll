package com.hazia.medicall.data.blob

import com.hazia.medicall.core.crypto.LineCipher
import com.hazia.medicall.core.crypto.sha256
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.Ids
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.BlobRefEntity
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.MAIN
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.REMOVED

/**
 * Files kept for a profile (SPEC:R10, SPEC:R23). A file is stored once by the SHA-256 of its plain
 * bytes, sealed whole by [cipher] and kept as that sealed line's UTF-8 text, so no copy, here or in
 * the sync folder, holds the plain bytes. A `blob_ref` row names it for its profile; [BlobSync]
 * carries the sealed bytes through the folder.
 */
class Blobs internal constructor(
    private val writes: LocalWrites,
    db: MedicAllDatabase,
    private val store: BlobStore,
    private val cipher: LineCipher,
) {
    private val refs = db.blobRefDao()

    /**
     * Stores [bytes] for [profileId] as [name] of type [mime], and returns the new file reference's
     * id. The same bytes added again are stored once, under two references. A profile that does not
     * exist is refused by the reference's foreign key (SPEC:R15) when the write commits.
     */
    suspend fun add(profileId: String, name: String, mime: String, bytes: ByteArray): String {
        val hash = storeBytes(bytes)
        val id = Ids.new()
        writes.write(mapOf(BLOB_REF to listOf(id))) { staged ->
            val s = writes.clock.tick()
            writes.merger.created(staged, Row(BLOB_REF, id, mapOf(
                MAIN to Group(s, mapOf("profile_id" to profileId, "sha256" to hash, "size" to bytes.size.toLong(), "name" to name, "mime" to mime)),
                REMOVED to Group(s, mapOf("removed_at" to null)),
            )))
        }
        return id
    }

    /**
     * Stores [bytes] by their hash, which it returns, with no file reference: a restore re-adding a
     * file a merged reference names (SPEC:R22), or a repair (SPEC:R23). A copy here that is damaged is
     * replaced; an intact one is left as it is.
     */
    internal fun storeBytes(bytes: ByteArray): String {
        val hash = sha256Hex(bytes)
        if (verified(store, cipher, hash) == null) store.write(hash, cipher.seal(bytes).encodeToByteArray())
        return hash
    }

    /**
     * The plain bytes of file reference [id], or null: no such reference, the bytes have not arrived
     * from the device that stored them, or the copy here does not open or opens to other bytes than
     * its name (SPEC:R23). A damaged copy is dropped, so the next sync fetches it again.
     */
    suspend fun read(id: String): ByteArray? {
        val ref = refs.blobRefs(listOf(id)).singleOrNull() ?: return null
        return verified(store, cipher, ref.sha256)
    }

    /** Every file reference, the bin's included: what a backup holds (SPEC:R22). */
    internal suspend fun refs(): List<BlobRefEntity> = refs.allBlobRefs()

    /** The names of the copies held here. */
    internal fun held(): List<String> = store.list()

    /**
     * The plain bytes of the copy named [hash], when it opens and matches its name (SPEC:R23); null
     * otherwise. Unlike [read], a damaged copy is left where it is, so the integrity check that looks
     * for damage finds it, and repairs it from a backup.
     */
    internal fun intact(hash: String): ByteArray? = store.read(hash)?.let { opened(cipher, it, hash) }
}

/** The SHA-256 of [bytes] in lower-case hex. */
internal fun sha256Hex(bytes: ByteArray): String =
    sha256(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** The plain bytes [sealed] opens to under [cipher], when they match [name]; null otherwise. */
internal fun opened(cipher: LineCipher, sealed: ByteArray, name: String): ByteArray? =
    cipher.open(sealed.decodeToString())?.takeIf { sha256Hex(it) == name }

/** [name]'s plain bytes in [store] when they open and match their name; a damaged copy is deleted. */
internal fun verified(store: BlobStore, cipher: LineCipher, name: String): ByteArray? {
    val sealed = store.read(name) ?: return null
    opened(cipher, sealed, name)?.let { return it }
    store.delete(name)
    return null
}
