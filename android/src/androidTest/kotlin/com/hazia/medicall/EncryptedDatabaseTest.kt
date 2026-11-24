package com.hazia.medicall

import androidx.room.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hazia.medicall.data.openEncryptedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** SQLCipher on a real phone (SPEC:R10). Each test uses a throwaway database name, never the app's own file. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-${UUID.randomUUID()}.db"
    private val keyA = ByteArray(32) { (it * 5 + 1).toByte() }
    private val keyB = ByteArray(32) { (it * 3 + 2).toByte() }

    @After fun clean() {
        context.getDatabasePath(name).parentFile?.listFiles { f -> f.name.startsWith(name) }?.forEach { it.delete() }
        File(context.filesDir, "vault-test.sealed").delete()
        File(context.filesDir, "vault-test-passphrase.sealed").delete()
    }

    private fun openAndProbe(key: ByteArray) {
        val db = openEncryptedDatabase(context, key, name).database
        runBlocking { db.useReaderConnection { c -> c.usePrepared("SELECT count(*) FROM profile") { it.step() } } }
        db.close()
    }

    @Test
    fun fileIsNotPlainSqlite_SPEC_R10() {
        openAndProbe(keyA)
        val header = context.getDatabasePath(name).readBytes().copyOf(16)
        assertFalse("SQLite format 3".toByteArray().plus(0) contentEquals header)
    }

    @Test
    fun wrongKeyCannotReadAndLeavesTheFile() {
        openAndProbe(keyA)
        val file = context.getDatabasePath(name)
        val before = file.readBytes()

        val failure = runCatching { openAndProbe(keyB) }.exceptionOrNull()

        assertNotNull("another key must not open the file", failure)
        assertTrue("the file is still there", file.exists())
        assertArrayEquals("and unchanged", before, file.readBytes())
        openAndProbe(keyA) // the right key still opens it
    }

    @Test
    fun keySurvivesRestart() {
        val file = File(context.filesDir, "vault-test.sealed")
        KeyVault(context, file).store(keyA)
        assertFalse("the key is not stored in the clear", file.readBytes().toList().windowed(32).any { it.toByteArray() contentEquals keyA })
        assertArrayEquals(keyA, KeyVault(context, file).load())
    }

    // SPEC:R109 the passphrase is entered once per device: it survives a restart, sealed, never in the clear.
    @Test
    fun passphraseSurvivesRestart_SPEC_R109() {
        val keyFile = File(context.filesDir, "vault-test.sealed")
        val file = File(context.filesDir, "vault-test-passphrase.sealed")
        val passphrase = "invented-walk-passphrase-é"
        KeyVault(context, keyFile, file).storePassphrase(passphrase.toCharArray())
        val stored = file.readBytes()
        val plain = passphrase.encodeToByteArray()
        assertFalse("the passphrase is not stored in the clear", stored.toList().windowed(plain.size).any { it.toByteArray() contentEquals plain })
        assertEquals(passphrase, KeyVault(context, keyFile, file).loadPassphrase()?.concatToString())
        assertFalse("the key file is apart and untouched", keyFile.exists())
    }

    @Test
    fun unreadableFileIsKeptNotDeleted() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val junk = ByteArray(8192) { (it % 251).toByte() }
        file.writeBytes(junk)

        val failure = runCatching { openAndProbe(keyA) }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(file.exists())
        assertArrayEquals("SQLCipher cannot tell junk from a wrong key, so the file is left as it was", junk, file.readBytes())
        assertEquals(0, file.parentFile!!.listFiles { f -> f.name.startsWith("$name.unopenable-") }!!.size)
    }
}
