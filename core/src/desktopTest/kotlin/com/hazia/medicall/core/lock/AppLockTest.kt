package com.hazia.medicall.core.lock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The app lock of SPEC:R11, on an elapsed clock the test moves. Every PIN is invented. */
class AppLockTest {

    private var now = 1_000_000L
    private var saved: LockState = LockState.OFF
    private fun lock(state: LockState = saved) = AppLock(state, { now }, { saved = it })

    private val pin = "2468".toCharArray()
    private fun pin(s: String) = s.toCharArray()
    private val SECOND = 1_000L
    private val MINUTE = 60_000L

    @Test
    fun aPinIsFourToSixDigits_SPEC_R11() {
        for (bad in listOf("123", "1234567", "12a4", "")) {
            assertFailsWith<IllegalArgumentException>(bad) { lock().setPin(pin(bad)) }
        }
        lock().setPin(pin("2468"))
        assertTrue(lock().isOn)
        saved = LockState.OFF
        lock().setPin(pin("246813"))
        assertTrue(lock().isOn)
    }

    @Test
    fun aLockThatIsOnStartsLockedAndOpensWithItsPin_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        assertTrue(l.isLocked)
        assertIs<Verdict.Wrong>(l.unlock(pin("1357")))
        assertTrue(l.isLocked)
        assertEquals(Verdict.Ok, l.unlock(pin))
        assertFalse(l.isLocked)
    }

    @Test
    fun theLockClosesAfterTheTimeout_SPEC_R11() {
        val l = lock()
        l.setPin(pin)
        assertEquals(Verdict.Ok, l.unlock(pin))
        l.onBackground(); now += 59 * SECOND; l.onForeground()
        assertFalse(l.isLocked)
        l.onBackground(); now += 60 * SECOND; l.onForeground()
        assertTrue(l.isLocked)

        assertEquals(Verdict.Ok, l.unlock(pin))
        l.setTimeout(0)
        l.onBackground(); l.onForeground()
        assertTrue(l.isLocked)
        assertFailsWith<IllegalArgumentException> { l.setTimeout(2) }
        assertEquals(0, saved.timeoutMinutes)
    }

    @Test
    fun aClockBelowTheBackgroundMomentLocks_SPEC_R11() {
        val l = lock()
        l.setPin(pin)
        l.onBackground(); now -= 5 * SECOND; l.onForeground() // a reboot: the elapsed clock started again
        assertTrue(l.isLocked)
    }

    @Test
    fun aCheckThatThrowsCountsAsAWrongTry_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        assertFailsWith<IllegalStateException> { l.removeForgottenPinIf { error("the folder cannot be read") } }
        assertEquals(1, saved.wrongTries)
        assertEquals(Verdict.Wrong(AppLock.FREE_TRIES - 2), l.unlock(pin("1357")))
    }

    @Test
    fun aSlowTryDoesNotHoldUpTheLocksClock_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        val checking = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val tryThread = Thread { l.removeForgottenPinIf { checking.countDown(); release.await(); false } }.apply { start() }
        assertTrue(checking.await(5, java.util.concurrent.TimeUnit.SECONDS))
        // The try is mid-check: the app going to the background and back must not wait for it.
        val clock = Thread { l.onBackground(); l.onForeground() }.apply { start() }
        clock.join(2_000)
        val clockWaited = clock.isAlive
        release.countDown()
        tryThread.join(5_000)
        clock.join(5_000)
        assertFalse(clockWaited)
        assertEquals(1, saved.wrongTries)
    }

    @Test
    fun aSaveThatFailsChangesNothing_SPEC_R11() {
        lock().setPin(pin)
        var failing = true
        val l = AppLock(saved, { now }, { if (failing) throw java.io.IOException("invented full disk") else saved = it })
        assertFailsWith<java.io.IOException> { l.setTimeout(15) }
        assertFailsWith<java.io.IOException> { l.setFingerprint(true) }
        // What the lock holds still matches what is kept: the next save writes the right state.
        failing = false
        l.setTimeout(5)
        assertEquals(5, saved.timeoutMinutes)
        assertFalse(saved.fingerprint)
        assertTrue(l.isOn)
    }

    @Test
    fun wrongPinsAtOnceAreEachCounted_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        val verdicts = java.util.Collections.synchronizedList(mutableListOf<Verdict>())
        val threads = List(6) { Thread { verdicts += l.unlock(pin("1357")) } }
        threads.forEach { it.start() }
        threads.forEach { it.join(30_000) }
        assertEquals(
            listOf(Verdict.Wrong(4), Verdict.Wrong(3), Verdict.Wrong(2), Verdict.Wrong(1), Verdict.Wrong(0), Verdict.Wait(30 * SECOND)).toSet(),
            verdicts.toSet(),
        )
        assertEquals(6, verdicts.size)
        // The sixth came during the wait: refused unchecked, not counted.
        assertEquals(5, saved.wrongTries)
        assertTrue(l.isLocked)
    }

    @Test
    fun anUnlockedLockWithNoPinNeverCloses_SPEC_R11() {
        val l = lock()
        l.onBackground(); now += 60 * MINUTE; l.onForeground()
        assertFalse(l.isOn)
        assertFalse(l.isLocked)
    }

    @Test
    fun wrongTriesWaitLonger_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        repeat(4) { assertEquals(Verdict.Wrong(4 - it), l.unlock(pin("1357"))) }
        assertEquals(Verdict.Wrong(0), l.unlock(pin("1357")))
        assertEquals(Verdict.Wait(30 * SECOND), l.unlock(pin))
        now += 10 * SECOND
        assertEquals(Verdict.Wait(20 * SECOND), l.unlock(pin))
        assertTrue(l.isLocked)
        now += 20 * SECOND
        assertEquals(Verdict.Wait(60 * SECOND), l.unlock(pin("1357")))
        // Doubling, capped at one hour.
        var expected = 60 * SECOND
        repeat(10) {
            now += expected
            expected = minOf(expected * 2, 60 * MINUTE)
            assertEquals(Verdict.Wait(expected), l.unlock(pin("1357")))
        }
        now += expected
        assertEquals(Verdict.Ok, l.unlock(pin))
        assertEquals(0, saved.wrongTries)
        l.onBackground(); now += 2 * MINUTE; l.onForeground()
        assertEquals(Verdict.Wrong(4), l.unlock(pin("1357")))
    }

    @Test
    fun triesSurviveARestart_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        repeat(5) { l.unlock(pin("1357")) }
        now += 5 * SECOND
        val again = lock()
        assertTrue(again.isLocked)
        assertEquals(Verdict.Wait(25 * SECOND), again.unlock(pin))
    }

    @Test
    fun aClockSetBackDoesNotEndTheWait_SPEC_R11() {
        lock().setPin(pin)
        repeat(5) { lock().unlock(pin("1357")) }
        // A reboot: elapsed time starts again below the last wrong try.
        now = 2_000L
        val l = lock()
        assertEquals(Verdict.Wait(30 * SECOND), l.unlock(pin))
        now += 30 * SECOND
        assertEquals(Verdict.Ok, l.unlock(pin))
    }

    @Test
    fun stateHoldsNoPin_SPEC_R11() {
        val secret = pin("975318")
        lock().setPin(secret)
        val bytes = saved.encode()
        assertFalse("975318" in bytes.decodeToString())
        assertFalse(bytes.toList().windowed(6).any { it == "975318".encodeToByteArray().toList() })
        val back = LockState.decode(bytes)
        assertEquals(saved, back)
        assertEquals(Verdict.Ok, lock(back).unlock(pin("975318")))
    }

    @Test
    fun anUnreadableStateKeepsTheLockClosed_SPEC_R11() {
        for (bytes in listOf("not a lock".encodeToByteArray(), ByteArray(0), """{"v":2}""".encodeToByteArray())) {
            val state = LockState.decode(bytes)
            assertEquals(LockState.UNREADABLE, state)
            val l = lock(state)
            assertTrue(l.isOn)
            assertTrue(l.isLocked)
            assertIs<Verdict.Wrong>(l.unlock(pin))
            assertTrue(l.isLocked)
            // Only a folder's passphrase opens it.
            assertEquals(Verdict.Ok, l.removeForgottenPinIf { true })
            assertFalse(l.isOn)
        }
    }

    @Test
    fun anUnknownTimeoutSnapsToTheNearestShorter_SPEC_R11() {
        lock().setPin(pin)
        fun withTimeout(m: Int) = LockState.decode(saved.encode().decodeToString().replace(""""timeout":1""", """"timeout":$m""").encodeToByteArray())
        assertEquals(5, withTimeout(7).timeoutMinutes)
        assertEquals(5, withTimeout(10).timeoutMinutes)
        assertEquals(15, withTimeout(30).timeoutMinutes)
        assertEquals(0, withTimeout(-3).timeoutMinutes)
        assertEquals(saved.pinHash, withTimeout(10).pinHash)
    }

    @Test
    fun fingerprintUnlocksOnlyWhenOn_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        assertFailsWith<IllegalStateException> { l.unlockedByFingerprint() }
        assertTrue(l.isLocked)
        assertEquals(Verdict.Ok, l.unlock(pin))
        l.setFingerprint(true)
        l.onBackground(); now += 2 * MINUTE; l.onForeground()
        assertTrue(l.isLocked)
        l.unlockedByFingerprint()
        assertFalse(l.isLocked)
        assertFailsWith<IllegalStateException> { lock(LockState.OFF).setFingerprint(true) }
    }

    @Test
    fun changeAndTurnOffNeedTheOldPin_SPEC_R11() {
        val l = lock()
        l.setPin(pin)
        assertFailsWith<IllegalStateException> { l.setPin(pin("1111")) }
        assertEquals(Verdict.Wrong(4), l.changePin(pin("1357"), pin("1111")))
        assertEquals(Verdict.Ok, l.changePin(pin, pin("1111")))
        assertEquals(Verdict.Ok, lock().unlock(pin("1111")))
        assertEquals(Verdict.Wrong(4), l.turnOff(pin))
        assertEquals(Verdict.Ok, l.turnOff(pin("1111")))
        assertFalse(lock().isOn)
    }

    @Test
    fun aPassphraseTryCountsLikeAPin_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        var asked = 0
        assertEquals(Verdict.Wrong(4), l.removeForgottenPinIf { asked++; false })
        assertTrue(l.isOn)
        repeat(4) { l.removeForgottenPinIf { asked++; false } }
        // During the wait the passphrase is not even checked.
        assertEquals(Verdict.Wait(30 * SECOND), l.removeForgottenPinIf { asked++; true })
        assertEquals(5, asked)
        now += 30 * SECOND
        assertEquals(Verdict.Ok, l.removeForgottenPinIf { true })
        assertFalse(l.isOn)
        assertFalse(l.isLocked)
        assertEquals(0, saved.wrongTries)
    }

    @Test
    fun aForgottenPinIsRemoved_SPEC_R11() {
        lock().setPin(pin)
        val l = lock()
        repeat(5) { l.unlock(pin("1357")) }
        l.removeForgottenPin()
        assertFalse(l.isOn)
        assertFalse(l.isLocked)
        assertEquals(LockState.OFF.copy(timeoutMinutes = saved.timeoutMinutes), saved)
    }
}
