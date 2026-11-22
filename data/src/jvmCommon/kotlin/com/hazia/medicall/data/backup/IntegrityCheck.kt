package com.hazia.medicall.data.backup

import com.hazia.medicall.data.blob.BlobName
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.blob.sha256Hex
import java.io.File
import java.io.IOException

/**
 * What one integrity check found (SPEC:R23), each file by its SHA-256: [checked] files in all,
 * [damaged] the ones whose copy here does not open, opens to other bytes than its hash, or is missing
 * though this device held it, [repaired] those restored from a backup, [unrepaired] those no
 * reachable backup holds intact: what the "Backup check failed" notification of SPEC:R73 reports
 * (phase 7). [notYetArrived] are files a reference names that this device never held: on their way
 * from the device that added them, neither damaged nor counted in [checked].
 */
data class IntegrityReport(
    val checked: Int,
    val damaged: List<String>,
    val repaired: List<String>,
    val unrepaired: List<String>,
    val notYetArrived: List<String> = emptyList(),
)

/**
 * Checks every file this device stores, and every file a file reference names, against its SHA-256
 * (SPEC:R23), straight from the stored sealed bytes: nothing reads them through [Blobs.read] first,
 * which would delete a damaged copy. A reference whose hash is not a SHA-256 in hex is passed over: it
 * names no file. Each damaged file is searched for in the backups of [target], newest first
 * ([BackupWriter.newestFirst]), each read in one pass: its `records/files.csv` (written before the
 * files) says which entry holds which hash, and only the entries wanted are opened. The first copy
 * whose bytes match the hash is stored again; a backup whose copy does not match, or that does not
 * open, is passed over for the one before it.
 *
 * It runs before each backup write ([BackupWriter]), and on its own once a week
 * ([BackupSchedule.checkDue]). Blocking: call it off the main thread.
 */
class IntegrityCheck(
    private val blobs: Blobs,
    private val target: BackupTarget,
    private val reader: ArchiveReader = SevenZip,
    private val workDir: File = File(System.getProperty("java.io.tmpdir") ?: "."),
) {

    /** Checks and repairs under [password], the passphrase of SPEC:R109 that seals the backups. */
    suspend fun run(password: CharArray): IntegrityReport {
        val held = blobs.held().toSet()
        blobs.markHeld(held)
        val heldOnce = blobs.heldOnce()
        val names = (held + blobs.refs().map { it.sha256 }.filter(BlobName::isValid)).toSortedSet()
        val notYetArrived = names.filter { it !in held && it !in heldOnce }
        val damaged = (names - notYetArrived.toSet()).filter { blobs.intact(it) == null }
        val wanted = damaged.toMutableSet()
        val repaired = mutableSetOf<String>()
        if (wanted.isNotEmpty() && target.reachable()) {
            for (backup in BackupWriter.newestFirst(target.list())) {
                if (wanted.isEmpty()) break
                val found = repairFrom(backup, password, wanted)
                wanted -= found
                repaired += found
            }
        }
        blobs.markHeld(repaired)
        return IntegrityReport(names.size - notYetArrived.size, damaged, damaged.filter { it in repaired }, damaged.filter { it in wanted }, notYetArrived)
    }

    /** The files of [wanted] stored again from [backup], read in one pass; none when it cannot be read or does not open. */
    private fun repairFrom(backup: String, password: CharArray, wanted: Set<String>): Set<String> {
        val found = mutableSetOf<String>()
        val file = File.createTempFile(TEMP_PREFIX, ".7z", workDir)
        try {
            val input = target.openRead(backup) ?: return found
            input.use { i -> file.outputStream().use { i.copyTo(it) } }
            var index: Map<String, String>? = null
            reader.readEach(file, password) { entry ->
                if (entry.name == BackupContent.FILES_INDEX) {
                    index = entry.bytesOrNull()?.decodeToString()?.let(BackupContent::filesIndex)
                    return@readEach
                }
                val hash = index?.get(entry.name) ?: return@readEach
                if (hash !in wanted || hash in found) return@readEach
                val plain = entry.bytesOrNull() ?: return@readEach
                if (sha256Hex(plain) != hash) return@readEach
                blobs.storeBytes(plain)
                found += hash
            }
        } catch (_: IOException) {
            // This backup cannot be read: the one before it may hold the file.
        } finally {
            file.delete()
        }
        return found
    }

    private companion object {
        const val TEMP_PREFIX = "medicall-check-"
    }
}
