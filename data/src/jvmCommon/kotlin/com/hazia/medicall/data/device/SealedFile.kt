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

    /**
     * Keeps a copy of the file as it is beside it, never deleted, so the next [write] does not lose what it held. The
     * file itself stays in place until a [write] replaces it: an app that dies, or a key store that fails, between the
     * two still finds the file on the next start, never no file (a first run, the lock off).
     */
    fun keepCopyAside()
}
