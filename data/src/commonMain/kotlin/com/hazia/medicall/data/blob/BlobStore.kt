package com.hazia.medicall.data.blob

/**
 * A file's sealed bytes as this device keeps them, by blob name ([BlobName]); apart from the sync
 * folder, which is only how they travel. Blocking: call it off the main thread.
 */
interface BlobStore {
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
    fun list(): List<String>
}

/**
 * A stored file's name, on each device and in the sync folder: the SHA-256 of its plain bytes in
 * lower-case hex (SPEC:R23), so two devices storing the same file store one, and bytes that open
 * to something else are known for damaged.
 */
internal object BlobName {
    private val NAME = Regex("^[0-9a-f]{64}$")

    /** Whether [name] is one this app writes; anything else is left alone. */
    fun isValid(name: String) = NAME.matches(name)
}
