package com.hazia.medicall.data.backup

import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.HybridClock
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.DETAILS
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.NAME
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.REMOVED
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.openMedicAllDatabase
import com.hazia.medicall.data.sync.testCipher
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.sevenz.SealedSevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A backup larger than the heap is written, read back and restored (H1): the backup, its check and
 * its restore hold one entry at a time, not the archive. Runs [BigBackup] in a second JVM capped at
 * [HEAP_MB] MB, with [FILES] files of [FILE_MB] MB, 96 MB in all. The test's writer stores the entries
 * uncompressed (7z COPY): LZMA2's encoder alone takes some 94 MB, a constant this test is not about,
 * and random bytes do not compress, so the archive is as large as the files.
 */
class BigBackupTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun backupLargerThanTheHeapIsWrittenAndRestored_H1() {
        val java = ProcessHandle.current().info().command().get()
        val p = ProcessBuilder(java, "-Xmx${HEAP_MB}m", "-cp", System.getProperty("java.class.path"), BigBackup::class.java.name, tmp.root.path)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(5, TimeUnit.MINUTES)) { "the second JVM did not finish" }
        println(out)
        assertEquals(0, p.exitValue(), out)
        assertTrue("restored $FILES files" in out, out)
    }

    companion object {
        const val HEAP_MB = 64
        const val FILES = 48
        const val FILE_MB = 2
    }
}

/** A 7z writer as [SevenZip]'s, storing the entries uncompressed. */
internal object StoredSevenZip : ArchiveWriter {
    override fun write(target: File, password: CharArray, entries: List<ArchiveEntry>) {
        SealedSevenZOutputFile(target, password).use { out ->
            out.setContentCompression(SevenZMethod.COPY)
            for (entry in entries) {
                out.putArchiveEntry(SevenZArchiveEntry().apply { name = entry.name })
                entry.open().use(out::write)
                out.closeArchiveEntry()
            }
        }
    }
}

/** The second JVM of [BigBackupTest]: argument 0 is an empty folder to work in. */
object BigBackup {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            run(File(args[0]))
        } catch (e: Throwable) {
            e.printStackTrace(System.out)
            System.out.flush()
            exitProcess(1)
        }
        exitProcess(0)
    }

    private class Device(dir: File, name: String) {
        val db = openMedicAllDatabase(File(dir, "$name.db")).database
        val writes = LocalWrites(db, HybridClock(name, { NOW }))
        val store = DirectoryBlobStore(File(dir, "$name-blobs").toPath())
        val blobs = Blobs(writes, db, store, testCipher())
    }

    private fun run(dir: File) = runBlocking {
        val a = Device(dir, "A")
        val profile = "profile-1"
        a.writes.write(mapOf(PROFILE to listOf(profile))) { store ->
            val s = a.writes.clock.tick()
            a.writes.merger.created(store, Row(PROFILE, profile, mapOf(
                NAME to Group(s, mapOf("name" to "Profile A")),
                DETAILS to Group(s, mapOf("birth_date" to null, "kind" to "ADULT")),
                REMOVED to Group(s, mapOf("removed_at" to null)),
            )))
        }
        val random = Random(7)
        repeat(BigBackupTest.FILES) { a.blobs.add(profile, "Scan $it.bin", "application/octet-stream", random.nextBytes(BigBackupTest.FILE_MB shl 20)) }
        println("added ${BigBackupTest.FILES} files, ${BigBackupTest.FILES * BigBackupTest.FILE_MB} MB")

        val password = "invented-Passphrase-7".toCharArray()
        val folder = File(dir, "backups").apply { mkdirs() }
        val target = DirectoryBackupTarget(folder.toPath())
        val work = File(dir, "work").apply { mkdirs() }
        val result = BackupWriter(a.db, a.blobs, StoredSevenZip, target, wallMillis = { NOW }, zone = ZoneOffset.UTC, workDir = work).write(BackupKind.LATEST, password)
        check(result is BackupResult.Written && result.files == BigBackupTest.FILES) { "$result" }
        println("backup written, ${File(folder, BackupWriter.LATEST_NAME).length() shr 20} MB")

        val b = Device(dir, "B")
        val report = BackupRestore(b.writes, b.blobs, SevenZip, wallMillis = { NOW }).restore(File(folder, BackupWriter.LATEST_NAME), password)
        check(report != null && report.files == BigBackupTest.FILES && report.skipped == 0) { "$report" }
        check(b.store.list().toSet() == a.store.list().toSet()) { "the restored files differ" }
        println("restored ${report.files} files")

        // The integrity check copies the backup out of the folder and repairs from it, one entry at a time.
        val lost = a.store.list().first()
        a.store.delete(lost)
        val integrity = IntegrityCheck(a.blobs, target, SevenZip, work).run(password)
        check(integrity.repaired == listOf(lost) && integrity.unrepaired.isEmpty()) { "$integrity" }
        println("repaired 1 file from the backup")
        a.db.close()
        b.db.close()
    }

    private const val NOW = 1_763_632_800_000L
}
