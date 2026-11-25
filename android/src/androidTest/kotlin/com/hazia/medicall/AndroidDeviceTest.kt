package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.data.Opened
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Two sync folders on a real phone, over the stub documents provider (SPEC:R110, SPEC:R10). Throwaway names only. */
@RunWith(AndroidJUnit4::class)
class AndroidDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val run = UUID.randomUUID().toString()
    private val settings = File(context.filesDir, "device-test-$run.sealed")
    private val treeA = StubTree(context)
    private val treeB = StubTree(context)
    private val made = mutableListOf<AndroidDevice>()

    private fun device() = AndroidDevice(context, KeyVault(context).sealedFile(settings)).also { made += it }

    @After fun clean() {
        made.forEach { it.close() }
        val ids = made.firstOrNull()?.device?.folders()?.map { it.id }.orEmpty()
        for (id in ids) {
            context.deleteDatabase("medicall-$id.db")
            context.filesDir.listFiles { f -> id in f.name }?.forEach { it.deleteRecursively() }
            File(context.filesDir, "blobs/$id").deleteRecursively()
            File(context.filesDir, "${AndroidDevice.JOURNALS}/$id").deleteRecursively()
        }
        context.filesDir.listFiles { f -> run in f.name }?.forEach { it.delete() }
        treeA.clean()
        treeB.clean()
    }

    @Test
    fun twoFoldersOpenAfterARestart_SPEC_R110() = runBlocking {
        val first = device()
        val a = first.device.join("Folder A", treeA.uri.toString(), "invented-passphrase-a".toCharArray())
        val b = first.device.join("Folder B", treeB.uri.toString(), "invented-passphrase-b".toCharArray())
        assertTrue(a is Opened.Ready && b is Opened.Ready)
        (a as Opened.Ready).core.addProfile("Profile A")
        (b as Opened.Ready).core.addProfile("Profile B")
        a.core.syncNow(); b.core.syncNow()
        first.close()

        // As after a restart: nothing typed, each folder opens from its own vault.
        val again = device()
        val opened = again.openAll()
        assertEquals(2, opened.size)
        assertTrue(opened.values.all { it.getOrNull() is Opened.Ready })
        assertEquals(listOf("Profile A", "Profile B"), again.device.profiles().map { it.second.name })
        val (idA, idB) = again.device.folders().map { it.id }
        assertFalse(KeyVault.forFolder(context, idA).load()!! contentEquals KeyVault.forFolder(context, idB).load()!!)
    }

    @Test
    fun aRefusedJoinLeavesNothing_SPEC_R110() = runBlocking {
        val first = device()
        assertTrue(first.device.join("Folder A", treeA.uri.toString(), "invented-passphrase-a".toCharArray()) is Opened.Ready)
        // A second device (its own settings file) joining the same folder with a wrong passphrase.
        val stranger = AndroidDevice(context, KeyVault(context).sealedFile(File(context.filesDir, "device-test-$run-2.sealed"))).also { made += it }
        val before = appFiles()
        val refused = stranger.device.join("Folder A", treeA.uri.toString(), "a-wrong-passphrase-entirely".toCharArray())
        assertEquals(Unlock.WrongPassphrase, (refused as Opened.Refused).reason)
        // No database, vault file, file store or write journal is left behind.
        assertEquals(before, appFiles())
        assertTrue(stranger.device.folders().isEmpty())
    }

    @Test
    fun leavingAFolderSetsItAsideAndKeepsTheOther_SPEC_R110() = runBlocking {
        val first = device()
        val a = first.device.join("Folder A", treeA.uri.toString(), "invented-passphrase-a".toCharArray()) as Opened.Ready
        val b = first.device.join("Folder B", treeB.uri.toString(), "invented-passphrase-b".toCharArray()) as Opened.Ready
        a.core.addProfile("Profile A")
        b.core.addProfile("Profile B")
        val (idA, idB) = first.device.folders().map { it.id }
        first.device.leave(idA)

        assertEquals(listOf(idB), first.device.folders().map { it.id })
        // Set aside, not deleted (SPEC:R10): the file is renamed and its key kept; the passphrase is forgotten.
        assertFalse("medicall-$idA.db" in context.databaseList())
        assertTrue(context.databaseList().any { it.startsWith("medicall-$idA.db") })
        assertTrue(KeyVault.forFolder(context, idA).load() != null)
        assertTrue(KeyVault.forFolder(context, idA).loadPassphrase() == null)
        first.close()

        val again = device()
        again.openAll()
        assertEquals(listOf("Profile B"), again.device.profiles().map { it.second.name })
        // The left folder is no longer in the registry, so clean() would miss it.
        context.databaseList().filter { it.startsWith("medicall-$idA.db") }.forEach { context.deleteDatabase(it) }
        context.filesDir.listFiles { f -> idA in f.name }?.forEach { it.deleteRecursively() }
        File(context.filesDir, "blobs/$idA").deleteRecursively()
        File(context.filesDir, "${AndroidDevice.JOURNALS}/$idA").deleteRecursively()
        Unit
    }

    // SPEC:R10 a folder whose key was lost and whose passphrase is refused leaves one set-aside database (its data),
    // however many times it is tried: the empty ones made with a dropped key are not kept.
    @Test
    fun refusedOpensPileUpNoEmptyDatabases_SPEC_R10() = runBlocking {
        val first = device()
        val a = first.device.join("Folder A", treeA.uri.toString(), "invented-passphrase-a".toCharArray()) as Opened.Ready
        a.core.addProfile("Profile A")
        val id = first.device.folders().single().id
        first.close()
        // The key store lost the database key, and the kept passphrase no longer opens (changed elsewhere).
        assertTrue(File(context.filesDir, "database-key-v2-$id.sealed").delete())
        KeyVault.forFolder(context, id).storePassphrase("a-passphrase-changed-elsewhere".toCharArray())
        repeat(3) {
            val again = device()
            assertTrue(again.openAll().values.single().getOrNull() is Opened.Refused)
            again.close()
        }
        val aside = context.databaseList().filter { Regex("medicall-$id\\.db\\.unopenable-\\d+").matches(it) }
        assertEquals(aside.toString(), 1, aside.size)
        assertFalse("medicall-$id.db" in context.databaseList())
        aside.forEach { context.deleteDatabase(it) }
        context.databaseList().filter { it.startsWith("medicall-$id.db") }.forEach { context.deleteDatabase(it) }
        File(context.getDatabasePath("medicall-$id.db").path + ".recovering").delete()
        Unit
    }

    // A crash between storing a folder's vault and remembering the folder, or a lost settings file, leaves files of a
    // folder no longer joined: its passphrase goes, its database is set aside, its key stays.
    @Test
    fun aSweepClearsWhatAFolderNoLongerJoinedLeft_SPEC_R110() = runBlocking {
        val orphan = "orphan-$run"
        KeyVault.forFolder(context, orphan).storePassphrase("invented-orphan-passphrase".toCharArray())
        KeyVault.forFolder(context, orphan).store(ByteArray(32) { 7 })
        val key = ByteArray(32) { 9 }
        com.hazia.medicall.data.openEncryptedDatabase(context, key, "medicall-$orphan.db").database.close()
        val first = device()
        first.device.join("Folder A", treeA.uri.toString(), "invented-passphrase-a".toCharArray())
        val joined = first.device.folders().single().id

        first.sweepOrphans { it == orphan || it == joined }

        assertFalse(File(context.filesDir, "passphrase-$orphan.sealed").exists())
        assertTrue("the key stays", File(context.filesDir, "database-key-v2-$orphan.sealed").exists())
        assertFalse("medicall-$orphan.db" in context.databaseList())
        val aside = context.databaseList().filter { it.startsWith("medicall-$orphan.db.unopenable-") }
        assertTrue(aside.isNotEmpty())
        // The joined folder keeps everything.
        assertTrue(File(context.filesDir, "passphrase-$joined.sealed").exists())
        assertTrue("medicall-$joined.db" in context.databaseList())
        aside.forEach { context.deleteDatabase(it) }
        context.filesDir.listFiles { f -> orphan in f.name }?.forEach { it.delete() }
        Unit
    }

    /** Every file the app holds in its private storage, with its size. */
    private fun appFiles(): Set<String> =
        listOf(context.filesDir, context.getDatabasePath("x").parentFile!!).flatMap { dir ->
            dir.walk().filter { it.isFile }.map { "${it.path}:${it.length()}" }.toList()
        }.toSet()
}
