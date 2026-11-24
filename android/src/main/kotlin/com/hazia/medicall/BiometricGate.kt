package com.hazia.medicall

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * The fingerprint of the app lock (SPEC:R11), through the system's own strong biometric prompt (Android 11 and
 * later, the app's minimum). It only says whether the person passed: the lock decides what that opens.
 */
object BiometricGate {
    /** True when the phone has a strong biometric enrolled. */
    fun available(activity: Activity): Boolean =
        activity.getSystemService(BiometricManager::class.java)?.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the prompt; [passed] runs on the main thread when it succeeds, [notPassed] with the reason otherwise. */
    fun ask(activity: Activity, title: String, cancel: String, passed: () -> Unit, notPassed: (String) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButton(cancel, activity.mainExecutor) { _, _ -> notPassed(cancel) }
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = passed()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = notPassed(errString.toString())
        })
    }
}
