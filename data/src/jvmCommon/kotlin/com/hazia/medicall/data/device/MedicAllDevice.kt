package com.hazia.medicall.data.device

import com.hazia.medicall.core.lock.AppLock
import com.hazia.medicall.core.lock.Verdict
import com.hazia.medicall.core.look.DeviceLook
import com.hazia.medicall.core.sync.Ids
import com.hazia.medicall.data.MedicAllCore
import com.hazia.medicall.data.Opened
import com.hazia.medicall.data.ProfileInfo
import com.hazia.medicall.data.sync.SyncFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One device with the sync folders it joins (SPEC:R110): one [MedicAllCore] per folder, each over its own
 * database, key, passphrase and backups (SPEC:R10, R109, R22), so a record of one folder is never written to
 * another. The [lock] guards them all and touches none of them (SPEC:R11).
 *
 * [open] builds a folder's core with its passphrase (on Android: its own vault, database and SAF folder). When it
 * does not return [Opened.Ready] it leaves nothing open and stores nothing; when the folder's database is already
 * open it builds the new core over it. [syncFolderOf] reaches a folder's sync folder without opening it (a
 * forgotten PIN checks a shut folder's key file through it). [setAside] closes a folder's database and puts its file aside, never
 * deleting it (SPEC:R10): [MedicAllCore.close] clears a core's passphrase but leaves the database to the shell.
 *
 * Joining, opening, opening again and leaving run one at a time. Call off the main thread.
 */
class MedicAllDevice(
    private val registry: FolderRegistry,
    private val open: suspend (JoinedFolder, CharArray) -> Opened,
    val lock: AppLock,
    private val syncFolderOf: (JoinedFolder) -> SyncFolder,
    private val setAside: suspend (JoinedFolder) -> Unit,
) {
    private val cores = ConcurrentHashMap<String, MedicAllCore>()
    private val changes = Mutex()

    /** The folders joined, in the order they were joined. */
    fun folders(): List<JoinedFolder> = registry.folders()

    /**
     * Light or dark for every profile on this device, and the device palette of the screens that belong to no
     * profile (SPEC:R114). Kept on this device alone: nothing goes to a sync folder or a backup.
     */
    fun look(): DeviceLook = registry.look()

    /** Sets [look] on this device alone. */
    fun setLook(look: DeviceLook) = registry.saveLook(look)

    /** The open core of folder [folderId], or null when it is not open (its passphrase not at hand, or left). */
    fun core(folderId: String): MedicAllCore? = cores[folderId]

    /**
     * Joins the sync folder at [folderUri] with [passphrase] (made into its key file when the folder has none). The
     * folder is remembered only when the passphrase opens it; a folder already joined throws an [IllegalStateException].
     * The caller still owns, and clears, [passphrase].
     */
    suspend fun join(label: String, folderUri: String, passphrase: CharArray): Opened = changes.withLock {
        registry.checkNew(folderUri)
        val candidate = JoinedFolder(Ids.new(), label, folderUri, backupUri = null)
        val opened = open(candidate, passphrase)
        if (opened is Opened.Ready) {
            try {
                registry.add(label, folderUri, candidate.id)
            } catch (e: Exception) {
                // Not remembered: nothing of it may stay open.
                opened.core.close()
                setAside(candidate)
                throw e
            }
            cores[candidate.id] = opened.core
        }
        opened
    }

    /**
     * Opens every joined folder not open yet, each with the passphrase [passphraseOf] gives (the shell's key store;
     * null when none is kept, and that folder stays shut). One folder that fails (its grant revoked, the key store
     * not answering) does not stop the others. Returns what each attempted folder gave, by folder id.
     */
    suspend fun openAll(passphraseOf: (JoinedFolder) -> CharArray?): Map<String, Result<Opened>> = changes.withLock {
        val results = linkedMapOf<String, Result<Opened>>()
        for (f in registry.folders()) {
            if (cores.containsKey(f.id)) continue
            results[f.id] = try {
                val passphrase = passphraseOf(f) ?: continue
                try {
                    Result.success(open(f, passphrase).also { if (it is Opened.Ready) cores[f.id] = it.core })
                } finally {
                    passphrase.fill(' ')
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        results
    }

    /**
     * Opens folder [folderId] again with [passphrase], as after a sync says the key folder moved on (a passphrase
     * changed on another device, SPEC:R109). Only a passphrase that opens it replaces the open core, which is then
     * closed; a refused one leaves everything as it was.
     */
    suspend fun reopen(folderId: String, passphrase: CharArray): Opened = changes.withLock {
        val folder = requireNotNull(registry.folders().firstOrNull { it.id == folderId }) { "no joined folder $folderId" }
        val opened = open(folder, passphrase)
        if (opened is Opened.Ready) cores.put(folderId, opened.core)?.close()
        opened
    }

    /**
     * Runs [block] with the ids of the folders joined, while no join, open or leave runs, so a folder being joined
     * (its vault stored, not remembered yet) is never taken for an orphan. For a shell's clean-up of what a crash
     * or a lost settings file left behind.
     */
    suspend fun <T> withJoinedIds(block: suspend (Set<String>) -> T): T = changes.withLock {
        block(registry.folders().map { it.id }.toSet())
    }

    /** Closes every open core, then runs [closeShell] (the shell's databases), while no join, open or leave runs. */
    suspend fun closeAll(closeShell: suspend () -> Unit) = changes.withLock {
        cores.values.forEach { it.close() }
        cores.clear()
        closeShell()
    }

    /** Every profile of every open folder, with its folder, in the order the folders were joined, the bin's included. */
    suspend fun profiles(): List<Pair<JoinedFolder, ProfileInfo>> =
        registry.folders().flatMap { f -> cores[f.id]?.profiles()?.map { f to it }.orEmpty() }

    /** Leaves folder [folderId]: its core closes, the device forgets it, and its database is set aside, not deleted. */
    suspend fun leave(folderId: String) = changes.withLock {
        val folder = requireNotNull(registry.folders().firstOrNull { it.id == folderId }) { "no joined folder $folderId" }
        cores.remove(folderId)?.close()
        registry.remove(folderId)
        setAside(folder)
    }

    /**
     * A forgotten PIN (SPEC:R11): removed when [passphrase] opens any joined folder's key file, open or shut (a
     * folder that did not open, as when the key store did not answer, is checked through [syncFolderOf]). A wrong
     * one counts as a wrong PIN, and during a wait it is not checked ([AppLock.removeForgottenPinIf]). A folder that
     * cannot be read (its grant revoked) counts as not opened, so the others are still tried and the try is still
     * counted. With no folder joined the device holds nothing the lock guards and no passphrase could be checked, so
     * the PIN goes (outside a wait): otherwise a lock whose state did not read would close the app for good.
     */
    fun removeForgottenPin(passphrase: CharArray): Verdict =
        lock.removeForgottenPinIf {
            val joined = registry.folders()
            joined.isEmpty() || joined.any { f ->
                runCatching {
                    cores[f.id]?.passphraseOpens(passphrase) ?: MedicAllCore.passphraseOpens(syncFolderOf(f), passphrase)
                }.getOrDefault(false)
            }
        }
}
