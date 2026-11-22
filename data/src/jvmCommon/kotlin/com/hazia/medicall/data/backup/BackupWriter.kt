package com.hazia.medicall.data.backup

import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.blob.Blobs
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** The two backups of SPEC:R22: the automatic one, rewritten in place, and the weekly dated one. */
enum class BackupKind { LATEST, WEEKLY }

sealed interface BackupResult {
    /**
     * [name] was written in the backup folder, holding [files] attachments. [missing] are the hashes,
     * each once, of the files references name that it does not hold: not arrived here yet, or damaged
     * past repair. When a live reference's file is among them, the previous LATEST was kept beside it
     * as `<name>.prev` ([keptPrevious]), so the backup that still held it is not lost.
     */
    data class Written(val name: String, val files: Int, val missing: List<String> = emptyList(), val keptPrevious: Boolean = false) : BackupResult

    /** Nothing was replaced; [reason] says why, for the person (SPEC:R73 reports it, phase 7). */
    data class Failed(val reason: String) : BackupResult
}

/**
 * Writes a backup (SPEC:R22) of everything in [db] and [blobs] to [target], with [writer], under the
 * passphrase of SPEC:R109:
 * 1. [IntegrityCheck] runs first (SPEC:R23), so a damaged file is repaired from a backup and included;
 * 2. the archive is written to a temporary file in [workDir], each entry streamed;
 * 3. it is read back in one pass, every entry streamed and opening intact under the password;
 * 4. only then does it go to [target], whose write lands it whole, checks its hash, and keeps the
 *    previous copy until the new one is in place ([BackupTarget.write]).
 *
 * Memory holds one entry at a time, never the archive. A failure on the way, an unreachable folder or
 * a full disk among them, is returned as [BackupResult.Failed], never thrown, and leaves the previous
 * backup as it was.
 */
class BackupWriter(
    private val db: MedicAllDatabase,
    private val blobs: Blobs,
    private val writer: ArchiveWriter,
    private val target: BackupTarget,
    private val wallMillis: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val workDir: File = File(System.getProperty("java.io.tmpdir") ?: "."),
    private val reader: ArchiveReader = SevenZip,
) {
    private val check = IntegrityCheck(blobs, target, reader, workDir)

    suspend fun write(kind: BackupKind, password: CharArray): BackupResult {
        var tmp: File? = null
        return try {
            if (!target.reachable()) return BackupResult.Failed("The backup folder cannot be reached.")
            val name = when (kind) {
                BackupKind.LATEST -> LATEST_NAME
                BackupKind.WEEKLY -> weeklyName(Instant.ofEpochMilli(wallMillis()).atZone(zone).toLocalDate().toString())
            }
            val integrity = check.run(password)
            val content = BackupContent.of(db, blobs, zone, leaveOut = integrity.unrepaired.toSet())
            tmp = File.createTempFile(TEMP_PREFIX, ".7z", workDir)
            writer.write(tmp, password, content.entries)
            if (!readsBackWhole(tmp, password, content.entries.map { it.name })) return BackupResult.Failed("The backup did not read back whole.")
            val keepPrevious = kind == BackupKind.LATEST && content.missingLive
            target.write(name, tmp, keepPrevious)
            BackupResult.Written(name, content.files, content.missing, keepPrevious && BackupTarget.prev(name) in target.list())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupResult.Failed(e.message ?: e::class.simpleName ?: "The backup could not be written.")
        } finally {
            tmp?.delete()
        }
    }

    /** Whether [archive] opens and holds [names], in order, each streaming back intact. */
    private fun readsBackWhole(archive: File, password: CharArray, names: List<String>): Boolean {
        val read = mutableListOf<String>()
        var damaged = false
        val buffer = ByteArray(64 * 1024)
        val opened = reader.readEach(archive, password) { entry ->
            read += entry.name
            try {
                entry.open().use { input -> while (input.read(buffer) >= 0) Unit }
            } catch (_: IOException) {
                damaged = true
            }
        }
        return opened && !damaged && read == names
    }

    companion object {
        const val LATEST_NAME = "MedicAll-backup-latest.7z"
        private const val TEMP_PREFIX = "medicall-backup-"
        private val WEEKLY = Regex("^MedicAll-backup-(\\d{4}-\\d{2}-\\d{2})\\.7z$")

        /** The weekly backup's name for [date], `YYYY-MM-DD`. */
        fun weeklyName(date: String) = "MedicAll-backup-$date.7z"

        /**
         * The backups among [names], newest first: the automatic one, then the weekly ones by date;
         * each followed by its kept previous copy ([BackupTarget.prev]), which is also listed when
         * the backup itself is not, as an interrupted replace leaves it.
         */
        fun newestFirst(names: List<String>): List<String> {
            val present = names.toSet()
            val bases = present.map { it.removeSuffix(BackupTarget.prev("")) }.toSet()
            fun withPrevious(n: String) = listOf(n, BackupTarget.prev(n)).filter { it in present }
            val weekly = bases.mapNotNull { n -> WEEKLY.matchEntire(n)?.let { n to it.groupValues[1] } }.sortedByDescending { it.second }.map { it.first }
            return withPrevious(LATEST_NAME) + weekly.flatMap(::withPrevious)
        }
    }
}
