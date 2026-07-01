package com.rustypastechat.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences

class SecurePreferences(context: Context) {

    private val prefs: EncryptedSharedPreferences = VaultCrypto.getOrCreateSecurePreferences(context)

    object Keys {
        const val BIOMETRIC_ENABLED = "biometric_enabled"
        const val LOCK_TIMEOUT_SECONDS = "lock_timeout_seconds"
        const val AUTH_TOKEN = "auth_token"
        const val LLM_API_KEY = "llm_api_key"
        const val PASTE_SERVER_URL = "paste_server_url"
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(Keys.BIOMETRIC_ENABLED, false)
        set(v) = prefs.edit().putBoolean(Keys.BIOMETRIC_ENABLED, v).apply()

    var lockTimeoutSeconds: Int
        get() = prefs.getInt(Keys.LOCK_TIMEOUT_SECONDS, 30)
        set(v) = prefs.edit().putInt(Keys.LOCK_TIMEOUT_SECONDS, v).apply()

    var authToken: String
        get() = prefs.getString(Keys.AUTH_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(Keys.AUTH_TOKEN, v).apply()

    var llmApiKey: String
        get() = prefs.getString(Keys.LLM_API_KEY, "") ?: ""
        set(v) = prefs.edit().putString(Keys.LLM_API_KEY, v).apply()

    var pasteServerUrl: String
        get() = prefs.getString(Keys.PASTE_SERVER_URL, "") ?: ""
        set(v) = prefs.edit().putString(Keys.PASTE_SERVER_URL, v).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
