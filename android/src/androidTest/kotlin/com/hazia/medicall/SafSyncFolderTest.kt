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

    @After fun clean() = tree.clean()

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
        val folder = SafSyncFolder(context, tree.uri)

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
        SafSyncFolder(context, tree.uri).replace(path, old)

        // A crash while the temporary copy is written: the target is untouched.
        runCatching { SafSyncFolder(context, tree.uri, CrashWriting(".tmp-log-000000.jsonl")).replace(path, new) }
            .also { assertTrue(it.exceptionOrNull() is Crash) }
        assertArrayEquals("the old bytes", old, SafSyncFolder(context, tree.uri).read(path))
        assertEquals("the torn temporary copy is dropped", listOf("log-000000.jsonl"), onDisk("devices/A"))

        // A crash after the temporary copy was written and checked, halfway through the target.
        runCatching { SafSyncFolder(context, tree.uri, CrashWriting("log-000000.jsonl")).replace(path, new) }
            .also { assertTrue(it.exceptionOrNull() is Crash) }
        assertEquals("the target is torn on disk", new.size / 2, File(tree.dir, path).readBytes().size)
        val folder = SafSyncFolder(context, tree.uri)
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
        val folder = SafSyncFolder(context, tree.uri)
        assertTrue(folder.reachable())

        StubDocumentsProvider.revoked = true
        assertFalse("a revoked grant", folder.reachable())
        StubDocumentsProvider.revoked = false

        tree.dir.deleteRecursively()
        assertFalse("a tree that is gone", folder.reachable())
    }

    @Test
    fun listSkipsTempFiles_SPEC_R16() {
        val folder = SafSyncFolder(context, tree.uri)
        folder.replace(path, old)
        runCatching { SafSyncFolder(context, tree.uri, CrashWriting("log-000000.jsonl")).replace(path, new) }

        assertEquals("the crash left its temporary copy", listOf(".tmp-log-000000.jsonl", "log-000000.jsonl"), onDisk("devices/A"))
        assertEquals(listOf("log-000000.jsonl"), folder.list("devices/A"))
    }
}
