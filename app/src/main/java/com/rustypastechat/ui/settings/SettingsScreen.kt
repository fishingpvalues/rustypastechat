package com.rustypastechat.ui.settings

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustypastechat.ui.components.GlassCard
import com.rustypastechat.ui.components.GlassShape
import com.rustypastechat.ui.theme.Blue
import java.io.File

private sealed class SettingsPage(val ordinal: Int) {
    object Main : SettingsPage(0)
    object Server : SettingsPage(1)
    object Llm : SettingsPage(2)
    object Security : SettingsPage(3)
    object About : SettingsPage(4)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding -> content(padding) }
}

@Composable
private fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentPage by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }
    val context = LocalContext.current

    AnimatedContent(
        targetState = currentPage.ordinal,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally { it } + androidx.compose.animation.fadeIn())
                    .togetherWith(slideOutHorizontally { -it } + androidx.compose.animation.fadeOut())
            } else {
                (slideInHorizontally { -it } + androidx.compose.animation.fadeIn())
                    .togetherWith(slideOutHorizontally { it } + androidx.compose.animation.fadeOut())
            }
        },
        label = "settingsPage"
    ) { _ ->
        when (currentPage) {
            SettingsPage.Main -> MainPage(
                onNavigateBack = onNavigateBack,
                onServerSettings = { currentPage = SettingsPage.Server },
                onLlmSettings = { currentPage = SettingsPage.Llm },
                onSecuritySettings = { currentPage = SettingsPage.Security },
                onAbout = { currentPage = SettingsPage.About },
                llmEnabled = uiState.settings.llmEnabled,
                hasServerUrl = uiState.settings.pasteServerUrl.isNotBlank(),
                biometricEnabled = uiState.settings.biometricEnabled,
                uiState = uiState,
                onClearCache = { viewModel.clearCache(context.applicationContext as Application) },
                onFetchStats = { viewModel.fetchStats(context.applicationContext as Application) }
            )
            SettingsPage.Server -> ServerPage(
                uiState = uiState,
                onUpdateUrl = viewModel::updateServerUrl,
                onUpdateToken = viewModel::updateAuthToken,
                onTestConnection = viewModel::testConnection,
                onSave = { viewModel.saveSettings(); currentPage = SettingsPage.Main },
                onBack = { currentPage = SettingsPage.Main }
            )
            SettingsPage.Llm -> LlmPage(
                uiState = uiState,
                onUpdateEnabled = viewModel::updateLlmEnabled,
                onUpdateEndpoint = viewModel::updateLlmEndpoint,
                onUpdateApiKey = viewModel::updateLlmApiKey,
                onUpdateModel = viewModel::updateLlmModel,
                onSave = { viewModel.saveSettings(); currentPage = SettingsPage.Main },
                onBack = { currentPage = SettingsPage.Main }
            )
            SettingsPage.About -> AboutPage(
                onBack = { currentPage = SettingsPage.Main }
            )
            SettingsPage.Security -> SecurityPage(
                uiState = uiState,
                onUpdateBiometric = viewModel::updateBiometricEnabled,
                onUpdateLockTimeout = viewModel::updateLockTimeout,
                onSave = { viewModel.saveSettings(); currentPage = SettingsPage.Main },
                onBack = { currentPage = SettingsPage.Main }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainPage(
    onNavigateBack: () -> Unit,
    onServerSettings: () -> Unit,
    onLlmSettings: () -> Unit,
    onSecuritySettings: () -> Unit,
    onAbout: () -> Unit,
    llmEnabled: Boolean,
    hasServerUrl: Boolean,
    biometricEnabled: Boolean,
    uiState: SettingsUiState,
    onClearCache: () -> Unit,
    onFetchStats: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "Connection",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                    SettingsNavRow(
                        icon = if (hasServerUrl) Icons.Rounded.Cloud else Icons.Rounded.Cloud,
                        title = "Paste Server",
                        subtitle = if (hasServerUrl) "Connected" else "Not configured",
                        onClick = onServerSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "AI Features",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Psychology,
                        title = "LLM Integration",
                        subtitle = if (llmEnabled) "OpenAI compatible" else "Disabled",
                        onClick = onLlmSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "Security",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric Lock",
                        subtitle = if (biometricEnabled) "Enabled" else "Disabled",
                        onClick = onSecuritySettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "Data",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Delete,
                        title = "Cache",
                        subtitle = uiState.cacheSize,
                        onClick = { onFetchStats() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "App",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Info,
                        title = "About",
                        subtitle = "Version 1.0.0",
                        onClick = onAbout
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ServerPage(
    uiState: SettingsUiState,
    onUpdateUrl: (String) -> Unit,
    onUpdateToken: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    SubPageScaffold(title = "Paste Server", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = uiState.settings.pasteServerUrl,
                        onValueChange = onUpdateUrl,
                        label = { Text("Server URL") },
                        placeholder = { Text("https://paste.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Link, null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.settings.authToken,
                        onValueChange = onUpdateToken,
                        label = { Text("Auth Token") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Key, null) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onTestConnection,
                        enabled = !uiState.isTesting && uiState.settings.pasteServerUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isTesting) "Testing..." else "Test Connection")
                    }
                    uiState.testResult?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Connected"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}

@Composable
private fun LlmPage(
    uiState: SettingsUiState,
    onUpdateEnabled: (Boolean) -> Unit,
    onUpdateEndpoint: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    SubPageScaffold(title = "LLM Integration", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Psychology,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Enable AI Assistant",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.settings.llmEnabled,
                            onCheckedChange = onUpdateEnabled
                        )
                    }
                    if (uiState.settings.llmEnabled) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = uiState.settings.llmEndpoint,
                            onValueChange = onUpdateEndpoint,
                            label = { Text("Endpoint URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Link, null) }
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.settings.llmApiKey,
                            onValueChange = onUpdateApiKey,
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Key, null) },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.settings.llmModel,
                            onValueChange = onUpdateModel,
                            label = { Text("Model") },
                            placeholder = { Text("gpt-3.5-turbo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Science, null) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SubPageScaffold(title = "About", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            GlassCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                elevated = true
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "RustyPaste Chat",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Blue
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "A lightweight chat app for your rustypaste instance. Upload text and media as paste notes, chat with an AI assistant via OpenAI-compatible endpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("Built with Jetpack Compose") },
                        leadingContent = {
                            Icon(Icons.Rounded.Palette, null, tint = Blue, modifier = Modifier.size(20.dp))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Material Design 3") },
                        leadingContent = {
                            Icon(Icons.Rounded.SettingsBrightness, null, tint = Blue, modifier = Modifier.size(20.dp))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Powered by rustypaste") },
                        leadingContent = {
                            Icon(Icons.Rounded.Cloud, null, tint = Blue, modifier = Modifier.size(20.dp))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityPage(
    uiState: SettingsUiState,
    onUpdateBiometric: (Boolean) -> Unit,
    onUpdateLockTimeout: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val lockOptions = listOf(15 to "15s", 30 to "30s", 60 to "1 min", 300 to "5 min")
    SubPageScaffold(title = "Biometric Lock", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Biometric Lock",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Require fingerprint or PIN to open the app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.settings.biometricEnabled,
                            onCheckedChange = onUpdateBiometric
                        )
                    }

                    if (uiState.settings.biometricEnabled) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Auto-lock after",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lockOptions.forEach { (secs, label) ->
                                FilterChip(
                                    selected = uiState.settings.lockTimeoutSeconds == secs,
                                    onClick = { onUpdateLockTimeout(secs) },
                                    label = {
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}
