package com.hazia.medicall.data.backup

import com.hazia.medicall.data.NAME
import com.hazia.medicall.data.sync.World
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Restoring a backup merges it, so newer data is never lost (SPEC:R22). */
class BackupRestoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val worlds = mutableListOf<World>()
    @After fun close() = worlds.forEach { it.close() }

    private val password = "invented-Passphrase-7".toCharArray()
    private val lab1 = "Lab 1 result, invented for a test: 42 units".encodeToByteArray()
    private val note = "An invented note".encodeToByteArray()

    private fun world(): World = World(tmp.newFolder(), 1).also {
        worlds += it
        it.syncthing.now = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli()
    }

    private fun backup(d: World.Device): ByteArray {
        val target = MemoryBackupTarget()
        val result = runBlocking { BackupWriter(d.db, d.blobs, SevenZip, target, zone = ZoneOffset.UTC, workDir = tmp.newFolder()).write(BackupKind.LATEST, password) }
        check(result is BackupResult.Written) { "$result" }
        return target.read(BackupWriter.LATEST_NAME)!!
    }

    private fun restorer(d: World.Device) = BackupRestore(d.writes, d.blobs, SevenZip, workDir = tmp.newFolder())

    @Test
    fun freshDeviceRestoresEverything_SPEC_R22() {
        val w = world()
        val a = w.Device("A")
        val profileA = a.create("Profile A")
        val profileB = a.create("Profile B")
        val lab = runBlocking { a.blobs.add(profileA, "Lab 1.pdf", "application/pdf", lab1) }
        runBlocking { a.blobs.add(profileB, "Note.txt", "text/plain", note) }
        val archive = backup(a)

        val b = w.Device("B")
        val report = runBlocking { restorer(b).restore(archive, password) }

        assertEquals(RestoreReport(rows = 4, files = 2, skipped = 0), report)
        assertEquals(a.rows(), b.rows())
        assertEquals(a.blobRefs(), b.blobRefs())
        assertEquals(a.blobStore.files.keys, b.blobStore.files.keys)
        assertContentEquals(lab1, runBlocking { b.blobs.read(lab) })
    }

    @Test
    fun oldBackupNeverOverwritesNewer() {
        val w = world()
        val a = w.Device("A")
        val profile = a.create("Profile A")
        val archive = backup(a)
        w.syncthing.now += 60_000
        a.edit(profile, NAME, "Profile A, renamed")

        val report = assertNotNull(runBlocking { restorer(a).restore(archive, password) })

        assertEquals(1, report.rows)
        assertEquals("Profile A, renamed", a.rows().getValue(profile).groups.getValue(NAME).values["name"])
    }

    @Test
    fun wrongPasswordRestoresNothing() {
        val w = world()
        val a = w.Device("A")
        val profile = a.create("Profile A")
        runBlocking { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1) }
        val archive = backup(a)

        val b = w.Device("B")
        assertNull(runBlocking { restorer(b).restore(archive, "wrong-Passphrase-7".toCharArray()) })
        assertNull(runBlocking { restorer(b).restore("not an archive".encodeToByteArray(), password) })

        assertEquals(emptyMap(), b.rows())
        assertEquals(emptyMap(), b.blobRefs())
        assertTrue(b.blobStore.files.isEmpty())
    }

    @Test
    fun reAddingByHashMakesNoSecondReference() {
        val w = world()
        val a = w.Device("A")
        val profile = a.create("Profile A")
        runBlocking { a.blobs.add(profile, "Lab 1.pdf", "application/pdf", lab1) }
        val refs = a.blobRefs()
        val archive = backup(a)
        a.blobStore.files.clear()

        val hash = a.blobs.storeBytes(lab1)
        assertEquals(refs.values.single().groups.getValue("main").values["sha256"], hash)
        assertEquals(refs, a.blobRefs())

        a.blobStore.files.clear()
        assertEquals(RestoreReport(rows = 2, files = 1, skipped = 0), runBlocking { restorer(a).restore(archive, password) })
        assertEquals(refs, a.blobRefs())
        assertEquals(setOf(hash), a.blobStore.files.keys)
    }
}
