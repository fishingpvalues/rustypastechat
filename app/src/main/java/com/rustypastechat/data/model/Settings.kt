package com.rustypastechat.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class VoiceQuality(val bitRate: Int, val sampleRate: Int, val label: String) {
    LOW(64_000, 22_050, "Low — smaller files"),
    STANDARD(96_000, 44_100, "Standard"),
    HIGH(128_000, 44_100, "High")
}

enum class ImageQuality(val jpegQuality: Int, val maxDimension: Int, val label: String) {
    LOW(70, 1280, "Low — smaller files"),
    STANDARD(85, 1920, "Standard"),
    HIGH(95, 2560, "High")
}

data class AppSettings(
    val pasteServerUrl: String = "",
    val authToken: String = "",
    val llmEnabled: Boolean = false,
    val llmEndpoint: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "gpt-3.5-turbo",
    val llmContextWindowSize: Int = 10,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val showDateHeaders: Boolean = true,
    val markdownEnabled: Boolean = true,
    val voiceQuality: VoiceQuality = VoiceQuality.STANDARD,
    val imageQuality: ImageQuality = ImageQuality.STANDARD,
    val encryptMediaCache: Boolean = true
)
