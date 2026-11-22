package com.hazia.medicall.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val NONCE_BYTES = 12
private const val TAG_BITS = 128
private val random = SecureRandom()

internal actual fun pbkdf2Sha256(passphrase: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    val spec = PBEKeySpec(passphrase, salt, iterations, length * 8)
    try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

internal actual fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray {
    val nonce = secureRandomBytes(NONCE_BYTES)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
    return nonce + cipher.doFinal(plain)
}

internal actual fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray? {
    if (sealed.size < NONCE_BYTES + TAG_BITS / 8) return null
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
    return try {
        cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    } catch (_: AEADBadTagException) {
        null
    }
}

internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)
