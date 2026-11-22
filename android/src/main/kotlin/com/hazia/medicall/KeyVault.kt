package com.hazia.medicall

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the key derived from the passphrase on this device (SPEC:R10, SPEC:R109), so the passphrase is
 * asked once per device. The key is sealed with an AES-256-GCM key that lives in the Android key store
 * and never leaves it; the sealed bytes sit in app-private storage, which the manifest keeps out of
 * Android's backups (`allowBackup="false"`).
 */
class KeyVault(context: Context, private val file: File = File(context.applicationContext.filesDir, FILE_NAME)) {

    fun store(key: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.iv + cipher.doFinal(key)
        val temp = File(file.path + ".tmp")
        temp.writeBytes(sealed)
        check(temp.renameTo(file) || (file.delete() && temp.renameTo(file))) { "could not store the key" }
    }

    /** The stored key, or null when none is stored or it cannot be opened (the key store lost its key). */
    fun load(): ByteArray? {
        if (!file.exists()) return null
        val sealed = file.readBytes()
        if (sealed.size <= NONCE_BYTES) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, sealed, 0, NONCE_BYTES))
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        }.getOrNull()
    }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance(KEYSTORE).apply { load(null) }.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun wrappingKey(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "medicall-database-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        const val FILE_NAME = "database-key.sealed"
    }
}
