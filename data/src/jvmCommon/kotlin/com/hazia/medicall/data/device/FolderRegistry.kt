package com.hazia.medicall.data.device

import com.hazia.medicall.core.lock.LockState
import com.hazia.medicall.core.look.DeviceLook
import com.hazia.medicall.core.look.LightDark
import com.hazia.medicall.core.look.Palette
import com.hazia.medicall.core.sync.Ids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A sync folder this device joins (SPEC:R110): [folderUri] is where Syncthing keeps it, [backupUri] the backup
 * folder chosen for it (SPEC:R22), null until chosen. Its records live in their own database, [databaseName],
 * under their own key (SPEC:R10).
 */
data class JoinedFolder(val id: String, val label: String, val folderUri: String, val backupUri: String?) {
    val databaseName: String get() = "medicall-$id.db"
}

/**
 * Everything the device keeps apart from the folders' databases: the folders it joins, its app lock (SPEC:R11) and
 * its [look], light or dark and the device palette (SPEC:R114), which no folder and no backup holds.
 */
data class DeviceSettings(val folders: List<JoinedFolder>, val lock: LockState, val look: DeviceLook = DeviceLook.DEFAULT) {
    fun encode(): ByteArray = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("folders", JsonArray(folders.map { f ->
            buildJsonObject {
                put("id", JsonPrimitive(f.id))
                put("label", JsonPrimitive(f.label))
                put("folder", JsonPrimitive(f.folderUri))
                put("backup", f.backupUri?.let(::JsonPrimitive) ?: JsonNull)
            }
        }))
        put("lock", Json.parseToJsonElement(lock.encode().decodeToString()))
        put("look", buildJsonObject {
            put("lightDark", JsonPrimitive(look.lightDark.id))
            put("palette", JsonPrimitive(look.palette.id))
        })
    }.toString().encodeToByteArray()

    companion object {
        val EMPTY = DeviceSettings(emptyList(), LockState.OFF)

        /**
         * [bytes] as [encode] wrote them, and whether every part read ([Decoded.whole]). No file is a first run
         * ([EMPTY]). Otherwise each part reads on its own: a folder entry that does not read is left out and the others
         * kept, and a lock that does not read stays closed ([LockState.UNREADABLE]), never off. A file without a look
         * (written before there was one) reads whole with [DeviceLook.DEFAULT]; a look that does not read is
         * [DeviceLook.DEFAULT] too, and the file is not whole.
         */
        fun decode(bytes: ByteArray?): Decoded {
            if (bytes == null) return Decoded(EMPTY, whole = true)
            val o = try {
                Json.parseToJsonElement(bytes.decodeToString()).jsonObject.also { require(it.getValue("v").jsonPrimitive.int == 1) }
            } catch (_: Exception) {
                return Decoded(DeviceSettings(emptyList(), LockState.UNREADABLE), whole = false)
            }
            var whole = true
            val folders = (runCatching { o.getValue("folders").jsonArray }.getOrNull() ?: JsonArray(emptyList()).also { whole = false })
                .mapNotNull { e ->
                    runCatching {
                        val f = e.jsonObject
                        fun text(key: String) = f.getValue(key).jsonPrimitive.takeUnless { it is JsonNull }?.content
                        JoinedFolder(text("id")!!, text("label")!!, text("folder")!!, text("backup"))
                    }.getOrNull().also { if (it == null) whole = false }
                }
            val lockBytes = runCatching { (o.getValue("lock") as JsonObject).toString().encodeToByteArray() }.getOrNull()
            val lock = lockBytes?.let(LockState::decode) ?: LockState.UNREADABLE
            if (lock == LockState.UNREADABLE) whole = false
            val look = if ("look" !in o) DeviceLook.DEFAULT else runCatching {
                val l = o.getValue("look").jsonObject
                DeviceLook(
                    LightDark.byId(l.getValue("lightDark").jsonPrimitive.content)!!,
                    Palette.byId(l.getValue("palette").jsonPrimitive.content)!!,
                )
            }.getOrNull() ?: DeviceLook.DEFAULT.also { whole = false }
            return Decoded(DeviceSettings(folders, lock, look), whole)
        }
    }

    /** What [decode] read; [whole] is false when some part did not read, and the file should be kept aside. */
    data class Decoded(val settings: DeviceSettings, val whole: Boolean)
}

/**
 * The folders this device joins, its app lock and its look (SPEC:R110, SPEC:R11, SPEC:R114), kept in [file]. Every change is
 * written at once, one at a time. No passphrase is kept here: each folder's is in the key store beside its
 * database key. A file that opens but does not read whole is set aside before anything is written over it, so
 * what it held is kept for repair; what did read is used meanwhile.
 */
class FolderRegistry(private val file: SealedFile) {
    private var settings = DeviceSettings.decode(file.read()).let { d ->
        if (!d.whole) file.setAside()
        d.settings
    }

    @Synchronized fun folders(): List<JoinedFolder> = settings.folders

    /**
     * Joins a new folder at [folderUri], under [id] (a new one by default); a folder already joined throws an
     * [IllegalStateException].
     */
    @Synchronized fun add(label: String, folderUri: String, id: String = Ids.new()): JoinedFolder {
        checkNew(folderUri)
        require(settings.folders.none { it.id == id }) { "folder id $id is taken" }
        val folder = JoinedFolder(id, label, folderUri, backupUri = null)
        save(settings.copy(folders = settings.folders + folder))
        return folder
    }

    /** Throws an [IllegalStateException] when [folderUri] is already joined. */
    @Synchronized fun checkNew(folderUri: String) = check(settings.folders.none { it.folderUri == folderUri }) { "this folder is already joined" }

    @Synchronized fun rename(id: String, label: String) = change(id) { it.copy(label = label) }

    @Synchronized fun setBackup(id: String, backupUri: String?) = change(id) { it.copy(backupUri = backupUri) }

    /** Forgets folder [id]; its database is set aside by the caller, never deleted. */
    @Synchronized fun remove(id: String) {
        require(settings.folders.any { it.id == id }) { "no joined folder $id" }
        save(settings.copy(folders = settings.folders.filter { it.id != id }))
    }

    @Synchronized fun lock(): LockState = settings.lock

    @Synchronized fun saveLock(state: LockState) = save(settings.copy(lock = state))

    /** Light or dark and the device palette (SPEC:R114). */
    @Synchronized fun look(): DeviceLook = settings.look

    @Synchronized fun saveLook(look: DeviceLook) = save(settings.copy(look = look))

    private fun change(id: String, block: (JoinedFolder) -> JoinedFolder) {
        require(settings.folders.any { it.id == id }) { "no joined folder $id" }
        save(settings.copy(folders = settings.folders.map { if (it.id == id) block(it) else it }))
    }

    private fun save(new: DeviceSettings) {
        file.write(new.encode())
        settings = new
    }
}
