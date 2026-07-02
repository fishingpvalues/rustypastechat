package com.rustypastechat.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
        onUpdateBiometric = viewModel::updateBiometricEnabled,
        onUpdateLockTimeout = viewModel::updateLockTimeout,
        onUpdateThemeMode = viewModel::updateThemeMode,
        onUpdateDynamicColor = viewModel::updateDynamicColor,
        onUpdateShowDateHeaders = viewModel::updateShowDateHeaders,
        onSave = viewModel::saveSettings,
        onTestConnection = viewModel::testConnection,
        onClearCache = viewModel::clearCache,
        onFetchStats = viewModel::fetchStats,
        onNavigateBack = onNavigateBack
    )
}
