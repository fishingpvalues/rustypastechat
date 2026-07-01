package com.rustypastechat.data.model

data class ChatThread(
    val id: String,
    val name: String,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val messageCount: Int = 0,
    val isActive: Boolean = true
) {
    companion object {
        fun create(id: String, name: String): ChatThread = ChatThread(id = id, name = name)
    }
}
