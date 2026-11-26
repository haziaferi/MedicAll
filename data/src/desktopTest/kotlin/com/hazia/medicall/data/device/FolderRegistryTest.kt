package com.hazia.medicall.data.device

import com.hazia.medicall.core.lock.LockState
import com.hazia.medicall.core.look.DeviceLook
import com.hazia.medicall.core.look.LightDark
import com.hazia.medicall.core.look.Palette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A sealed file held in memory, as the key store's file holds it on Android. */
internal class MemorySealedFile(var bytes: ByteArray? = null) : SealedFile {
    /** What [setAside] moved out of the way, oldest first. */
    val keptAside = mutableListOf<ByteArray>()
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) {
        this.bytes = bytes.copyOf()
    }
    override fun setAside() {
        bytes?.let { keptAside += it }
        bytes = null
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
        val registry = FolderRegistry(MemorySealedFile(null))
        assertTrue(registry.folders().isEmpty())
        assertEquals(LockState.OFF, registry.lock())
    }

    @Test
    fun anUnreadableFileIsKeptAsideAndTheLockStaysClosed_SPEC_R11() {
        for (raw in listOf("not settings", "", """{"v":2,"folders":[]}""")) {
            val file = MemorySealedFile(raw.encodeToByteArray())
            val registry = FolderRegistry(file)
            assertTrue(registry.folders().isEmpty())
            assertEquals(LockState.UNREADABLE, registry.lock())
            registry.add("Folder A", "content://tree/folder-a")
            assertEquals(listOf(raw), file.keptAside.map { it.decodeToString() })
        }
    }

    @Test
    fun aBadFolderEntryKeepsTheOthersAndTheLock_SPEC_R110() {
        val file = MemorySealedFile()
        val registry = FolderRegistry(file)
        registry.add("Folder A", "content://tree/folder-a")
        val b = registry.add("Folder B", "content://tree/folder-b")
        val lock = LockState.OFF.copy(pinHash = "00ff", salt = "0102")
        registry.saveLock(lock)
        val original = file.bytes!!.decodeToString()
        // Folder A's entry loses its address.
        file.bytes = original.replace(""""folder":"content://tree/folder-a",""", "").encodeToByteArray()

        val again = FolderRegistry(file)
        assertEquals(listOf(b), again.folders())
        assertEquals(lock, again.lock())
        assertEquals(1, file.keptAside.size)
    }

    @Test
    fun theLookStartsOnAlbarelloFollowingTheSystem_SPEC_R114() {
        val registry = FolderRegistry(MemorySealedFile(null))
        assertEquals(DeviceLook(LightDark.SYSTEM, Palette.ALBARELLO), registry.look())
    }

    @Test
    fun theLookIsKeptWithTheFoldersAndTheLock_SPEC_R114() {
        val file = MemorySealedFile()
        val registry = FolderRegistry(file)
        val folder = registry.add("Folder A", "content://tree/folder-a")
        val lock = LockState.OFF.copy(pinHash = "00ff", salt = "0102")
        registry.saveLock(lock)
        for (lightDark in LightDark.entries) for (palette in Palette.entries) {
            registry.saveLook(DeviceLook(lightDark, palette))
            val again = FolderRegistry(file)
            assertEquals(DeviceLook(lightDark, palette), again.look())
            assertEquals(listOf(folder), again.folders())
            assertEquals(lock, again.lock())
        }
        assertTrue(file.keptAside.isEmpty())
    }

    @Test
    fun aFileFromBeforeTheLookReadsWhole_SPEC_R114() {
        val file = MemorySealedFile()
        val registry = FolderRegistry(file)
        registry.add("Folder A", "content://tree/folder-a")
        val withoutLook = Json.parseToJsonElement(file.bytes!!.decodeToString()).jsonObject.let { JsonObject(it - "look") }
        file.bytes = withoutLook.toString().encodeToByteArray()

        val again = FolderRegistry(file)
        assertEquals(DeviceLook.DEFAULT, again.look())
        assertEquals(1, again.folders().size)
        assertTrue(file.keptAside.isEmpty())
    }

    @Test
    fun aLookThatDoesNotReadIsTheDefaultAndKeepsTheRest_SPEC_R114() {
        val file = MemorySealedFile()
        val registry = FolderRegistry(file)
        val folder = registry.add("Folder A", "content://tree/folder-a")
        registry.saveLook(DeviceLook(LightDark.DARK, Palette.MURANO))
        val original = file.bytes!!.decodeToString()
        file.bytes = original.replace(""""palette":"murano"""", """"palette":"no-such-palette"""").encodeToByteArray()

        val again = FolderRegistry(file)
        assertEquals(DeviceLook.DEFAULT, again.look())
        assertEquals(listOf(folder), again.folders())
        assertEquals(1, file.keptAside.size)
    }
}
