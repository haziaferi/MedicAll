package com.hazia.medicall.data.backup

import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.isConstraintViolation
import com.hazia.medicall.data.sync.RecordCodec
import com.hazia.medicall.data.sync.RowRecord
import com.hazia.medicall.data.sync.RowTable
import java.io.File

/**
 * What one restore did: [rows] the backup's rows merged, [files] its files stored here, [skipped] what
 * it could not take: a records line that does not read or a row its table refuses (a file reference
 * whose profile was purged here, say), and a file whose entry does not read back intact.
 */
data class RestoreReport(val rows: Int, val files: Int, val skipped: Int)

/**
 * Restores a backup written by [BackupWriter] (SPEC:R22). Every row of `records/records.json` goes
 * through the same merge as a sync import ([com.hazia.medicall.core.sync.Merger.import], through
 * [writes]), with the stamps the backup holds; so in every field group the later stamp wins, and an
 * old backup never overwrites newer data. Every file under `files/` is stored again by its hash, with
 * no second file reference. A restore may run on a device holding data: nothing here is deleted.
 */
class BackupRestore internal constructor(
    private val writes: LocalWrites,
    private val blobs: Blobs,
    private val reader: ArchiveReader = SevenZip,
    private val workDir: File = File(System.getProperty("java.io.tmpdir") ?: "."),
) {
    private val tables: Map<String, RowTable> = writes.tables

    /**
     * Restores [archive]; null when it does not open with [password], the passphrase of SPEC:R109
     * (or is not a 7z archive), in which case nothing is written.
     */
    suspend fun restore(archive: ByteArray, password: CharArray): RestoreReport? {
        val file = File.createTempFile(TEMP_PREFIX, ".7z", workDir)
        try {
            file.writeBytes(archive)
            return restore(file, password)
        } finally {
            file.delete()
        }
    }

    private suspend fun restore(archive: File, password: CharArray): RestoreReport? {
        var records: ByteArray? = null
        var files = 0
        var skipped = 0
        // Files first, so a reference merged below finds its file here.
        val opened = reader.readEach(archive, password) { name, bytes ->
            when {
                name == BackupContent.RECORDS -> records = bytes
                !name.startsWith(BackupContent.FILES) -> {}
                bytes == null -> skipped++
                else -> { blobs.storeBytes(bytes); files++ }
            }
        }
        if (!opened) return null
        val lines = records?.decodeToString()?.lines()?.filter { it.isNotEmpty() } ?: return RestoreReport(0, files, skipped + 1)
        val rows = mutableListOf<Row>()
        for (line in lines) {
            val row = (runCatching { RecordCodec.decode(line) }.getOrNull() as? RowRecord)?.row
            if (row != null && tables[row.table]?.fits(row) == true) rows += row else skipped++
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
        return RestoreReport(merged, files, skipped)
    }

    /** One transaction: [rows] merged with their stamps, as an import merges them. */
    private suspend fun merge(rows: List<Row>): Int {
        writes.write(rows.groupBy({ it.table }, { it.id })) { store -> writes.merger.import(store, rows, emptyMap()) }
        return rows.size
    }

    private companion object {
        const val TEMP_PREFIX = "medicall-restore-"
    }
}
