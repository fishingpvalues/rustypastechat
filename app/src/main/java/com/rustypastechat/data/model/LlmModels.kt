package com.rustypastechat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LlmChatRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val stream: Boolean = false
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: String
)

@Serializable
data class LlmChatResponse(
    val choices: List<LlmChoice> = emptyList()
)

@Serializable
data class LlmChoice(
    val message: LlmMessage? = null,
    val index: Int = 0,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class LlmDeltaResponse(
    val choices: List<LlmDeltaChoice> = emptyList()
)

@Serializable
data class LlmDeltaChoice(
    val delta: LlmDelta? = null,
    val index: Int = 0
)

@Serializable
data class LlmDelta(
    val content: String? = null,
    val role: String? = null
)
