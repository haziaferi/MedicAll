package com.hazia.medicall.data.sync

import com.hazia.medicall.core.crypto.LineCipher
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.HybridClock
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.DETAILS
import com.hazia.medicall.data.LocalWrites
import com.hazia.medicall.data.MAIN
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.NAME
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.REMOVED
import com.hazia.medicall.data.blob.BlobSync
import com.hazia.medicall.data.blob.Blobs
import com.hazia.medicall.data.blob.MemoryBlobStore
import com.hazia.medicall.data.openMedicAllDatabase
import com.hazia.medicall.data.syncedTables
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals

/** The groups the ordinary-use workload edits: neither asks a person (none does in MedicAll, SPEC:R16). */
val GROUPS = listOf(NAME, DETAILS)

/** One invented key every device of a [World] shares, as the passphrase of SPEC:R109 would give them. */
internal val TEST_KEY = ByteArray(32) { (it * 7 + 3).toByte() }

internal fun testCipher() = LineCipher(mapOf("k1" to TEST_KEY), "k1")

/** A second invented key, as a passphrase change on one device would add before its peers have it. */
internal val TEST_KEY_2 = ByteArray(32) { (it * 11 + 5).toByte() }

/** A cipher holding [TEST_KEY] as k1 and [TEST_KEY_2] as k2, sealing under [current]. */
internal fun twoKeyCipher(current: String) = LineCipher(mapOf("k1" to TEST_KEY, "k2" to TEST_KEY_2), current)

/** A table held in memory, for rows no real table has. Its saves fail once [savesLeft] runs out. */
internal class MemoryTable : RowTable {
    val rows = mutableMapOf<String, Row>()
    var savesLeft = Int.MAX_VALUE
    override suspend fun load(ids: Collection<String>) = ids.mapNotNull { rows[it] }
    override suspend fun all() = rows.values.toList()
    override suspend fun save(rows: Collection<Row>) {
        check(savesLeft-- > 0) { "disk full" }
        rows.forEach { this.rows[it.id] = it }
    }
    override suspend fun delete(ids: Collection<String>) = ids.forEach { rows.remove(it) }
    override fun fits(row: Row) = true
    override fun parents(row: Row) = emptyList<Pair<String, String>>()
}

/**
 * Devices sharing one folder through [syncthing], writing profiles through [LocalWrites], each with
 * a [LineCipher] made from [TEST_KEY]. [truth] holds the newest version of every group any device
 * wrote: what every device must hold once sync settles (ADR 01's join).
 */
internal class World(private val dir: File, seed: Int, private val segmentBytes: Int = 16 * 1024, private val snapshotEvery: Int = 64) {
    val syncthing = SyncthingDouble(Random(seed))
    val truth = mutableMapOf<Pair<String, String>, Group>()
    private val opened = mutableListOf<MedicAllDatabase>()
    private val cipher = testCipher()

    /** A folder line as a device writes it: [plain] sealed, then a newline. */
    fun sealed(plain: String) = cipher.seal(plain.encodeToByteArray()) + "\n"

    /** The plain records of the file at [path] in [device]'s copy of the folder. */
    fun plainLines(device: String, path: String): List<String> =
        syncthing.folder(device).read(path)!!.decodeToString().lines().filter { it.isNotEmpty() }.map { requireNotNull(cipher.open(it)).decodeToString() }

    /**
     * [id] is the sync identity; two devices given the same one model a cloned device id.
     * [extra] adds tables, or replaces a real one, for rows written straight into the folder.
     * [cipher] is the keys this device holds; by default only [TEST_KEY].
     */
    inner class Device(
        val name: String,
        val id: String = name,
        extra: Map<String, RowTable> = emptyMap(),
        recovered: Boolean = false,
        cipher: LineCipher = testCipher(),
    ) {
        /** How far this device's wall clock is from the shared one, in milliseconds. */
        var skew = 0L
        private val wall = { syncthing.now + skew }
        val clock = HybridClock(id, wall)
        val db = openMedicAllDatabase(File(dir, "$name.db")).database.also { opened += it }
        private val tables = db.syncedTables() + extra
        private var made = 0
        val writes = LocalWrites(db, clock)
        val sync = FolderSync(
            db, syncthing.folder(name), id, clock, tables, cipher, wall, segmentBytes, snapshotEvery, recovered = recovered,
        )
        val blobStore = MemoryBlobStore()
        val blobs = Blobs(writes, db, blobStore, cipher)
        private val blobSync = BlobSync(db, syncthing.folder(name), blobStore, cipher, wall)

        /** A new profile, every group stamped with one tick. */
        fun create(name: String = "Profile ${this.name}@${syncthing.now}"): String {
            val rowId = "${this.name}-${made++}"
            runBlocking {
                writes.write(mapOf(PROFILE to listOf(rowId))) { store ->
                    val s = clock.tick()
                    writes.merger.created(store, Row(PROFILE, rowId, mapOf(
                        NAME to Group(s, mapOf("name" to name)),
                        DETAILS to Group(s, mapOf("birth_date" to null, "kind" to "ADULT")),
                        REMOVED to Group(s, mapOf("removed_at" to null)),
                    )))
                }
            }
            record(rowId)
            return rowId
        }

        /** A file reference on [profile]. */
        fun addBlobRef(profile: String): String {
            val rowId = "${this.name}-${made++}"
            runBlocking {
                writes.write(mapOf(BLOB_REF to listOf(rowId))) { store ->
                    val s = clock.tick()
                    writes.merger.created(store, Row(BLOB_REF, rowId, mapOf(
                        MAIN to Group(s, mapOf("profile_id" to profile, "sha256" to "ab".repeat(32), "size" to 3L, "name" to "Lab 1.pdf", "mime" to "application/pdf")),
                        REMOVED to Group(s, mapOf("removed_at" to null)),
                    )))
                }
            }
            return rowId
        }

        fun edit(rowId: String, group: String, value: String = "$name@${syncthing.now}") {
            runBlocking {
                when (group) {
                    NAME -> writes.edit(PROFILE, rowId, NAME) { mapOf("name" to value) }
                    DETAILS -> writes.edit(PROFILE, rowId, DETAILS) { mapOf("kind" to value) }
                    else -> error("the workload does not edit $group")
                }
            }
            record(rowId)
        }

        /** Delete forever; a peer applies it through its import. */
        fun purge(rowId: String) = runBlocking { writes.write(mapOf(PROFILE to listOf(rowId))) { writes.merger.purge(it, rowId) } }

        private fun record(rowId: String) {
            for ((g, v) in rows().getValue(rowId).groups) {
                truth.merge(rowId to g, v) { old, new -> if (new.stamp > old.stamp) new else old }
            }
        }

        fun rows(): Map<String, Row> = runBlocking { tables.getValue(PROFILE).all() }.associateBy { it.id }

        fun blobRefs(): Map<String, Row> = runBlocking { tables.getValue(BLOB_REF).all() }.associateBy { it.id }

        /** Reads the logs, then fetches the files they name ([BlobSync]). */
        fun import() = runBlocking { sync.import().also { blobSync.exchange() } }

        /** Publishes the files first, so a peer reading the logs finds them, then writes the logs. */
        fun export() = runBlocking { blobSync.exchange(); sync.export() }

        fun held(): Map<Pair<String, String>, Group> =
            rows().values.flatMap { r -> r.groups.map { (g, v) -> (r.id to g) to v } }.toMap()
    }

    /** Every device exports, every pair meets in full, and every device imports, until an import changes nothing. */
    fun settle(devices: List<Device>) {
        repeat(20) {
            devices.forEach { it.export() }
            for (a in devices) for (b in devices) if (a.name < b.name) syncthing.session(a.name, b.name)
            if (devices.sumOf { it.import().changed } == 0) return
        }
        error("sync did not settle")
    }

    fun assertJoined(devices: List<Device>) {
        for (d in devices) assertEquals(truth, d.held(), "device ${d.name} differs from the join")
    }

    fun close() = opened.forEach { it.close() }
}
