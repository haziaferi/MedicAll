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

/** The profile table's name: what [com.hazia.medicall.data.bin.BinEntry.table] holds for a profile. */
const val PROFILE = "profile"

/*
 * A profile's field groups (SPEC:R15, SPEC:R16), one per thing changed apart, so a rename on one
 * device and a change of kind on another both survive the merge. Its removal is a group of its own.
 */
internal const val NAME = "name"

/** The kind. Lines written before schema 3 carry the birth date here too; [BIRTH] outranks it when later. */
internal const val DETAILS = "details"

/** The birth date, apart from the kind, so a birth date set on one device and a kind changed on another both hold. */
internal const val BIRTH = "birth"
internal const val REMOVED = "removed"

/** The modules switched on or off, one Boolean per [Module.key] (SPEC:R14). */
internal const val MODULES = "modules"

/** The device that created the profile, written once (SPEC:R110: reminders are on by default only there). */
internal const val ORIGIN = "origin"

/**
 * The parts of MedicAll a profile can switch on or off (SPEC:R14). Attachments and the emergency card are
 * always on, so they are not here; [key] is the field name in the profile's [MODULES] group.
 */
enum class Module(val key: String) {
    MEDICINES("medicines"),
    LOG("log"),
    RESULTS("results"),
    CALENDAR("calendar"),
}

/**
 * The stamp of a group a row came without: a profile written before the group existed (phase 1, schema 1).
 * Any real write is later, so it wins the merge; until then the group's defaults hold.
 */
internal val NEVER_WRITTEN = Stamp(0, "")

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
    @ColumnInfo(name = "modules_medicines", defaultValue = "1") val modulesMedicines: Boolean = true,
    @ColumnInfo(name = "modules_log", defaultValue = "1") val modulesLog: Boolean = true,
    @ColumnInfo(name = "modules_results", defaultValue = "1") val modulesResults: Boolean = true,
    @ColumnInfo(name = "modules_calendar", defaultValue = "1") val modulesCalendar: Boolean = true,
    @ColumnInfo(name = "modules_hlc", defaultValue = "0") val modulesHlc: Long = NEVER_WRITTEN.hlc,
    @ColumnInfo(name = "modules_device", defaultValue = "") val modulesDevice: String = NEVER_WRITTEN.device,
    /** The device that created the profile; null for a profile from before it was recorded. */
    @ColumnInfo(name = "created_device") val createdDevice: String? = null,
    @ColumnInfo(name = "origin_hlc", defaultValue = "0") val originHlc: Long = NEVER_WRITTEN.hlc,
    @ColumnInfo(name = "origin_device", defaultValue = "") val originDevice: String = NEVER_WRITTEN.device,
    /** [birthDate]'s own stamp ([BIRTH]); schema 3 starts it at the details stamp. */
    @ColumnInfo(name = "birth_hlc", defaultValue = "0") val birthHlc: Long = NEVER_WRITTEN.hlc,
    @ColumnInfo(name = "birth_device", defaultValue = "") val birthDevice: String = NEVER_WRITTEN.device,
) {
    /** The modules switched on, in [Module]'s order. */
    val modules: Set<Module>
        get() = buildSet {
            if (modulesMedicines) add(Module.MEDICINES)
            if (modulesLog) add(Module.LOG)
            if (modulesResults) add(Module.RESULTS)
            if (modulesCalendar) add(Module.CALENDAR)
        }
}

@Dao
internal interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id IN (:ids)")
    suspend fun profiles(ids: List<String>): List<ProfileEntity>

    @Query("SELECT * FROM profile ORDER BY id")
    suspend fun allProfiles(): List<ProfileEntity>

    /** What is in the bin (SPEC:R18). */
    @Query("SELECT * FROM profile WHERE removed_at IS NOT NULL ORDER BY removed_at, id")
    suspend fun removedProfiles(): List<ProfileEntity>

    @Upsert
    suspend fun putProfiles(rows: List<ProfileEntity>)

    @Query("DELETE FROM profile WHERE id IN (:ids)")
    suspend fun deleteProfiles(ids: List<String>)
}

internal fun profileTable(dao: ProfileDao) =
    EntityTable(dao::profiles, dao::allProfiles, dao::putProfiles, dao::deleteProfiles, ProfileEntity::toRow, Row::toProfileEntity, Row::withBirthGroup) { emptyList() }

/**
 * A profile row written before schema 3 has no [BIRTH] group: its birth date sits in [DETAILS]. It gets a [BIRTH]
 * group with that value and the details stamp, so the merge weighs it against the other birth dates, not against
 * the kind. A row that has [BIRTH] is left as it is.
 */
internal fun Row.withBirthGroup(): Row {
    if (BIRTH in groups) return this
    val details = groups[DETAILS] ?: return this
    if ("birth_date" !in details.values) return this
    return copy(groups = groups + (BIRTH to Group(details.stamp, mapOf("birth_date" to details.values["birth_date"]))))
}

/**
 * [DETAILS] also carries a copy of the birth date, unread by this version ([BIRTH] holds it), so a build from
 * before schema 3, which reads the birth date from [DETAILS] alone, never takes it for cleared.
 */
internal fun ProfileEntity.toRow() = Row(PROFILE, id, mapOf(
    NAME to Group(Stamp(nameHlc, nameDevice), mapOf("name" to name)),
    DETAILS to Group(Stamp(detailsHlc, detailsDevice), mapOf("kind" to kind, "birth_date" to birthDate)),
    BIRTH to Group(Stamp(birthHlc, birthDevice), mapOf("birth_date" to birthDate)),
    REMOVED to Group(Stamp(removedHlc, removedDevice), mapOf("removed_at" to removedAt)),
    MODULES to Group(Stamp(modulesHlc, modulesDevice), mapOf(
        Module.MEDICINES.key to modulesMedicines,
        Module.LOG.key to modulesLog,
        Module.RESULTS.key to modulesResults,
        Module.CALENDAR.key to modulesCalendar,
    )),
    ORIGIN to Group(Stamp(originHlc, originDevice), mapOf("created_device" to createdDevice)),
))

/** A new profile's [MODULES] group: every module on (SPEC:R14, provisional default). */
internal fun allModulesOn(): Map<String, Any?> = Module.entries.associate { it.key to true }

/**
 * Throws when [this] is not a profile this version can read. A row without [MODULES] or [ORIGIN] (written
 * before they existed) reads with every module on and no origin, stamped [NEVER_WRITTEN]. The birth date is
 * [BIRTH]'s; a row without it is read as [withBirthGroup] makes it.
 */
internal fun Row.toProfileEntity(): ProfileEntity {
    val name = groups.getValue(NAME)
    val details = groups.getValue(DETAILS)
    val birth = withBirthGroup().groups[BIRTH] ?: Group(NEVER_WRITTEN, mapOf("birth_date" to null))
    val removed = groups.getValue(REMOVED)
    val modules = groups[MODULES] ?: Group(NEVER_WRITTEN, allModulesOn())
    val origin = groups[ORIGIN] ?: Group(NEVER_WRITTEN, mapOf("created_device" to null))
    fun module(m: Module) = modules.values[m.key] as Boolean? ?: true
    return ProfileEntity(
        id = id,
        name = name.values["name"] as String,
        nameHlc = name.stamp.hlc,
        nameDevice = name.stamp.device,
        birthDate = birth.values["birth_date"] as String?,
        kind = details.values["kind"] as String,
        detailsHlc = details.stamp.hlc,
        detailsDevice = details.stamp.device,
        removedAt = removed.values["removed_at"] as Long?,
        removedHlc = removed.stamp.hlc,
        removedDevice = removed.stamp.device,
        modulesMedicines = module(Module.MEDICINES),
        modulesLog = module(Module.LOG),
        modulesResults = module(Module.RESULTS),
        modulesCalendar = module(Module.CALENDAR),
        modulesHlc = modules.stamp.hlc,
        modulesDevice = modules.stamp.device,
        createdDevice = origin.values["created_device"] as String?,
        originHlc = origin.stamp.hlc,
        originDevice = origin.stamp.device,
        birthHlc = birth.stamp.hlc,
        birthDevice = birth.stamp.device,
    )
}
