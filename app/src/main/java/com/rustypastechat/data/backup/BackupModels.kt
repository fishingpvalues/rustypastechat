package com.rustypastechat.data.backup

import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.ChatCategory
import com.rustypastechat.data.model.ChatThread
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val settings: SerializableSettings,
    val chats: List<SerializableChat> = emptyList()
)

@Serializable
data class SerializableSettings(
    val pasteServerUrl: String = "",
    val authTokenHint: String = "",
    val llmEnabled: Boolean = false,
    val llmEndpoint: String = "",
    val llmModel: String = "gpt-3.5-turbo",
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val themeMode: String = "SYSTEM",
    val useDynamicColor: Boolean = true,
    val showDateHeaders: Boolean = true
) {
    companion object {
        fun fromAppSettings(s: AppSettings) = SerializableSettings(
            pasteServerUrl = s.pasteServerUrl,
            authTokenHint = if (s.authToken.isNotBlank()) "***set***" else "",
            llmEnabled = s.llmEnabled,
            llmEndpoint = s.llmEndpoint,
            llmModel = s.llmModel,
            biometricEnabled = s.biometricEnabled,
            lockTimeoutSeconds = s.lockTimeoutSeconds,
            themeMode = s.themeMode.name,
            useDynamicColor = s.useDynamicColor,
            showDateHeaders = s.showDateHeaders
        )
    }

    fun mergeInto(appSettings: AppSettings): AppSettings = appSettings.copy(
        pasteServerUrl = pasteServerUrl.ifBlank { appSettings.pasteServerUrl },
        llmEnabled = llmEnabled,
        llmEndpoint = llmEndpoint.ifBlank { appSettings.llmEndpoint },
        llmModel = llmModel.ifBlank { appSettings.llmModel },
        biometricEnabled = biometricEnabled,
        lockTimeoutSeconds = lockTimeoutSeconds,
        themeMode = try { com.rustypastechat.data.model.ThemeMode.valueOf(themeMode) } catch (_: Exception) { appSettings.themeMode },
        useDynamicColor = useDynamicColor,
        showDateHeaders = showDateHeaders
    )
}

@Serializable
data class SerializableChat(
    val id: String,
    val name: String,
    val category: String = "GENERAL",
    val avatarColor: Long = 0xFF1A73E8
) {
    companion object {
        fun fromChatThread(c: ChatThread) = SerializableChat(
            id = c.id, name = c.name, category = c.category.name, avatarColor = c.avatarColor
        )
    }

    fun toChatThread(): ChatThread = ChatThread(
        id = id, name = name,
        category = try { ChatCategory.valueOf(category) } catch (_: Exception) { ChatCategory.GENERAL },
        avatarColor = avatarColor
    )
}
