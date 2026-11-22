package com.hazia.medicall.core.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The key a passphrase gives (SPEC:R109): PBKDF2-HMAC-SHA256, 32 bytes. */
object KeyDerivation {
    const val ITERATIONS = 600_000
    const val KEY_BYTES = 32

    fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray =
        deriveBytes(passphrase, salt, iterations, KEY_BYTES)

    internal fun deriveBytes(passphrase: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray =
        pbkdf2Sha256(passphrase, salt, iterations, length)
}

/**
 * Seals what goes into the sync folder (SPEC:R16): AES-256-GCM, one line of text per sealed item,
 * written as `<keyId>.<base64url(nonce + ciphertext + tag)>`. The key id lets a line written under
 * an older key still open after the passphrase changes ([keys] holds every key this device knows).
 */
@OptIn(ExperimentalEncodingApi::class)
class LineCipher(private val keys: Map<String, ByteArray>, val currentKeyId: String) {

    init {
        require(currentKeyId in keys) { "no key for the current key id" }
        require('.' !in currentKeyId) { "a key id cannot contain a dot" }
    }

    fun seal(plain: ByteArray): String =
        currentKeyId + "." + Base64.UrlSafe.encode(aesGcmSeal(keys.getValue(currentKeyId), plain))

    /** Every key this cipher can open lines with, by key id; only [KeyFolder] reads it, to carry them across a passphrase change. */
    internal fun knownKeys(): Map<String, ByteArray> = keys

    /** The plain bytes, or null when the key id is unknown, the text is not a sealed line, or it was altered. */
    fun open(line: String): ByteArray? {
        val dot = line.indexOf('.')
        if (dot <= 0) return null
        val key = keys[line.substring(0, dot)] ?: return null
        val sealed = runCatching { Base64.UrlSafe.decode(line, dot + 1) }.getOrNull() ?: return null
        return aesGcmOpen(key, sealed)
    }
}
