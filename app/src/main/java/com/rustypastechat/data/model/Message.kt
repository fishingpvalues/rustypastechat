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
    val chatId: String = "default",
    val isEdited: Boolean = false,
    val reactions: List<String> = emptyList(),
    // Non-null for a message that lives inside a Thread (a follow-up conversation branched
    // off an LLM response) — its value is the id of the thread's root message. Threaded
    // messages are excluded from the main chat's flat timeline; see ChatScreen/ChatViewModel.
    val threadRootId: String? = null
) {
    companion object {
        const val CHAT_PREFIX = "chat_"
        const val LEGACY_PREFIX = "c_"
        const val DEFAULT_CHAT = "default"

        // Appended to a paste's content (not its filename) when a message is edited in
        // place — rustypaste has no metadata fields of its own, so this is the only way
        // an "edited" flag survives a re-upload and a later reconstruction from the server.
        const val EDITED_SENTINEL = "\u0000edited"

        // Same trick for reactions: comma-joined emoji appended after the edited sentinel
        // (if any), so re-uploading the same paste file is enough to persist a reaction —
        // no separate per-reaction file or scan needed.
        const val REACTIONS_SENTINEL_PREFIX = "\u0000reactions:"

        fun stripEditedSentinel(text: String): Pair<String, Boolean> {
            // rustypaste normalizes stored text to end with a trailing newline, so the
            // sentinel check must tolerate that rather than matching the raw suffix.
            val trimmed = text.trimEnd('\n', '\r')
            return if (trimmed.endsWith(EDITED_SENTINEL)) trimmed.removeSuffix(EDITED_SENTINEL) to true else text to false
        }

        /** Strips a trailing reactions sentinel (if present) and returns the remaining
         *  text plus the parsed emoji list. Must run *before* [stripEditedSentinel] is
         *  applied to whatever text is left, since reactions are appended last. */
        fun stripReactionsSentinel(text: String): Pair<String, List<String>> {
            val trimmed = text.trimEnd('\n', '\r')
            val idx = trimmed.lastIndexOf(REACTIONS_SENTINEL_PREFIX)
            if (idx == -1) return text to emptyList()
            val before = trimmed.substring(0, idx)
            val emojiList = trimmed.substring(idx + REACTIONS_SENTINEL_PREFIX.length)
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return before to emojiList
        }

        fun appendReactionsSentinel(text: String, reactions: List<String>): String =
            if (reactions.isEmpty()) text else "$text$REACTIONS_SENTINEL_PREFIX${reactions.joinToString(",")}"

        // Tags a message as belonging to a Thread (a follow-up conversation branched off an
        // LLM response). Appended closest to the raw text — before the edited/reactions
        // sentinels, since it's set once at creation while those are layered on afterward.
        const val THREAD_SENTINEL_PREFIX = "\u0000thread:"

        fun stripThreadSentinel(text: String): Pair<String, String?> {
            val trimmed = text.trimEnd('\n', '\r')
            val idx = trimmed.lastIndexOf(THREAD_SENTINEL_PREFIX)
            if (idx == -1) return text to null
            val before = trimmed.substring(0, idx)
            val rootId = trimmed.substring(idx + THREAD_SENTINEL_PREFIX.length).trim()
            return before to rootId.ifBlank { null }
        }

        fun appendThreadSentinel(text: String, rootId: String?): String =
            if (rootId == null) text else "$text$THREAD_SENTINEL_PREFIX$rootId"

        // Marks a paste as an LLM-generated reply. Direction alone ("i" = not sent by this
        // device) isn't enough to tell an LLM reply apart from a received message imported
        // from a real conversation (e.g. WhatsApp import) — both use the same "i" direction
        // in the filename, so this sentinel is the only reliable signal.
        const val LLM_SENTINEL = "\u0000llm"

        fun stripLlmSentinel(text: String): Pair<String, Boolean> {
            val trimmed = text.trimEnd('\n', '\r')
            return if (trimmed.endsWith(LLM_SENTINEL)) trimmed.removeSuffix(LLM_SENTINEL) to true else text to false
        }

        fun appendLlmSentinel(text: String): String = "$text$LLM_SENTINEL"

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

data class ChatHistoryPage(
    val messages: List<Message>,
    val hasMore: Boolean
)

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
    AUDIO,
    FILE
}
