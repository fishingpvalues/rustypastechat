package com.rustypastechat.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricLockManager(private val context: Context) {

    companion object {
        private const val BIOMETRIC_KEY_ALIAS = "rustypastechat_biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    fun isAvailable(): Boolean = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun getOrCreateBiometricCipher(): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            var secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            if (secretKey == null) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
                )
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        BIOMETRIC_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setInvalidatedByBiometricEnrollment(true)
                        .build()
                )
                secretKey = keyGenerator.generateKey()
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (_: Exception) { null }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "RustyPaste Chat",
        subtitle: String = "Unlock to access your messages",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = createCallback(onSuccess, onError)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    fun authenticateWithCrypto(
        activity: FragmentActivity,
        title: String = "RustyPaste Chat",
        subtitle: String = "Unlock to access your encrypted data",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val cipher = getOrCreateBiometricCipher()
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = createCallback(onSuccess, onError)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        if (cipher != null) {
            BiometricPrompt(activity, executor, callback).authenticate(
                promptInfo, BiometricPrompt.CryptoObject(cipher)
            )
        } else {
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        }
    }

    private fun createCallback(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
                onError(errString.toString())
            }
        }
    }
}
