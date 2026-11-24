package com.hazia.medicall.data.device

/**
 * One small file of this device's own settings, sealed by the platform's key store (SPEC:R10): on Android,
 * `KeyVault.deviceFile`. It never goes to a sync folder or a backup.
 */
interface SealedFile {
    /** The file's plain bytes, or null when there is none or it no longer opens (the key store lost its key). */
    fun read(): ByteArray?

    /** Replaces the whole file in one step, sealed. */
    fun write(bytes: ByteArray)
}
