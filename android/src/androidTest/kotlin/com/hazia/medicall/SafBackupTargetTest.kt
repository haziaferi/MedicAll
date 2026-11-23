package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hazia.medicall.data.backup.BackupTarget
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** The backup folder through the Storage Access Framework (SPEC:R22), with the contract of [BackupTarget]. */
@RunWith(AndroidJUnit4::class)
class SafBackupTargetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tree = StubTree(context)
    private val name = "medicall-latest.7z"
    private val prev = BackupTarget.prev(name)
    private val sources = File(context.cacheDir, "saf-backup-test-${UUID.randomUUID()}").also { it.mkdirs() }

    @After fun clean() {
        tree.clean()
        sources.deleteRecursively()
    }

    /** Invented bytes, large enough to take several buffers. */
    private fun source(seed: Int): File {
        val bytes = ByteArray(300_000) { ((it * 31 + seed * 7) % 251).toByte() }
        return File(sources, "v$seed.bin").also { it.writeBytes(bytes) }
    }

    private fun bytes(target: BackupTarget, file: String) = target.openRead(file)!!.use { it.readBytes() }

    private fun onDisk(): List<String> = tree.dir.list()?.sorted().orEmpty()

    /** Writes half of [name]'s bytes and stops, as a crash would. */
    private class CrashWriting(private val name: String) : SafBackupTarget.Io() {
        override fun copy(name: String, from: InputStream, to: OutputStream) {
            if (name != this.name) return super.copy(name, from, to)
            val all = from.readBytes()
            to.write(all, 0, all.size / 2)
            to.flush()
            throw Crash()
        }
    }

    /** Stops right after the old copy was moved aside, before the new one is started. */
    private class CrashAfterAside(private val name: String) : SafBackupTarget.Io() {
        override fun moved(from: String, to: String) {
            if (from == name) throw Crash()
        }
    }

    /** Flips one byte of every copy it lands, as a failing card might. */
    private class Corrupting : SafBackupTarget.Io() {
        override fun copy(name: String, from: InputStream, to: OutputStream) {
            val all = from.readBytes()
            all[all.size / 2] = (all[all.size / 2].toInt() xor 0x01).toByte()
            to.write(all)
        }
    }

    private fun crashes(block: () -> Unit) {
        try {
            block()
            fail("expected a crash")
        } catch (_: Crash) {
        }
    }

    @Test
    fun replaceKeepsThePreviousOnlyWhenAsked_SPEC_R22() {
        val target = SafBackupTarget(context, tree.uri)
        val v1 = source(1)
        val v2 = source(2)
        val v3 = source(3)

        target.write(name, v1)
        assertEquals(listOf(name), target.list())
        target.write(name, v2, keepPrevious = true)
        assertEquals(listOf(name, prev), target.list().sorted())
        assertArrayEquals(v2.readBytes(), bytes(target, name))
        assertArrayEquals(v1.readBytes(), bytes(target, prev))

        // A kept .prev is never overwritten: v2 waits aside, then goes; without keepPrevious the .prev goes too.
        target.write(name, v3)
        assertEquals(listOf(name), target.list())
        assertEquals("no temporary document is left", listOf(name), onDisk())
        assertArrayEquals(v3.readBytes(), bytes(target, name))
        assertNull(target.openRead(prev))
    }

    @Test
    fun badCopyLeavesThePreviousAsItWas_SPEC_R22() {
        SafBackupTarget(context, tree.uri).write(name, source(1))

        try {
            SafBackupTarget(context, tree.uri, Corrupting()).write(name, source(2))
            fail("a copy that does not read back whole must throw")
        } catch (_: IOException) {
        }

        val target = SafBackupTarget(context, tree.uri)
        assertEquals(listOf(name), onDisk())
        assertArrayEquals(source(1).readBytes(), bytes(target, name))
    }

    @Test
    fun interruptedReplaceLeavesTheOldCopyOrTheNew_SPEC_R22() {
        val v1 = source(1)
        SafBackupTarget(context, tree.uri).write(name, v1)

        // Between moving the old copy aside and starting the new one: the old copy is the .prev.
        crashes { SafBackupTarget(context, tree.uri, CrashAfterAside(name)).write(name, source(2)) }
        val target = SafBackupTarget(context, tree.uri)
        assertEquals(listOf(prev), target.list())
        assertArrayEquals(v1.readBytes(), bytes(target, prev))

        // The next write lands the new one and drops the old.
        target.write(name, source(3))
        assertEquals(listOf(name), onDisk())
        assertArrayEquals(source(3).readBytes(), bytes(target, name))

        // Halfway through rewriting the target: the old copy is still whole under .prev.
        crashes { SafBackupTarget(context, tree.uri, CrashWriting(name)).write(name, source(4)) }
        assertArrayEquals(source(3).readBytes(), bytes(target, prev))
        target.write(name, source(5))
        assertEquals(listOf(name), onDisk())
        assertArrayEquals(source(5).readBytes(), bytes(target, name))
    }

    @Test
    fun keptPreviousSurvivesTwoIncompleteWrites_SPEC_R22() {
        val target = SafBackupTarget(context, tree.uri)
        val complete = source(1)
        target.write(name, complete)
        target.write(name, source(2), keepPrevious = true) // incomplete: the caller keeps the .prev
        assertArrayEquals(complete.readBytes(), bytes(target, prev))

        // Two writes interrupted in a row, at each of the two places a crash can leave work behind.
        crashes { SafBackupTarget(context, tree.uri, CrashAfterAside(name)).write(name, source(3), keepPrevious = true) }
        assertArrayEquals(complete.readBytes(), bytes(target, prev))
        crashes { SafBackupTarget(context, tree.uri, CrashWriting(name)).write(name, source(4), keepPrevious = true) }
        assertArrayEquals(complete.readBytes(), bytes(target, prev))
        crashes { SafBackupTarget(context, tree.uri, CrashWriting(name)).write(name, source(5), keepPrevious = true) }
        assertArrayEquals(complete.readBytes(), bytes(target, prev))

        // An incomplete write that lands still keeps it; a complete one drops it and every leftover.
        target.write(name, source(6), keepPrevious = true)
        assertArrayEquals(complete.readBytes(), bytes(target, prev))
        assertEquals(listOf(name, prev), onDisk())
        target.write(name, source(7))
        assertEquals(listOf(name), onDisk())
        assertArrayEquals(source(7).readBytes(), bytes(target, name))
    }

    @Test
    fun revokedGrantIsUnreachable_SPEC_R22() {
        val target = SafBackupTarget(context, tree.uri)
        assertTrue(target.reachable())
        target.write(name, source(1))

        StubDocumentsProvider.revoked = true
        assertFalse("a revoked grant", target.reachable())
        try {
            target.write(name, source(2))
            fail("writing through a revoked grant must throw")
        } catch (_: SecurityException) {
        }
        StubDocumentsProvider.revoked = false
        assertArrayEquals(source(1).readBytes(), bytes(target, name))

        tree.dir.deleteRecursively()
        assertFalse("a tree that is gone", target.reachable())
    }
}
