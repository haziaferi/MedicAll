package com.hazia.medicall.core.crypto

/** PBKDF2-HMAC-SHA256 of [passphrase] over [salt], [length] bytes long. */
internal expect fun pbkdf2Sha256(passphrase: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray

/** AES-256-GCM with a fresh random 12-byte nonce: returns nonce + ciphertext + 16-byte tag. */
internal expect fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray

/** The plain bytes of [sealed] (nonce + ciphertext + tag), or null when the tag does not verify. */
internal expect fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray?

internal expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

expect fun sha256(data: ByteArray): ByteArray

expect fun secureRandomBytes(count: Int): ByteArray
