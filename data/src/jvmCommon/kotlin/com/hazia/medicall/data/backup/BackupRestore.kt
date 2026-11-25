package com.hazia.medicall.data.backup

import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.isConstraintViolation
import com.hazia.medicall.data.sync.LineGuard
import com.hazia.medicall.data.sync.RecordCodec
import com.hazia.medicall.data.sync.RowRecord
import com.hazia.medicall.data.sync.RowTable
import java.io.File
import java.io.IOException

/**
 * What one restore did: [rows] the backup's rows merged, [files] its files stored here, [skipped] what
 * it could not take: a records line that does not read or a row its table refuses (a file reference
 * whose profile was purged here, say), and a file whose entry does not read back intact. [ahead] are
 * rows stamped more than a day past this device's clock ([LineGuard]), refused so they cannot push its
 * clock into the future. [tooLarge] names the file entries that declare more bytes than this device's
 * reader takes ([SevenZipArchives]' cap, a quarter of the heap): intact in the backup, not restored here.
 */
data class RestoreReport(val rows: Int, val files: Int, val skipped: Int, val ahead: Int = 0, val tooLarge: List<String> = emptyList())

/**
 * Restores a backup written by [BackupWriter] (SPEC:R22). Every row of `records/records.json` goes
 * through the same merge as a sync import ([com.hazia.medicall.core.sync.Merger.import], through
 * [writes]), with the stamps the backup holds; so in every field group the later stamp wins, and an
 * old backup never overwrites newer data. A row whose newest stamp is more than a day past
 * [wallMillis] is refused, by the rule sync's import holds such a row by ([LineGuard]). Every file
 * under `files/` is stored again by its hash, with no second file reference. A restore may run on a
 * device holding data: nothing here is deleted. The archive is read in one pass, one entry in memory
 * at a time.
 */
class BackupRestore internal constructor(
    private val writes: LocalWrites,
    private val blobs: Blobs,
    private val reader: ArchiveReader = SevenZip,
    private val wallMillis: () -> Long = System::currentTimeMillis,
) {
    private val tables: Map<String, RowTable> = writes.tables

    /**
     * Restores [archive]; null when it does not open with [password], the passphrase of SPEC:R109, is
     * not a 7z archive, or opens without a password, which no MedicAll backup does: then nothing is
     * written. Throws an [java.io.IOException] when [archive] itself cannot be read.
     */
    suspend fun restore(archive: File, password: CharArray): RestoreReport? {
        var records: ByteArray? = null
        var files = 0
        var skipped = 0
        val stored = mutableListOf<String>()
        val tooLarge = mutableListOf<String>()
        // Files first, so a reference merged below finds its file here.
        val opened = reader.readEach(archive, password) { entry ->
            when {
                entry.name == BackupContent.RECORDS -> records = entry.bytesOrNull()
                !entry.name.startsWith(BackupContent.FILES) -> {}
                else -> {
                    val bytes = try {
                        entry.open().use { it.readBytes() }
                    } catch (_: EntryTooLargeException) {
                        tooLarge += entry.name
                        null
                    } catch (_: IOException) {
                        skipped++
                        null
                    }
                    if (bytes != null) {
                        stored += blobs.storeBytes(bytes)
                        files++
                    }
                }
            }
        }
        if (!opened) return null
        blobs.markHeld(stored)
        val lines = records?.decodeToString()?.lines()?.filter { it.isNotEmpty() } ?: return RestoreReport(0, files, skipped + 1, tooLarge = tooLarge)
        val wall = wallMillis()
        val rows = mutableListOf<Row>()
        var ahead = 0
        for (line in lines) {
            val decoded = runCatching { RecordCodec.decode(line) }.getOrNull() as? RowRecord
            // In this version's groups first, as an import reads a folder line ([RowTable.upgrade]).
            val record = decoded?.let { r -> tables[r.row.table]?.let { RowRecord(it.upgrade(r.row)) } }
            when {
                record == null || tables[record.row.table]?.fits(record.row) != true -> skipped++
                LineGuard.isAhead(record, wall) -> ahead++
                else -> rows += record.row
            }
        }
        val merged = try {
            merge(rows)
        } catch (e: Exception) {
            if (!isConstraintViolation(e)) throw e
            // A row refused, a file reference whose profile was purged here say: merge them one by one,
            // parents first as the records are written, and count the refused ones.
            var n = 0
            for (row in rows) {
                try {
                    n += merge(listOf(row))
                } catch (e: Exception) {
                    if (!isConstraintViolation(e)) throw e
                    skipped++
                }
            }
            n
        }
        return RestoreReport(merged, files, skipped, ahead, tooLarge)
    }

    /** One transaction: [rows] merged with their stamps, as an import merges them. */
    private suspend fun merge(rows: List<Row>): Int {
        writes.write(rows.groupBy({ it.table }, { it.id })) { store -> writes.merger.import(store, rows, emptyMap()) }
        return rows.size
    }
}
