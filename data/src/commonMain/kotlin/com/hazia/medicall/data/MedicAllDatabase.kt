package com.hazia.medicall.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.hazia.medicall.data.blob.BlobDao
import com.hazia.medicall.data.blob.BlobHeldEntity
import com.hazia.medicall.data.blob.BlobSeenEntity
import com.hazia.medicall.data.sync.AskEntity
import com.hazia.medicall.data.sync.BaseEntity
import com.hazia.medicall.data.sync.ClockEntity
import com.hazia.medicall.data.sync.KnownTablesEntity
import com.hazia.medicall.data.sync.LostEntity
import com.hazia.medicall.data.sync.OutboxEntity
import com.hazia.medicall.data.sync.PurgeEntity
import com.hazia.medicall.data.sync.ReadEntity
import com.hazia.medicall.data.sync.RowTable
import com.hazia.medicall.data.sync.SyncDao
import com.hazia.medicall.data.sync.WaitingEntity

/**
 * MedicAll's one database: Factotum's sync tables (SPEC:R16), then the records, each with a stamp
 * per field group and a foreign key to its profile (SPEC:R15), then the local, never synced
 * `blob_seen` that [com.hazia.medicall.data.blob.BlobSync] counts its 30 days in, and `blob_held`,
 * the files this device has held, which the integrity check of SPEC:R23 tells damage by, and
 * `profile_device`, each profile's switches on this device (SPEC:R110).
 */
@Database(
    entities = [
        PurgeEntity::class, ClockEntity::class,
        BaseEntity::class, AskEntity::class, ReadEntity::class, OutboxEntity::class, KnownTablesEntity::class,
        WaitingEntity::class, LostEntity::class,
        ProfileEntity::class, BlobRefEntity::class,
        BlobSeenEntity::class, BlobHeldEntity::class, ProfileOnDeviceEntity::class,
    ],
    version = SCHEMA_VERSION,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = BirthGroupSplit::class)],
    exportSchema = true,
)
@ConstructedBy(MedicAllDatabaseConstructor::class)
abstract class MedicAllDatabase : RoomDatabase() {
    internal abstract fun syncDao(): SyncDao
    internal abstract fun profileDao(): ProfileDao
    internal abstract fun blobRefDao(): BlobRefDao
    internal abstract fun blobDao(): BlobDao
    internal abstract fun profileDeviceDao(): ProfileOnDeviceDao
}

/**
 * The synced tables, by the name their folder records carry, parents before children;
 * [SchemaTriggers] queues writes to each for export.
 */
internal val SYNCED_TABLES = listOf(PROFILE, BLOB_REF)

/** In [SYNCED_TABLES]' order, parents before children, which is the order a snapshot is written in. */
internal fun MedicAllDatabase.syncedTables(): Map<String, RowTable> =
    mapOf(PROFILE to profileTable(profileDao()), BLOB_REF to blobRefTable(blobRefDao()))
        .also { check(it.keys.toList() == SYNCED_TABLES) { "every synced table needs its export triggers" } }

/**
 * SPEC:R16: in every group the later stamp wins. No group asks a person when both sides changed
 * it (Factotum's item schedule did), and none keeps the losing version (Factotum's page text did).
 */
internal val ASK_GROUPS = emptySet<String>()
internal val KEEP_LOSER_GROUPS = emptySet<String>()

// Room's KSP processor generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object MedicAllDatabaseConstructor : RoomDatabaseConstructor<MedicAllDatabase>

const val DATABASE_NAME = "medicall.db"

/**
 * The schema's version; a file below it is upgraded by Room's migrations on open. Version 2 adds the profile's
 * modules and origin (SPEC:R14); version 3 gives the birth date its own group's stamp (SPEC:R16).
 */
internal const val SCHEMA_VERSION = 3

/**
 * Schema 2 to 3: the birth date starts with the stamp the details group gave it, so a peer's later change of
 * kind or birth date still wins, and no earlier one does.
 */
internal class BirthGroupSplit : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE profile SET birth_hlc = details_hlc, birth_device = details_device")
    }
}
