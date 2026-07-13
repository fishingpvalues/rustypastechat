package com.rustypastechat.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rustypastechat.data.model.ThemeMode
import com.rustypastechat.ui.components.GlassCard

private sealed class SettingsPage(val ordinal: Int) {
    object Main : SettingsPage(0)
    object Appearance : SettingsPage(1)
    object Server : SettingsPage(2)
    object Llm : SettingsPage(3)
    object Security : SettingsPage(4)
    object Storage : SettingsPage(5)
    object Tools : SettingsPage(6)
    object About : SettingsPage(7)
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedContentLambdaTargetStateParameter")
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onUpdateServerUrl: (String) -> Unit,
    onUpdateAuthToken: (String) -> Unit,
    onUpdateLlmEnabled: (Boolean) -> Unit,
    onUpdateLlmEndpoint: (String) -> Unit,
    onUpdateLlmApiKey: (String) -> Unit,
    onUpdateLlmModel: (String) -> Unit,
    onUpdateLlmContextWindow: (Int) -> Unit,
    onUpdateBiometric: (Boolean) -> Unit,
    onUpdateLockTimeout: (Int) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateDynamicColor: (Boolean) -> Unit,
    onUpdateShowDateHeaders: (Boolean) -> Unit,
    onUpdateMarkdownEnabled: (Boolean) -> Unit,
    onUpdateVoiceQuality: (com.rustypastechat.data.model.VoiceQuality) -> Unit,
    onUpdateImageQuality: (com.rustypastechat.data.model.ImageQuality) -> Unit,
    onUpdateEncryptMediaCache: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onClearCache: () -> Unit,
    onFetchStats: () -> Unit,
    onFetchBackups: () -> Unit,
    onCreateBackup: (List<Any>) -> Unit,
    onExportSftp: (List<Any>) -> Unit,
    onTestSftp: () -> Unit,
    onRestoreBackup: (android.net.Uri) -> Unit,
    onDeleteBackup: (java.io.File) -> Unit,
    onUpdateSftpHost: (String) -> Unit,
    onUpdateSftpPort: (String) -> Unit,
    onUpdateSftpUser: (String) -> Unit,
    onUpdateSftpPassword: (String) -> Unit,
    onUpdateSftpPath: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var currentPage by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }

    // Settings' sub-pages aren't nav-graph destinations (they're local AnimatedContent
    // state), so without this the system back button/gesture would skip past them
    // straight to ChatList instead of returning to the Settings main page first —
    // inconsistent with every other back-navigation in the app.
    BackHandler(enabled = currentPage != SettingsPage.Main) {
        when (currentPage) {
            SettingsPage.Appearance, SettingsPage.Server, SettingsPage.Llm, SettingsPage.Security -> onSave()
            else -> {}
        }
        currentPage = SettingsPage.Main
    }

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
                onAppearance = { currentPage = SettingsPage.Appearance },
                onServerSettings = { currentPage = SettingsPage.Server },
                onLlmSettings = { currentPage = SettingsPage.Llm },
                onSecuritySettings = { currentPage = SettingsPage.Security },
                onStorageSettings = { currentPage = SettingsPage.Storage },
                onToolsSettings = { currentPage = SettingsPage.Tools },
                onAbout = { currentPage = SettingsPage.About },
                uiState = uiState
            )
            SettingsPage.Appearance -> AppearancePage(
                uiState = uiState,
                onUpdateThemeMode = onUpdateThemeMode,
                onUpdateDynamicColor = onUpdateDynamicColor,
                onUpdateShowDateHeaders = onUpdateShowDateHeaders,
                onUpdateMarkdownEnabled = onUpdateMarkdownEnabled,
                onSave = { onSave(); currentPage = SettingsPage.Main },
                // Editing a field only updates in-memory UI state (SettingsViewModel.update*);
                // it's never persisted until onSave() runs. Save on exit too, or back
                // navigation silently discards whatever the user just typed/toggled.
                onBack = { onSave(); currentPage = SettingsPage.Main }
            )
            SettingsPage.Server -> ServerPage(
                uiState = uiState,
                onUpdateUrl = onUpdateServerUrl,
                onUpdateToken = onUpdateAuthToken,
                onTestConnection = onTestConnection,
                onSave = { onSave(); currentPage = SettingsPage.Main },
                onBack = { onSave(); currentPage = SettingsPage.Main }
            )
            SettingsPage.Llm -> LlmPage(
                uiState = uiState,
                onUpdateEnabled = onUpdateLlmEnabled,
                onUpdateEndpoint = onUpdateLlmEndpoint,
                onUpdateApiKey = onUpdateLlmApiKey,
                onUpdateModel = onUpdateLlmModel,
                onUpdateContextWindow = onUpdateLlmContextWindow,
                onSave = { onSave(); currentPage = SettingsPage.Main },
                onBack = { onSave(); currentPage = SettingsPage.Main }
            )
            SettingsPage.Security -> SecurityPage(
                uiState = uiState,
                onUpdateBiometric = onUpdateBiometric,
                onUpdateLockTimeout = onUpdateLockTimeout,
                onSave = { onSave(); currentPage = SettingsPage.Main },
                onBack = { onSave(); currentPage = SettingsPage.Main }
            )
            SettingsPage.Storage -> StoragePage(
                uiState = uiState,
                onClearCache = onClearCache,
                onFetchStats = onFetchStats,
                onFetchBackups = onFetchBackups,
                onCreateBackup = onCreateBackup,
                onExportSftp = onExportSftp,
                onTestSftp = onTestSftp,
                onRestoreBackup = onRestoreBackup,
                onDeleteBackup = onDeleteBackup,
                onUpdateSftpHost = onUpdateSftpHost,
                onUpdateSftpPort = onUpdateSftpPort,
                onUpdateSftpUser = onUpdateSftpUser,
                onUpdateSftpPassword = onUpdateSftpPassword,
                onUpdateSftpPath = onUpdateSftpPath,
                onUpdateVoiceQuality = onUpdateVoiceQuality,
                onUpdateImageQuality = onUpdateImageQuality,
                onUpdateEncryptMediaCache = onUpdateEncryptMediaCache,
                onBack = { currentPage = SettingsPage.Main }
            )
            SettingsPage.Tools -> com.rustypastechat.ui.tools.ToolsPage(
                onBack = { currentPage = SettingsPage.Main }
            )
            SettingsPage.About -> AboutPage(
                onBack = { currentPage = SettingsPage.Main }
            )
        }
    }
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
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainPage(
    onNavigateBack: () -> Unit,
    onAppearance: () -> Unit,
    onServerSettings: () -> Unit,
    onLlmSettings: () -> Unit,
    onSecuritySettings: () -> Unit,
    onStorageSettings: () -> Unit,
    onToolsSettings: () -> Unit,
    onAbout: () -> Unit,
    uiState: SettingsUiState
) {
    val s = uiState.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // APPEARANCE
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("Appearance")
                    SettingsNavRow(
                        icon = Icons.Rounded.Palette,
                        title = "Theme & Layout",
                        subtitle = themeSummary(s.themeMode, s.useDynamicColor),
                        onClick = onAppearance
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CONNECTION
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("Connection")
                    SettingsNavRow(
                        icon = Icons.Rounded.Cloud,
                        title = "Paste Server",
                        subtitle = if (s.pasteServerUrl.isNotBlank()) truncateUrl(s.pasteServerUrl) else "Not configured",
                        onClick = onServerSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DANGER ZONE (LLM) — separated from regular settings because enabling it sends
            // message content to a third-party API, outside the device's control.
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("Danger Zone")
                    SettingsNavRow(
                        icon = Icons.Rounded.Warning,
                        title = "Danger Zone",
                        subtitle = if (s.llmEnabled) "AI assistant enabled — ${s.llmModel}" else "AI assistant disabled",
                        onClick = onLlmSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECURITY
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("Security")
                    SettingsNavRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric Lock",
                        subtitle = if (s.biometricEnabled) lockSummary(s.lockTimeoutSeconds) else "Disabled",
                        onClick = onSecuritySettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STORAGE
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("Data & Storage")
                    SettingsNavRow(
                        icon = Icons.Rounded.Storage,
                        title = "Manage Storage",
                        subtitle = uiState.cacheSize,
                        onClick = { onStorageSettings() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TOOLS
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("Developer Tools")
                    SettingsNavRow(
                        icon = Icons.Rounded.Build,
                        title = "Tools",
                        subtitle = "UUID, hashes, Base64, JSON, QR, diff",
                        onClick = onToolsSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ABOUT
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SectionLabel("About")
                    SettingsNavRow(
                        icon = Icons.Rounded.Info,
                        title = "About RustyPaste Chat",
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
private fun AppearancePage(
    uiState: SettingsUiState,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateDynamicColor: (Boolean) -> Unit,
    onUpdateShowDateHeaders: (Boolean) -> Unit,
    onUpdateMarkdownEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val s = uiState.settings

    SubPageScaffold(title = "Appearance", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {

            // Theme mode
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Theme")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.values().forEach { mode ->
                            val selected = s.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateThemeMode(mode) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (mode) {
                                                ThemeMode.SYSTEM -> Icons.Rounded.PhonelinkSetup
                                                ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                                ThemeMode.DARK -> Icons.Rounded.DarkMode
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Dynamic color (Android 12+ only)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dynamic Color", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Use colors from your wallpaper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = s.useDynamicColor, onCheckedChange = onUpdateDynamicColor)
                        }
                    }
                }
            }

            // Date headers
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Date Headers", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Show date separators in chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = s.showDateHeaders, onCheckedChange = onUpdateShowDateHeaders)
                    }
                }
            }

            // Markdown formatting
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.TextFormat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Markdown Formatting", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Render **bold**, headings, lists, code blocks, and diagrams. Turn off to show messages as plain typed text.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = s.markdownEnabled, onCheckedChange = onUpdateMarkdownEnabled)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Server Configuration")
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = uiState.settings.pasteServerUrl,
                        onValueChange = onUpdateUrl,
                        label = { Text("Server URL") },
                        placeholder = { Text("https://paste.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(20.dp)) },
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.settings.authToken,
                        onValueChange = onUpdateToken,
                        label = { Text("Auth Token") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Key, null, modifier = Modifier.size(20.dp)) },
                        shape = MaterialTheme.shapes.medium
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
                            color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
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
    onUpdateContextWindow: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val contextWindowOptions = listOf(0, 4, 10, 20, 40)
    SubPageScaffold(title = "Danger Zone", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            GlassCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Enabling the AI assistant sends your message content to the endpoint below, outside this device's control. Only turn it on for endpoints you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable AI Assistant", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Get auto-replies from an LLM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.settings.llmEnabled, onCheckedChange = onUpdateEnabled)
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
                            leadingIcon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(20.dp)) },
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.settings.llmApiKey,
                            onValueChange = onUpdateApiKey,
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Key, null, modifier = Modifier.size(20.dp)) },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.settings.llmModel,
                            onValueChange = onUpdateModel,
                            label = { Text("Model") },
                            placeholder = { Text("gpt-3.5-turbo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Science, null, modifier = Modifier.size(20.dp)) },
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Text("Context window", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "How many previous messages are sent to the LLM as context alongside your new message.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            contextWindowOptions.forEach { size ->
                                FilterChip(
                                    selected = uiState.settings.llmContextWindowSize == size,
                                    onClick = { onUpdateContextWindow(size) },
                                    label = { Text(if (size == 0) "None" else "$size", style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Lock", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Require fingerprint or PIN to open the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.settings.biometricEnabled, onCheckedChange = onUpdateBiometric)
                    }

                    if (uiState.settings.biometricEnabled) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Text("Auto-lock after", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lockOptions.forEach { (secs, label) ->
                                FilterChip(
                                    selected = uiState.settings.lockTimeoutSeconds == secs,
                                    onClick = { onUpdateLockTimeout(secs) },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}

@Composable
private fun StoragePage(
    uiState: SettingsUiState,
    onClearCache: () -> Unit,
    onFetchStats: () -> Unit,
    onFetchBackups: () -> Unit,
    onCreateBackup: (List<Any>) -> Unit,
    onExportSftp: (List<Any>) -> Unit,
    onTestSftp: () -> Unit,
    onRestoreBackup: (android.net.Uri) -> Unit,
    onDeleteBackup: (java.io.File) -> Unit,
    onUpdateSftpHost: (String) -> Unit,
    onUpdateSftpPort: (String) -> Unit,
    onUpdateSftpUser: (String) -> Unit,
    onUpdateSftpPassword: (String) -> Unit,
    onUpdateSftpPath: (String) -> Unit,
    onUpdateVoiceQuality: (com.rustypastechat.data.model.VoiceQuality) -> Unit,
    onUpdateImageQuality: (com.rustypastechat.data.model.ImageQuality) -> Unit,
    onUpdateEncryptMediaCache: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onRestoreBackup(it) } }

    SubPageScaffold(title = "Data & Storage", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {

            // Cache
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Cache")
                    Spacer(Modifier.height(4.dp))
                    ListItem(
                        headlineContent = { Text("Cache Size", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(uiState.cacheSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    FilledTonalButton(
                        onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.cacheSize != "0 B" && uiState.cacheSize != "0 KB"
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Cache")
                    }
                }
            }

            // Media quality & encryption
            Spacer(Modifier.height(12.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Media")

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.HighQuality, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Photo quality", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        com.rustypastechat.data.model.ImageQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = uiState.settings.imageQuality == quality,
                                onClick = { onUpdateImageQuality(quality) },
                                label = { Text(quality.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Voice message quality", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        com.rustypastechat.data.model.VoiceQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = uiState.settings.voiceQuality == quality,
                                onClick = { onUpdateVoiceQuality(quality) },
                                label = { Text(quality.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Encrypt Media Cache", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Store downloaded photos/videos encrypted on-device instead of in Coil's plain image cache. Slightly slower to load.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = uiState.settings.encryptMediaCache, onCheckedChange = onUpdateEncryptMediaCache)
                    }
                }
            }

            // Backup & Restore
            Spacer(Modifier.height(12.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Backup & Restore")
                    Spacer(Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { onCreateBackup(emptyList()) }, modifier = Modifier.weight(1f),
                            enabled = uiState.backupStatus == null
                        ) {
                            Icon(Icons.Rounded.Cloud, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Backup")
                        }
                        FilledTonalButton(
                            onClick = { restoreLauncher.launch("*/*") }, modifier = Modifier.weight(1f),
                            enabled = uiState.backupStatus == null
                        ) {
                            Icon(Icons.Rounded.Cloud, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                    uiState.backupStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // SFTP Server
            Spacer(Modifier.height(12.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("SFTP Backup Server")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.sftpHost, onValueChange = onUpdateSftpHost,
                        label = { Text("Host") }, placeholder = { Text("sftp.example.com") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.sftpPort, onValueChange = onUpdateSftpPort,
                            label = { Text("Port") }, placeholder = { Text("22") },
                            modifier = Modifier.weight(0.3f), singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value = uiState.sftpUser, onValueChange = onUpdateSftpUser,
                            label = { Text("Username") },
                            modifier = Modifier.weight(0.7f), singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.sftpPassword, onValueChange = onUpdateSftpPassword,
                        label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.sftpPath, onValueChange = onUpdateSftpPath,
                        label = { Text("Remote path") }, placeholder = { Text("/backups") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = onTestSftp, modifier = Modifier.weight(1f),
                            enabled = !uiState.sftpTesting
                        ) { Text("Test") }
                        FilledTonalButton(
                            onClick = { onExportSftp(emptyList()) }, modifier = Modifier.weight(1f),
                            enabled = !uiState.sftpTesting
                        ) { Text("Upload Backup") }
                    }
                    uiState.sftpResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Uploaded") || it.startsWith("Connected")) MaterialTheme.colorScheme.primary
                            else if (it.startsWith("SFTP")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Backup history
            if (uiState.backupFiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionLabel("Backup History")
                        Spacer(Modifier.height(4.dp))
                        uiState.backupFiles.forEach { file ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))} | ${file.length() / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteBackup(file) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (file != uiState.backupFiles.last()) HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Info")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Backups are encrypted with AES-256-GCM using the Android Keystore. Export to SFTP for off-device storage. Chat history remains on your rustypaste server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Cache") },
            text = { Text("Remove all cached files and images? Your chat data on the server will not be affected.") },
            confirmButton = { TextButton(onClick = { onClearCache(); showClearConfirm = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SubPageScaffold(title = "About", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, elevated = true) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.rustypastechat.ui.components.RustyMark(
                        modifier = Modifier.size(56.dp),
                        markColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("RustyPaste Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        leadingContent = { Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        supportingContent = { Text("Modern declarative UI toolkit", style = MaterialTheme.typography.bodySmall) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Material Design 3") },
                        leadingContent = { Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        supportingContent = { Text("Dynamic theming and adaptive layouts", style = MaterialTheme.typography.bodySmall) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Powered by rustypaste") },
                        leadingContent = { Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        supportingContent = { Text("Self-hosted pastebin backend", style = MaterialTheme.typography.bodySmall) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Hilt Dependency Injection") },
                        leadingContent = { Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        supportingContent = { Text("Clean architecture with DI", style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }
    }
}

private fun themeSummary(mode: ThemeMode, dynamic: Boolean): String = buildString {
    append(when (mode) {
        ThemeMode.SYSTEM -> "System default"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    })
    if (dynamic && mode != ThemeMode.LIGHT) append(", Dynamic")
}

private fun lockSummary(seconds: Int): String = when {
    seconds == 15 -> "After 15 seconds"
    seconds == 30 -> "After 30 seconds"
    seconds == 60 -> "After 1 minute"
    seconds == 300 -> "After 5 minutes"
    else -> "After $seconds seconds"
}

private fun truncateUrl(url: String): String {
    val stripped = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
    return if (stripped.length > 32) stripped.take(29) + "..." else stripped
}
