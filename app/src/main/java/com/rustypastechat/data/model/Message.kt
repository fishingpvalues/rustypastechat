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
)

@Serializable
enum class MediaType {
    IMAGE,
    VIDEO,
    FILE
}
