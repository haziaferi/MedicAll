package com.hazia.medicall.data.backup

import com.hazia.medicall.data.sync.World
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A backup opens in desktop 7-Zip with the passphrase, and lists no names without it (SPEC:R22,
 * SPEC:R112). Skipped where 7-Zip is not installed, as on the Linux CI runner.
 */
class SevenZipInteropTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()
    @After fun close() = worlds.forEach { it.close() }

    private val sevenZip = File(System.getenv("SEVEN_ZIP") ?: "C:/Program Files/7-Zip/7z.exe")
    private val password = "invented-Passphrase-7"

    @Test
    fun backupOpensIn7ZipOnlyWithThePassphrase_SPEC_R22() {
        assumeTrue("7-Zip is not installed at $sevenZip", sevenZip.isFile)
        val w = World(tmp.newFolder(), 1).also { worlds += it }
        w.syncthing.now = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli()
        val a = w.Device("A")
        val profile = a.create("Profile A")
        val files = 30
        runBlocking { repeat(files) { a.blobs.add(profile, "Lab $it.pdf", "application/pdf", "Invented result $it".encodeToByteArray()) } }
        val folder = tmp.newFolder("backups").toPath()
        val writer = BackupWriter(a.db, a.blobs, SevenZip, DirectoryBackupTarget(folder), zone = ZoneOffset.UTC, workDir = tmp.newFolder())

        val started = System.nanoTime()
        val result = runBlocking { writer.write(BackupKind.LATEST, password.toCharArray()) }
        println("backup with $files files written and read back in ${(System.nanoTime() - started) / 1_000_000} ms")
        assertIs<BackupResult.Written>(result)
        val archive = folder.resolve(BackupWriter.LATEST_NAME).toFile()

        val listed = run("l", "-p$password", archive.path)
        assertEquals(0, listed.first, listed.second)
        assertTrue("HOW-TO-OPEN.txt" in listed.second, listed.second)
        assertTrue("records${File.separator}records.json" in listed.second || "records/records.json" in listed.second, listed.second)
        assertTrue("2026-11-20 Lab 29.pdf" in listed.second, listed.second)

        val tested = run("t", "-p$password", archive.path)
        assertEquals(0, tested.first, tested.second)
        assertTrue("Everything is Ok" in tested.second, tested.second)

        val noPassword = run("l", archive.path)
        assertTrue(noPassword.first != 0, noPassword.second)
        for (name in listOf("HOW-TO-OPEN", "records", "Lab 1.pdf")) assertFalse(name in noPassword.second, noPassword.second)
        assertTrue("Wrong password?" in noPassword.second || "Enter password" in noPassword.second, noPassword.second)

        val wrong = run("l", "-pwrong-Passphrase-7", archive.path)
        assertTrue(wrong.first != 0, wrong.second)
        assertFalse("HOW-TO-OPEN" in wrong.second, wrong.second)
    }

    /** Runs 7-Zip with [args], its input an empty file so a password prompt reads nothing; its exit code and output. */
    private fun run(vararg args: String): Pair<Int, String> {
        val empty = tmp.newFile()
        val p = ProcessBuilder(listOf(sevenZip.path) + args).redirectErrorStream(true).redirectInput(empty).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(60, TimeUnit.SECONDS)) { "7-Zip did not finish" }
        println("$ 7z ${args.joinToString(" ") { if (it.startsWith("-p")) "-p***" else it }}  -> exit ${p.exitValue()}")
        out.lines().filter { it.isNotBlank() }.forEach { println("    $it") }
        return p.exitValue() to out
    }
}
