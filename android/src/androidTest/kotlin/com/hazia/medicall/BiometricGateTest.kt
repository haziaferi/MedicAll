package com.hazia.medicall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fingerprint key of the app lock (SPEC:R11). Only the paths that need no finger are tested here; the prompt
 * itself is walked on the phone. A key exists only after [BiometricGate.enroll], which this test never calls.
 */
@RunWith(AndroidJUnit4::class)
class BiometricGateTest {

    @Test
    fun askingWithNoKeyNeverMakesOne_SPEC_R11() {
        BiometricGate.forget()
        var lost = false
        var reason: String? = null
        assertNull(BiometricGate.cipherForPrompt(notPassed = { reason = it }, lost = { lost = true }))
        assertTrue("the lock must turn its fingerprint off", lost)
        assertTrue(reason != null)
        // A new fingerprint enrolled on the phone must not get a key by asking twice.
        assertNull(BiometricGate.cipherForPrompt(notPassed = {}, lost = {}))
        assertFalse(BiometricGate.hasKey())
    }
}
