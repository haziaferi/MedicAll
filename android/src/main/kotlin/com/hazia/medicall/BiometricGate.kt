package com.hazia.medicall

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The fingerprint of the app lock (SPEC:R11), through the system's own strong biometric prompt (Android 11 and
 * later, the app's minimum). It says whether the person passed: the lock decides what that opens.
 *
 * A pass counts only when the prompt hands back a working key-store cipher: the key needs a strong biometric for
 * every use, so a success callback reached any other way (a hooked or replayed callback) cannot encrypt with it.
 *
 * The key is made only by [enroll], when the person (already past the lock) switches the fingerprint on, so it
 * holds the fingerprints of that moment. Adding or removing a fingerprint on the phone invalidates it; [ask] then
 * deletes it and calls `lost`, the lock turns its fingerprint off, and only the PIN opens until the person
 * switches the fingerprint on again. A missing key is never made by [ask].
 */
object BiometricGate {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "medicall-fingerprint-v1"
    private val CHALLENGE = "medicall-fingerprint".encodeToByteArray()

    /** True when the phone has a strong biometric enrolled. */
    fun available(activity: Activity): Boolean =
        activity.getSystemService(BiometricManager::class.java)?.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Makes a new fingerprint key, bound to the fingerprints enrolled now, replacing any old one. */
    fun enroll() {
        forget()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            generateKey()
        }
    }

    /** Deletes the fingerprint key, as when the fingerprint is switched off. */
    fun forget() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    /**
     * Shows the prompt; [passed] runs on the main thread when it succeeds, [notPassed] with the reason otherwise.
     * [lost] runs (then [notPassed]) when the key is gone or the phone's fingerprints changed: the fingerprint must
     * be switched off until it is switched on again past the PIN.
     */
    fun ask(activity: Activity, title: String, cancel: String, passed: () -> Unit, notPassed: (String) -> Unit, lost: () -> Unit) {
        val cipher = cipherForPrompt(notPassed, lost) ?: return
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButton(cancel, activity.mainExecutor) { _, _ -> notPassed(cancel) }
            .build()
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher ?: return notPassed("The prompt returned no key.")
                    val works = runCatching { unlocked.doFinal(CHALLENGE) }.isSuccess
                    if (works) passed() else notPassed("The fingerprint key did not open.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = notPassed(errString.toString())
            })
    }

    /** A cipher over the existing key for the prompt to unlock, or null after [notPassed] (and [lost] when the key is gone). */
    internal fun cipherForPrompt(notPassed: (String) -> Unit, lost: () -> Unit): Cipher? = try {
        val key = existingKey()
        if (key == null) {
            lost()
            notPassed("The fingerprint is not set up here: unlock with the PIN.")
            null
        } else {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        forget()
        lost()
        notPassed("The phone's fingerprints changed: unlock with the PIN, then switch the fingerprint on again.")
        null
    } catch (e: Exception) {
        notPassed("The fingerprint key is not available: ${e.message ?: e}")
        null
    }

    internal fun hasKey(): Boolean = existingKey() != null

    private fun existingKey(): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(ALIAS, null) as? SecretKey
}
