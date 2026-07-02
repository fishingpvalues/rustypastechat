package com.rustypastechat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.ChatCategory
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.repository.PasteRepository
import com.rustypastechat.ui.common.OneTimeEvent
import com.rustypastechat.util.FuzzySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatListState(
    val chats: List<ChatThread> = emptyList(),
    val allMessages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val isSearching: Boolean = false,
    val selectedCategory: ChatCategory? = null,
    val error: OneTimeEvent<String?> = OneTimeEvent(null)
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val pasteRepo: PasteRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    private val allChats = mutableListOf<ChatThread>()

    init {
        viewModelScope.launch {
            prefs.settingsFlow.collect { s ->
                if (s.pasteServerUrl.isNotBlank()) {
                    _state.update { it.copy(isConnected = true) }
                    loadChats()
                } else _state.update { it.copy(isConnected = false) }
            }
        }
    }

    fun loadChats() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        pasteRepo.loadChatHistory(Message.DEFAULT_CHAT)
            .onSuccess { messages ->
                val chats = groupMessagesByChat(messages)
                allChats.clear()
                allChats.addAll(chats)
                _state.update { it.copy(chats = filterByCategory(allChats, it.selectedCategory), allMessages = messages, isLoading = false) }
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = OneTimeEvent(e.message)) }
            }
    }

    fun setCategoryFilter(category: ChatCategory?) {
        val newCategory = if (_state.value.selectedCategory == category) null else category
        _state.update { it.copy(selectedCategory = newCategory, chats = filterByCategory(allChats, newCategory)) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isBlank()) { _state.update { it.copy(searchResults = emptyList()) }; return }
        val results = _state.value.allMessages.filter { msg ->
            msg.text.isNotBlank() && FuzzySearch.search(query, msg.text)
        }
        _state.update { it.copy(searchResults = results) }
    }

    fun clearSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun createChat(name: String, category: ChatCategory = ChatCategory.GENERAL) = viewModelScope.launch {
        val id = UUID.randomUUID().toString().take(8)
        val chat = ChatThread.create(id, name.take(30), category)
        allChats.add(0, chat)
        _state.update { it.copy(chats = filterByCategory(allChats, it.selectedCategory)) }
    }

    fun renameChat(chatId: String, newName: String) {
        val idx = allChats.indexOfFirst { it.id == chatId }
        if (idx >= 0) {
            allChats[idx] = allChats[idx].copy(name = newName)
            _state.update { it.copy(chats = filterByCategory(allChats, it.selectedCategory)) }
        }
    }

    fun setChatCategory(chatId: String, category: ChatCategory) {
        val idx = allChats.indexOfFirst { it.id == chatId }
        if (idx >= 0) {
            allChats[idx] = allChats[idx].copy(category = category)
            _state.update { it.copy(chats = filterByCategory(allChats, it.selectedCategory)) }
        }
    }

    fun deleteChat(chatId: String) {
        allChats.removeAll { it.id == chatId }
        _state.update { it.copy(chats = filterByCategory(allChats, it.selectedCategory)) }
    }

    private fun filterByCategory(chats: List<ChatThread>, category: ChatCategory?): List<ChatThread> {
        return if (category == null) chats else chats.filter { it.category == category }
    }

    private fun groupMessagesByChat(messages: List<Message>): List<ChatThread> {
        val grouped = messages.groupBy { it.chatId }
        return grouped.map { (chatId, msgs) ->
            val last = msgs.lastOrNull()
            val existing = allChats.find { it.id == chatId }
            ChatThread(
                id = chatId,
                name = existing?.name ?: chatName(chatId),
                lastMessage = last?.text?.take(80) ?: "",
                lastTimestamp = last?.timestamp ?: 0L,
                messageCount = msgs.size,
                category = existing?.category ?: ChatCategory.GENERAL,
                avatarColor = existing?.avatarColor ?: 0xFF1A73E8
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    private fun chatName(id: String): String = when (id) {
        Message.DEFAULT_CHAT -> "General"
        else -> "Chat $id"
    }
}
