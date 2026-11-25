package com.hazia.medicall

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.hazia.medicall.core.lock.AppLock
import com.hazia.medicall.data.MedicAllCore
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.Opened
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.device.FolderRegistry
import com.hazia.medicall.data.device.JoinedFolder
import com.hazia.medicall.data.device.MedicAllDevice
import com.hazia.medicall.data.device.SealedFile
import com.hazia.medicall.data.deviceId
import com.hazia.medicall.data.isRecovering
import com.hazia.medicall.data.openEncryptedDatabase
import com.hazia.medicall.data.ownLogRead
import com.hazia.medicall.data.setAsideDatabaseFiles
import java.io.File

/**
 * [MedicAllDevice] on an Android phone (SPEC:R110): each joined folder opens with its own vault
 * ([KeyVault.forFolder]: database key and passphrase), its own SQLCipher database, file store, work folder and
 * write journal, over its Storage Access Framework tree. The device's settings and lock state sit in the sealed
 * file of [KeyVault.deviceFile]; the lock measures time on [SystemClock.elapsedRealtime] (SPEC:R11).
 *
 * Blocking: build and call it off the main thread. [close] closes every database it opened.
 */
class AndroidDevice internal constructor(context: Context, settings: SealedFile) {
    constructor(context: Context) : this(context, KeyVault.deviceFile(context))

    private val app = context.applicationContext
    private val databases = java.util.concurrent.ConcurrentHashMap<String, MedicAllDatabase>()
    private val registry = FolderRegistry(settings)

    /** This phone's device id, one for every folder: a sync folder's `devices/<id>/` names the phone. */
    val deviceId: String = deviceId(File(app.filesDir, DEVICE_FILE))

    val device = MedicAllDevice(
        registry,
        open = ::open,
        lock = AppLock(registry.lock(), SystemClock::elapsedRealtime, registry::saveLock),
        syncFolderOf = ::syncFolder,
        setAside = { f ->
            databases.remove(f.id)?.close()
            setAsideDatabaseFiles(app.getDatabasePath(f.databaseName).path, System.currentTimeMillis())
            // The passphrase would open the folder's future content: a device that left keeps only the database key,
            // beside the database it set aside.
            KeyVault.forFolder(app, f.id).forgetPassphrase()
        },
    )

    /** Opens every joined folder with the passphrase its vault keeps (SPEC:R10: entered once per device). */
    suspend fun openAll(): Map<String, Result<Opened>> = device.openAll { f -> KeyVault.forFolder(app, f.id).loadPassphrase() }

    /** The lock's timeout, in minutes, as kept (SPEC:R11). */
    fun lockMinutes(): Int = registry.lock().timeoutMinutes

    /** Whether the lock's fingerprint is on. */
    fun fingerprintOn(): Boolean = registry.lock().fingerprint

    /** The backup folder chosen for [folderId] (SPEC:R22), or null while none is. */
    fun backupTarget(folderId: String): SafBackupTarget? =
        device.folders().firstOrNull { it.id == folderId }?.backupUri?.let { SafBackupTarget(app, Uri.parse(it)) }

    fun setBackup(folderId: String, tree: Uri) = registry.setBackup(folderId, tree.toString())

    fun close() {
        device.folders().forEach { device.core(it.id)?.close() }
        databases.values.forEach { it.close() }
        databases.clear()
    }

    /**
     * Opens folder [f] with [passphrase]. The database opens with the folder's own key (a new one sets an old file
     * aside, SPEC:R10); the key and the passphrase are stored in the folder's vault only once the passphrase has
     * opened the folder, so a refused or failed open stores nothing. The database is then closed again, and one
     * made just now for a folder not joined yet holds nothing and is removed.
     */
    private suspend fun open(f: JoinedFolder, passphrase: CharArray): Opened {
        databases[f.id]?.let { return reopen(f, it, passphrase) }
        val vault = KeyVault.forFolder(app, f.id)
        val key = vault.databaseKey()
        val joined = f.id in device.folders().map { it.id }
        var db: MedicAllDatabase? = null
        try {
            val open = openEncryptedDatabase(app, key.bytes, f.databaseName, setAsideExisting = key.isNew)
            db = open.database
            val opened = MedicAllCore.open(
                open.database, syncFolder(f), deviceId, passphrase,
                DirectoryBlobStore(File(app.filesDir, "blobs/${f.id}").toPath()), File(app.cacheDir, "work/${f.id}"),
                recovered = open.recovered,
                ownLogRead = { ownLogRead(app, f.databaseName) },
            )
            if (opened is Opened.Ready) {
                try {
                    if (key.isNew) vault.store(key.bytes)
                    vault.storePassphrase(passphrase)
                } catch (e: Exception) {
                    // Not kept: the core goes too, and its copy of the passphrase with it.
                    opened.core.close()
                    throw e
                }
                databases[f.id] = open.database
                db = null
            }
            return opened
        } finally {
            key.bytes.fill(0)
            // Not kept: closed, and removed when it was made just now for a folder not joined.
            db?.close()
            if (db != null && !joined && key.isNew) {
                app.deleteDatabase(f.databaseName)
                ownLogRead(app, f.databaseName)
            }
        }
    }

    /** A new core over the folder's database, already open ([MedicAllDevice.reopen]); a passphrase that opens it is kept. */
    private suspend fun reopen(f: JoinedFolder, db: MedicAllDatabase, passphrase: CharArray): Opened {
        val opened = MedicAllCore.open(
            db, syncFolder(f), deviceId, passphrase,
            DirectoryBlobStore(File(app.filesDir, "blobs/${f.id}").toPath()), File(app.cacheDir, "work/${f.id}"),
            recovered = isRecovering(app, f.databaseName),
            ownLogRead = { ownLogRead(app, f.databaseName) },
        )
        if (opened is Opened.Ready) {
            try {
                KeyVault.forFolder(app, f.id).storePassphrase(passphrase)
            } catch (e: Exception) {
                opened.core.close()
                throw e
            }
        }
        return opened
    }

    private fun syncFolder(f: JoinedFolder) =
        SafSyncFolder(app, Uri.parse(f.folderUri), deviceId, File(app.filesDir, "$JOURNALS/${f.id}"), SafSyncFolder.Io())
            .also { check(it.reachable()) { "The sync folder of ${f.label} cannot be reached." } }

    companion object {
        /** The phone's device id file, as phase 1 named it, so a phone keeps its id. */
        const val DEVICE_FILE = "device-id"

        /** One write journal per joined folder ([SafSyncFolder]): the same path in two folders must not share a note. */
        const val JOURNALS = "saf-journals"
    }
}
