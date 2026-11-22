package com.hazia.medicall.data.backup

import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.MAIN
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.NAME
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.blob.BlobName
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.sync.RecordCodec
import com.hazia.medicall.data.sync.RowRecord
import com.hazia.medicall.data.syncedTables
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * What one backup holds (SPEC:R22), readable without MedicAll:
 *
 * | Entry | Content |
 * |---|---|
 * | `HOW-TO-OPEN.txt` | [HOW_TO_OPEN] |
 * | `records/records.json` | every synced row with its groups and stamps, one [RecordCodec] line each: what a restore merges |
 * | `records/<table>.csv` | one CSV per synced table (RFC 4180), a header row, then each row's values without stamps |
 * | `records/files.csv` | `path,sha256,blob_ref`: which entry under `files/` holds which file, for the integrity check (SPEC:R23) |
 * | `files/<profile name>/<YYYY-MM-DD> <file name>` | each file's plain bytes |
 *
 * Every file reference, the bin's included, gets an entry when this device holds its file; one whose
 * file has not arrived from its device yet, or that the caller leaves out as damaged (the integrity
 * check runs first, [BackupWriter]), is left out, and its hash is in [missing]. A reference whose hash
 * is not a SHA-256 in hex names no file and is passed over. Each file is opened once, when its entry is
 * written. Its date is the day its reference was last written, in [zone].
 *
 * Names are made safe on Windows and Android ([safe]): no separators or reserved characters, no
 * trailing dots or spaces, and no reserved device name (CON, PRN, AUX, NUL, COM1–COM9, LPT1–LPT9,
 * with or without an extension), which gets a leading "_". A name another entry of the folder already
 * has (compared without case, as Windows does) gets " (2)", " (3)": before a file name's last dot, at
 * the end of a folder name, in the order the files were added; so a profile name two profiles share
 * gives "Dr. Smith" and "Dr. Smith (2)".
 */
internal class BackupContent private constructor(
    val entries: List<ArchiveEntry>,
    val files: Int,
    /** The hashes, each once, of the files references name, the bin's included, that this backup does not hold. */
    val missing: List<String>,
) {

    companion object {
        const val HOW_TO_OPEN_NAME = "HOW-TO-OPEN.txt"
        const val RECORDS = "records/records.json"
        const val FILES_INDEX = "records/files.csv"
        const val FILES = "files/"

        const val HOW_TO_OPEN = "This is a MedicAll backup. Open it with 7-Zip (7-zip.org) or another program that reads " +
            "7z archives, using your MedicAll passphrase. The files folder holds your attachments under readable names; " +
            "the records folder holds your records as CSV and JSON."

        /** The backup of [db] and [blobs], without the files of [leaveOut]: those the integrity check found damaged and could not repair. */
        suspend fun of(db: MedicAllDatabase, blobs: Blobs, zone: ZoneId, leaveOut: Set<String> = emptySet()): BackupContent {
            val rows = db.syncedTables().mapValues { (_, t) -> t.all() }
            val entries = mutableListOf(text(HOW_TO_OPEN_NAME, HOW_TO_OPEN))
            entries += text(RECORDS, rows.values.flatten().joinToString("") { RecordCodec.encode(RowRecord(it)) + "\n" })
            for ((table, tableRows) in rows) entries += text("records/$table.csv", csv(tableRows))

            val refs = rows[BLOB_REF].orEmpty()
            val files = fileEntries(rows[PROFILE].orEmpty(), refs, blobs, zone, leaveOut)
            entries += text(FILES_INDEX, Csv.write(listOf(listOf("path", "sha256", "blob_ref")) + files.map { listOf(it.path, it.hash, it.refId) }))
            for (f in files) {
                entries += ArchiveEntry(f.path, f.size) {
                    ByteArrayInputStream(blobs.intact(f.hash) ?: throw IOException("file ${f.hash} was damaged while the backup was written"))
                }
            }
            val included = files.map { it.hash }.toSet()
            val named = refs.map { it.groups.getValue(MAIN).values["sha256"] as String }.filter(BlobName::isValid)
            return BackupContent(entries, files.size, named.distinct().filter { it !in included })
        }

        /** The files a backup holds, read from `records/files.csv`: entry path to SHA-256. */
        fun filesIndex(csv: String): Map<String, String> =
            Csv.read(csv).drop(1).filter { it.size >= 2 }.associate { it[0] to it[1] }

        private class FileEntry(val path: String, val hash: String, val refId: String, val size: Long)

        private fun fileEntries(profiles: List<Row>, refs: List<Row>, blobs: Blobs, zone: ZoneId, leaveOut: Set<String>): List<FileEntry> {
            val held = blobs.held().toSet()
            val folders = Names()
            val folderOf = profiles.sortedWith(compareBy({ it.groups.getValue(NAME).stamp }, { it.id }))
                .associate { it.id to folders.unique(safe(it.groups.getValue(NAME).values["name"] as String, "Profile"), file = false) }
            val names = mutableMapOf<String, Names>()
            val out = mutableListOf<FileEntry>()
            for (ref in refs.sortedWith(compareBy({ it.groups.getValue(MAIN).stamp }, { it.id }))) {
                val main = ref.groups.getValue(MAIN)
                val hash = main.values["sha256"] as String
                if (!BlobName.isValid(hash) || hash !in held || hash in leaveOut) continue
                val folder = folderOf[main.values["profile_id"] as String] ?: continue
                val day = Instant.ofEpochMilli(main.stamp.hlc).atZone(zone).toLocalDate()
                val file = names.getOrPut(folder) { Names() }.unique("$day ${safe(main.values["name"] as String, "file")}", file = true)
                out += FileEntry("$FILES$folder/$file", hash, ref.id, main.values["size"] as Long)
            }
            return out
        }

        private fun text(name: String, content: String): ArchiveEntry {
            val bytes = content.encodeToByteArray()
            return ArchiveEntry(name, bytes.size.toLong()) { ByteArrayInputStream(bytes) }
        }

        /** A header of `id` and every field in group order, then each row's values; stamps are left to `records.json`. */
        private fun csv(rows: List<Row>): String {
            val columns = LinkedHashSet<Pair<String, String>>()
            for (row in rows) for ((g, group) in row.groups) for (f in group.values.keys) columns += g to f
            val header = listOf("id") + columns.map { it.second }
            return Csv.write(listOf(header) + rows.map { row -> listOf(row.id) + columns.map { (g, f) -> row.groups[g]?.values?.get(f)?.toString().orEmpty() } })
        }

        /**
         * [name] made safe as one path segment on Windows and Android: no separators, reserved
         * characters, trailing dots or spaces, and a "_" before a reserved device name.
         */
        internal fun safe(name: String, fallback: String): String {
            val cleaned = name.map { c -> if (c < ' ' || c in "<>:\"/\\|?*") '_' else c }.joinToString("").trim().trimEnd('.', ' ')
            if (cleaned.isEmpty()) return fallback
            return if (cleaned.substringBefore('.').trimEnd().uppercase() in RESERVED) "_$cleaned" else cleaned
        }

        /** Windows' reserved device names: a file named one, with any extension, opens the device. */
        private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
    }

    /** Names already used in one folder, without case. */
    private class Names {
        private val used = mutableSetOf<String>()

        /** [name], or with " (2)", " (3)" when it is taken: before the last dot of a [file]'s name, at the end of a folder's. */
        fun unique(name: String, file: Boolean): String {
            val dot = name.lastIndexOf('.').takeIf { file && it > 0 } ?: name.length
            var candidate = name
            var n = 2
            while (!used.add(candidate.lowercase())) candidate = "${name.substring(0, dot)} (${n++})${name.substring(dot)}"
            return candidate
        }
    }
}

/** RFC 4180 CSV: fields holding a comma, a quote or a line break are quoted, lines end in CRLF. */
internal object Csv {
    fun write(lines: List<List<String>>): String = lines.joinToString("") { line -> line.joinToString(",") { field(it) } + "\r\n" }

    private fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun read(text: String): List<List<String>> {
        val lines = mutableListOf<List<String>>()
        var line = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == ',' -> { line += field.toString(); field.clear() }
                c == '\r' -> {}
                c == '\n' -> { line += field.toString(); field.clear(); lines += line; line = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || line.isNotEmpty()) { line += field.toString(); lines += line }
        return lines
    }
}
