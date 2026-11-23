package com.hazia.medicall.data

import com.hazia.medicall.core.sync.Stamp
import com.hazia.medicall.data.sync.PurgeEntity
import com.hazia.medicall.data.sync.loadClock
import com.hazia.medicall.data.sync.saveClock
import com.hazia.medicall.data.sync.stamp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SyncStorageTest {

    /** A device id is a UUID, as every MedicAll id is (SPEC:R15). */
    private fun isUuid(id: String) = Uuid.parseOrNull(id) != null

    @get:Rule val tmp = TemporaryFolder()

    private fun <T> withDatabase(file: File, block: suspend (MedicAllDatabase) -> T): T {
        val db = openMedicAllDatabase(file).database
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    @Test
    fun aPurgeOutlivesAReopen() {
        val file = File(tmp.root, DATABASE_NAME)
        withDatabase(file) { it.syncDao().putPurge(PurgeEntity("3f2b8c1e-5d4a-4c6b-9e7f-0a1b2c3d4e5f", 42, "A")) }

        val purges = withDatabase(file) { it.syncDao().purges() }

        assertEquals(listOf(Stamp(42, "A")), purges.map { it.stamp() })
    }

    @Test
    fun theClockResumesAheadOfAWallClockSetBackwards() {
        val file = File(tmp.root, DATABASE_NAME)
        withDatabase(file) { db ->
            val clock = db.syncDao().loadClock("A") { 1_000_000 }
            clock.tick()
            db.syncDao().saveClock(clock)
        }

        val next = withDatabase(file) { db -> db.syncDao().loadClock("A") { 5 }.tick() }

        assertEquals(1_000_001, next.hlc)
    }

    @Test
    fun theDeviceIdIsMadeOnceAndKept() {
        val file = File(tmp.root, "device.id")

        val first = deviceId(file)

        assertTrue(isUuid(first))
        assertEquals(first, deviceId(file))
    }

    @Test
    fun anUnreadableDeviceIdIsSetAsideAndReplaced() {
        val file = File(tmp.root, "device.id").apply { writeText("garbled") }

        val id = deviceId(file, now = { 9 })

        assertTrue(isUuid(id))
        assertNotEquals("garbled", id)
        assertEquals("garbled", File(file.path + UNOPENABLE_SUFFIX + 9).readText())
    }
}
