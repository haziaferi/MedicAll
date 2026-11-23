package com.hazia.medicall.data

import com.hazia.medicall.core.crypto.KeyDerivation
import com.hazia.medicall.core.crypto.KeyFolder
import com.hazia.medicall.core.crypto.LineCipher
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.core.crypto.sha256
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.HybridClock
import com.hazia.medicall.core.sync.Ids
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.backup.BackupKind
import com.hazia.medicall.data.backup.BackupRestore
import com.hazia.medicall.data.backup.BackupResult
import com.hazia.medicall.data.backup.BackupTarget
import com.hazia.medicall.data.backup.BackupWriter
import com.hazia.medicall.data.backup.IntegrityCheck
import com.hazia.medicall.data.backup.IntegrityReport
import com.hazia.medicall.data.backup.RestoreReport
import com.hazia.medicall.data.backup.SevenZip
import com.hazia.medicall.data.bin.Bin
import com.hazia.medicall.data.bin.BinEntry
import com.hazia.medicall.data.blob.BlobStore
import com.hazia.medicall.data.blob.BlobSync
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.sync.FolderSync
import com.hazia.medicall.data.sync.ImportReport
import com.hazia.medicall.data.sync.SyncFolder
import com.hazia.medicall.data.sync.loadClock
import java.io.File
import java.time.ZoneId

/** A profile as [MedicAllCore.profiles] lists it; [removedAt] is set while it is in the bin. */
data class ProfileInfo(val id: String, val name: String, val kind: String, val removedAt: Long?)

/** A file reference as [MedicAllCore.files] lists it; [removedAt] is set while it is in the bin. */
data class FileInfo(val id: String, val profileId: String, val name: String, val sha256: String, val removedAt: Long?)

/**
 * The phase 1 data layer as one device runs it (SPEC:R100): profiles, files, the bin, sync and backups
 * over one database, one sync folder and one cipher, wired in one place, as Factotum's `Factotum` wires
 * its repositories. A shell builds it once with [open]; the debug harness of the phase 1 walk is the
 * first shell, and tests build it the same way. It adds no rule of its own: each call goes to the class
 * that holds the rule.
 *
 * - [syncNow] publishes the files the logs will name, writes the logs, then reads the peers' logs and
 *   fetches the files they name ([BlobSync], [FolderSync]).
 * - The backup calls take the backup folder each time, since a shell may pick it after this is built,
 *   and the passphrase, which seals the backups (SPEC:R109).
 *
 * Blocking where the folders block: call it off the main thread.
 */
class MedicAllCore private constructor(
    private val db: MedicAllDatabase,
    folder: SyncFolder,
    device: String,
    clock: HybridClock,
    cipher: LineCipher,
    blobStore: BlobStore,
    private val wallMillis: () -> Long,
    private val workDir: File,
    private val zone: ZoneId,
    recovered: Boolean,
) {
    private val writes = LocalWrites(db, clock)
    private val profiles = db.profileDao()
    private val refs = db.blobRefDao()
    private val blobs = Blobs(writes, db, blobStore, cipher)
    private val bin = Bin(writes, db, wallMillis)
    private val blobSync = BlobSync(db, folder, blobStore, cipher, wallMillis)
    private val sync = FolderSync(db, folder, device, clock, writes.tables, cipher, wallMillis, recovered = recovered)
    private val restorer = BackupRestore(writes, blobs, SevenZip, wallMillis)

    /** A new profile named [name], of [kind] (SPEC:R15), every group stamped with one tick; returns its id. */
    suspend fun addProfile(name: String, kind: String = PERSON): String {
        val id = Ids.new()
        writes.write(mapOf(PROFILE to listOf(id))) { store ->
            val s = writes.clock.tick()
            writes.merger.created(store, Row(PROFILE, id, mapOf(
                NAME to Group(s, mapOf("name" to name)),
                DETAILS to Group(s, mapOf("birth_date" to null, "kind" to kind)),
                REMOVED to Group(s, mapOf("removed_at" to null)),
            )))
        }
        return id
    }

    /** Renames profile [id]: its name group only, so a change of kind elsewhere survives the merge (SPEC:R16). */
    suspend fun rename(id: String, name: String) = writes.edit(PROFILE, id, NAME) { mapOf("name" to name) }

    /** Sets profile [id]'s kind: its details group, apart from the name, keeping the birth date it has. */
    suspend fun changeKind(id: String, kind: String) {
        val birthDate = profiles.profiles(listOf(id)).singleOrNull()?.birthDate
        writes.edit(PROFILE, id, DETAILS) { mapOf("birth_date" to birthDate, "kind" to kind) }
    }

    /** Every profile, the bin's included, by name then id. */
    suspend fun profiles(): List<ProfileInfo> =
        profiles.allProfiles().map { ProfileInfo(it.id, it.name, it.kind, it.removedAt) }.sortedWith(compareBy({ it.name }, { it.id }))

    /** Stores [bytes] for [profileId] as [name] of type [mime] ([Blobs.add]); returns the file reference's id. */
    suspend fun addFile(profileId: String, name: String, mime: String, bytes: ByteArray): String = blobs.add(profileId, name, mime, bytes)

    /** The plain bytes of file reference [id], or null when they have not arrived or are damaged ([Blobs.read]). */
    suspend fun readFile(id: String): ByteArray? = blobs.read(id)

    /** Every file reference, the bin's included. */
    suspend fun files(): List<FileInfo> = refs.allBlobRefs().map { FileInfo(it.id, it.profileId, it.name, it.sha256, it.removedAt) }

    /** Puts a profile ([PROFILE]) or a file reference ([BLOB_REF]) in the bin ([Bin.remove]). */
    suspend fun remove(table: String, id: String) = bin.remove(table, id)

    /** Takes it out of the bin ([Bin.restore]). */
    suspend fun restore(table: String, id: String) = bin.restore(table, id)

    /** What is in the bin, oldest removal first. */
    suspend fun binContents(): List<BinEntry> = bin.contents()

    /** Empties the bin for good; refuses unless [confirmation] is "DELETE" ([Bin.empty], SPEC:R19). */
    suspend fun emptyBin(confirmation: String) = bin.empty(confirmation)

    /** Publishes files and writes the logs, then reads the peers' logs and fetches their files. */
    suspend fun syncNow(): ImportReport {
        blobSync.exchange()
        sync.export()
        return sync.import().also { blobSync.exchange() }
    }

    /** Writes a backup of [kind] to [target] under [passphrase] ([BackupWriter], SPEC:R22). */
    suspend fun backUp(target: BackupTarget, passphrase: CharArray, kind: BackupKind = BackupKind.LATEST): BackupResult =
        BackupWriter(db, blobs, SevenZip, target, wallMillis, zone, workDir).write(kind, passphrase)

    /**
     * Restores the newest backup in [target] ([BackupWriter.newestFirst], [BackupRestore]); null when it
     * does not open with [passphrase]. Throws an [IllegalStateException] when [target] holds no backup.
     */
    suspend fun restoreBackup(target: BackupTarget, passphrase: CharArray): RestoreReport? {
        val name = BackupWriter.newestFirst(target.list()).firstOrNull() ?: throw IllegalStateException("The backup folder holds no backup.")
        val copy = File.createTempFile(TEMP_PREFIX, ".7z", workDir)
        try {
            val input = target.openRead(name) ?: throw IllegalStateException("The backup $name is gone.")
            input.use { i -> copy.outputStream().use { i.copyTo(it) } }
            return restorer.restore(copy, passphrase)
        } finally {
            copy.delete()
        }
    }

    /** Checks every file against its hash and repairs the damaged ones from [target]'s backups ([IntegrityCheck], SPEC:R23). */
    suspend fun checkFiles(target: BackupTarget, passphrase: CharArray): IntegrityReport =
        IntegrityCheck(blobs, target, SevenZip, workDir).run(passphrase)

    companion object {
        /** The kind every profile has in version 1 (SPEC:R15: pet is in the data model, not offered). */
        const val PERSON = "person"
        const val PET = "pet"
        private const val TEMP_PREFIX = "medicall-restore-"

        /**
         * Builds the data layer over [db], whose clock resumes from what it stored, writing [folder] as
         * [device] with [cipher] (from [unlockOrCreate]), keeping files in [blobStore], and working in [workDir].
         */
        suspend fun open(
            db: MedicAllDatabase,
            folder: SyncFolder,
            device: String,
            cipher: LineCipher,
            blobStore: BlobStore,
            workDir: File,
            wallMillis: () -> Long = System::currentTimeMillis,
            zone: ZoneId = ZoneId.systemDefault(),
            recovered: Boolean = false,
        ): MedicAllCore {
            workDir.mkdirs()
            val clock = db.syncDao().loadClock(device, wallMillis)
            return MedicAllCore(db, folder, device, clock, cipher, blobStore, wallMillis, workDir, zone, recovered)
        }

        /**
         * The sync cipher for [passphrase] (SPEC:R109): the key file in [folder] unlocked, or, when the folder
         * has none, made ([KeyFolder.create], which refuses a passphrase shorter than 12 characters).
         */
        fun unlockOrCreate(folder: SyncFolder, passphrase: CharArray): Unlock {
            val keys = KeyFolder(folder)
            return when (val u = keys.unlock(passphrase)) {
                Unlock.NoKeyFile -> Unlock.Unlocked(keys.create(passphrase))
                else -> u
            }
        }

        /**
         * This device's database key (SPEC:R10), derived from [passphrase] ([KeyDerivation]) with a salt
         * made from [device], so each device's database has its own key and no salt needs storing apart.
         * The shell keeps it in the platform's key store, so a restart does not ask for the passphrase.
         */
        fun databaseKey(passphrase: CharArray, device: String): ByteArray =
            KeyDerivation.derive(passphrase, sha256("medicall-database-key|$device".encodeToByteArray()).copyOf(16))
    }
}
