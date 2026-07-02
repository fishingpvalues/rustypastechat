package com.rustypastechat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.security.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_PASTE_SERVER_URL = stringPreferencesKey("paste_server_url")
        private val KEY_LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        private val KEY_LLM_ENDPOINT = stringPreferencesKey("llm_endpoint")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
    }

    private val securePrefs = SecurePreferences(context)

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val secureToken = securePrefs.authToken
        val secureApiKey = securePrefs.llmApiKey
        AppSettings(
            pasteServerUrl = prefs[KEY_PASTE_SERVER_URL] ?: "",
            authToken = secureToken,
            llmEnabled = prefs[KEY_LLM_ENABLED] ?: false,
            llmEndpoint = prefs[KEY_LLM_ENDPOINT] ?: "",
            llmApiKey = secureApiKey,
            llmModel = prefs[KEY_LLM_MODEL] ?: "gpt-3.5-turbo",
            biometricEnabled = securePrefs.biometricEnabled,
            lockTimeoutSeconds = securePrefs.lockTimeoutSeconds
        )
    }

    /** Synchronous token access for OkHttp interceptors (backed by EncryptedSharedPreferences). */
    val authTokenSync: String?
        get() = securePrefs.authToken.ifBlank { null }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PASTE_SERVER_URL] = settings.pasteServerUrl
            prefs[KEY_LLM_ENABLED] = settings.llmEnabled
            prefs[KEY_LLM_ENDPOINT] = settings.llmEndpoint
            prefs[KEY_LLM_MODEL] = settings.llmModel.ifBlank { "gpt-3.5-turbo" }
        }
        securePrefs.apply {
            authToken = settings.authToken
            llmApiKey = settings.llmApiKey
            biometricEnabled = settings.biometricEnabled
            lockTimeoutSeconds = settings.lockTimeoutSeconds
        }
    }
}
