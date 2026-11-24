package com.hazia.medicall.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A profile's switches on this device alone (SPEC:R110): whether its reminders fire here and whether it is
 * shown here. Local, like `blob_seen`: not a synced table, so no trigger queues it for export and no backup
 * holds it. Null means the default: reminders on only on the device that created the profile, shown on.
 * A purged profile takes its row with it.
 */
@Entity(
    tableName = "profile_device",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class ProfileOnDeviceEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: String,
    val reminders: Boolean? = null,
    val shown: Boolean? = null,
)

@Dao
internal interface ProfileOnDeviceDao {
    @Query("SELECT * FROM profile_device WHERE profile_id = :profileId")
    suspend fun get(profileId: String): ProfileOnDeviceEntity?

    @Query("SELECT * FROM profile_device ORDER BY profile_id")
    suspend fun all(): List<ProfileOnDeviceEntity>

    @Upsert
    suspend fun put(row: ProfileOnDeviceEntity)
}
