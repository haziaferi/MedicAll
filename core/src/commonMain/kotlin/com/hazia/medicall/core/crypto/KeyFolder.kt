package com.hazia.medicall.core.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The shortest passphrase accepted (provisional: the plan's question 3). */
const val MIN_PASSPHRASE = 12

/** The few file operations the key file needs; the sync folder provides them. Paths are `/`-separated. */
interface KeyFiles {
    fun read(path: String): ByteArray?
    fun replace(path: String, bytes: ByteArray)
    fun list(dir: String): List<String>
    fun delete(path: String)
}

/**
 * The key file every syncing device reads (SPEC:R109): `keys/keyinfo.json`, holding the current key's
 * id, salt and iteration count, a check value that tells a wrong passphrase from a right one, and every
 * older key sealed under the current one, so lines written before a passphrase change still open.
 * It holds no key in the clear: without the passphrase it opens nothing.
 */
@OptIn(ExperimentalEncodingApi::class)
class KeyFolder internal constructor(private val files: KeyFiles, private val iterations: Int) {

    constructor(files: KeyFiles) : this(files, KeyDerivation.ITERATIONS)

    private class Info(val keyId: String, val salt: ByteArray, val iterations: Int, val check: ByteArray, val older: Map<String, String>)

    /** Makes the key file for a folder that has none, and returns its cipher. */
    fun create(passphrase: CharArray): LineCipher {
        requireLength(passphrase)
        check(files.read(PATH) == null) { "A key file already exists" }
        return writeNew(passphrase, previous = emptyMap())
    }

    /** The cipher for [passphrase], with every older key, or null when the passphrase is wrong or there is no key file. */
    fun unlock(passphrase: CharArray): LineCipher? {
        val info = files.read(PATH)?.let(::parse) ?: return null
        return cipherFor(info, KeyDerivation.derive(passphrase, info.salt, info.iterations))
    }

    /** A new passphrase: a new key id and salt, with every key known so far sealed into the chain. Throws when [old] is wrong. */
    fun change(old: CharArray, new: CharArray): LineCipher {
        requireLength(new)
        val current = requireNotNull(unlock(old)) { "Wrong passphrase" }
        return writeNew(new, previous = current.knownKeys())
    }

    /**
     * Two devices that started offline each made a key file, and Syncthing renamed one of them
     * `keyinfo.sync-conflict-*.json`. The file with the lower key id is kept and the copies go. True when the
     * file this device had read lost: it must then unlock again and re-encrypt what it wrote.
     */
    fun settleConflicts(): Boolean {
        val copies = files.list(DIR).filter { it.startsWith("keyinfo") && ".sync-conflict-" in it }
        if (copies.isEmpty()) return false
        val main = files.read(PATH)
        val candidates = (listOfNotNull(main) + copies.mapNotNull { files.read("$DIR/$it") })
            .mapNotNull { bytes -> parse(bytes)?.let { it to bytes } }
        val winner = candidates.minByOrNull { it.first.keyId } ?: return false
        val lost = main == null || parse(main)?.keyId != winner.first.keyId
        if (lost) files.replace(PATH, winner.second)
        copies.forEach { files.delete("$DIR/$it") }
        return lost
    }

    private fun writeNew(passphrase: CharArray, previous: Map<String, ByteArray>): LineCipher {
        val salt = secureRandomBytes(SALT_BYTES)
        val key = KeyDerivation.derive(passphrase, salt, iterations)
        val keyId = newKeyId()
        val sealer = LineCipher(mapOf(keyId to key), keyId)
        val info = Info(keyId, salt, iterations, checkValue(key), previous.mapValues { sealer.seal(it.value) })
        files.replace(PATH, encode(info))
        return requireNotNull(cipherFor(info, key))
    }

    private fun cipherFor(info: Info, key: ByteArray): LineCipher? {
        if (!constantTimeEquals(checkValue(key), info.check)) return null
        val current = LineCipher(mapOf(info.keyId to key), info.keyId)
        val older = info.older.mapValues { (_, sealed) -> current.open(sealed) ?: return null }
        return LineCipher(older + (info.keyId to key), info.keyId)
    }

    private fun checkValue(key: ByteArray) = hmacSha256(key, CHECK_LABEL.encodeToByteArray())

    private fun encode(info: Info): ByteArray = buildJsonObject {
        put("version", 1)
        put("keyId", info.keyId)
        put("salt", Base64.encode(info.salt))
        put("iterations", info.iterations)
        put("check", Base64.encode(info.check))
        putJsonObject("older") { info.older.forEach { (id, sealed) -> put(id, sealed) } }
    }.toString().encodeToByteArray()

    private fun parse(bytes: ByteArray): Info? = runCatching {
        val o = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        Info(
            keyId = o.getValue("keyId").jsonPrimitive.content,
            salt = Base64.decode(o.getValue("salt").jsonPrimitive.content),
            iterations = o.getValue("iterations").jsonPrimitive.int,
            check = Base64.decode(o.getValue("check").jsonPrimitive.content),
            older = (o["older"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content },
        )
    }.getOrNull()

    private fun requireLength(passphrase: CharArray) =
        require(passphrase.size >= MIN_PASSPHRASE) { "Passphrase too short" }

    private fun newKeyId() = secureRandomBytes(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        const val DIR = "keys"
        const val PATH = "$DIR/keyinfo.json"
        private const val SALT_BYTES = 16
        private const val CHECK_LABEL = "medicall-key-check"
    }
}
