package com.hazia.medicall.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.hazia.medicall.data.sync.AskEntity
import com.hazia.medicall.data.sync.BaseEntity
import com.hazia.medicall.data.sync.ClockEntity
import com.hazia.medicall.data.sync.KnownTablesEntity
import com.hazia.medicall.data.sync.LostEntity
import com.hazia.medicall.data.sync.OutboxEntity
import com.hazia.medicall.data.sync.PurgeEntity
import com.hazia.medicall.data.sync.ReadEntity
import com.hazia.medicall.data.sync.SyncDao
import com.hazia.medicall.data.sync.WaitingEntity

/**
 * MedicAll's one database: Factotum's sync tables (SPEC:R16), then the records, each with a stamp
 * per field group and a foreign key to its profile (SPEC:R15).
 */
@Database(
    entities = [
        PurgeEntity::class, ClockEntity::class,
        BaseEntity::class, AskEntity::class, ReadEntity::class, OutboxEntity::class, KnownTablesEntity::class,
        WaitingEntity::class, LostEntity::class,
        ProfileEntity::class, BlobRefEntity::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
@ConstructedBy(MedicAllDatabaseConstructor::class)
abstract class MedicAllDatabase : RoomDatabase() {
    internal abstract fun syncDao(): SyncDao
    internal abstract fun profileDao(): ProfileDao
    internal abstract fun blobRefDao(): BlobRefDao
}

/**
 * The synced tables, by the name their folder records carry, parents before children;
 * [SchemaTriggers] queues writes to each for export.
 */
internal val SYNCED_TABLES = listOf(PROFILE, BLOB_REF)

// Room's KSP processor generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object MedicAllDatabaseConstructor : RoomDatabaseConstructor<MedicAllDatabase>

const val DATABASE_NAME = "medicall.db"

/** The schema's version; a file below it is upgraded by Room's migrations on open. Version 1 has none. */
internal const val SCHEMA_VERSION = 1
