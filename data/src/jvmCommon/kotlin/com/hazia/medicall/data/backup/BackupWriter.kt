package com.hazia.medicall.data.backup

import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.blob.Blobs
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The two backups of SPEC:R22: the automatic one, rewritten in place, and the weekly dated one. */
enum class BackupKind { LATEST, WEEKLY }

sealed interface BackupResult {
    /** [name] was written in the backup folder, holding [files] attachments. */
    data class Written(val name: String, val files: Int) : BackupResult

    /** Nothing was replaced; [reason] says why, for the person (SPEC:R73 reports it, phase 7). */
    data class Failed(val reason: String) : BackupResult
}

/**
 * Writes a backup (SPEC:R22) of everything in [db] and [blobs] to [target], with [writer], under the
 * passphrase of SPEC:R109. The archive is written first to a temporary file in [workDir] and read back
 * whole, every entry opening intact under the password; only then does it go to [target], whose write
 * replaces the previous file of that name whole or not at all, and is read back again. A failure on the
 * way, an unreachable folder or a full disk among them, is returned as [BackupResult.Failed], never
 * thrown, and leaves the previous backup as it was.
 *
 * A file whose copy here is damaged is left out of the backup; the integrity check that runs before
 * each write (SPEC:R23) repairs it first.
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

    suspend fun write(kind: BackupKind, password: CharArray): BackupResult {
        var tmp: File? = null
        return try {
            if (!target.reachable()) return BackupResult.Failed("The backup folder cannot be reached.")
            val name = when (kind) {
                BackupKind.LATEST -> LATEST_NAME
                BackupKind.WEEKLY -> weeklyName(Instant.ofEpochMilli(wallMillis()).atZone(zone).toLocalDate().toString())
            }
            val content = BackupContent.of(db, blobs, zone)
            tmp = File.createTempFile(TEMP_PREFIX, ".7z", workDir)
            writer.write(tmp, password, content.entries)
            if (!readsBackWhole(tmp, password, content.entries.map { it.name })) return BackupResult.Failed("The backup did not read back whole.")
            val bytes = tmp.readBytes()
            target.write(name, bytes)
            if (!bytes.contentEquals(target.read(name))) return BackupResult.Failed("The backup folder did not keep the backup whole.")
            BackupResult.Written(name, content.files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupResult.Failed(e.message ?: e::class.simpleName ?: "The backup could not be written.")
        } finally {
            tmp?.delete()
        }
    }

    private fun readsBackWhole(archive: File, password: CharArray, names: List<String>): Boolean {
        val read = mutableListOf<String>()
        var damaged = false
        val opened = reader.readEach(archive, password) { name, bytes ->
            read += name
            if (bytes == null) damaged = true
        }
        return opened && !damaged && read == names
    }

    companion object {
        const val LATEST_NAME = "MedicAll-backup-latest.7z"
        private const val TEMP_PREFIX = "medicall-backup-"
        private val WEEKLY = Regex("^MedicAll-backup-(\\d{4}-\\d{2}-\\d{2})\\.7z$")

        /** The weekly backup's name for [date], `YYYY-MM-DD`. */
        fun weeklyName(date: String) = "MedicAll-backup-$date.7z"

        /** The backups among [names], newest first: the automatic one, then the weekly ones by date. */
        fun newestFirst(names: List<String>): List<String> =
            names.filter { it == LATEST_NAME } + names.mapNotNull { n -> WEEKLY.matchEntire(n)?.let { n to it.groupValues[1] } }
                .sortedByDescending { it.second }.map { it.first }
    }
}
