package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hazia.medicall.core.lock.LockState
import com.hazia.medicall.data.device.FolderRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStoreException
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

    // A sealed file that opens but is not settings: kept aside once, as a copy, and the lock stays closed on every start.
    @Test
    fun aDeviceFileThatDoesNotReadStaysClosedAndIsKeptAsideOnce_SPEC_R11() {
        KeyVault(context).sealedFile(settings).write("not settings".encodeToByteArray())

        repeat(2) {
            assertEquals(LockState.UNREADABLE, FolderRegistry(KeyVault(context).sealedFile(settings)).lock())
            assertTrue("the settings file stays in place", settings.exists())
        }
        val copies = context.filesDir.listFiles { f -> f.name.startsWith(settings.name + KeyVault.LOST_SUFFIX) }!!.toList()
        assertEquals(1, copies.size)
        val kept = assertNotNull(KeyVault(context).sealedFile(copies.single()).read())
        assertEquals("not settings", kept.decodeToString())
        assertTrue("no temporary file is left", context.filesDir.listFiles { f -> f.name.startsWith(settings.name + ".tmp-") }!!.isEmpty())
    }

    // The key store answers the read, then fails as the settings are written back: the file must still be there.
    @Test
    fun aWriteThatFailsAfterTheCopyLeavesTheFileAndTheLockClosed_SPEC_R11() {
        KeyVault(context).sealedFile(settings).write("not settings".encodeToByteArray())
        val sealed = settings.readBytes()
        var calls = 0
        val failing = KeyVault(context, File(context.filesDir, "unused-key-$run.sealed")) {
            if (calls++ == 0) KeyVault.existingKey() else throw KeyStoreException("the key store did not answer")
        }
        assertThrows(KeyStoreException::class.java) { FolderRegistry(failing.sealedFile(settings)) }
        assertTrue("the settings file is left as it was", sealed contentEquals settings.readBytes())

        assertEquals(LockState.UNREADABLE, FolderRegistry(KeyVault(context).sealedFile(settings)).lock())
        assertEquals(LockState.UNREADABLE, FolderRegistry(KeyVault(context).sealedFile(settings)).lock())
        val copies = context.filesDir.listFiles { f -> f.name.startsWith(settings.name + KeyVault.LOST_SUFFIX) }!!.toList()
        assertEquals("one copy per start that did not write back, none lost", 2, copies.size)
        for (copy in copies) assertEquals("not settings", assertNotNull(KeyVault(context).sealedFile(copy).read()).decodeToString())
        assertTrue("no temporary file is left", context.filesDir.listFiles { f -> f.name.startsWith(settings.name + ".tmp-") }!!.isEmpty())
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
