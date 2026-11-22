package com.hazia.medicall.data.bin

import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.BlobRefDao
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.MAIN
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.ProfileDao
import com.hazia.medicall.data.REMOVED

/** One record in the bin: its table ([PROFILE] or [BLOB_REF]), its id and when it was removed, in epoch milliseconds. */
data class BinEntry(val table: String, val id: String, val removedAt: Long)

/**
 * The bin (SPEC:R18). Removing a record sets its `removed` group, which syncs like any edit, and needs
 * no confirmation; it can be restored for [RETENTION_DAYS] days. After that, [purgeExpired] deletes it for
 * good through the merge's purge, which travels so no peer brings it back. Emptying the bin by hand is
 * the one irreversible step, and asks for "DELETE" typed out (SPEC:R19).
 *
 * A profile's file references go to the bin with it, stamped with the same moment, and come back with it;
 * a reference removed on its own before stays where it was. Removing a record already in the bin changes
 * nothing, so its 30 days are not started again.
 *
 * Every step reads what it acts on inside its own write transaction, so an import cannot change the bin
 * between the reading and the writing.
 */
class Bin internal constructor(
    private val writes: LocalWrites,
    db: MedicAllDatabase,
    private val wallMillis: () -> Long,
    private val profiles: ProfileDao = db.profileDao(),
    private val blobRefs: BlobRefDao = db.blobRefDao(),
) {

    suspend fun remove(table: String, id: String) {
        requireBinTable(table)
        val at = wallMillis()
        setRemoved(at) {
            when (table) {
                PROFILE -> {
                    val profile = profiles.profiles(listOf(id)).singleOrNull()
                    if (profile?.removedAt != null) emptyMap()
                    else mapOf(PROFILE to listOf(id), BLOB_REF to blobRefs.blobRefsOf(id).filter { it.removedAt == null }.map { it.id })
                }
                else -> if (blobRefs.blobRefs(listOf(id)).singleOrNull()?.removedAt != null) emptyMap() else mapOf(BLOB_REF to listOf(id))
            }
        }
    }

    /**
     * Takes [id] out of the bin; a record that is not in it is left as it is. A profile brings back the
     * file references removed with it. A file reference whose profile is in the bin is refused with an
     * [IllegalStateException]: restore the profile, which brings it back too.
     */
    suspend fun restore(table: String, id: String) {
        requireBinTable(table)
        setRemoved(null) {
            when (table) {
                PROFILE -> {
                    val at = profiles.profiles(listOf(id)).singleOrNull()?.removedAt
                    if (at == null) emptyMap()
                    else mapOf(PROFILE to listOf(id), BLOB_REF to blobRefs.blobRefsOf(id).filter { it.removedAt == at }.map { it.id })
                }
                else -> {
                    val ref = requireNotNull(blobRefs.blobRefs(listOf(id)).singleOrNull()) { "no row $id" }
                    if (ref.removedAt == null) {
                        emptyMap()
                    } else {
                        check(profiles.profiles(listOf(ref.profileId)).single().removedAt == null) {
                            "the profile of $id is in the bin: restore the profile"
                        }
                        mapOf(BLOB_REF to listOf(id))
                    }
                }
            }
        }
    }

    /** Everything in the bin, oldest removal first. */
    suspend fun contents(): List<BinEntry> =
        (profiles.removedProfiles().map { BinEntry(PROFILE, it.id, it.removedAt!!) } +
            blobRefs.removedBlobRefs().map { BinEntry(BLOB_REF, it.id, it.removedAt!!) })
            .sortedWith(compareBy({ it.removedAt }, { it.table }, { it.id }))

    /**
     * Deletes for good what has been in the bin longer than [RETENTION_DAYS] days. The days count from
     * the later of `removed_at` and the removal's stamp: the remover's hybrid clock is never behind a
     * stamp it had seen, so a remover whose wall clock runs behind cannot shorten them on its peers.
     */
    suspend fun purgeExpired() {
        val cutoff = wallMillis() - RETENTION_DAYS * DAY_MILLIS
        purge { since -> since < cutoff }
    }

    /** Deletes everything in the bin for good; refuses unless [confirmation] is "DELETE" (SPEC:R19). */
    suspend fun empty(confirmation: String) {
        require(confirmation == CONFIRMATION) { "Type $CONFIRMATION to empty the bin" }
        purge { true }
    }

    private fun requireBinTable(table: String) = require(table == PROFILE || table == BLOB_REF) { "no bin for $table" }

    /** Sets `removed_at` to [at] on the rows [ids] finds, inside the write transaction, with one clock tick. */
    private suspend fun setRemoved(at: Long?, ids: suspend () -> Map<String, List<String>>) {
        writes.write({ ids().filterValues { it.isNotEmpty() } }) { store, targets ->
            if (targets.isEmpty()) return@write
            val s = writes.clock.tick()
            targets.values.flatten().forEach { id ->
                val row = requireNotNull(store.row(id)) { "no row $id" }
                store.put(row.edit(REMOVED, s, mapOf("removed_at" to at)))
            }
        }
    }

    /**
     * Purges what is in the bin since a moment [due] accepts, read inside the write transaction and
     * checked again on the rows the transaction loaded: a row restored meanwhile is not purged. Children
     * go before parents, so a profile's references go before it; a profile purged takes all its references.
     */
    private suspend fun purge(due: (Long) -> Boolean) {
        writes.write({
            val profileIds = profiles.removedProfiles().filter { due(maxOf(it.removedAt!!, it.removedHlc)) }.map { it.id }
            val refIds = blobRefs.removedBlobRefs().filter { due(maxOf(it.removedAt!!, it.removedHlc)) }.map { it.id } +
                profileIds.flatMap { p -> blobRefs.blobRefsOf(p).map { it.id } }
            mapOf(BLOB_REF to refIds.distinct(), PROFILE to profileIds).filterValues { it.isNotEmpty() }
        }) { store, targets ->
            val goneProfiles = targets[PROFILE].orEmpty().filter { id -> store.row(id)?.inBinSince()?.let(due) == true }.toSet()
            val goneRefs = targets[BLOB_REF].orEmpty().filter { id ->
                val ref = store.row(id) ?: return@filter false
                ref.groups[MAIN]?.values?.get("profile_id") as String? in goneProfiles ||ref.inBinSince()?.let(due) == true
            }
            (goneRefs + goneProfiles).forEach { writes.merger.purge(store, it) }
        }
    }

    /** The moment this row went to the bin, as [purgeExpired] counts it, or null when it is not in the bin. */
    private fun Row.inBinSince(): Long? {
        val removed = groups[REMOVED] ?: return null
        val at = removed.values["removed_at"] as Long? ?: return null
        return maxOf(at, removed.stamp.hlc)
    }

    companion object {
        const val RETENTION_DAYS = 30L
        const val CONFIRMATION = "DELETE"
        private const val DAY_MILLIS = 86_400_000L
    }
}
