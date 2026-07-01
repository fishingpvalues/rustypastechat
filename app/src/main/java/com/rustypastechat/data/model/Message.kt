package com.rustypastechat.data.model

import kotlinx.serialization.Serializable

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Serializable
data class Message(
    val id: String,
    val text: String,
    val isOutgoing: Boolean = true,
    val status: MessageStatus = MessageStatus.SENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUrl: String? = null,
    val mediaType: MediaType? = null,
    val pasteFileName: String? = null,
    val isLlmResponse: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToIsOutgoing: Boolean? = null,
    val isOneshot: Boolean = false,
    val expiresAt: Long? = null,
    val isImported: Boolean = false,
    val chatId: String = "default"
) {
    companion object {
        const val CHAT_PREFIX = "chat_"
        const val LEGACY_PREFIX = "c_"
        const val DEFAULT_CHAT = "default"

        fun buildFileName(chatId: String, timestamp: Long, isOutgoing: Boolean, id: String): String {
            val dir = if (isOutgoing) "o" else "i"
            val cid = if (chatId == DEFAULT_CHAT) "" else "${chatId}_"
            return "${CHAT_PREFIX}${cid}${timestamp}_${dir}_${id.take(8)}.txt"
        }

        fun buildMediaFileName(chatId: String, timestamp: Long, id: String, ext: String): String {
            val cid = if (chatId == DEFAULT_CHAT) "" else "${chatId}_"
            return "${CHAT_PREFIX}${cid}${timestamp}_m_${id.take(8)}.$ext"
        }

        fun isChatFile(fileName: String): Boolean = fileName.startsWith(CHAT_PREFIX) || fileName.startsWith(LEGACY_PREFIX)

        fun extractChatId(fileName: String): String {
            if (fileName.startsWith(LEGACY_PREFIX)) return DEFAULT_CHAT
            if (!fileName.startsWith(CHAT_PREFIX)) return DEFAULT_CHAT
            val afterPrefix = fileName.removePrefix(CHAT_PREFIX)
            val parts = afterPrefix.split("_")
            // If first part is a timestamp, it's a default chat file
            return if (parts[0].toLongOrNull() != null) DEFAULT_CHAT else parts[0]
        }

        fun parseFromFileName(fileName: String): ParsedFileName? {
            if (fileName.startsWith(CHAT_PREFIX)) {
                val rest = fileName.removePrefix(CHAT_PREFIX)
                val parts = rest.split("_")
                if (parts.size < 3) return null
                var idx = 0
                // Skip chat ID if present (not a number)
                val maybeTs = parts[0].toLongOrNull()
                val chatId = if (maybeTs == null) { idx++; parts[0] } else DEFAULT_CHAT
                val ts = parts[idx].toLongOrNull() ?: return null; idx++
                val dir = parts[idx]
                val isOutgoing = when (dir) { "o" -> true; "i" -> false; "m" -> true; else -> return null }
                return ParsedFileName(ts, isOutgoing, dir == "m", chatId)
            }
            if (fileName.startsWith(LEGACY_PREFIX)) {
                val parts = fileName.removePrefix(LEGACY_PREFIX).split("_")
                if (parts.size < 3) return null
                val ts = parts[0].toLongOrNull() ?: return null
                val dir = parts[1]
                val isOutgoing = when (dir) { "o" -> true; "i" -> false; "m" -> true; else -> return null }
                return ParsedFileName(ts, isOutgoing, dir == "m", DEFAULT_CHAT)
            }
            return null
        }
    }
}

data class ParsedFileName(
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isMedia: Boolean,
    val chatId: String = "default"
)

data class ReplyTarget(
    val messageId: String,
    val text: String,
    val isOutgoing: Boolean
)

@Serializable
enum class MediaType {
    IMAGE,
    VIDEO,
    FILE
}
