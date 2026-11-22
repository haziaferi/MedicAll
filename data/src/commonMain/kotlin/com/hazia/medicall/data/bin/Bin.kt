package com.hazia.medicall.data.bin

import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.REMOVED

/** One record in the bin: its table, its id and when it was removed, in epoch milliseconds. */
data class BinEntry(val table: String, val id: String, val removedAt: Long)

/**
 * The bin (SPEC:R18). Removing a record sets its `removed` group, which syncs like any edit, and needs
 * no confirmation; it can be restored for [RETENTION_DAYS] days. After that, [purgeExpired] deletes it for
 * good through the merge's purge, which travels so no peer brings it back. Emptying the bin by hand is
 * the one irreversible step, and asks for "DELETE" typed out (SPEC:R19).
 *
 * A profile's file references go to the bin with it, stamped with the same moment, and come back with it;
 * a reference removed on its own before stays where it was.
 */
class Bin internal constructor(
    private val writes: LocalWrites,
    private val db: MedicAllDatabase,
    private val wallMillis: () -> Long,
) {
    private val profiles = db.profileDao()
    private val blobRefs = db.blobRefDao()

    suspend fun remove(table: String, id: String) {
        val at = wallMillis()
        val ids = when (table) {
            PROFILE -> mapOf(PROFILE to listOf(id), BLOB_REF to blobRefs.blobRefsOf(id).filter { it.removedAt == null }.map { it.id })
            BLOB_REF -> mapOf(BLOB_REF to listOf(id))
            else -> throw IllegalArgumentException("no bin for $table")
        }
        setRemoved(ids, at)
    }

    suspend fun restore(table: String, id: String) {
        val ids = when (table) {
            PROFILE -> {
                val at = profiles.profiles(listOf(id)).singleOrNull()?.removedAt ?: return
                mapOf(PROFILE to listOf(id), BLOB_REF to blobRefs.blobRefsOf(id).filter { it.removedAt == at }.map { it.id })
            }
            BLOB_REF -> mapOf(BLOB_REF to listOf(id))
            else -> throw IllegalArgumentException("no bin for $table")
        }
        setRemoved(ids, null)
    }

    /** Everything in the bin, oldest removal first. */
    suspend fun contents(): List<BinEntry> =
        (profiles.removedProfiles().map { BinEntry(PROFILE, it.id, it.removedAt!!) } +
            blobRefs.removedBlobRefs().map { BinEntry(BLOB_REF, it.id, it.removedAt!!) })
            .sortedWith(compareBy({ it.removedAt }, { it.table }, { it.id }))

    /** Deletes for good what has been in the bin longer than [RETENTION_DAYS] days. */
    suspend fun purgeExpired() {
        val cutoff = wallMillis() - RETENTION_DAYS * DAY_MILLIS
        purge(contents().filter { it.removedAt < cutoff })
    }

    /** Deletes everything in the bin for good; refuses unless [confirmation] is "DELETE" (SPEC:R19). */
    suspend fun empty(confirmation: String) {
        require(confirmation == CONFIRMATION) { "Type $CONFIRMATION to empty the bin" }
        purge(contents())
    }

    private suspend fun setRemoved(ids: Map<String, List<String>>, at: Long?) {
        val targets = ids.filterValues { it.isNotEmpty() }
        if (targets.isEmpty()) return
        writes.write(targets) { store ->
            val s = writes.clock.tick()
            targets.values.flatten().forEach { id ->
                val row = requireNotNull(store.row(id)) { "no row $id" }
                store.put(row.edit(REMOVED, s, mapOf("removed_at" to at)))
            }
        }
    }

    /** Children before parents, so a profile's references go before it; a profile purged takes the rest of its references. */
    private suspend fun purge(entries: List<BinEntry>) {
        if (entries.isEmpty()) return
        val profileIds = entries.filter { it.table == PROFILE }.map { it.id }
        val refIds = (entries.filter { it.table == BLOB_REF }.map { it.id } + profileIds.flatMap { p -> blobRefs.blobRefsOf(p).map { it.id } }).distinct()
        writes.write(mapOf(BLOB_REF to refIds, PROFILE to profileIds).filterValues { it.isNotEmpty() }) { store ->
            (refIds + profileIds).forEach { id -> if (store.row(id) != null) writes.merger.purge(store, id) }
        }
    }

    companion object {
        const val RETENTION_DAYS = 30L
        const val CONFIRMATION = "DELETE"
        private const val DAY_MILLIS = 86_400_000L
    }
}
