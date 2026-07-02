package com.rustypastechat.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val pasteServerUrl: String = "",
    val authToken: String = "",
    val llmEnabled: Boolean = false,
    val llmEndpoint: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "gpt-3.5-turbo",
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val showDateHeaders: Boolean = true
)
