package com.rustypastechat.ui.chat.components

import com.rustypastechat.data.model.MediaType
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus

/**
 * What TalkBack reads for one message bubble.
 *
 * The bubble merges its descendants, so this single string is the whole
 * announcement - a screen reader user never hears the individual icons. It is
 * a pure function so the wording can be tested without inflating Compose,
 * which is what lets the edge cases below be pinned at all.
 *
 * Order is sender, content, time, status, matching the order a sighted user
 * reads the bubble in.
 */
fun messageAccessibilityLabel(
    message: Message,
    timeText: String,
    isOutgoing: Boolean = message.isOutgoing
): String = buildString {
    append(if (isOutgoing) "You" else "Message")

    when {
        message.mediaType == MediaType.FILE && message.text.isNotBlank() ->
            append(": file ${message.text}")
        message.text.isNotBlank() ->
            append(": ${message.text}")
        !message.mediaUrl.isNullOrBlank() ->
            append(if (message.mediaType == MediaType.VIDEO) ": video" else ": image")
    }

    // A foreign paste has no time to announce. Appending an empty one left a
    // dangling ", " that TalkBack reads as a pause for nothing.
    if (timeText.isNotBlank()) append(", $timeText")

    statusLabel(message, isOutgoing)?.let { append(", $it") }
}

/**
 * Spoken form of the delivery state. Incoming messages have no status: the
 * ticks are drawn only on your own messages, and announcing "delivered" on a
 * message someone sent you is wrong.
 */
fun statusLabel(message: Message, isOutgoing: Boolean = message.isOutgoing): String? =
    if (!isOutgoing) null else when (message.status) {
        MessageStatus.SENDING -> "sending"
        MessageStatus.SENT -> "sent"
        MessageStatus.DELIVERED -> "delivered"
        MessageStatus.READ -> "read"
        MessageStatus.FAILED -> "failed to send"
    }
