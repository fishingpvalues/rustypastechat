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
    val isLlmResponse: Boolean = false
) {
    companion object {
        const val CHAT_PREFIX = "c_"
        const val OUT_SUFFIX = "_o_"
        const val IN_SUFFIX = "_i_"
        const val MEDIA_SUFFIX = "_m_"

        fun buildFileName(timestamp: Long, isOutgoing: Boolean, id: String): String {
            val dir = if (isOutgoing) "o" else "i"
            return "${CHAT_PREFIX}${timestamp}_${dir}_${id.take(8)}.txt"
        }

        fun buildMediaFileName(timestamp: Long, id: String, ext: String): String {
            return "${CHAT_PREFIX}${timestamp}_m_${id.take(8)}.$ext"
        }

        fun isChatFile(fileName: String): Boolean = fileName.startsWith(CHAT_PREFIX)

        fun parseFromFileName(fileName: String): ParsedFileName? {
            if (!fileName.startsWith(CHAT_PREFIX)) return null
            val parts = fileName.removePrefix(CHAT_PREFIX).split("_")
            if (parts.size < 3) return null
            val ts = parts[0].toLongOrNull() ?: return null
            val dir = parts[1]
            val isOutgoing = when (dir) {
                "o" -> true
                "i" -> false
                "m" -> true
                else -> return null
            }
            return ParsedFileName(ts, isOutgoing, dir == "m")
        }
    }
}

data class ParsedFileName(
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isMedia: Boolean
)

@Serializable
enum class MediaType {
    IMAGE,
    VIDEO,
    FILE
}
