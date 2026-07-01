package com.rustypastechat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rustypastechat.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_PASTE_SERVER_URL = stringPreferencesKey("paste_server_url")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        private val KEY_LLM_ENDPOINT = stringPreferencesKey("llm_endpoint")
        private val KEY_LLM_API_KEY = stringPreferencesKey("llm_api_key")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            pasteServerUrl = prefs[KEY_PASTE_SERVER_URL] ?: "",
            authToken = prefs[KEY_AUTH_TOKEN] ?: "",
            llmEnabled = prefs[KEY_LLM_ENABLED] ?: false,
            llmEndpoint = prefs[KEY_LLM_ENDPOINT] ?: "",
            llmApiKey = prefs[KEY_LLM_API_KEY] ?: "",
            llmModel = prefs[KEY_LLM_MODEL] ?: "gpt-3.5-turbo"
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PASTE_SERVER_URL] = settings.pasteServerUrl
            prefs[KEY_AUTH_TOKEN] = settings.authToken
            prefs[KEY_LLM_ENABLED] = settings.llmEnabled
            prefs[KEY_LLM_ENDPOINT] = settings.llmEndpoint
            prefs[KEY_LLM_API_KEY] = settings.llmApiKey
            prefs[KEY_LLM_MODEL] = settings.llmModel.ifBlank { "gpt-3.5-turbo" }
        }
    }

    fun getCurrentToken(): String? {
        var token: String? = null
        kotlinx.coroutines.runBlocking {
            context.dataStore.data.first().let { prefs ->
                token = prefs[KEY_AUTH_TOKEN]
            }
        }
        return token
    }
}
