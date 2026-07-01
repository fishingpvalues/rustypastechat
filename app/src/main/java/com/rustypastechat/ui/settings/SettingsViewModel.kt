package com.rustypastechat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

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
                val api = com.rustypastechat.data.api.ApiClientFactory.createPasteApi(
                    settings.pasteServerUrl,
                    object : com.rustypastechat.data.api.PasteAuthInterceptor.TokenProvider {
                        override fun getToken(): String? = settings.authToken.ifBlank { null }
                    }
                )
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
}
