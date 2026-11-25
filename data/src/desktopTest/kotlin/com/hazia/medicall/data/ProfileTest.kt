package com.hazia.medicall.data

import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.hazia.medicall.core.crypto.KeyFolder
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.Ids
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.core.sync.Stamp
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.sync.DirectorySyncFolder
import com.hazia.medicall.data.sync.FolderLayout
import com.hazia.medicall.data.sync.RecordCodec
import com.hazia.medicall.data.sync.RowRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Profiles' modules, birth date and origin (SPEC:R14), and their sync (SPEC:R16). Every value is invented. */
class ProfileTest {

    @get:Rule val tmp = TemporaryFolder()

    private val opened = mutableListOf<MedicAllDatabase>()
    @After fun close() = opened.forEach { it.close() }

    private val passphrase = "invented-profile-passphrase".toCharArray()
    private val today = LocalDate.of(2026, 11, 24)
    private val wall = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val syncRoot by lazy { tmp.newFolder("sync").toPath() }

    private fun device(name: String, zone: ZoneOffset = ZoneOffset.UTC): MedicAllCore {
        val dir = tmp.newFolder(name)
        val db = openMedicAllDatabase(File(dir, "medicall.db")).database.also { opened += it }
        return assertIs<Opened.Ready>(runBlocking {
            MedicAllCore.open(db, DirectorySyncFolder(syncRoot), name, passphrase, DirectoryBlobStore(dir.toPath().resolve("blobs")), File(dir, "work"), { wall }, zone)
        }).core
    }

    @Test
    fun todayIsTheCoresOwnTimeZone_SPEC_R14() = runBlocking {
        // At 12:00 UTC it is already the next day at UTC+14: that day is a birth date there, not in UTC.
        val east = device("dev-east", ZoneOffset.ofHours(14))
        assertEquals(today.plusDays(1).toString(), east.profile(east.addProfile("Profile A", birthDate = today.plusDays(1).toString())).birthDate)
        val utc = device("dev-utc")
        assertFailsWith<IllegalArgumentException> { utc.addProfile("Profile B", birthDate = today.plusDays(1).toString()) }
        Unit
    }

    @Test
    fun aBirthDateAndAKindChangedApartBothHold_SPEC_R14() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.changeKind(id, MedicAllCore.PET)
        a.setBirthDate(id, "1990-01-01")
        a.changeKind(id, MedicAllCore.PERSON)
        val p = a.profile(id)
        assertEquals("1990-01-01", p.birthDate)
        assertEquals(MedicAllCore.PERSON, p.kind)
    }

    private suspend fun MedicAllCore.profile(id: String) = profiles().single { it.id == id }

    @Test
    fun twoProfilesKeepTheirModulesApart_SPEC_R14() = runBlocking {
        val a = device("dev-a")
        val first = a.addProfile("Profile A")
        val second = a.addProfile("Profile B")
        a.setModule(first, Module.LOG, false)
        assertEquals(setOf(Module.MEDICINES, Module.RESULTS, Module.CALENDAR), a.profile(first).modules)
        assertEquals(Module.entries.toSet(), a.profile(second).modules)
    }

    @Test
    fun aNewProfileHasEveryModuleOnAndItsOrigin_SPEC_R14() = runBlocking {
        val a = device("dev-a")
        val p = a.profile(a.addProfile("Profile A"))
        assertEquals(Module.entries.toSet(), p.modules)
        assertEquals("dev-a", p.createdDevice)
        assertNull(p.birthDate)
    }

    @Test
    fun renameAndModuleChangeBothSurviveSync_SPEC_R16() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.syncNow()
        val b = device("dev-b")
        b.syncNow()
        a.rename(id, "Profile A2")
        b.setModule(id, Module.RESULTS, false)
        a.syncNow(); b.syncNow(); a.syncNow()
        for (d in listOf(a, b)) {
            val p = d.profile(id)
            assertEquals("Profile A2", p.name)
            assertEquals(setOf(Module.MEDICINES, Module.LOG, Module.CALENDAR), p.modules)
            assertEquals("dev-a", p.createdDevice)
        }
    }

    @Test
    fun aBirthDateAndAKindChangedOnTwoDevicesBothHold_SPEC_R16() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.syncNow()
        val b = device("dev-b")
        b.syncNow()
        a.setBirthDate(id, "1990-01-01")
        b.changeKind(id, MedicAllCore.PET)
        a.syncNow(); b.syncNow(); a.syncNow()
        for (d in listOf(a, b)) {
            val p = d.profile(id)
            assertEquals("1990-01-01", p.birthDate)
            assertEquals(MedicAllCore.PET, p.kind)
        }
    }

    /** A profile line as phase 2a wrote it: the birth date inside the details group, stamped [s]. */
    private fun writeOldDetailsLine(id: String, s: Stamp, birthDate: String?, segment: Long = 0) {
        val folder = DirectorySyncFolder(syncRoot)
        val cipher = assertIs<Unlock.Unlocked>(KeyFolder(folder).unlock(passphrase.copyOf())).cipher
        val row = Row(PROFILE, id, mapOf(
            NAME to Group(s, mapOf("name" to "Profile Old")),
            DETAILS to Group(s, mapOf("birth_date" to birthDate, "kind" to "person")),
            REMOVED to Group(s, mapOf("removed_at" to null)),
            MODULES to Group(s, allModulesOn()),
            ORIGIN to Group(s, mapOf("created_device" to "dev-old")),
        ))
        val line = (cipher.seal(RecordCodec.encode(RowRecord(row)).encodeToByteArray()) + "\n").encodeToByteArray()
        folder.replace(FolderLayout.segment("dev-old", segment), line)
    }

    @Test
    fun aBirthDateInsideAnOlderDetailsGroupIsRead_SPEC_R16() = runBlocking {
        val a = device("dev-a")
        a.syncNow()
        val id = Ids.new()
        writeOldDetailsLine(id, Stamp(1_000, "dev-old"), "1980-05-05")
        a.syncNow()
        assertEquals("1980-05-05", a.profile(id).birthDate)
        // A later birth date set here wins over the older line's.
        a.setBirthDate(id, "1981-06-06")
        a.syncNow()
        assertEquals("1981-06-06", a.profile(id).birthDate)
    }

    /** Seals [row] as a line of [device]'s log, segment 0. */
    private fun writeLine(device: String, row: Row) {
        val folder = DirectorySyncFolder(syncRoot)
        val cipher = assertIs<Unlock.Unlocked>(KeyFolder(folder).unlock(passphrase.copyOf())).cipher
        folder.replace(FolderLayout.segment(device, 0), (cipher.seal(RecordCodec.encode(RowRecord(row)).encodeToByteArray()) + "\n").encodeToByteArray())
    }

    private fun profileRow(id: String, details: Group, birth: Group?): Row {
        val s = Stamp(10, "dev-first")
        return Row(PROFILE, id, buildMap {
            put(NAME, Group(s, mapOf("name" to "Profile A")))
            put(DETAILS, details)
            birth?.let { put(BIRTH, it) }
            put(REMOVED, Group(s, mapOf("removed_at" to null)))
            put(MODULES, Group(s, allModulesOn()))
            put(ORIGIN, Group(s, mapOf("created_device" to "dev-first")))
        })
    }

    @Test
    fun anOlderBirthDateConvergesWhateverTheReadOrder_SPEC_R16() = runBlocking {
        val id = Ids.new()
        // An old build sets the birth date at 20; a new one, not having seen it, changes the kind at 30.
        val old = profileRow(id, Group(Stamp(20, "dev-old"), mapOf("birth_date" to "1985-05-05", "kind" to "person")), birth = null)
        val new = profileRow(
            id,
            Group(Stamp(30, "dev-new"), mapOf("kind" to "pet", "birth_date" to "1990-01-01")),
            birth = Group(Stamp(10, "dev-first"), mapOf("birth_date" to "1990-01-01")),
        )
        // Device Y reads the old line, then the new one.
        val y = device("dev-y")
        y.syncNow()
        writeLine("dev-old", old)
        y.syncNow()
        writeLine("dev-new", new)
        y.syncNow()
        // Device W reads both in one import.
        val w = device("dev-w")
        w.syncNow()
        for (d in listOf(y, w)) {
            val p = d.profile(id)
            assertEquals("1985-05-05", p.birthDate)
            assertEquals(MedicAllCore.PET, p.kind)
        }
    }

    @Test
    fun anOlderProfileLineReadsWithDefaults_SPEC_R14() = runBlocking {
        val a = device("dev-a")
        a.syncNow()
        val folder = DirectorySyncFolder(syncRoot)
        val cipher = assertIs<Unlock.Unlocked>(KeyFolder(folder).unlock(passphrase.copyOf())).cipher
        val s = Stamp(1_000, "dev-old")
        val id = Ids.new()
        // A profile as phase 1 wrote it: three groups, no modules, no origin.
        val row = Row(PROFILE, id, mapOf(
            NAME to Group(s, mapOf("name" to "Profile Old")),
            DETAILS to Group(s, mapOf("birth_date" to null, "kind" to "person")),
            REMOVED to Group(s, mapOf("removed_at" to null)),
        ))
        val line = (cipher.seal(RecordCodec.encode(RowRecord(row)).encodeToByteArray()) + "\n").encodeToByteArray()
        folder.replace(FolderLayout.segment("dev-old", 0), line)
        a.syncNow()
        val p = a.profile(id)
        assertEquals(Module.entries.toSet(), p.modules)
        assertNull(p.createdDevice)
        a.setModule(id, Module.CALENDAR, false)
        assertEquals(setOf(Module.MEDICINES, Module.LOG, Module.RESULTS), a.profile(id).modules)
    }

    @Test
    fun birthDateIsADate_SPEC_R14() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        for (bad in listOf("1990-02-30", "34", "", today.plusDays(1).toString(), "1899-12-31", "0001-01-01")) {
            assertFailsWith<IllegalArgumentException>(bad) { a.setBirthDate(id, bad) }
        }
        a.setBirthDate(id, "1900-01-01")
        assertEquals("1900-01-01", a.profile(id).birthDate)
        a.setBirthDate(id, "1990-02-28")
        assertEquals("1990-02-28", a.profile(id).birthDate)
        a.setBirthDate(id, today.toString())
        assertEquals(today.toString(), a.profile(id).birthDate)
        a.setBirthDate(id, null)
        assertNull(a.profile(id).birthDate)
        assertFailsWith<IllegalArgumentException> { a.addProfile("Profile B", birthDate = "34") }
        assertEquals("1990-01-01", a.profile(a.addProfile("Profile C", birthDate = "1990-01-01")).birthDate)
    }

    @Test
    fun aKindChangeKeepsTheBirthDate_SPEC_R14() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A", birthDate = "1990-01-01")
        a.changeKind(id, MedicAllCore.PET)
        assertEquals("1990-01-01", a.profile(id).birthDate)
        assertEquals(MedicAllCore.PET, a.profile(id).kind)
    }

    @Test
    fun schemaOneUpgradesToTheLatest_SPEC_R15() {
        val file = File(tmp.newFolder("old"), "medicall.db")
        writeVersionOne(file)
        val db = openMedicAllDatabase(file).database.also { opened += it }
        val p = runBlocking { db.profileDao().allProfiles() }.single()
        assertEquals("Profile Old", p.name)
        assertEquals("1990-01-01", p.birthDate)
        val info = p.toRow().toProfileEntity()
        assertEquals(p, info)
        assertEquals(true, p.modulesMedicines && p.modulesLog && p.modulesResults && p.modulesCalendar)
        assertNull(p.createdDevice)
        // The birth date keeps the stamp it had inside the details group, so no peer's later change is outranked.
        val row = p.toRow()
        assertEquals(Stamp(5, "dev-old"), row.groups.getValue(BIRTH).stamp)
        assertEquals(Stamp(5, "dev-old"), row.groups.getValue(DETAILS).stamp)
        // The details group keeps a copy of the birth date for builds from before schema 3.
        assertEquals(mapOf("kind" to "person", "birth_date" to "1990-01-01"), row.groups.getValue(DETAILS).values)
        // The old file's triggers went before the upgrade; the current ones are made again.
        val triggers = runBlocking {
            db.useReaderConnection { c -> c.usePrepared("SELECT name FROM sqlite_master WHERE type = 'trigger'") { s -> buildList { while (s.step()) add(s.getText(0)) } } }
        }
        assertFalse("old_sentinel" in triggers, triggers.toString())
        assertTrue("profile_outbox_update" in triggers, triggers.toString())
    }

    /** A database file as version 1 made it, from the exported schema, holding one profile. */
    private fun writeVersionOne(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.hazia.medicall.data.MedicAllDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            for (entity in schema.getValue("entities").jsonArray) {
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                connection.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { connection.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL(
                "INSERT INTO profile VALUES ('p-old', 'Profile Old', 5, 'dev-old', '1990-01-01', 'person', 5, 'dev-old', NULL, 5, 'dev-old')"
            )
            // The triggers a version 1 open left, and one no version makes: an upgrade must not keep either.
            connection.execSQL("CREATE TRIGGER profile_outbox_update AFTER UPDATE ON profile BEGIN INSERT INTO sync_outbox(id, tbl) VALUES (NEW.id, 'profile'); END")
            connection.execSQL("CREATE TRIGGER old_sentinel AFTER UPDATE ON profile BEGIN SELECT 1; END")
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }
}
