package com.hazia.medicall

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.hazia.medicall.data.MedicAllCore
import com.hazia.medicall.data.device.SealedFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database key [KeyVault.databaseKey] returns. [isNew] when it was made just now: no database made
 * before can open with it, and it is not stored yet; the caller stores it ([KeyVault.store]) once the
 * database has opened with it, so a crash before then leaves no key that opens nothing.
 */
class DatabaseKey(val bytes: ByteArray, val isNew: Boolean)

/**
 * Keeps the database key (random, made once: [databaseKey]) and the passphrase on this device (SPEC:R10,
 * SPEC:R109), so the passphrase is entered once per device: sync unlocks the key file with it, and the
 * backups and the repair of SPEC:R23 open with it, without asking again. Each is sealed in its own file
 * with an AES-256-GCM key that lives in the Android key store and never leaves it; the sealed bytes sit
 * in app-private storage, which the manifest keeps out of Android's backups (`allowBackup="false"`).
 *
 * A key the key store really lost (no entry, or a sealed file it no longer opens) is told apart from an
 * error of the moment (the key store not answering), which is thrown: only the first makes a new key.
 * [wrappingKeyNow] is the key store's key, or null when it has none; tests replace it.
 */
class KeyVault internal constructor(
    context: Context,
    private val file: File,
    private val passphraseFile: File,
    private val wrappingKeyNow: () -> SecretKey?,
) {
    constructor(
        context: Context,
        file: File = File(context.applicationContext.filesDir, FILE_NAME),
        passphraseFile: File = File(file.absoluteFile.parentFile, PASSPHRASE_FILE_NAME),
    ) : this(context, file, passphraseFile, ::existingKey)

    /** For tests: a vault over [file] whose key store answers with [wrappingKeyNow]. */
    internal constructor(context: Context, file: File, wrappingKeyNow: () -> SecretKey?) :
        this(context, file, File(file.absoluteFile.parentFile, PASSPHRASE_FILE_NAME), wrappingKeyNow)

    private sealed interface Sealed {
        data object Missing : Sealed
        data object Lost : Sealed
        class Opened(val bytes: ByteArray) : Sealed
    }

    fun store(key: ByteArray) = seal(file, key)

    /**
     * The kept database key, or a new one ([MedicAllCore.newDatabaseKey]) when none is kept or the key store
     * lost the key that sealed it; the sealed file of a lost key is kept aside, never overwritten. A new key is
     * not stored here (see [DatabaseKey]). Throws when the key store fails for a moment, so nothing is replaced.
     */
    fun databaseKey(): DatabaseKey = when (val s = open(file)) {
        is Sealed.Opened -> DatabaseKey(s.bytes, isNew = false)
        Sealed.Missing -> DatabaseKey(MedicAllCore.newDatabaseKey(), isNew = true)
        Sealed.Lost -> {
            check(file.renameTo(File(file.path + LOST_SUFFIX + System.currentTimeMillis()))) { "could not set aside ${file.name}" }
            DatabaseKey(MedicAllCore.newDatabaseKey(), isNew = true)
        }
    }

    /** The stored key, or null when none is stored or the key store lost the key that sealed it; throws on an error of the moment. */
    fun load(): ByteArray? = (open(file) as? Sealed.Opened)?.bytes

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

    /** The stored passphrase, or null when none is stored or it cannot be opened; the caller clears it after use. Throws on an error of the moment. */
    fun loadPassphrase(): CharArray? {
        val bytes = (open(passphraseFile) as? Sealed.Opened)?.bytes ?: return null
        return try {
            val decoded = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            CharArray(decoded.remaining()).also { decoded.get(it) }.also { if (decoded.hasArray()) decoded.array().fill(' ') }
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * [target] as a [SealedFile] sealed by this vault's key-store key: read gives null when it is missing or the key
     * store lost its key (the device then starts as on a first run), and throws on an error of the moment.
     */
    internal fun sealedFile(target: File): SealedFile = object : SealedFile {
        override fun read(): ByteArray? = when (val s = open(target)) {
            is Sealed.Opened -> s.bytes
            Sealed.Missing -> null
            // Moved aside, never overwritten: a key store that answers again later may still open it. Here the device
            // starts as on a first run, the lock off (SPEC:R11), so the file is moved, not copied.
            Sealed.Lost -> null.also { check(target.renameTo(lostName(target))) { "could not set aside ${target.name}" } }
        }

        override fun write(bytes: ByteArray) = seal(target, bytes)

        // A copy, not a rename: [target] stays until [write] renames the new file over it.
        override fun keepCopyAside() {
            if (!target.exists()) return
            val temp = File(target.path + ".tmp-" + java.util.UUID.randomUUID())
            try {
                java.io.FileOutputStream(temp).use { out ->
                    target.inputStream().use { it.copyTo(out) }
                    out.fd.sync()
                }
                check(temp.renameTo(lostName(target))) { "could not keep a copy of ${target.name}" }
            } finally {
                temp.delete()
            }
        }
    }

    /** A name beside [target] that no kept copy holds yet, so a copy never replaces an older one. */
    private fun lostName(target: File): File {
        val stamp = target.path + LOST_SUFFIX + System.currentTimeMillis()
        return generateSequence(0) { it + 1 }.map { File(if (it == 0) stamp else "$stamp-$it") }.first { !it.exists() }
    }

    /** Forgets the kept passphrase, as when the device leaves the folder; the database key stays beside its set-aside database. */
    fun forgetPassphrase() {
        passphraseFile.delete()
    }

    /**
     * Writes [plain] sealed to [target] in one step: to a temporary file of its own, flushed to the disk, then renamed
     * over [target], so neither a power cut nor a second writer leaves a short or mixed file. The rename replaces
     * [target] in one step on Android; [target] is never deleted first, so a rename that fails leaves it as it was,
     * never no file (for the device's settings, a first run with the lock off).
     */
    private fun seal(target: File, plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKeyNow() ?: newWrappingKey())
        val sealed = cipher.iv + cipher.doFinal(plain)
        val temp = File(target.path + ".tmp-" + java.util.UUID.randomUUID())
        try {
            java.io.FileOutputStream(temp).use { out ->
                out.write(sealed)
                out.fd.sync()
            }
            check(temp.renameTo(target)) { "could not store ${target.name}" }
        } finally {
            temp.delete()
        }
    }

    /**
     * [source] opened. Missing: no file. Lost: the key store has no key, or its key fails the file's
     * authentication tag (a key made anew, or a file cut short). Any other failure is thrown.
     */
    private fun open(source: File): Sealed {
        if (!source.exists()) return Sealed.Missing
        val sealed = source.readBytes()
        if (sealed.size <= NONCE_BYTES) return Sealed.Lost
        val key = wrappingKeyNow() ?: return Sealed.Lost
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, NONCE_BYTES))
            Sealed.Opened(cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES))
        } catch (_: AEADBadTagException) {
            Sealed.Lost
        }
    }

    private fun newWrappingKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
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

        /**
         * `-v2` since the key became random (SPEC:R10, 2026-11-24): a phone that kept a key derived from the
         * passphrase finds no v2 file, makes a random key and sets its old database aside, which the sync folder
         * fills again. The old `database-key.sealed` is left where it is.
         */
        const val FILE_NAME = "database-key-v2.sealed"
        const val PASSPHRASE_FILE_NAME = "passphrase.sealed"

        /** Beside a sealed key file the key store can no longer open; it is kept, never deleted. */
        const val LOST_SUFFIX = ".unopenable-"

        /** The device's own settings: the folders it joins and its app lock (SPEC:R110, SPEC:R11). */
        const val DEVICE_FILE_NAME = "device-settings.sealed"

        /**
         * The vault of joined folder [folderId] (SPEC:R110): its own database key and passphrase files, sealed with the
         * one key-store key. A device that joins two folders keeps two database keys and two passphrases (SPEC:R109).
         */
        fun forFolder(context: Context, folderId: String): KeyVault {
            require(folderId.isNotEmpty() && folderId.all { it.isLetterOrDigit() || it == '-' }) { "not a folder id: $folderId" }
            val dir = context.applicationContext.filesDir
            return KeyVault(context, File(dir, "database-key-v2-$folderId.sealed"), File(dir, "passphrase-$folderId.sealed"))
        }

        /** The device's settings file ([DEVICE_FILE_NAME]), sealed by the key store, never synced or backed up. */
        fun deviceFile(context: Context): SealedFile =
            KeyVault(context).sealedFile(File(context.applicationContext.filesDir, DEVICE_FILE_NAME))

        /** The key store's key, or null when it has none; throws when the key store cannot be read. */
        internal fun existingKey(): SecretKey? =
            (KeyStore.getInstance(KEYSTORE).apply { load(null) }.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }
}
