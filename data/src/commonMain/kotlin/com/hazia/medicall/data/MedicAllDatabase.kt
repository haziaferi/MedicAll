package com.hazia.medicall.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.hazia.medicall.data.blob.BlobDao
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
 * `blob_seen` that [com.hazia.medicall.data.blob.BlobSync] counts its 30 days in.
 */
@Database(
    entities = [
        PurgeEntity::class, ClockEntity::class,
        BaseEntity::class, AskEntity::class, ReadEntity::class, OutboxEntity::class, KnownTablesEntity::class,
        WaitingEntity::class, LostEntity::class,
        ProfileEntity::class, BlobRefEntity::class,
        BlobSeenEntity::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
@ConstructedBy(MedicAllDatabaseConstructor::class)
abstract class MedicAllDatabase : RoomDatabase() {
    internal abstract fun syncDao(): SyncDao
    internal abstract fun profileDao(): ProfileDao
    internal abstract fun blobRefDao(): BlobRefDao
    internal abstract fun blobDao(): BlobDao
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

/** The schema's version; a file below it is upgraded by Room's migrations on open. Version 1 has none. */
internal const val SCHEMA_VERSION = 1
