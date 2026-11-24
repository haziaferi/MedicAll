package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        val dbsBefore = context.databaseList().toSet()
        // A second device (its own settings file) joining the same folder with a wrong passphrase.
        val stranger = AndroidDevice(context, KeyVault(context).sealedFile(File(context.filesDir, "device-test-$run-2.sealed"))).also { made += it }
        assertTrue(stranger.device.join("Folder A", treeA.uri.toString(), "a-wrong-passphrase-entirely".toCharArray()) is Opened.Refused)
        assertEquals(dbsBefore, context.databaseList().toSet())
        assertTrue(stranger.device.folders().isEmpty())
    }
}
