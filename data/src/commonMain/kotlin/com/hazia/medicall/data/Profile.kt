package com.hazia.medicall.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.core.sync.Stamp
import com.hazia.medicall.data.sync.EntityTable

internal const val PROFILE = "profile"

/*
 * A profile's field groups (SPEC:R15, SPEC:R16), one per thing changed apart, so a rename on one
 * device and a change of kind on another both survive the merge. Its removal is a group of its own.
 */
internal const val NAME = "name"
internal const val DETAILS = "details"
internal const val REMOVED = "removed"

/** One person whose records MedicAll keeps (SPEC:R15): every other record belongs to one. */
@Entity(tableName = PROFILE)
internal data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "name_hlc") val nameHlc: Long,
    @ColumnInfo(name = "name_device") val nameDevice: String,
    /** An ISO date, `2026-11-22`, or null when not given. */
    @ColumnInfo(name = "birth_date") val birthDate: String?,
    val kind: String,
    @ColumnInfo(name = "details_hlc") val detailsHlc: Long,
    @ColumnInfo(name = "details_device") val detailsDevice: String,
    /** When the profile went to the bin, in epoch milliseconds; null while it is live. */
    @ColumnInfo(name = "removed_at") val removedAt: Long?,
    @ColumnInfo(name = "removed_hlc") val removedHlc: Long,
    @ColumnInfo(name = "removed_device") val removedDevice: String,
)

@Dao
internal interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id IN (:ids)")
    suspend fun profiles(ids: List<String>): List<ProfileEntity>

    @Query("SELECT * FROM profile ORDER BY id")
    suspend fun allProfiles(): List<ProfileEntity>

    @Upsert
    suspend fun putProfiles(rows: List<ProfileEntity>)

    @Query("DELETE FROM profile WHERE id IN (:ids)")
    suspend fun deleteProfiles(ids: List<String>)
}

internal fun profileTable(dao: ProfileDao) =
    EntityTable(dao::profiles, dao::allProfiles, dao::putProfiles, dao::deleteProfiles, ProfileEntity::toRow, Row::toProfileEntity) { emptyList() }

internal fun ProfileEntity.toRow() = Row(PROFILE, id, mapOf(
    NAME to Group(Stamp(nameHlc, nameDevice), mapOf("name" to name)),
    DETAILS to Group(Stamp(detailsHlc, detailsDevice), mapOf("birth_date" to birthDate, "kind" to kind)),
    REMOVED to Group(Stamp(removedHlc, removedDevice), mapOf("removed_at" to removedAt)),
))

/** Throws when [this] is not a profile this version can read. */
internal fun Row.toProfileEntity(): ProfileEntity {
    val name = groups.getValue(NAME)
    val details = groups.getValue(DETAILS)
    val removed = groups.getValue(REMOVED)
    return ProfileEntity(
        id = id,
        name = name.values["name"] as String,
        nameHlc = name.stamp.hlc,
        nameDevice = name.stamp.device,
        birthDate = details.values["birth_date"] as String?,
        kind = details.values["kind"] as String,
        detailsHlc = details.stamp.hlc,
        detailsDevice = details.stamp.device,
        removedAt = removed.values["removed_at"] as Long?,
        removedHlc = removed.stamp.hlc,
        removedDevice = removed.stamp.device,
    )
}
