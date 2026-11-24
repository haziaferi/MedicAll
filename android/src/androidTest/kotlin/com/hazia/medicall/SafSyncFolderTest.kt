package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStream

/** The sync folder through the Storage Access Framework (SPEC:R16), on a tree of the debug build's stub provider. */
@RunWith(AndroidJUnit4::class)
class SafSyncFolderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tree = StubTree(context)
    private val path = "devices/A/log-000000.jsonl"
    private val old = "one\n".toByteArray()
    private val new = "one\ntwo\n".toByteArray()

    private val journals = File(context.cacheDir, "saf-test-journals")

    @After fun clean() {
        tree.clean()
        journals.deleteRecursively()
    }

    /** The folder as [device] sees it, with that device's own write journal, as each phone has its own. */
    private fun folder(device: String = "A", io: SafSyncFolder.Io = SafSyncFolder.Io()) =
        SafSyncFolder(context, tree.uri, device, File(journals, device), io)

    /** Puts a whole, sealed temporary file for [target] on disk, as Syncthing would deliver one from a peer. */
    private fun plantSealedTemp(target: String, bytes: ByteArray) {
        val file = File(tree.dir, target)
        File(file.parentFile, ".tmp-" + file.name).writeBytes(bytes + SafSyncFolder.seal(bytes))
    }

    /** Writes half of the bytes of [name] and stops, as a crash would. */
    private class CrashWriting(private val name: String) : SafSyncFolder.Io() {
        override fun write(name: String, bytes: ByteArray, to: OutputStream) {
            if (name != this.name) return super.write(name, bytes, to)
            to.write(bytes, 0, bytes.size / 2)
            to.flush()
            throw Crash()
        }
    }

    private fun onDisk(dir: String): List<String> = File(tree.dir, dir).list()?.sorted().orEmpty()

    @Test
    fun replaceThenReadReturnsNewBytes_SPEC_R16() {
        val folder = folder()

        folder.replace(path, old)
        folder.replace(path, new)

        assertArrayEquals(new, folder.read(path))
        assertEquals(new.size.toLong(), folder.size(path))
        assertEquals(listOf("log-000000.jsonl"), folder.list("devices/A"))
        assertEquals(listOf("A"), folder.list("devices"))
        assertEquals("no temporary file is left", listOf("log-000000.jsonl"), onDisk("devices/A"))
        assertNull(folder.read("devices/A/log-000001.jsonl"))
        assertNull(folder.size("devices/B/log-000000.jsonl"))
        assertEquals(emptyList<String>(), folder.list("devices/B"))

        folder.delete(path)
        assertNull(folder.read(path))
    }

    @Test
    fun interruptedReplaceLeavesOldOrNew_SPEC_R16() {
        folder().replace(path, old)

        // A crash while the temporary copy is written: the target is untouched.
        runCatching { folder(io = CrashWriting(".tmp-log-000000.jsonl")).replace(path, new) }
            .also { assertTrue(it.exceptionOrNull() is Crash) }
        assertArrayEquals("the old bytes", old, folder().read(path))
        assertEquals("the torn temporary copy is dropped", listOf("log-000000.jsonl"), onDisk("devices/A"))

        // A crash after the temporary copy was written and checked, halfway through the target.
        runCatching { folder(io = CrashWriting("log-000000.jsonl")).replace(path, new) }
            .also { assertTrue(it.exceptionOrNull() is Crash) }
        assertEquals("the target is torn on disk", new.size / 2, File(tree.dir, path).readBytes().size)
        val folder = folder()
        assertArrayEquals("a reader gets the new bytes, mended from the whole temporary copy", new, folder.read(path))
        assertEquals(listOf("log-000000.jsonl"), onDisk("devices/A"))

        // And the next replace lands whole.
        val newer = "one\ntwo\nthree\n".toByteArray()
        folder.replace(path, newer)
        assertArrayEquals(newer, folder.read(path))
        assertEquals(listOf("log-000000.jsonl"), onDisk("devices/A"))
    }

    @Test
    fun revokedGrantIsUnreachable_SPEC_R16() {
        val folder = folder()
        assertTrue(folder.reachable())

        StubDocumentsProvider.revoked = true
        assertFalse("a revoked grant", folder.reachable())
        StubDocumentsProvider.revoked = false

        tree.dir.deleteRecursively()
        assertFalse("a tree that is gone", folder.reachable())
    }

    // SPEC:R16 a peer's sealed temporary file, arriving after the peer wrote newer bytes, is neither landed nor deleted.
    @Test
    fun peersTempFileIsNotLanded_SPEC_R16() {
        folder("A").replace(path, new)
        plantSealedTemp(path, old) // A's older write, reaching B after A's newer target did
        val b = folder("B")

        assertArrayEquals("B reads A's newest bytes", new, b.read(path))
        assertEquals(new.size.toLong(), b.size(path))
        assertArrayEquals("the target on disk is untouched", new, File(tree.dir, path).readBytes())
        assertEquals("B leaves A's temporary file alone", listOf(".tmp-log-000000.jsonl", "log-000000.jsonl"), onDisk("devices/A"))

        // The key file is no device's own: B did not write it, so it does not land a temporary copy of it either.
        val keyFile = "keys/keyinfo.json"
        folder("A").replace(keyFile, new)
        plantSealedTemp(keyFile, old)
        assertArrayEquals(new, b.read(keyFile))
        assertEquals(listOf(".tmp-keyinfo.json", "keyinfo.json"), onDisk("keys"))
    }

    // SPEC:R16 this device's own torn write outside devices/<id>/ (the key file) is still finished from its journal.
    @Test
    fun ownTornWriteOutsideItsDirectoryIsFinished_SPEC_R16() {
        val keyFile = "keys/keyinfo.json"
        folder("A").replace(keyFile, old)
        runCatching { folder("A", CrashWriting("keyinfo.json")).replace(keyFile, new) }
            .also { assertTrue(it.exceptionOrNull() is Crash) }

        assertArrayEquals("another device does not finish it", new.copyOf(new.size / 2), folder("B").read(keyFile))
        assertArrayEquals("the device that wrote it does", new, folder("A").read(keyFile))
        assertEquals(listOf("keyinfo.json"), onDisk("keys"))
    }

    // SPEC:R16 the sync folder gets a .stignore keeping temporary files local; one already there is kept as it is.
    @Test
    fun stignoreKeepsTempFilesLocal_SPEC_R16() {
        folder().replace(path, old)
        val ignore = File(tree.dir, ".stignore")
        assertTrue(ignore.readText().lines().contains(".tmp-*"))

        val other = StubTree(context)
        try {
            val own = "// the person's own rules\n*.bak\n"
            File(other.dir, ".stignore").writeText(own)
            SafSyncFolder(context, other.uri, "A", File(journals, "A"), SafSyncFolder.Io()).replace(path, old)
            assertEquals(own, File(other.dir, ".stignore").readText())
        } finally {
            other.clean()
        }
    }

    @Test
    fun listSkipsTempFiles_SPEC_R16() {
        val folder = folder()
        folder.replace(path, old)
        runCatching { folder(io = CrashWriting("log-000000.jsonl")).replace(path, new) }

        assertEquals("the crash left its temporary copy", listOf(".tmp-log-000000.jsonl", "log-000000.jsonl"), onDisk("devices/A"))
        assertEquals(listOf("log-000000.jsonl"), folder.list("devices/A"))
    }
}
