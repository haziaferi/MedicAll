package com.hazia.medicall.data

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the Android database encrypted with SQLCipher (SPEC:R10), with [key] (the 32 bytes the vault
 * keeps) handed over as SQLCipher's raw-key literal `x'<64 hex digits>'`, so SQLCipher runs no key
 * derivation of its own. Goes through [openOrRecover], as the plain open path does.
 *
 * Room runs in its open-helper mode here, since SQLCipher plugs in as a SupportSQLite factory; the
 * schema callback and the connection API work in that mode too. A file SQLCipher cannot read reports
 * "file is not a database", which a wrong key and a damaged file share, so it is never set aside
 * automatically: the open throws and the file stays exactly as it was. SQLCipher's own corruption
 * handler does not delete a file it holds a key for (Emivita's EmivitaDatabase, sqlcipher-android 4.19.1).
 *
 * [setAsideExisting] is for a key made just now (the key store had none, or lost it): no database made
 * before can open with it, so an existing file is set aside first, kept, and the result says `recovered`,
 * which makes the first sync read this device's own log back (SPEC:R10). That mark lasts, in a file beside
 * the database written before anything is set aside, so a restart before that sync still says `recovered`;
 * [ownLogRead] clears it once the sync has read the log.
 */
fun openEncryptedDatabase(
    context: Context,
    key: ByteArray,
    name: String = DATABASE_NAME,
    setAsideExisting: Boolean = false,
): DatabaseOpen<MedicAllDatabase> {
    require(key.size == 32) { "the database key is 32 bytes" }
    SqlCipherLibrary.load()
    val app = context.applicationContext
    val file = app.getDatabasePath(name)
    file.parentFile?.mkdirs()
    val marker = recoveringMarker(app, name)
    fun setAsideMarked() {
        marker.createNewFile()
        setAsideDatabaseFiles(file.path, System.currentTimeMillis())
    }
    if (setAsideExisting && file.exists()) setAsideMarked()
    val literal = ("x'" + key.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } + "'").encodeToByteArray()
    val open = openOrRecover(
        build = {
            Room.databaseBuilder<MedicAllDatabase>(app, name)
                .openHelperFactory(SupportOpenHelperFactory(literal))
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(SchemaTriggers)
                .build()
        },
        probe = { db -> runBlocking { db.useReaderConnection { it.usePrepared("SELECT count(*) FROM sqlite_master") { s -> s.step() } } } },
        close = { it.close() },
        setAside = ::setAsideMarked,
        isUnopenable = ::isCorruptDatabase,
    )
    return DatabaseOpen(open.database, recovered = open.recovered || marker.exists())
}

/** Clears the lasting mark of a database made anew, once the sync has read this device's own log back into it. */
fun ownLogRead(context: Context, name: String = DATABASE_NAME) {
    recoveringMarker(context.applicationContext, name).delete()
}

private fun recoveringMarker(app: Context, name: String) = java.io.File(app.getDatabasePath(name).path + RECOVERING_SUFFIX)

/** Beside a database made anew until its own log is read back; not a database file, so never set aside with one. */
private const val RECOVERING_SUFFIX = ".recovering"

/** sqlcipher-android does not load its native library by itself; every process that opens the database needs it once. */
private object SqlCipherLibrary {
    private val loaded by lazy { System.loadLibrary("sqlcipher") }
    fun load() = loaded
}
