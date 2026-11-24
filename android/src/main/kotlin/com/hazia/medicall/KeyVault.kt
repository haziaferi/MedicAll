package com.hazia.medicall

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.hazia.medicall.data.MedicAllCore
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The database key [KeyVault.databaseKey] returns; [isNew] when it was made just now, so no database made before can open with it. */
class DatabaseKey(val bytes: ByteArray, val isNew: Boolean)

/**
 * Keeps the database key (random, made once: [databaseKey]) and the passphrase on this device (SPEC:R10,
 * SPEC:R109), so the passphrase is entered once per device: sync unlocks the key file with it, and the
 * backups and the repair of SPEC:R23 open with it, without asking again. Each is sealed in its own file
 * with an AES-256-GCM key that lives in the Android key store and never leaves it; the sealed bytes sit
 * in app-private storage, which the manifest keeps out of Android's backups (`allowBackup="false"`).
 */
class KeyVault(
    context: Context,
    private val file: File = File(context.applicationContext.filesDir, FILE_NAME),
    private val passphraseFile: File = File(file.absoluteFile.parentFile, PASSPHRASE_FILE_NAME),
) {

    fun store(key: ByteArray) = seal(file, key)

    /**
     * The kept database key, or a new one ([MedicAllCore.newDatabaseKey]) stored at once when none is kept
     * or the kept one no longer opens (the key store lost its key). A new key cannot open a database made
     * before it: the caller opens with `setAsideExisting = isNew`.
     */
    fun databaseKey(): DatabaseKey =
        load()?.let { DatabaseKey(it, isNew = false) } ?: DatabaseKey(MedicAllCore.newDatabaseKey().also(::store), isNew = true)

    /** The stored key, or null when none is stored or it cannot be opened (the key store lost its key). */
    fun load(): ByteArray? = open(file)

    /** Seals [passphrase] (as UTF-8) beside the key; the caller still owns, and clears, [passphrase]. */
    fun storePassphrase(passphrase: CharArray) {
        val encoded = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(passphrase))
        val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        try {
            seal(passphraseFile, bytes)
        } finally {
            bytes.fill(0)
            if (encoded.hasArray()) encoded.array().fill(0)
        }
    }

    /** The stored passphrase, or null when none is stored or it cannot be opened; the caller clears it after use. */
    fun loadPassphrase(): CharArray? {
        val bytes = open(passphraseFile) ?: return null
        return try {
            val decoded = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            CharArray(decoded.remaining()).also { decoded.get(it) }.also { if (decoded.hasArray()) decoded.array().fill(' ') }
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        } finally {
            bytes.fill(0)
        }
    }

    private fun seal(target: File, plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.iv + cipher.doFinal(plain)
        val temp = File(target.path + ".tmp")
        temp.writeBytes(sealed)
        check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "could not store ${target.name}" }
    }

    private fun open(source: File): ByteArray? {
        if (!source.exists()) return null
        val sealed = source.readBytes()
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
        const val PASSPHRASE_FILE_NAME = "passphrase.sealed"
    }
}
