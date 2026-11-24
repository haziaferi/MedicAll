package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hazia.medicall.data.device.FolderRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The device's settings file and the vaults of joined folders, on a real key store (SPEC:R10, SPEC:R110). Throwaway names only. */
@RunWith(AndroidJUnit4::class)
class SealedDeviceFileTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val run = UUID.randomUUID().toString()
    private val settings = File(context.filesDir, "device-test-$run.sealed")

    @After fun clean() {
        context.filesDir.listFiles { f -> run in f.name }?.forEach { it.delete() }
    }

    @Test
    fun aDeviceFileIsSealed_SPEC_R10() {
        val registry = FolderRegistry(KeyVault(context).sealedFile(settings))
        registry.add("Folder A", "content://invented/tree/folder-a")
        val raw = settings.readBytes().decodeToString(throwOnInvalidSequence = false)
        assertFalse("the label is not readable on disk", "Folder A" in raw)
        assertFalse("nor the folder's address", "folder-a" in raw)

        val again = FolderRegistry(KeyVault(context).sealedFile(settings)).folders()
        assertEquals(listOf("Folder A"), again.map { it.label })
    }

    @Test
    fun eachFolderKeepsItsOwnKeyAndPassphrase_SPEC_R109() {
        val a = KeyVault.forFolder(context, "test-a-$run")
        val b = KeyVault.forFolder(context, "test-b-$run")
        val keyA = a.databaseKey().also { assertTrue(it.isNew); a.store(it.bytes) }.bytes
        val keyB = b.databaseKey().also { assertTrue(it.isNew); b.store(it.bytes) }.bytes
        assertFalse(keyA contentEquals keyB)
        a.storePassphrase("invented-passphrase-a".toCharArray())
        b.storePassphrase("invented-passphrase-b".toCharArray())

        val a2 = KeyVault.forFolder(context, "test-a-$run")
        assertTrue(keyA contentEquals assertNotNull(a2.load()))
        assertEquals("invented-passphrase-a", String(assertNotNull(a2.loadPassphrase())))
        assertEquals("invented-passphrase-b", String(assertNotNull(KeyVault.forFolder(context, "test-b-$run").loadPassphrase())))
    }

    private fun <T> assertNotNull(value: T?): T {
        assertNotNull(null, value)
        return value!!
    }
}
