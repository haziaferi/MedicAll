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
    /**
     * The passphrase opens the folder's key. [unreadableKeyIds] names the older keys in its chain that did
     * not open (an entry altered or cut short): lines written under them stay unreadable until a copy of
     * that key turns up, but everything else works.
     */
    class Unlocked(val cipher: LineCipher, val unreadableKeyIds: List<String> = emptyList()) : Unlock
    data object WrongPassphrase : Unlock
    data object NoKeyFile : Unlock

    /** The key file itself cannot be read: it was altered, cut short or written by another format, so no passphrase opens it. */
    data object Damaged : Unlock

    /**
     * The folder has no key file yet, but another device already writes to it: its key file has not
     * arrived. No key is made, so the device does not start a key of its own; wait for the sync and try
     * again. [KeyFolder.unlock] never returns it: the caller that knows the device's id does.
     */
    data object KeyFileNotArrived : Unlock
}

/**
 * The key file every syncing device reads (SPEC:R109): `keys/keyinfo.json`, holding the current key's
 * id, salt and iteration count, a check value that tells a wrong passphrase from a right one, and every
 * older key sealed under the current one, so lines written before a passphrase change still open.
 * It holds no key in the clear: without the passphrase it opens nothing.
 *
 * A key is never thrown away. When key files clash, the one with the lowest key id becomes the folder's
 * key, and every other variant is kept in `keys/retired/<keyId>-<digest>.json` (SPEC:R16, conflict copies
 * are merged, then deleted); a file this format cannot read is kept in `keys/unreadable/`. A retired key
 * made with the same passphrase opens with the next [unlock], in memory, and is written into the chain at
 * the next [change] or [fold]; one made with another passphrase waits until [fold] is given that passphrase.
 *
 * Trust (as for sync, SPEC:R16, the folder belongs to the person's own devices): anyone who can write the
 * sync folder can plant files here. They cannot read anything. Planted retired files can slow an unlock,
 * which derives at most [MAX_RETIRED_TRIES] of them, and slow [fold], which tries every one. A planted key
 * file with a lower key id wins [settleConflicts], so every device then gets [Unlock.WrongPassphrase]: the
 * real key file is kept in `keys/retired/`, but no call restores it yet. This is an accepted limit.
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
        val (chain, unreadable) = chainOf(info, key)
        val all = retiredOpenedBy(passphrase, chain, MAX_RETIRED_TRIES) + chain
        return Unlock.Unlocked(LineCipher(all, info.keyId), unreadable.filter { it !in all }.sorted())
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
            Unlock.NoKeyFile, Unlock.KeyFileNotArrived -> throw IllegalStateException("No key file")
            Unlock.Damaged -> throw IllegalStateException("The key file is damaged")
        }
        return writeNew(new, previous = current.knownKeys())
    }

    /**
     * Two devices that started offline each made a key file (or changed the passphrase apart), and Syncthing
     * renamed one of them `keyinfo.sync-conflict-*.json`. The file with the lowest key id stays the folder's
     * key. Copies with that same id and the same salt, rounds and check value add the chain entries the
     * folder's file lacks (each entry is sealed under that one key, so no passphrase is needed); where both
     * hold an entry, the folder's file wins. Every variant that differs from the result is kept in
     * `keys/retired/`, and a file this format cannot read in `keys/unreadable/`, before any copy is deleted.
     * True when the file this device had read lost: it must then unlock again.
     */
    fun settleConflicts(): Boolean {
        val copies = files.list(DIR).filter { it.startsWith("keyinfo") && ".sync-conflict-" in it }
        if (copies.isEmpty()) return false
        val main = files.read(PATH)
        val all = listOfNotNull(main?.let { "keyinfo.json" to it }) + copies.mapNotNull { name -> files.read("$DIR/$name")?.let { name to it } }
        val readable = all.mapNotNull { (_, bytes) -> parse(bytes)?.let { it to bytes } } // the folder's own file first
        val winnerId = readable.minOfOrNull { it.first.keyId } ?: return false // nothing readable: leave every file as it is
        all.filter { parse(it.second) == null }.forEach { (name, bytes) -> files.replace("$UNREADABLE/$name-${digest(bytes)}", bytes) }
        val base = readable.first { it.first.keyId == winnerId }.first
        val mergeable = readable.map { it.first }.filter { it.keyId == winnerId && it.salt.contentEquals(base.salt) && it.iterations == base.iterations && it.check.contentEquals(base.check) }
        val older = mergeable.fold(emptyMap<String, String>()) { o, i -> o + i.older.filterKeys { it !in o } }
        val merged = encode(Info(base.keyId, base.salt, base.iterations, base.check, older))
        readable.filter { !it.second.contentEquals(merged) }.forEach { (info, bytes) -> files.replace(retiredPath(info.keyId, bytes), bytes) }
        val lost = main == null || parse(main)?.keyId != winnerId
        files.replace(PATH, merged)
        copies.forEach { files.delete("$DIR/$it") }
        return lost
    }

    /** The id of the folder's key, or null when there is no key file or it does not parse. Needs no passphrase. */
    fun currentKeyId(): String? = files.read(PATH)?.let(::parse)?.keyId

    /** The ids of the readable retired key files, as [settleConflicts] keeps them. Needs no passphrase. */
    fun retiredKeyIds(): Set<String> = retired().map { it.keyId }.toSet()

    /** The ids of retired keys that [passphrase]'s chain does not yet cover: what still waits for [fold]. Never throws on a bad file. */
    fun unfoldedKeyIds(passphrase: CharArray): List<String> {
        val info = files.read(PATH)?.let(::parse) ?: return emptyList()
        val key = KeyDerivation.derive(passphrase, info.salt, info.iterations)
        if (!checks(info, key)) return emptyList()
        val chain = chainOf(info, key).first
        val known = chain + retiredOpenedBy(passphrase, chain, Int.MAX_VALUE)
        return retired().filter { !covered(it, known) }.map { it.keyId }.distinct().sorted()
    }

    /** Key files kept aside because this format cannot read them, and retired files that no longer parse: keys that may be missing. */
    fun unreadableKeyFiles(): List<String> =
        files.list(UNREADABLE).map { "$UNREADABLE/$it" } +
            files.list(RETIRED).filter { name -> files.read("$RETIRED/$name")?.let(::parse) == null }.map { "$RETIRED/$it" }

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
        val chain = chainOf(info, key).first
        val added = retiredOpenedBy(retiredPassphrase, chain, Int.MAX_VALUE).filterKeys { it !in chain }
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
        return LineCipher(chainOf(info, key).first, keyId)
    }

    private fun checks(info: Info, key: ByteArray) = constantTimeEquals(checkValue(key), info.check)

    /** Every key [info] holds that opens, by id, once [key] is known to be its key, and the ids of older entries that did not open. */
    private fun chainOf(info: Info, key: ByteArray): Pair<Map<String, ByteArray>, List<String>> {
        val current = LineCipher(mapOf(info.keyId to key), info.keyId)
        val opened = info.older.mapValues { (_, sealed) -> current.open(sealed) }
        val older = opened.filterValues { it != null }.mapValues { it.value!! }
        return (older + (info.keyId to key)) to opened.filterValues { it == null }.keys.toList()
    }

    /** Every readable retired key file, Syncthing's copies of them included. */
    private fun retired(): List<Info> =
        files.list(RETIRED).filter { it.endsWith(".json") }.mapNotNull { name -> files.read("$RETIRED/$name")?.let(::parse) }

    /** True when [known] already holds [r]'s key and every key in its chain, so opening [r] would add nothing. */
    private fun covered(r: Info, known: Map<String, ByteArray>) = r.keyId in known && known.keys.containsAll(r.older.keys)

    /**
     * The keys of the retired files [passphrase] opens. A file [known] already covers costs nothing; at
     * most [limit] others are derived, lowest id first.
     */
    private fun retiredOpenedBy(passphrase: CharArray, known: Map<String, ByteArray>, limit: Int): Map<String, ByteArray> =
        retired()
            .filter { !covered(it, known) }
            .distinctBy { Triple(it.keyId, it.older.keys, digest(it.salt)) }
            .sortedBy { it.keyId }
            .take(limit)
            .mapNotNull { r ->
                val key = KeyDerivation.derive(passphrase, r.salt, r.iterations)
                if (checks(r, key)) chainOf(r, key).first else null
            }
            .fold(emptyMap()) { all, keys -> all + keys }

    /** A retired file's name carries a digest of its bytes too, so two different files for one key id never overwrite each other. */
    private fun retiredPath(keyId: String, bytes: ByteArray): String = "$RETIRED/$keyId-${digest(bytes)}.json"

    private fun digest(bytes: ByteArray) = sha256(bytes).take(4).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

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
