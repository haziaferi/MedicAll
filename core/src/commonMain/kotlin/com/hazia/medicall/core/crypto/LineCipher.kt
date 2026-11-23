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
 * The key id is bound into the tag as associated data, so a line cannot be relabelled with another id.
 *
 * Nonces are random, 96 bits: safe for about 2^32 seals per key, far beyond what one person's records need.
 */
@OptIn(ExperimentalEncodingApi::class)
class LineCipher(keys: Map<String, ByteArray>, val currentKeyId: String) {

    private val keys: Map<String, ByteArray> = keys.mapValues { it.value.copyOf() }

    init {
        require(currentKeyId in this.keys) { "no key for the current key id" }
        require('.' !in currentKeyId) { "a key id cannot contain a dot" }
    }

    fun seal(plain: ByteArray): String =
        currentKeyId + "." + Base64.UrlSafe.encode(aesGcmSeal(keys.getValue(currentKeyId), plain, currentKeyId.encodeToByteArray()))

    /** Every key this cipher can open lines with, by key id (copies); only [KeyFolder] reads it, to carry them across a passphrase change. */
    internal fun knownKeys(): Map<String, ByteArray> = keys.mapValues { it.value.copyOf() }

    /** The plain bytes, or null when the key id is unknown, the text is not a sealed line, or it was altered. */
    fun open(line: String): ByteArray? {
        val dot = line.indexOf('.')
        if (dot <= 0) return null
        val keyId = line.substring(0, dot)
        val key = keys[keyId] ?: return null
        val sealed = runCatching { Base64.UrlSafe.decode(line, dot + 1) }.getOrNull() ?: return null
        return aesGcmOpen(key, sealed, keyId.encodeToByteArray())
    }

    /**
     * The key id [line] names, when it has the shape of a sealed line (an id of letters, digits, `-`
     * or `_`, a dot, then base64url long enough for a nonce and a tag), or null. With [knows], it
     * tells a line sealed under a key this device does not hold yet from one that was altered.
     */
    fun keyIdOf(line: String): String? {
        val dot = line.indexOf('.')
        if (dot <= 0) return null
        val keyId = line.substring(0, dot)
        if (!keyId.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }) return null
        val sealed = runCatching { Base64.UrlSafe.decode(line, dot + 1) }.getOrNull() ?: return null
        return keyId.takeIf { sealed.size >= NONCE_BYTES + TAG_BYTES }
    }

    /** Whether this cipher holds the key [keyId]. */
    fun knows(keyId: String): Boolean = keyId in keys

    private companion object {
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
    }
}
