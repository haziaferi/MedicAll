package com.hazia.medicall.data

import com.hazia.medicall.core.crypto.KeyFolder
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.core.look.Palette
import com.hazia.medicall.core.sync.Group
import com.hazia.medicall.core.sync.Ids
import com.hazia.medicall.core.sync.Row
import com.hazia.medicall.core.sync.Stamp
import com.hazia.medicall.data.backup.BackupResult
import com.hazia.medicall.data.backup.DirectoryBackupTarget
import com.hazia.medicall.data.backup.SevenZip
import com.hazia.medicall.data.backup.read
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.sync.DirectorySyncFolder
import com.hazia.medicall.data.sync.FolderLayout
import com.hazia.medicall.data.sync.RecordCodec
import com.hazia.medicall.data.sync.RowRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The per-device switches of SPEC:R110: reminders here and shown here, never synced or backed up. Every value is invented. */
class ProfileOnDeviceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val opened = mutableListOf<MedicAllDatabase>()
    @After fun close() = opened.forEach { it.close() }

    private val passphrase = "invented-device-passphrase".toCharArray()
    private val DAY = 86_400_000L
    private var wall = 1_795_000_000_000L
    private val syncRoot by lazy { tmp.newFolder("sync").toPath() }

    private fun device(name: String, dir: File = tmp.newFolder(name), recovered: Boolean = false): MedicAllCore {
        val db = openMedicAllDatabase(File(dir, "medicall.db")).database.also { opened += it }
        return assertIs<Opened.Ready>(runBlocking {
            MedicAllCore.open(db, DirectorySyncFolder(syncRoot), name, passphrase, DirectoryBlobStore(dir.toPath().resolve("blobs")), File(dir, "work"), { wall }, ZoneOffset.UTC, recovered)
        }).core
    }

    /** Every file under [root], by its relative path, with its bytes. */
    private fun snapshot(root: Path): Map<String, List<Byte>> = Files.walk(root).use { s ->
        s.filter { it.isRegularFile() }.toList().associate { root.relativize(it).toString() to it.readBytes().toList() }
    }

    @Test
    fun aSyncedProfileFiresNoReminderOnTheSecondDevice_SPEC_R110() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.syncNow()
        val b = device("dev-b")
        b.syncNow()
        assertTrue(a.remindersHere(id))
        assertFalse(b.remindersHere(id))
        b.setRemindersHere(id, true)
        a.syncNow(); b.syncNow()
        assertTrue(b.remindersHere(id))
        assertTrue(a.remindersHere(id))
        a.setRemindersHere(id, false)
        a.syncNow(); b.syncNow()
        assertFalse(a.remindersHere(id))
        assertTrue(b.remindersHere(id))
    }

    @Test
    fun aProfileOfUnknownOriginHasRemindersOff_SPEC_R110() = runBlocking {
        val a = device("dev-a")
        a.syncNow()
        val folder = DirectorySyncFolder(syncRoot)
        val cipher = assertIs<Unlock.Unlocked>(KeyFolder(folder).unlock(passphrase.copyOf())).cipher
        val s = Stamp(1_000, "dev-old")
        val id = Ids.new()
        val row = Row(PROFILE, id, mapOf(
            NAME to Group(s, mapOf("name" to "Profile Old")),
            DETAILS to Group(s, mapOf("birth_date" to null, "kind" to "person")),
            REMOVED to Group(s, mapOf("removed_at" to null)),
        ))
        folder.replace(FolderLayout.segment("dev-old", 0), (cipher.seal(RecordCodec.encode(RowRecord(row)).encodeToByteArray()) + "\n").encodeToByteArray())
        a.syncNow()
        assertFalse(a.remindersHere(id))
        assertTrue(a.shownHere(id))
    }

    @Test
    fun switchesStayOnThisDevice_SPEC_R110() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.syncNow()
        val before = snapshot(syncRoot)
        a.setRemindersHere(id, false)
        a.setShownHere(id, false)
        a.syncNow()
        assertEquals(before, snapshot(syncRoot))
        assertFalse(a.shownHere(id))

        val backupRoot = tmp.newFolder("backups").toPath()
        assertIs<BackupResult.Written>(a.backUp(DirectoryBackupTarget(backupRoot)))
        val archive = Files.list(backupRoot).use { s -> s.toList().single { it.toString().endsWith(".7z") } }.toFile()
        val names = assertNotNull(SevenZip.list(archive, passphrase))
        for (name in names) {
            assertFalse("profile_device" in name, name)
            val text = SevenZip.read(archive, passphrase, name)?.decodeToString().orEmpty()
            assertFalse("profile_device" in text || "reminders" in text, name)
        }
    }

    @Test
    fun aRebuiltDatabaseKeepsTheCreatorsReminders_SPEC_R10() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.setShownHere(id, false)
        a.syncNow()
        // The key store lost the key: an empty database in a new place, filled again from the folder.
        val rebuilt = device("dev-a", tmp.newFolder("dev-a-rebuilt"), recovered = true)
        rebuilt.syncNow()
        assertTrue(rebuilt.remindersHere(id))
        // A switch set by hand is not in the folder: it is back to its default.
        assertTrue(rebuilt.shownHere(id))
    }

    @Test
    fun aProfileInTheBinFiresNoReminderAndIsNotShown_SPEC_R110() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.remove(PROFILE, id)
        assertFalse(a.remindersHere(id))
        assertFalse(a.shownHere(id))
        // Taken out of the bin, its switches are as they were.
        a.restore(PROFILE, id)
        assertTrue(a.remindersHere(id))
        assertTrue(a.shownHere(id))
    }

    @Test
    fun twoSwitchesSetAtOnceBothHold_SPEC_R110() = runBlocking {
        val a = device("dev-a")
        val ids = List(40) { a.addProfile("Profile $it") }
        coroutineScope {
            for (id in ids) {
                launch(Dispatchers.IO) { a.setRemindersHere(id, false) }
                launch(Dispatchers.IO) { a.setShownHere(id, false) }
            }
        }
        for (id in ids) {
            assertFalse(a.remindersHere(id), id)
            assertFalse(a.shownHere(id), id)
        }
    }

    @Test
    fun aSwitchSetAsItsProfileIsPurgedSaysNoProfile_SPEC_R110() = runBlocking {
        val a = device("dev-a")
        val ids = List(60) { a.addProfile("Profile $it") }
        ids.forEach { a.remove(PROFILE, it) }
        wall += 31 * DAY
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        coroutineScope {
            launch(Dispatchers.IO) { a.purgeExpired() }
            for (id in ids) launch(Dispatchers.IO) {
                try {
                    a.setRemindersHere(id, true)
                } catch (e: Exception) {
                    failures += e
                }
            }
        }
        // Set before the purge (then purged with it) or refused after it: never a database error.
        assertTrue(failures.all { it is IllegalArgumentException }, failures.map { it::class.simpleName to it.message }.distinct().toString())
        assertEquals(emptyList(), opened.single().profileDeviceDao().all())
    }

    @Test
    fun aProfilesPaletteIsChosenOnEachDeviceAndNeverSynced_SPEC_R114() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.syncNow()
        val b = device("dev-b")
        b.syncNow()
        // A new profile opens in Albarello.
        assertEquals(Palette.ALBARELLO, a.paletteHere(id))
        assertEquals(Palette.ALBARELLO, b.paletteHere(id))

        // Two rounds settle the folder: the control is that a round with no change leaves it as it was.
        a.syncNow(); b.syncNow()
        val settled = snapshot(syncRoot)
        a.syncNow(); b.syncNow()
        assertEquals(settled, snapshot(syncRoot), "the control: a round with no change")
        val folderBefore = snapshot(syncRoot)
        a.setPaletteHere(id, Palette.MURANO)
        b.setPaletteHere(id, Palette.CAPSULE)
        a.syncNow(); b.syncNow()
        assertEquals(folderBefore, snapshot(syncRoot), "a palette changes no file in the sync folder")
        assertEquals(Palette.MURANO, a.paletteHere(id))
        assertEquals(Palette.CAPSULE, b.paletteHere(id))

        val backupRoot = tmp.newFolder("backups").toPath()
        assertIs<BackupResult.Written>(a.backUp(DirectoryBackupTarget(backupRoot)))
        val archive = Files.list(backupRoot).use { s -> s.toList().single { it.toString().endsWith(".7z") } }.toFile()
        for (name in assertNotNull(SevenZip.list(archive, passphrase))) {
            val text = SevenZip.read(archive, passphrase, name)?.decodeToString().orEmpty()
            assertFalse("murano" in text || "palette" in text, name)
        }
    }

    @Test
    fun aPaletteThisVersionDoesNotKnowIsAlbarello_SPEC_R114() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.setShownHere(id, true)
        opened.single().profileDeviceDao().setPalette(id, "a-later-palette")
        assertEquals(Palette.ALBARELLO, a.paletteHere(id))
        // Setting the palette leaves the other switches as they were.
        a.setPaletteHere(id, Palette.PLUM_AND_OLIVE)
        assertEquals(Palette.PLUM_AND_OLIVE, a.paletteHere(id))
        assertTrue(a.shownHere(id))
    }

    @Test
    fun aPurgedProfileTakesItsSwitches_SPEC_R97() = runBlocking {
        val a = device("dev-a")
        val id = a.addProfile("Profile A")
        a.setRemindersHere(id, false)
        a.remove(PROFILE, id)
        wall += 31 * DAY
        a.syncNow()
        assertTrue(a.profiles().none { it.id == id })
        assertEquals(emptyList(), opened.single().profileDeviceDao().all())
    }
}
