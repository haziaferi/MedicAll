package com.hazia.medicall.data.blob

/**
 * A device's stored files in memory, as a test's devices keep them. Like [DirectoryBlobStore], it
 * refuses a name this app does not write. [reads] counts the reads of each name.
 */
internal class MemoryBlobStore : BlobStore {
    val files = mutableMapOf<String, ByteArray>()
    val reads = mutableMapOf<String, Int>()

    override fun read(name: String): ByteArray? {
        check(name)
        reads.merge(name, 1, Int::plus)
        return files[name]?.copyOf()
    }

    override fun write(name: String, bytes: ByteArray) { files[check(name)] = bytes.copyOf() }
    override fun delete(name: String) { files.remove(check(name)) }
    override fun list() = files.keys.toList()

    private fun check(name: String) = name.also { require(BlobName.isValid(it)) { "not a blob name: $it" } }
}
