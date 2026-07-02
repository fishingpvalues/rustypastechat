package com.rustypastechat.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.ThemeMode
import com.rustypastechat.ui.common.OneTimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val cacheSize: String = "0 KB",
    val pasteCount: Int = 0,
    val error: OneTimeEvent<String?> = OneTimeEvent(null)
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val application: Application,
    private val preferencesManager: PreferencesManager,
    private val apiClientFactory: ApiClientFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(settings = it.settings.copy(pasteServerUrl = url)) }
    }

    fun updateAuthToken(token: String) {
        _uiState.update { it.copy(settings = it.settings.copy(authToken = token)) }
    }

    fun updateLlmEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(llmEnabled = enabled)) }
    }

    fun updateLlmEndpoint(endpoint: String) {
        _uiState.update { it.copy(settings = it.settings.copy(llmEndpoint = endpoint)) }
    }

    fun updateLlmApiKey(key: String) {
        _uiState.update { it.copy(settings = it.settings.copy(llmApiKey = key)) }
    }

    fun updateLlmModel(model: String) {
        _uiState.update { it.copy(settings = it.settings.copy(llmModel = model)) }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(biometricEnabled = enabled)) }
    }

    fun updateLockTimeout(seconds: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(lockTimeoutSeconds = seconds)) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(settings = it.settings.copy(themeMode = mode)) }
    }

    fun updateDynamicColor(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(useDynamicColor = enabled)) }
    }

    fun updateShowDateHeaders(show: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(showDateHeaders = show)) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            preferencesManager.saveSettings(_uiState.value.settings)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun testConnection() {
        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            try {
                val settings = _uiState.value.settings
                if (settings.pasteServerUrl.isBlank()) {
                    _uiState.update { it.copy(isTesting = false, testResult = "Server URL is empty") }
                    return@launch
                }
                val api = apiClientFactory.createPasteApi(settings.pasteServerUrl)
                val response = api.listFiles()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isTesting = false, testResult = "Connected! ${response.body()?.size ?: 0} pastes on server") }
                } else {
                    _uiState.update { it.copy(isTesting = false, testResult = "Server responded: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTesting = false, testResult = "Connection error: ${e.message}") }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val cacheDir = application.cacheDir
            cacheDir.deleteRecursively()
            _uiState.update { it.copy(cacheSize = "0 KB") }
        }
    }

    fun fetchStats() {
        viewModelScope.launch {
            val cacheDir = application.cacheDir
            val cacheSize = if (cacheDir.exists()) {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
            _uiState.update { it.copy(cacheSize = formatBytes(cacheSize)) }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}
