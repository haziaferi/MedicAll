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
 * Adding or removing a fingerprint on the phone invalidates the key; the next try asks for the PIN and makes a
 * new key.
 */
object BiometricGate {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "medicall-fingerprint-v1"
    private val CHALLENGE = "medicall-fingerprint".encodeToByteArray()

    /** True when the phone has a strong biometric enrolled. */
    fun available(activity: Activity): Boolean =
        activity.getSystemService(BiometricManager::class.java)?.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the prompt; [passed] runs on the main thread when it succeeds, [notPassed] with the reason otherwise. */
    fun ask(activity: Activity, title: String, cancel: String, passed: () -> Unit, notPassed: (String) -> Unit) {
        val cipher = try {
            cipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            forgetKey()
            return notPassed("The phone's fingerprints changed: unlock with the PIN.")
        } catch (e: Exception) {
            return notPassed("The fingerprint key is not available: ${e.message ?: e}")
        }
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

    /** A cipher over the fingerprint key, ready for the prompt to unlock. */
    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
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

    private fun forgetKey() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }
}
