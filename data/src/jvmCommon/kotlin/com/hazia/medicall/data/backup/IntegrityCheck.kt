package com.hazia.medicall.data.backup

import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.blob.sha256Hex
import java.io.File

/**
 * What one integrity check found (SPEC:R23), each file by its SHA-256: [checked] files in all,
 * [damaged] the ones whose copy here is missing, does not open or opens to other bytes than its hash,
 * [repaired] those restored from a backup, [unrepaired] those no reachable backup holds intact: what
 * the "Backup check failed" notification of SPEC:R73 reports (phase 7).
 */
data class IntegrityReport(val checked: Int, val damaged: List<String>, val repaired: List<String>, val unrepaired: List<String>)

/**
 * Checks every file this device stores, and every file a file reference names, against its SHA-256
 * (SPEC:R23), straight from the stored sealed bytes: nothing reads them through [Blobs.read] first,
 * which would delete a damaged copy. A file that is missing counts as damaged. Each damaged file is
 * searched for in the backups of [target], newest first ([BackupWriter.newestFirst]), through the
 * backup's `records/files.csv`; the first copy whose bytes match the hash is stored again, and a
 * backup whose copy does not match, or that does not open, is passed over for the one before it.
 *
 * It runs before each backup write, and on its own once a week ([BackupSchedule.checkDue]).
 * Blocking: call it off the main thread.
 */
class IntegrityCheck(
    private val blobs: Blobs,
    private val target: BackupTarget,
    private val reader: ArchiveReader = SevenZip,
    private val workDir: File = File(System.getProperty("java.io.tmpdir") ?: "."),
) {

    /** Checks and repairs under [password], the passphrase of SPEC:R109 that seals the backups. */
    suspend fun run(password: CharArray): IntegrityReport {
        val names = (blobs.held() + blobs.refs().map { it.sha256 }).toSortedSet()
        val damaged = names.filter { blobs.intact(it) == null }
        val wanted = damaged.toMutableSet()
        val repaired = mutableSetOf<String>()
        if (wanted.isNotEmpty() && target.reachable()) {
            for (backup in BackupWriter.newestFirst(target.list())) {
                if (wanted.isEmpty()) break
                val bytes = target.read(backup) ?: continue
                val file = File.createTempFile(TEMP_PREFIX, ".7z", workDir)
                try {
                    file.writeBytes(bytes)
                    val index = reader.read(file, password, BackupContent.FILES_INDEX)?.decodeToString()?.let(BackupContent::filesIndex) ?: continue
                    for ((path, hash) in index) {
                        if (hash !in wanted) continue
                        val plain = reader.read(file, password, path) ?: continue
                        if (sha256Hex(plain) != hash) continue
                        blobs.storeBytes(plain)
                        wanted -= hash
                        repaired += hash
                    }
                } finally {
                    file.delete()
                }
            }
        }
        return IntegrityReport(names.size, damaged, damaged.filter { it in repaired }, damaged.filter { it in wanted })
    }

    private companion object {
        const val TEMP_PREFIX = "medicall-check-"
    }
}
