package com.hazia.medicall.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.core.sync.Stamp
import com.hazia.medicall.data.sync.EntityTable

/** The file reference table's name: what [com.hazia.medicall.data.bin.BinEntry.table] holds for a file reference. */
const val BLOB_REF = "blob_ref"

/** A file reference's group holding what it points at; its removal is [REMOVED], as a profile's. */
internal const val MAIN = "main"

/**
 * A file kept for a profile (SPEC:R15), named by the SHA-256 of its bytes. It belongs to one
 * profile, and the foreign key refuses one whose profile does not exist. The key is deferred, as
 * in Factotum, so an import may write a reference and its profile in either order inside one
 * transaction; a purged profile takes its references with it.
 */
@Entity(
    tableName = BLOB_REF,
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE, deferred = true)],
    indices = [Index("profile_id")],
)
internal data class BlobRefEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    /** Lower-case hex. */
    val sha256: String,
    /** In bytes. */
    val size: Long,
    val name: String,
    val mime: String,
    @ColumnInfo(name = "main_hlc") val mainHlc: Long,
    @ColumnInfo(name = "main_device") val mainDevice: String,
    @ColumnInfo(name = "removed_at") val removedAt: Long?,
    @ColumnInfo(name = "removed_hlc") val removedHlc: Long,
    @ColumnInfo(name = "removed_device") val removedDevice: String,
)

@Dao
internal interface BlobRefDao {
    @Query("SELECT * FROM blob_ref WHERE id IN (:ids)")
    suspend fun blobRefs(ids: List<String>): List<BlobRefEntity>

    @Query("SELECT * FROM blob_ref ORDER BY id")
    suspend fun allBlobRefs(): List<BlobRefEntity>

    @Query("SELECT * FROM blob_ref WHERE profile_id = :profile ORDER BY id")
    suspend fun blobRefsOf(profile: String): List<BlobRefEntity>

    /** What is in the bin (SPEC:R18). */
    @Query("SELECT * FROM blob_ref WHERE removed_at IS NOT NULL ORDER BY removed_at, id")
    suspend fun removedBlobRefs(): List<BlobRefEntity>

    @Upsert
    suspend fun putBlobRefs(rows: List<BlobRefEntity>)

    @Query("DELETE FROM blob_ref WHERE id IN (:ids)")
    suspend fun deleteBlobRefs(ids: List<String>)
}

internal fun blobRefTable(dao: BlobRefDao) =
    EntityTable(dao::blobRefs, dao::allBlobRefs, dao::putBlobRefs, dao::deleteBlobRefs, BlobRefEntity::toRow, Row::toBlobRefEntity) {
        listOf(PROFILE to it.profileId)
    }

internal fun BlobRefEntity.toRow() = Row(BLOB_REF, id, mapOf(
    MAIN to Group(Stamp(mainHlc, mainDevice), mapOf("profile_id" to profileId, "sha256" to sha256, "size" to size, "name" to name, "mime" to mime)),
    REMOVED to Group(Stamp(removedHlc, removedDevice), mapOf("removed_at" to removedAt)),
))

/** Throws when [this] is not a file reference this version can read. */
internal fun Row.toBlobRefEntity(): BlobRefEntity {
    val main = groups.getValue(MAIN)
    val removed = groups.getValue(REMOVED)
    return BlobRefEntity(
        id = id,
        profileId = main.values["profile_id"] as String,
        sha256 = main.values["sha256"] as String,
        size = main.values["size"] as Long,
        name = main.values["name"] as String,
        mime = main.values["mime"] as String,
        mainHlc = main.stamp.hlc,
        mainDevice = main.stamp.device,
        removedAt = removed.values["removed_at"] as Long?,
        removedHlc = removed.stamp.hlc,
        removedDevice = removed.stamp.device,
    )
}
