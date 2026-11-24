package com.hazia.medicall.core.lock

import com.hazia.medicall.core.crypto.pbkdf2Sha256
import com.hazia.medicall.core.crypto.secureRandomBytes
import kotlin.concurrent.Volatile
import kotlin.jvm.Synchronized

/** What a PIN attempt gave. */
sealed interface Verdict {
    data object Ok : Verdict

    /** A wrong PIN; [triesLeft] more before a wait starts. */
    data class Wrong(val triesLeft: Int) : Verdict

    /** Too many wrong PINs: nothing is checked for [millis] more. */
    data class Wait(val millis: Long) : Verdict
}

/**
 * The optional app lock of SPEC:R11: a PIN of 4 to 6 digits, with an optional fingerprint, closing after
 * [LockState.timeoutMinutes] in the background. It guards the screens only and encrypts nothing.
 *
 * Times are read from [elapsedMillis], a clock that only moves forward while the device runs (Android's
 * `SystemClock.elapsedRealtime`), so changing the wall clock neither ends nor stretches a wait. After a
 * reboot that clock starts again below the last wrong try: the wait then starts again in full, never ends early.
 * Every change of state goes to [save] at once, so a wait survives the app being killed. Calls are serialised,
 * so two PIN tries at once cannot both pass the wait check or lose a count.
 */
class AppLock(
    state: LockState,
    private val elapsedMillis: () -> Long,
    private val save: (LockState) -> Unit,
) {
    private var state = state

    val isOn: Boolean get() = state.pinHash != null

    /** True while the screens must ask for the PIN; a lock that is on starts locked. Read from any thread. */
    @Volatile
    var isLocked: Boolean = isOn
        private set

    private var backgroundAt: Long? = null

    /** Turns the lock on with [pin]; refused while a PIN is set (use [changePin]). The caller clears [pin]. */
    @Synchronized fun setPin(pin: CharArray) {
        check(!isOn) { "a PIN is already set: change it instead" }
        update(withPin(state, pin))
        isLocked = false
    }

    /** Replaces the PIN when [old] is right; a wrong [old] counts as a wrong try. */
    @Synchronized fun changePin(old: CharArray, new: CharArray): Verdict {
        checkDigits(new)
        return attempt(old).also { if (it == Verdict.Ok) update(withPin(state, new)) }
    }

    /** Turns the lock off when [pin] is right, keeping the timeout chosen. */
    @Synchronized fun turnOff(pin: CharArray): Verdict = attempt(pin).also { if (it == Verdict.Ok) removeForgottenPin() }

    /** Opens the lock with [pin]. */
    @Synchronized fun unlock(pin: CharArray): Verdict {
        if (!isOn) return Verdict.Ok
        return attempt(pin).also { if (it == Verdict.Ok) isLocked = false }
    }

    /** The system's strong biometric prompt succeeded; refused unless the lock and its fingerprint are on. */
    @Synchronized fun unlockedByFingerprint() {
        check(isOn && state.fingerprint) { "the fingerprint is not on" }
        update(state.copy(wrongTries = 0, lastWrongAtElapsed = null))
        isLocked = false
    }

    /**
     * Removes the PIN with no check: the caller has just verified the passphrase of a joined sync folder
     * (SPEC:R11, a forgotten PIN). The timeout chosen is kept.
     */
    @Synchronized internal fun removeForgottenPin() {
        update(LockState.OFF.copy(timeoutMinutes = state.timeoutMinutes))
        isLocked = false
    }

    @Synchronized fun onBackground() {
        backgroundAt = elapsedMillis()
    }

    /** Locks when the app was in the background for the timeout or longer (any time at all for a timeout of 0). */
    @Synchronized fun onForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        val now = elapsedMillis()
        if (isOn && (now < since || now - since >= state.timeoutMinutes * MINUTE)) isLocked = true
    }

    /** One of [TIMEOUTS], in minutes. */
    @Synchronized fun setTimeout(minutes: Int) {
        require(minutes in TIMEOUTS) { "the timeout is one of $TIMEOUTS minutes" }
        update(state.copy(timeoutMinutes = minutes))
    }

    @Synchronized fun setFingerprint(on: Boolean) {
        check(!on || isOn) { "set a PIN before the fingerprint" }
        update(state.copy(fingerprint = on))
    }

    /**
     * A forgotten PIN (SPEC:R11): [passphraseOpens] says whether the passphrase typed opens a joined sync folder.
     * It is asked only outside a wait, and a wrong passphrase counts as a wrong PIN, so neither route can be tried
     * faster than the other. When it opens, the PIN is removed as by [removeForgottenPin].
     */
    @Synchronized fun removeForgottenPinIf(passphraseOpens: () -> Boolean): Verdict =
        attempt(passphraseOpens).also { if (it == Verdict.Ok) removeForgottenPin() }

    /** Checks [pin] against the kept hash, counting a wrong one; nothing is checked during a wait. */
    private fun attempt(pin: CharArray): Verdict =
        attempt { hash(pin, state.salt!!.hexBytes()).constantTimeEquals(state.pinHash!!.hexBytes()) }

    private fun attempt(right: () -> Boolean): Verdict {
        check(isOn) { "no PIN is set" }
        val left = remainingWait()
        if (left > 0) return Verdict.Wait(left)
        if (right()) {
            update(state.copy(wrongTries = 0, lastWrongAtElapsed = null))
            return Verdict.Ok
        }
        val tries = state.wrongTries + 1
        update(state.copy(wrongTries = tries, lastWrongAtElapsed = elapsedMillis()))
        return if (tries <= FREE_TRIES) Verdict.Wrong(FREE_TRIES - tries) else Verdict.Wait(waitAfter(tries))
    }

    /** Milliseconds left of the wait the wrong tries so far impose; a clock now below the last wrong try (a reboot) starts it again. */
    private fun remainingWait(): Long {
        val last = state.lastWrongAtElapsed ?: return 0
        if (state.wrongTries < FREE_TRIES) return 0
        val now = elapsedMillis()
        if (now < last) {
            update(state.copy(lastWrongAtElapsed = now))
            return waitAfter(state.wrongTries)
        }
        return (last + waitAfter(state.wrongTries) - now).coerceAtLeast(0)
    }

    private fun update(new: LockState) {
        state = new
        save(new)
    }

    companion object {
        val PIN_DIGITS = 4..6
        val TIMEOUTS = listOf(0, 1, 5, 15)
        const val DEFAULT_TIMEOUT = 1
        const val FREE_TRIES = 5
        const val FIRST_WAIT = 30_000L
        const val MAX_WAIT = 3_600_000L
        private const val MINUTE = 60_000L
        private const val ITERATIONS = 100_000

        /** The wait after the [tries]-th wrong PIN: none before [FREE_TRIES], then [FIRST_WAIT] doubling, at most [MAX_WAIT]. */
        internal fun waitAfter(tries: Int): Long {
            if (tries < FREE_TRIES) return 0
            var wait = FIRST_WAIT
            repeat(tries - FREE_TRIES) { wait = minOf(wait * 2, MAX_WAIT) }
            return wait
        }

        private fun checkDigits(pin: CharArray) =
            require(pin.size in PIN_DIGITS && pin.all { it in '0'..'9' }) { "a PIN is ${PIN_DIGITS.first} to ${PIN_DIGITS.last} digits" }

        private fun withPin(state: LockState, pin: CharArray): LockState {
            checkDigits(pin)
            val salt = secureRandomBytes(16)
            return state.copy(pinHash = hash(pin, salt).hex(), salt = salt.hex(), wrongTries = 0, lastWrongAtElapsed = null)
        }

        private fun hash(pin: CharArray, salt: ByteArray) = pbkdf2Sha256(pin, salt, ITERATIONS, 32)

        private fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
            if (size != other.size) return false
            var diff = 0
            for (i in indices) diff = diff or (this[i].toInt() xor other[i].toInt())
            return diff == 0
        }

        private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        private fun String.hexBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
