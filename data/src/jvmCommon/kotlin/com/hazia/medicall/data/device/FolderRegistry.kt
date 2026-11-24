package com.hazia.medicall.data.device

import com.hazia.medicall.core.lock.LockState
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

/** Everything the device keeps apart from the folders' databases: the folders it joins and its app lock (SPEC:R11). */
data class DeviceSettings(val folders: List<JoinedFolder>, val lock: LockState) {
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
    }.toString().encodeToByteArray()

    companion object {
        val EMPTY = DeviceSettings(emptyList(), LockState.OFF)

        /** [bytes] as [encode] wrote them; anything else is [EMPTY], as on a first run. */
        fun decode(bytes: ByteArray?): DeviceSettings {
            if (bytes == null) return EMPTY
            return try {
                val o = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
                require(o.getValue("v").jsonPrimitive.int == 1)
                val folders = o.getValue("folders").jsonArray.map { e ->
                    val f = e.jsonObject
                    fun text(key: String) = f.getValue(key).jsonPrimitive.takeUnless { it is JsonNull }?.content
                    JoinedFolder(text("id")!!, text("label")!!, text("folder")!!, text("backup"))
                }
                DeviceSettings(folders, LockState.decode((o.getValue("lock") as JsonObject).toString().encodeToByteArray()))
            } catch (_: Exception) {
                EMPTY
            }
        }
    }
}

/**
 * The folders this device joins and its app lock (SPEC:R110, SPEC:R11), kept in [file]. Every change is
 * written at once. No passphrase is kept here: each folder's is in the key store beside its database key.
 */
class FolderRegistry(private val file: SealedFile) {
    private var settings = DeviceSettings.decode(file.read())

    fun folders(): List<JoinedFolder> = settings.folders

    /**
     * Joins a new folder at [folderUri], under [id] (a new one by default); a folder already joined throws an
     * [IllegalStateException].
     */
    fun add(label: String, folderUri: String, id: String = Ids.new()): JoinedFolder {
        checkNew(folderUri)
        require(settings.folders.none { it.id == id }) { "folder id $id is taken" }
        val folder = JoinedFolder(id, label, folderUri, backupUri = null)
        save(settings.copy(folders = settings.folders + folder))
        return folder
    }

    /** Throws an [IllegalStateException] when [folderUri] is already joined. */
    fun checkNew(folderUri: String) = check(settings.folders.none { it.folderUri == folderUri }) { "this folder is already joined" }

    fun rename(id: String, label: String) = change(id) { it.copy(label = label) }

    fun setBackup(id: String, backupUri: String?) = change(id) { it.copy(backupUri = backupUri) }

    /** Forgets folder [id]; its database is set aside by the caller, never deleted. */
    fun remove(id: String) {
        require(settings.folders.any { it.id == id }) { "no joined folder $id" }
        save(settings.copy(folders = settings.folders.filter { it.id != id }))
    }

    fun lock(): LockState = settings.lock

    fun saveLock(state: LockState) = save(settings.copy(lock = state))

    private fun change(id: String, block: (JoinedFolder) -> JoinedFolder) {
        require(settings.folders.any { it.id == id }) { "no joined folder $id" }
        save(settings.copy(folders = settings.folders.map { if (it.id == id) block(it) else it }))
    }

    private fun save(new: DeviceSettings) {
        file.write(new.encode())
        settings = new
    }
}
