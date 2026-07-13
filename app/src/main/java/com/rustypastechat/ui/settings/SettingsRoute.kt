package com.rustypastechat.ui.settings

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rustypastechat.data.model.ChatThread
import java.io.File

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onUpdateServerUrl = viewModel::updateServerUrl,
        onUpdateAuthToken = viewModel::updateAuthToken,
        onUpdateLlmEnabled = viewModel::updateLlmEnabled,
        onUpdateLlmEndpoint = viewModel::updateLlmEndpoint,
        onUpdateLlmApiKey = viewModel::updateLlmApiKey,
        onUpdateLlmModel = viewModel::updateLlmModel,
        onUpdateLlmContextWindow = viewModel::updateLlmContextWindow,
        onUpdateBiometric = viewModel::updateBiometricEnabled,
        onUpdateLockTimeout = viewModel::updateLockTimeout,
        onUpdateThemeMode = viewModel::updateThemeMode,
        onUpdateDynamicColor = viewModel::updateDynamicColor,
        onUpdateShowDateHeaders = viewModel::updateShowDateHeaders,
        onUpdateMarkdownEnabled = viewModel::updateMarkdownEnabled,
        onUpdateVoiceQuality = viewModel::updateVoiceQuality,
        onUpdateImageQuality = viewModel::updateImageQuality,
        onUpdateEncryptMediaCache = viewModel::updateEncryptMediaCache,
        onSave = viewModel::saveSettings,
        onTestConnection = viewModel::testConnection,
        onClearCache = viewModel::clearCache,
        onFetchStats = viewModel::fetchStats,
        onFetchBackups = viewModel::fetchBackups,
        onCreateBackup = { chats -> viewModel.createBackup(chats as List<ChatThread>) },
        onExportSftp = { chats -> viewModel.exportToSftp(chats as List<ChatThread>) },
        onTestSftp = viewModel::testSftpConnection,
        onRestoreBackup = { uri -> viewModel.restoreBackup(uri) },
        onDeleteBackup = { file -> viewModel.deleteBackupFile(file) },
        onUpdateSftpHost = viewModel::updateSftpHost,
        onUpdateSftpPort = viewModel::updateSftpPort,
        onUpdateSftpUser = viewModel::updateSftpUser,
        onUpdateSftpPassword = viewModel::updateSftpPassword,
        onUpdateSftpPath = viewModel::updateSftpPath,
        onNavigateBack = onNavigateBack
    )
}
