package com.hazia.medicall.data.device

import com.hazia.medicall.core.lock.AppLock
import com.hazia.medicall.core.lock.Verdict
import com.hazia.medicall.core.sync.Ids
import com.hazia.medicall.data.MedicAllCore
import com.hazia.medicall.data.Opened
import com.hazia.medicall.data.ProfileInfo

/**
 * One device with the sync folders it joins (SPEC:R110): one [MedicAllCore] per folder, each over its own
 * database, key, passphrase and backups (SPEC:R10, R109, R22), so a record of one folder is never written to
 * another. The [lock] guards them all and touches none of them (SPEC:R11).
 *
 * [open] builds a folder's core with its passphrase (on Android: its own vault, database and SAF folder);
 * [setAside] closes a left folder's database and then puts its file aside, never deleting it (SPEC:R10): the
 * core's [MedicAllCore.close] clears its passphrase but leaves the database to the shell that opened it.
 * Call off the main thread.
 */
class MedicAllDevice(
    private val registry: FolderRegistry,
    private val open: suspend (JoinedFolder, CharArray) -> Opened,
    val lock: AppLock,
    private val setAside: suspend (JoinedFolder) -> Unit,
) {
    private val cores = linkedMapOf<String, MedicAllCore>()

    /** The folders joined, in the order they were joined. */
    fun folders(): List<JoinedFolder> = registry.folders()

    /** The open core of folder [folderId], or null when it is not open (its passphrase not at hand, or left). */
    fun core(folderId: String): MedicAllCore? = cores[folderId]

    /**
     * Joins the sync folder at [folderUri] with [passphrase] (made into its key file when the folder has none). The
     * folder is remembered only when the passphrase opens it; a folder already joined throws an [IllegalStateException].
     */
    suspend fun join(label: String, folderUri: String, passphrase: CharArray): Opened {
        registry.checkNew(folderUri)
        val candidate = JoinedFolder(Ids.new(), label, folderUri, backupUri = null)
        val opened = open(candidate, passphrase)
        if (opened is Opened.Ready) {
            registry.add(label, folderUri, candidate.id)
            cores[candidate.id] = opened.core
        }
        return opened
    }

    /**
     * Opens every joined folder not open yet, each with the passphrase [passphraseOf] gives (the shell's key store;
     * null when none is kept, and that folder stays shut). Returns what each attempted folder gave, by folder id.
     */
    suspend fun openAll(passphraseOf: (JoinedFolder) -> CharArray?): Map<String, Opened> {
        val results = linkedMapOf<String, Opened>()
        for (f in registry.folders()) {
            if (f.id in cores) continue
            val passphrase = passphraseOf(f) ?: continue
            try {
                val opened = open(f, passphrase)
                if (opened is Opened.Ready) cores[f.id] = opened.core
                results[f.id] = opened
            } finally {
                passphrase.fill(' ')
            }
        }
        return results
    }

    /** Every profile of every open folder, with its folder, the bin's included. */
    suspend fun profiles(): List<Pair<JoinedFolder, ProfileInfo>> {
        val byId = registry.folders().associateBy { it.id }
        return cores.flatMap { (id, core) -> core.profiles().map { byId.getValue(id) to it } }
    }

    /** Leaves folder [folderId]: its core closes, the device forgets it, and its database is set aside, not deleted. */
    suspend fun leave(folderId: String) {
        val folder = requireNotNull(registry.folders().firstOrNull { it.id == folderId }) { "no joined folder $folderId" }
        cores.remove(folderId)?.close()
        registry.remove(folderId)
        setAside(folder)
    }

    /**
     * A forgotten PIN (SPEC:R11): removed when [passphrase] opens any open folder. A wrong one counts as a wrong PIN,
     * and during a wait it is not checked ([AppLock.removeForgottenPinIf]).
     */
    fun removeForgottenPin(passphrase: CharArray): Verdict =
        lock.removeForgottenPinIf { cores.values.any { it.passphraseOpens(passphrase) } }
}
