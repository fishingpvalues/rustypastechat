package com.rustypastechat.ui.chatlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.repository.PasteRepository
import com.rustypastechat.util.FuzzySearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatListState(
    val chats: List<ChatThread> = emptyList(),
    val allMessages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val pasteRepo = PasteRepository(prefs)
    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.settingsFlow.collect { s ->
                if (s.pasteServerUrl.isNotBlank()) { _state.update { it.copy(isConnected = true) }; loadChats() }
                else _state.update { it.copy(isConnected = false) }
            }
        }
    }

    fun loadChats() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        pasteRepo.loadChatHistory(Message.DEFAULT_CHAT)
            .onSuccess { messages ->
                val chats = groupMessagesByChat(messages)
                _state.update { it.copy(chats = chats, allMessages = messages, isLoading = false) }
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        val results = _state.value.allMessages.filter { msg ->
            msg.text.isNotBlank() && FuzzySearch.search(query, msg.text)
        }
        _state.update { it.copy(searchResults = results) }
    }

    fun clearSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun createChat(name: String) = viewModelScope.launch {
        val id = UUID.randomUUID().toString().take(8)
        val chat = ChatThread.create(id, name.take(30))
        _state.update { it.copy(chats = listOf(chat) + it.chats) }
    }

    fun renameChat(chatId: String, newName: String) {
        _state.update { state ->
            state.copy(chats = state.chats.map {
                if (it.id == chatId) it.copy(name = newName) else it
            })
        }
    }

    fun deleteChat(chatId: String) {
        _state.update { state ->
            state.copy(chats = state.chats.filter { it.id != chatId })
        }
    }

    private fun groupMessagesByChat(messages: List<Message>): List<ChatThread> {
        val grouped = messages.groupBy { it.chatId }
        return grouped.map { (chatId, msgs) ->
            val last = msgs.lastOrNull()
            ChatThread(
                id = chatId,
                name = chatName(chatId),
                lastMessage = last?.text?.take(80) ?: "",
                lastTimestamp = last?.timestamp ?: 0L,
                messageCount = msgs.size
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    private fun chatName(id: String): String = when (id) {
        Message.DEFAULT_CHAT -> "General"
        else -> "Chat $id"
    }
}
