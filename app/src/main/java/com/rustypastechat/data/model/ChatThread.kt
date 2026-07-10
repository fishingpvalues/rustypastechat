package com.rustypastechat.data.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class ChatCategory(val label: String, val iconName: String) {
    GENERAL("\uD83D\uDCAC", "General"),
    WORK("\uD83D\uDCBC", "Work"),
    PERSONAL("\uD83C\uDFE0", "Personal"),
    PROJECT("\uD83D\uDEE0\uFE0F", "Project"),
    IDEAS("\uD83D\uDCA1", "Ideas"),
    NOTES("\uD83D\uDCDD", "Notes");

    val displayName: String get() = "$label $iconName"
}

data class ChatThread(
    val id: String,
    val name: String,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val messageCount: Int = 0,
    val unreadCount: Int = 0,
    val isActive: Boolean = true,
    val category: ChatCategory = ChatCategory.GENERAL,
    val avatarColor: Long = 0xFF1A73E8
) {
    val avatarColorCompose: Color get() = Color(avatarColor.toInt().let { it or (0xFF shl 24) })

    companion object {
        fun create(id: String, name: String, category: ChatCategory = ChatCategory.GENERAL): ChatThread =
            ChatThread(id = id, name = name, category = category, avatarColor = randomColor().toLong())

        private fun randomColor(): UInt = listOf(
            0xFF1A73E8u, 0xFF34A853u, 0xFF00C8B4u, 0xFFFF6D00u, 0xFFEA4335u,
            0xFF7B1FA2u, 0xFF00897Bu, 0xFFE91E63u, 0xFF3F51B5u
        ).random()
    }
}
