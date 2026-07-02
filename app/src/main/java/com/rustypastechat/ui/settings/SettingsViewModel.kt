package com.rustypastechat.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.backup.BackupManager
import com.rustypastechat.data.backup.SftpConfig
import com.rustypastechat.data.backup.SftpUploader
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.data.model.ThemeMode
import com.rustypastechat.security.EncryptedCache
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
    val backupStatus: String? = null,
    val sftpHost: String = "",
    val sftpPort: String = "22",
    val sftpUser: String = "",
    val sftpPassword: String = "",
    val sftpPath: String = "/",
    val sftpTesting: Boolean = false,
    val sftpResult: String? = null,
    val backupFiles: List<java.io.File> = emptyList(),
    val error: OneTimeEvent<String?> = OneTimeEvent(null)
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val apiClientFactory: ApiClientFactory,
    private val encryptedCache: EncryptedCache,
    private val backupManager: BackupManager,
    private val sftpUploader: SftpUploader
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
            encryptedCache.clearAll()
            val cacheDir = context.cacheDir
            cacheDir.deleteRecursively()
            _uiState.update { it.copy(cacheSize = "0 KB") }
        }
    }

    fun fetchStats() {
        viewModelScope.launch {
            val encryptedSize = encryptedCache.getCacheSizeBytes()
            val cacheDir = context.cacheDir
            val plainCacheSize = if (cacheDir.exists()) {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
            _uiState.update { it.copy(cacheSize = formatBytes(encryptedSize + plainCacheSize)) }
        }
    }

    fun fetchBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(backupFiles = backupManager.listBackups()) }
        }
    }

    fun createBackup(chats: List<ChatThread>) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = "Creating encrypted backup...") }
            val cacheFile = java.io.File(context.cacheDir, "backup.rpbackup")
            backupManager.createBackup(chats, cacheFile)
                .onSuccess {
                    _uiState.update { it.copy(backupStatus = null) }
                    val uri = backupManager.createShareableBackup(it)
                    _shareBackupUri(uri)
                    fetchBackups()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(backupStatus = null, error = OneTimeEvent("Backup failed: ${e.message}")) }
                }
        }
    }

    fun exportToSftp(chats: List<ChatThread>) {
        val host = _uiState.value.sftpHost
        val port = _uiState.value.sftpPort.toIntOrNull() ?: 22
        val user = _uiState.value.sftpUser
        if (host.isBlank() || user.isBlank()) {
            _uiState.update { it.copy(sftpResult = "Host and username required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(sftpTesting = true, sftpResult = "Creating backup...") }
            val cacheFile = java.io.File(context.cacheDir, "sftp_backup.rpbackup")
            backupManager.createBackup(chats, cacheFile)
                .onSuccess { backupFile ->
                    val config = SftpConfig(
                        host = host, port = port, username = user,
                        password = _uiState.value.sftpPassword,
                        remotePath = _uiState.value.sftpPath.ifBlank { "/" }
                    )
                    sftpUploader.upload(backupFile, config) { progress ->
                        _uiState.update { it.copy(sftpResult = progress) }
                    }.onSuccess { path ->
                        _uiState.update { it.copy(sftpTesting = false, sftpResult = "Uploaded to $path") }
                        backupFile.delete()
                    }.onFailure { e ->
                        _uiState.update { it.copy(sftpTesting = false, sftpResult = "SFTP: ${e.message}") }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(sftpTesting = false, sftpResult = "Backup failed: ${e.message}") }
                }
        }
    }

    fun testSftpConnection() {
        val host = _uiState.value.sftpHost
        val port = _uiState.value.sftpPort.toIntOrNull() ?: 22
        val user = _uiState.value.sftpUser
        if (host.isBlank() || user.isBlank()) {
            _uiState.update { it.copy(sftpResult = "Host and username required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(sftpTesting = true, sftpResult = "Testing..." ) }
            val config = SftpConfig(
                host = host, port = port, username = user,
                password = _uiState.value.sftpPassword
            )
            sftpUploader.testConnection(config)
                .onSuccess { msg -> _uiState.update { state -> state.copy(sftpTesting = false, sftpResult = msg) } }
                .onFailure { e -> _uiState.update { state -> state.copy(sftpTesting = false, sftpResult = "SFTP error: ${e.message}") } }
        }
    }

    fun updateSftpHost(v: String) { _uiState.update { it.copy(sftpHost = v) } }
    fun updateSftpPort(v: String) {
        val digits = v.filter { c -> c.isDigit() }
        _uiState.update { it.copy(sftpPort = digits) }
    }
    fun updateSftpUser(v: String) { _uiState.update { it.copy(sftpUser = v) } }
    fun updateSftpPassword(v: String) { _uiState.update { it.copy(sftpPassword = v) } }
    fun updateSftpPath(v: String) { _uiState.update { it.copy(sftpPath = v) } }

    fun restoreBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(backupStatus = "Restoring from backup...") }
            backupManager.restoreBackupFromUri(uri)
                .onSuccess { payload ->
                    val settings = payload.settings.mergeInto(_uiState.value.settings)
                    preferencesManager.saveSettings(settings)
                    _uiState.update { it.copy(backupStatus = "Restored ${payload.chats.size} chats") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(backupStatus = null, error = OneTimeEvent("Restore failed: ${e.message}")) }
                }
        }
    }

    fun deleteBackupFile(file: java.io.File) {
        backupManager.deleteBackup(file)
        fetchBackups()
    }

    private fun _shareBackupUri(uri: android.net.Uri) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "Share backup via")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}
