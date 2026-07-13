package com.rustypastechat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.ImageQuality
import com.rustypastechat.data.model.ThemeMode
import com.rustypastechat.data.model.VoiceQuality
import com.rustypastechat.security.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        private val KEY_LLM_CONTEXT_WINDOW = androidx.datastore.preferences.core.intPreferencesKey("llm_context_window")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_SHOW_DATE_HEADERS = booleanPreferencesKey("date_headers")
        private val KEY_ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        private val KEY_STARRED_IDS = stringSetPreferencesKey("starred_message_ids")
        private val KEY_HIDDEN_IDS = stringSetPreferencesKey("hidden_message_ids")
        private val KEY_SUPERSEDED_FILENAMES = stringSetPreferencesKey("superseded_paste_filenames")
        private val KEY_MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")
        private val KEY_VOICE_QUALITY = stringPreferencesKey("voice_quality")
        private val KEY_IMAGE_QUALITY = stringPreferencesKey("image_quality")
        private val KEY_ENCRYPT_MEDIA_CACHE = booleanPreferencesKey("encrypt_media_cache")
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
            llmContextWindowSize = prefs[KEY_LLM_CONTEXT_WINDOW] ?: 10,
            biometricEnabled = securePrefs.biometricEnabled,
            lockTimeoutSeconds = securePrefs.lockTimeoutSeconds,
            themeMode = try { ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: "SYSTEM") } catch (_: Exception) { ThemeMode.SYSTEM },
            useDynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            showDateHeaders = prefs[KEY_SHOW_DATE_HEADERS] ?: true,
            markdownEnabled = prefs[KEY_MARKDOWN_ENABLED] ?: true,
            voiceQuality = try { VoiceQuality.valueOf(prefs[KEY_VOICE_QUALITY] ?: "STANDARD") } catch (_: Exception) { VoiceQuality.STANDARD },
            imageQuality = try { ImageQuality.valueOf(prefs[KEY_IMAGE_QUALITY] ?: "STANDARD") } catch (_: Exception) { ImageQuality.STANDARD },
            encryptMediaCache = prefs[KEY_ENCRYPT_MEDIA_CACHE] ?: true
        )
    }

    val authTokenSync: String?
        get() = securePrefs.authToken.ifBlank { null }

    val hasSeenOnboardingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_SEEN] ?: false
    }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_SEEN] = true }
    }

    suspend fun getDraft(chatId: String): String {
        return context.dataStore.data.map { prefs -> prefs[draftKey(chatId)] ?: "" }.first()
    }

    suspend fun saveDraft(chatId: String, text: String) {
        context.dataStore.edit { prefs ->
            val key = draftKey(chatId)
            if (text.isBlank()) prefs.remove(key) else prefs[key] = text
        }
    }

    private fun draftKey(chatId: String) = stringPreferencesKey("draft_$chatId")

    val starredIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_STARRED_IDS] ?: emptySet()
    }

    suspend fun saveStarredIds(ids: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_STARRED_IDS] = ids }
    }

    /** Message ids "deleted for me" — hidden from this device's reconstructed chat history
     *  without touching the remote paste file, unlike a full delete-for-everyone. */
    val hiddenIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_IDS] ?: emptySet()
    }

    suspend fun saveHiddenIds(ids: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_HIDDEN_IDS] = ids }
    }

    /** Paste filenames that have been replaced by a fresh upload (edit/reaction content
     *  update) — rustypaste rejects re-uploading an existing filename ("file already
     *  exists"), so updates upload under a new name and the old one is marked superseded
     *  here. Deletion of the old file is attempted too, but the server may not allow it
     *  (delete requires a configured delete token), so this set is the source of truth
     *  that keeps the stale copy from reappearing as a duplicate message on reconstruction. */
    val supersededFileNamesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUPERSEDED_FILENAMES] ?: emptySet()
    }

    suspend fun addSupersededFileName(fileName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUPERSEDED_FILENAMES] = (prefs[KEY_SUPERSEDED_FILENAMES] ?: emptySet()) + fileName
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PASTE_SERVER_URL] = settings.pasteServerUrl
            prefs[KEY_LLM_ENABLED] = settings.llmEnabled
            prefs[KEY_LLM_ENDPOINT] = settings.llmEndpoint
            prefs[KEY_LLM_MODEL] = settings.llmModel.ifBlank { "gpt-3.5-turbo" }
            prefs[KEY_LLM_CONTEXT_WINDOW] = settings.llmContextWindowSize
            prefs[KEY_THEME_MODE] = settings.themeMode.name
            prefs[KEY_DYNAMIC_COLOR] = settings.useDynamicColor
            prefs[KEY_SHOW_DATE_HEADERS] = settings.showDateHeaders
            prefs[KEY_MARKDOWN_ENABLED] = settings.markdownEnabled
            prefs[KEY_VOICE_QUALITY] = settings.voiceQuality.name
            prefs[KEY_IMAGE_QUALITY] = settings.imageQuality.name
            prefs[KEY_ENCRYPT_MEDIA_CACHE] = settings.encryptMediaCache
        }
        securePrefs.apply {
            authToken = settings.authToken
            llmApiKey = settings.llmApiKey
            biometricEnabled = settings.biometricEnabled
            lockTimeoutSeconds = settings.lockTimeoutSeconds
        }
    }
}
