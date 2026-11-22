package com.hazia.medicall.data.blob

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.hazia.medicall.core.crypto.LineCipher
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.sync.FolderLayout
import com.hazia.medicall.data.sync.SyncFolder

/**
 * When this device last saw a file unreferenced, apart from the folder's copy and its own: what the
 * 30 days of collection count from. Local, never synced.
 */
@Entity(tableName = "blob_seen")
internal data class BlobSeenEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "folder_since") val folderSince: Long?,
    @ColumnInfo(name = "local_since") val localSince: Long?,
)

@Dao
internal interface BlobDao {
    @Query("SELECT * FROM blob_seen") suspend fun seen(): List<BlobSeenEntity>
    @Upsert suspend fun putSeen(rows: List<BlobSeenEntity>)
    @Query("DELETE FROM blob_seen WHERE name IN (:names)") suspend fun clearSeen(names: List<String>)
}

/**
 * Carries files through the sync folder, as `blobs/<sha256>` beside `devices/` (SPEC:R16,
 * attachments): the folder only carries them, and each device keeps its own copy in [store]. Both
 * hold the file sealed by [cipher] as UTF-8 text, and the bytes travel as they are; a copy counts as
 * good only when it opens and its plain bytes hash to its name (SPEC:R23).
 *
 * - **Publish**, before the logs are exported: every file a live file reference names, held here
 *   undamaged and missing from the folder (or there with another size), is written there; so a
 *   live file's copy collected or damaged comes back from any device holding it.
 * - **Fetch**, after an import: every file a reference here names and this device lacks is read
 *   from the folder, and kept only when it opens to the bytes its name promises; one sealed under a
 *   key this device does not hold yet is left for a later exchange.
 * - **Collect**: the folder's copy goes once this device has named it in no live reference for 30
 *   days, so a reference restored from the bin within them keeps its file, and a file that arrives
 *   up to 30 days before the line naming it is not taken. This device's copy goes once no
 *   reference here names it, the bin's included, for as long. Syncthing's conflict copies go at
 *   once: a file's name is its plain bytes' hash, so the copy is the same file or a damaged one.
 *
 * A known limit, as in Factotum: a file only a reference in the bin names travels no more once its
 * folder copy is collected, so a device away longer than 30 days restores that reference without it
 * until a device holding it names it live again.
 */
internal class BlobSync(
    db: MedicAllDatabase,
    private val folder: SyncFolder,
    private val store: BlobStore,
    private val cipher: LineCipher,
    private val wallMillis: () -> Long,
) {
    private val refs = db.blobRefDao()
    private val dao = db.blobDao()

    suspend fun exchange() {
        val all = refs.allBlobRefs()
        val shared = all.filter { it.removedAt == null }.map { it.sha256 }.toSet()
        val kept = all.map { it.sha256 }.toSet()
        val listed = folder.list(BLOBS)
        listed.filter(FolderLayout::isConflictCopy).forEach { folder.delete("$BLOBS/$it") }
        val inFolder = listed.filter(BlobName::isValid).toSet()
        val held = store.list().filter(BlobName::isValid).toMutableSet()
        for (name in kept - held) {
            if (name !in inFolder) continue
            val sealed = folder.read("$BLOBS/$name") ?: continue
            if (opened(cipher, sealed, name) == null) continue
            store.write(name, sealed)
            held += name
        }
        for (name in shared intersect held) {
            val sealed = store.read(name) ?: continue
            if (name in inFolder && folder.size("$BLOBS/$name") == sealed.size.toLong()) continue
            if (verified(store, cipher, name) == null) held -= name else folder.replace("$BLOBS/$name", sealed)
        }

        val now = wallMillis()
        val seen = dao.seen().associateBy { it.name }
        val next = mutableListOf<BlobSeenEntity>()
        val cleared = mutableListOf<String>()
        for (name in inFolder + held) {
            val was = seen[name]
            val folderSince = if (name in shared || name !in inFolder) null else was?.folderSince ?: now
            val localSince = if (name in kept || name !in held) null else was?.localSince ?: now
            if (folderSince != null && now - folderSince >= GRACE_MS) folder.delete("$BLOBS/$name")
            if (localSince != null && now - localSince >= GRACE_MS) store.delete(name)
            if (folderSince == null && localSince == null) cleared += name else next += BlobSeenEntity(name, folderSince, localSince)
        }
        dao.putSeen(next)
        dao.clearSeen(cleared + (seen.keys - inFolder - held))
    }

    private companion object {
        const val BLOBS = "blobs"
        const val GRACE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
