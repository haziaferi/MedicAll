package com.hazia.medicall.data

import com.hazia.medicall.core.crypto.KeyFolder
import com.hazia.medicall.core.crypto.LineCipher
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.core.crypto.secureRandomBytes
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
import com.hazia.medicall.data.sync.FolderLayout
import com.hazia.medicall.data.sync.FolderSync
import com.hazia.medicall.data.sync.ImportReport
import com.hazia.medicall.data.sync.SyncFolder
import com.hazia.medicall.data.sync.loadClock
import java.io.File
import java.time.ZoneId

/** A profile as [MedicAllCore.profiles] lists it; [removedAt] is set while it is in the bin. */
data class ProfileInfo(val id: String, val name: String, val kind: String, val removedAt: Long?)

/**
 * What [MedicAllCore.syncNow] did: [imported] is the import's report. [unlockAgain] is true when the key
 * folder changed under this device's cipher (a key-file clash settled against the key it holds, or a
 * key it lacks retired beside the folder's, or the passphrase changed on another device): the shell must
 * open the core again ([MedicAllCore.open]) with the kept passphrase, or ask for the new one when that is
 * refused. Until it does, lines sealed under the keys it lacks are held, and no backup is written.
 */
data class SyncReport(val imported: ImportReport, val unlockAgain: Boolean)

/** What [MedicAllCore.open] gives: the core, or why the passphrase did not unlock the sync key. */
sealed interface Opened {
    /** [unreadableKeyIds] as [Unlock.Unlocked] names them: older keys whose lines stay unreadable for now. */
    class Ready(val core: MedicAllCore, val unreadableKeyIds: List<String>) : Opened

    /** [reason] is never [Unlock.Unlocked]: a wrong passphrase, a damaged key file, or a peer's key file not arrived yet. */
    class Refused(val reason: Unlock) : Opened
}

/** A file reference as [MedicAllCore.files] lists it; [removedAt] is set while it is in the bin. */
data class FileInfo(val id: String, val profileId: String, val name: String, val sha256: String, val removedAt: Long?)

/**
 * The phase 1 data layer as one device runs it (SPEC:R100): profiles, files, the bin, sync and backups
 * over one database, one sync folder and one cipher, wired in one place, as Factotum's `Factotum` wires
 * its repositories. A shell builds it once with [open]; the debug harness of the phase 1 walk is the
 * first shell, and tests build it the same way. It adds no rule of its own: each call goes to the class
 * that holds the rule.
 *
 * - [syncNow] settles key-file clashes ([KeyFolder.settleConflicts]), publishes the files the logs will
 *   name, writes the logs, then reads the peers' logs and fetches the files they name ([BlobSync], [FolderSync]).
 * - The backup calls take the backup folder each time, since a shell may pick it after this is built.
 *   They seal with the passphrase that unlocked the sync key ([open] keeps a copy, [close] clears it),
 *   so a backup never goes out under a passphrase sync does not use (SPEC:R109).
 *
 * Blocking where the folders block: call it off the main thread.
 */
class MedicAllCore private constructor(
    private val db: MedicAllDatabase,
    folder: SyncFolder,
    device: String,
    clock: HybridClock,
    private val cipher: LineCipher,
    private val passphrase: CharArray,
    blobStore: BlobStore,
    private val wallMillis: () -> Long,
    private val workDir: File,
    private val zone: ZoneId,
    recovered: Boolean,
) {
    private val keys = KeyFolder(folder)

    /** Retired keys this cipher already lacked when it was built: those [unlockOrCreate]'s passphrase did not open, so unlocking again would not help. */
    private val retiredUnopened = keys.retiredKeyIds().filterNot(cipher::knows).toSet()
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

    /** Deletes for good what has been in the bin longer than 30 days ([Bin.purgeExpired], SPEC:R18); [syncNow] runs it. */
    suspend fun purgeExpired() = bin.purgeExpired()

    /**
     * Settles key-file conflict copies ([KeyFolder.settleConflicts], SPEC:R16), publishes files and writes
     * the logs, reads the peers' logs, purges what has been in the bin over 30 days ([purgeExpired],
     * SPEC:R18), then fetches the peers' files and starts the collection of the purged ones' copies. The
     * purge runs after the import, so a restore from the bin a peer made is merged before anything is
     * purged here; the purge travels with this device's next export. The report says when the key folder
     * has moved past this cipher, so the shell unlocks again ([SyncReport.unlockAgain]); the sync goes on
     * meanwhile, since every key this device sealed under stays in the folder, retired or current.
     */
    suspend fun syncNow(): SyncReport {
        val lost = keys.settleConflicts()
        val unlockAgain = lost || keysMovedOn()
        blobSync.exchange()
        sync.export()
        val imported = sync.import()
        purgeExpired()
        blobSync.exchange()
        return SyncReport(imported, unlockAgain)
    }

    /** True when the folder's key is not this cipher's, or a key retired since this cipher was built is one it lacks. */
    private fun keysMovedOn(): Boolean {
        val current = keys.currentKeyId()
        if (current != null && current != cipher.currentKeyId) return true
        return keys.retiredKeyIds().any { !cipher.knows(it) && it !in retiredUnopened }
    }

    /**
     * Writes a backup of [kind] to [target] ([BackupWriter], SPEC:R22) under the passphrase that unlocked sync
     * (SPEC:R109). While the folder's key is not the one that passphrase opened (changed on another device),
     * it writes nothing and returns [PASSPHRASE_CHANGED]: the shell opens the core again with the new one.
     */
    suspend fun backUp(target: BackupTarget, kind: BackupKind = BackupKind.LATEST): BackupResult {
        if (keys.currentKeyId() != cipher.currentKeyId) return BackupResult.Failed(PASSPHRASE_CHANGED)
        return BackupWriter(db, blobs, SevenZip, target, wallMillis, zone, workDir).write(kind, passphrase)
    }

    /**
     * Restores the newest backup in [target] ([BackupWriter.newestFirst], [BackupRestore]); null when it
     * does not open with [passphrase], by default the one that unlocked sync (an older backup may need an
     * older passphrase, which the person types). Throws an [IllegalStateException] when [target] holds no backup.
     */
    suspend fun restoreBackup(target: BackupTarget, passphrase: CharArray = this.passphrase): RestoreReport? {
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

    /** Checks every file against its hash and repairs the damaged ones from [target]'s backups that open with the sync passphrase ([IntegrityCheck], SPEC:R23). */
    suspend fun checkFiles(target: BackupTarget): IntegrityReport =
        IntegrityCheck(blobs, target, SevenZip, workDir).run(passphrase)

    /** Clears this core's copy of the passphrase; the core must not be used after. */
    fun close() = passphrase.fill(' ')

    companion object {
        /** The kind every profile has in version 1 (SPEC:R15: pet is in the data model, not offered). */
        const val PERSON = "person"
        const val PET = "pet"
        private const val TEMP_PREFIX = "medicall-restore-"

        /** [backUp]'s reason when the passphrase was changed on another device since this core was opened. */
        const val PASSPHRASE_CHANGED = "The passphrase was changed on another device: enter the new one before the next backup."

        /**
         * Unlocks (or makes) the sync key with [passphrase] ([unlockOrCreate]) and builds the data layer over
         * [db], whose clock resumes from what it stored, writing [folder] as [device], keeping files in
         * [blobStore], and working in [workDir]. The core keeps its own copy of [passphrase] for the backups
         * (SPEC:R109); the caller still owns, and clears, [passphrase].
         */
        suspend fun open(
            db: MedicAllDatabase,
            folder: SyncFolder,
            device: String,
            passphrase: CharArray,
            blobStore: BlobStore,
            workDir: File,
            wallMillis: () -> Long = System::currentTimeMillis,
            zone: ZoneId = ZoneId.systemDefault(),
            recovered: Boolean = false,
        ): Opened {
            val unlocked = when (val u = unlockOrCreate(folder, passphrase, device)) {
                is Unlock.Unlocked -> u
                else -> return Opened.Refused(u)
            }
            workDir.mkdirs()
            val clock = db.syncDao().loadClock(device, wallMillis)
            val core = MedicAllCore(db, folder, device, clock, unlocked.cipher, passphrase.copyOf(), blobStore, wallMillis, workDir, zone, recovered)
            return Opened.Ready(core, unlocked.unreadableKeyIds)
        }

        /**
         * The sync cipher for [passphrase] (SPEC:R109): the key file in [folder] unlocked, or, when the folder
         * has none, made ([KeyFolder.create], which refuses a passphrase shorter than 12 characters). Key-file
         * conflict copies are settled first (SPEC:R16), so the cipher holds every key the clash left.
         *
         * No key is made while `devices/` holds a directory other than [device]'s: another device already
         * writes here, so its key file is on its way, and a second key would only clash with it. That gives
         * [Unlock.KeyFileNotArrived]: wait for the sync and try again.
         */
        fun unlockOrCreate(folder: SyncFolder, passphrase: CharArray, device: String): Unlock {
            val keys = KeyFolder(folder)
            keys.settleConflicts()
            return when (val u = keys.unlock(passphrase)) {
                Unlock.NoKeyFile ->
                    if (folder.list(FolderLayout.DEVICES).any { it != device }) Unlock.KeyFileNotArrived
                    else Unlock.Unlocked(keys.create(passphrase))
                else -> u
            }
        }

        /**
         * A new database key (SPEC:R10): 32 random bytes, owing nothing to the passphrase, so changing the
         * passphrase (SPEC:R109) leaves the database as it is. The shell makes it once and keeps it only in the
         * platform's key store. A key store that loses it cannot get it back: the shell then sets the old
         * database aside and starts an empty one, which the sync folder and the backups fill again.
         */
        fun newDatabaseKey(): ByteArray = secureRandomBytes(32)
    }
}
