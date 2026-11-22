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

/** The shortest passphrase accepted, in UTF-16 units (provisional: the plan's question 3). */
const val MIN_PASSPHRASE = 12

/** The few file operations the key file needs; the sync folder provides them. Paths are `/`-separated. */
interface KeyFiles {
    fun read(path: String): ByteArray?
    fun replace(path: String, bytes: ByteArray)
    fun list(dir: String): List<String>
    fun delete(path: String)
}

/** What [KeyFolder.unlock] found. Only [Unlocked] carries a cipher; the others tell the person what is wrong. */
sealed interface Unlock {
    class Unlocked(val cipher: LineCipher) : Unlock
    data object WrongPassphrase : Unlock
    data object NoKeyFile : Unlock

    /** The key file, or one key in its chain, cannot be read: it was altered or cut short, so no passphrase opens it. */
    data object Damaged : Unlock
}

/**
 * The key file every syncing device reads (SPEC:R109): `keys/keyinfo.json`, holding the current key's
 * id, salt and iteration count, a check value that tells a wrong passphrase from a right one, and every
 * older key sealed under the current one, so lines written before a passphrase change still open.
 * It holds no key in the clear: without the passphrase it opens nothing.
 *
 * A key is never thrown away. When two key files clash, the one with the lower key id becomes the folder's
 * key and the other moves to `keys/retired/<keyId>.json` (SPEC:R16, conflict copies are merged, then
 * deleted). A retired key made with the same passphrase joins the chain at the next [unlock]; one made
 * with another passphrase waits until [fold] is given that passphrase.
 *
 * The caller owns the passphrase arrays and should clear them after use.
 */
@OptIn(ExperimentalEncodingApi::class)
class KeyFolder internal constructor(
    private val files: KeyFiles,
    private val iterations: Int,
    private val minIterations: Int,
) {

    constructor(files: KeyFiles) : this(files, KeyDerivation.ITERATIONS, MIN_FILE_ITERATIONS)

    private class Info(val keyId: String, val salt: ByteArray, val iterations: Int, val check: ByteArray, val older: Map<String, String>)

    /** Makes the key file for a folder that has none, and returns its cipher. */
    fun create(passphrase: CharArray): LineCipher {
        requireLength(passphrase)
        check(files.read(PATH) == null) { "A key file already exists" }
        return writeNew(passphrase, previous = emptyMap())
    }

    /**
     * The cipher for [passphrase], with every older key and every retired key it opens (in memory: the
     * file gains them at the next [change] or [fold]). Never throws on a bad file.
     */
    fun unlock(passphrase: CharArray): Unlock {
        val bytes = files.read(PATH) ?: return Unlock.NoKeyFile
        val info = parse(bytes) ?: return Unlock.Damaged
        val key = KeyDerivation.derive(passphrase, info.salt, info.iterations)
        if (!checks(info, key)) return Unlock.WrongPassphrase
        val chain = chainOf(info, key) ?: return Unlock.Damaged
        return Unlock.Unlocked(LineCipher(retiredOpenedBy(passphrase, chain) + chain, info.keyId))
    }

    /**
     * A new passphrase: a new key id and salt, with every key known so far sealed into the chain. Conflict
     * copies are settled first, so no key in them is left out. Throws when [old] is wrong or the file is damaged.
     */
    fun change(old: CharArray, new: CharArray): LineCipher {
        requireLength(new)
        settleConflicts()
        val current = when (val u = unlock(old)) {
            is Unlock.Unlocked -> u.cipher
            Unlock.WrongPassphrase -> throw IllegalArgumentException("Wrong passphrase")
            Unlock.NoKeyFile -> throw IllegalStateException("No key file")
            Unlock.Damaged -> throw IllegalStateException("The key file is damaged")
        }
        return writeNew(new, previous = current.knownKeys())
    }

    /**
     * Two devices that started offline each made a key file (or changed the passphrase apart), and Syncthing
     * renamed one of them `keyinfo.sync-conflict-*.json`. The file with the lowest key id stays the folder's
     * key, holding the union of every same-id copy's chain (each entry is sealed under that one key, so no
     * passphrase is needed). Every other key file moves to `keys/retired/`, and a copy no build of this
     * version can read moves to `keys/unreadable/`. Nothing is deleted that is not kept somewhere. True when
     * the file this device had read lost: it must then unlock again.
     */
    fun settleConflicts(): Boolean {
        val copies = files.list(DIR).filter { it.startsWith("keyinfo") && ".sync-conflict-" in it }
        if (copies.isEmpty()) return false
        val main = files.read(PATH)
        val all = listOfNotNull(main?.let { "keyinfo.json" to it }) + copies.mapNotNull { name -> files.read("$DIR/$name")?.let { name to it } }
        val readable = all.mapNotNull { (_, bytes) -> parse(bytes)?.let { it to bytes } }
        val winnerId = readable.minOfOrNull { it.first.keyId } ?: return false // nothing readable: leave every file as it is
        all.filter { parse(it.second) == null }.forEach { (name, bytes) -> files.replace("$UNREADABLE/$name", bytes) }
        readable.filter { it.first.keyId != winnerId }.forEach { (info, bytes) -> files.replace(retiredPath(info.keyId, bytes), bytes) }
        val same = readable.filter { it.first.keyId == winnerId }.map { it.first }
        val merged = same.first().let { Info(it.keyId, it.salt, it.iterations, it.check, same.fold(emptyMap()) { o, i -> o + i.older }) }
        val lost = main == null || parse(main)?.keyId != winnerId
        files.replace(PATH, encode(merged))
        copies.forEach { files.delete("$DIR/$it") }
        return lost
    }

    /** The ids of retired keys that [passphrase]'s chain does not yet hold: what still waits for [fold]. Never throws on a bad file. */
    fun unfoldedKeyIds(passphrase: CharArray): List<String> {
        val info = files.read(PATH)?.let(::parse) ?: return emptyList()
        val key = KeyDerivation.derive(passphrase, info.salt, info.iterations)
        val chain = (if (checks(info, key)) chainOf(info, key) else null) ?: return emptyList()
        val known = chain.keys + retiredOpenedBy(passphrase, chain).keys
        return retired().map { it.keyId }.filter { it !in known }.distinct().sorted()
    }

    /**
     * Folds the retired keys that [retiredPassphrase] opens into the chain of the folder's key, which
     * [passphrase] opens. Retired files are kept: they are small, and a copy elsewhere may be the only one
     * of a key a peer has not seen yet. True when at least one key was added to the chain.
     */
    fun fold(passphrase: CharArray, retiredPassphrase: CharArray): Boolean {
        val bytes = files.read(PATH) ?: throw IllegalStateException("No key file")
        val info = parse(bytes) ?: throw IllegalStateException("The key file is damaged")
        val key = KeyDerivation.derive(passphrase, info.salt, info.iterations)
        require(checks(info, key)) { "Wrong passphrase" }
        val chain = chainOf(info, key) ?: throw IllegalStateException("The key file is damaged")
        val added = retiredOpenedBy(retiredPassphrase, chain).filterKeys { it !in chain }
        if (added.isEmpty()) return false
        val sealer = LineCipher(mapOf(info.keyId to key), info.keyId)
        val older = info.older + added.mapValues { sealer.seal(it.value) }
        files.replace(PATH, encode(Info(info.keyId, info.salt, info.iterations, info.check, older)))
        return true
    }

    private fun writeNew(passphrase: CharArray, previous: Map<String, ByteArray>): LineCipher {
        val salt = secureRandomBytes(SALT_BYTES)
        val key = KeyDerivation.derive(passphrase, salt, iterations)
        val keyId = newKeyId()
        val sealer = LineCipher(mapOf(keyId to key), keyId)
        val info = Info(keyId, salt, iterations, checkValue(key), previous.filterKeys { it != keyId }.mapValues { sealer.seal(it.value) })
        files.replace(PATH, encode(info))
        return LineCipher(requireNotNull(chainOf(info, key)), keyId)
    }

    private fun checks(info: Info, key: ByteArray) = constantTimeEquals(checkValue(key), info.check)

    /** Every key [info] holds, by id, once [key] is known to be its key; null when an older entry fails to open. */
    private fun chainOf(info: Info, key: ByteArray): Map<String, ByteArray>? {
        val current = LineCipher(mapOf(info.keyId to key), info.keyId)
        val older = info.older.mapValues { (_, sealed) -> current.open(sealed) ?: return null }
        return older + (info.keyId to key)
    }

    /** Every readable retired key file, Syncthing's copies of them included. */
    private fun retired(): List<Info> =
        files.list(RETIRED).filter { it.endsWith(".json") }.mapNotNull { name -> files.read("$RETIRED/$name")?.let(::parse) }

    /**
     * The keys of the retired files [passphrase] opens. A file whose key and whole chain [known] already
     * holds costs nothing; at most [MAX_RETIRED_TRIES] others are derived, lowest id first, so planted files
     * cannot stall an unlock.
     */
    private fun retiredOpenedBy(passphrase: CharArray, known: Map<String, ByteArray>): Map<String, ByteArray> =
        retired()
            .filter { r -> r.keyId !in known || !known.keys.containsAll(r.older.keys) }
            .distinctBy { it.keyId to it.older.keys }
            .sortedBy { it.keyId }
            .take(MAX_RETIRED_TRIES)
            .mapNotNull { r ->
                val key = KeyDerivation.derive(passphrase, r.salt, r.iterations)
                if (checks(r, key)) chainOf(r, key) else null
            }
            .fold(emptyMap()) { all, keys -> all + keys }

    /** A retired file's name carries a digest of its bytes too, so two different files for one key id never overwrite each other. */
    private fun retiredPath(keyId: String, bytes: ByteArray): String =
        "$RETIRED/$keyId-" + sha256(bytes).take(4).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } + ".json"

    private fun checkValue(key: ByteArray) = hmacSha256(key, CHECK_LABEL.encodeToByteArray())

    private fun encode(info: Info): ByteArray = buildJsonObject {
        put("version", FORMAT_VERSION)
        put("keyId", info.keyId)
        put("salt", Base64.encode(info.salt))
        put("iterations", info.iterations)
        put("check", Base64.encode(info.check))
        putJsonObject("older") { info.older.forEach { (id, sealed) -> put(id, sealed) } }
    }.toString().encodeToByteArray()

    /** The file's contents, or null when anything in it is out of shape: the folder can be written by any peer. */
    private fun parse(bytes: ByteArray): Info? = runCatching {
        val o = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        if (o.getValue("version").jsonPrimitive.int != FORMAT_VERSION) return null
        val info = Info(
            keyId = o.getValue("keyId").jsonPrimitive.content,
            salt = Base64.decode(o.getValue("salt").jsonPrimitive.content),
            iterations = o.getValue("iterations").jsonPrimitive.int,
            check = Base64.decode(o.getValue("check").jsonPrimitive.content),
            older = (o["older"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content },
        )
        val valid = KEY_ID.matches(info.keyId) &&
            info.salt.size == SALT_BYTES &&
            info.check.size == CHECK_BYTES &&
            info.iterations in minIterations..MAX_FILE_ITERATIONS &&
            info.older.keys.all(KEY_ID::matches)
        if (valid) info else null
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
        const val RETIRED = "$DIR/retired"
        const val UNREADABLE = "$DIR/unreadable"

        /** Retired key files tried per unlock beyond those the chain already covers. */
        const val MAX_RETIRED_TRIES = 8

        /** A key file asking for fewer rounds than this is refused, so a planted file cannot weaken a new device's key. */
        const val MIN_FILE_ITERATIONS = 100_000

        /** A key file asking for more is refused, so a planted file cannot hang the device. */
        const val MAX_FILE_ITERATIONS = 10_000_000

        private const val FORMAT_VERSION = 1
        private const val SALT_BYTES = 16
        private const val CHECK_BYTES = 32
        private const val CHECK_LABEL = "medicall-key-check"
        private val KEY_ID = Regex("[0-9a-f]{16}")
    }
}
