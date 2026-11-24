package com.hazia.medicall.data.device

import com.hazia.medicall.core.lock.LockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A sealed file held in memory, as the key store's file holds it on Android. */
internal class MemorySealedFile(var bytes: ByteArray? = null) : SealedFile {
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) {
        this.bytes = bytes.copyOf()
    }
}

/** The folders a device joins and its lock settings (SPEC:R110, SPEC:R11). Every value is invented. */
class FolderRegistryTest {

    @Test
    fun foldersAreKeptInOrder_SPEC_R110() {
        val file = MemorySealedFile()
        val registry = FolderRegistry(file)
        val a = registry.add("Folder A", "content://tree/folder-a")
        val b = registry.add("Folder B", "content://tree/folder-b")
        assertEquals(listOf(a, b), registry.folders())
        registry.rename(b.id, "Folder B2")
        registry.setBackup(b.id, "content://tree/backup-b")
        registry.remove(a.id)

        val again = FolderRegistry(file).folders()
        assertEquals(listOf(JoinedFolder(b.id, "Folder B2", "content://tree/folder-b", "content://tree/backup-b")), again)
        assertEquals("medicall-${b.id}.db", again.single().databaseName)
    }

    @Test
    fun oneFolderUriIsJoinedOnce_SPEC_R110() {
        val registry = FolderRegistry(MemorySealedFile())
        registry.add("Folder A", "content://tree/folder-a")
        assertFailsWith<IllegalStateException> { registry.add("Folder A again", "content://tree/folder-a") }
        assertEquals(1, registry.folders().size)
    }

    @Test
    fun anUnknownFolderIsRefused_SPEC_R110() {
        val registry = FolderRegistry(MemorySealedFile())
        assertFailsWith<IllegalArgumentException> { registry.rename("no-such-id", "Folder X") }
        assertFailsWith<IllegalArgumentException> { registry.remove("no-such-id") }
    }

    @Test
    fun theLockIsKeptWithTheFolders_SPEC_R11() {
        val file = MemorySealedFile()
        val registry = FolderRegistry(file)
        registry.add("Folder A", "content://tree/folder-a")
        val lock = LockState.OFF.copy(pinHash = "00ff", salt = "0102", timeoutMinutes = 5, wrongTries = 2, lastWrongAtElapsed = 77)
        registry.saveLock(lock)
        val again = FolderRegistry(file)
        assertEquals(lock, again.lock())
        assertEquals(1, again.folders().size)
    }

    @Test
    fun noSettingsMeansFirstRun_SPEC_R11() {
        for (file in listOf(MemorySealedFile(null), MemorySealedFile("not settings".encodeToByteArray()), MemorySealedFile(ByteArray(0)))) {
            val registry = FolderRegistry(file)
            assertTrue(registry.folders().isEmpty())
            assertEquals(LockState.OFF, registry.lock())
        }
    }

    @Test
    fun theFileHoldsNoPassphrase_SPEC_R109() {
        val file = MemorySealedFile()
        FolderRegistry(file).add("Folder A", "content://tree/folder-a")
        val text = file.bytes!!.decodeToString()
        assertTrue("passphrase" !in text.lowercase())
    }
}
