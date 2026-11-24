package com.hazia.medicall.core.lock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * What the app lock keeps between starts (SPEC:R11), on this device only: never in a sync folder or a backup.
 * [pinHash] and [salt] are hex, null while no PIN is set; the PIN itself is never kept. [wrongTries] counts wrong
 * PINs since the last right one, and [lastWrongAtElapsed] is when the last was typed, on the elapsed clock
 * [AppLock] is given, so a wait outlives a restart of the app.
 */
data class LockState(
    val pinHash: String?,
    val salt: String?,
    val timeoutMinutes: Int,
    val fingerprint: Boolean,
    val wrongTries: Int,
    val lastWrongAtElapsed: Long?,
) {
    /** The state as bytes for the device's sealed settings file. */
    fun encode(): ByteArray = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("pinHash", pinHash?.let(::JsonPrimitive) ?: JsonNull)
        put("salt", salt?.let(::JsonPrimitive) ?: JsonNull)
        put("timeout", JsonPrimitive(timeoutMinutes))
        put("fingerprint", JsonPrimitive(fingerprint))
        put("tries", JsonPrimitive(wrongTries))
        put("lastWrong", lastWrongAtElapsed?.let(::JsonPrimitive) ?: JsonNull)
    }.toString().encodeToByteArray()

    companion object {
        /** No PIN: the lock is off. */
        val OFF = LockState(null, null, AppLock.DEFAULT_TIMEOUT, fingerprint = false, wrongTries = 0, lastWrongAtElapsed = null)

        /**
         * A kept state that opened but cannot be read (damaged, or written by a newer version): the lock stays closed,
         * since it may have been on. Its hash matches no PIN, so only a sync folder's passphrase opens it
         * ([AppLock.removeForgottenPinIf]). Only a file that is missing, or whose key the key store lost, means [OFF].
         */
        val UNREADABLE = LockState("00", "00", AppLock.DEFAULT_TIMEOUT, fingerprint = false, wrongTries = 0, lastWrongAtElapsed = null)

        /**
         * [bytes] as [encode] wrote them; anything else is [UNREADABLE]. A timeout that is not one of [AppLock.TIMEOUTS]
         * (the choices changed since it was kept) takes the nearest shorter choice, so the lock never stays open longer.
         */
        fun decode(bytes: ByteArray): LockState = try {
            val o = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            require(o.getValue("v").jsonPrimitive.int == 1)
            fun text(key: String) = o.getValue(key).jsonPrimitive.takeUnless { it is JsonNull }?.content
            LockState(
                pinHash = text("pinHash"),
                salt = text("salt"),
                timeoutMinutes = nearestShorter(o.getValue("timeout").jsonPrimitive.int),
                fingerprint = o.getValue("fingerprint").jsonPrimitive.boolean,
                wrongTries = o.getValue("tries").jsonPrimitive.int.also { require(it >= 0) },
                lastWrongAtElapsed = o.getValue("lastWrong").jsonPrimitive.longOrNull,
            ).also { require((it.pinHash == null) == (it.salt == null)) }
        } catch (_: Exception) {
            UNREADABLE
        }

        private fun nearestShorter(minutes: Int): Int = AppLock.TIMEOUTS.lastOrNull { it <= minutes } ?: AppLock.TIMEOUTS.first()
    }
}
