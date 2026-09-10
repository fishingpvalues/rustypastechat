package com.rustypastechat.ui.chatlist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.ChatMeta
import com.rustypastechat.data.local.ChatMetadataStore
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.ChatCategory
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.repository.PasteRepository
import com.rustypastechat.data.whatsapp.WhatsAppDateOrder
import com.rustypastechat.data.whatsapp.WhatsAppImportManager
import com.rustypastechat.data.whatsapp.WhatsAppImportResult
import com.rustypastechat.ui.common.OneTimeEvent
import com.rustypastechat.util.FuzzySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
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
    /** Archived chats are kept out of [chats] and shown in their own collapsed section. */
    val archivedChats: List<ChatThread> = emptyList(),
    val showArchived: Boolean = false,
    val isImportingWhatsApp: Boolean = false,
    val whatsAppImportProgress: Pair<Int, Int>? = null,
    val whatsAppImportResult: OneTimeEvent<WhatsAppImportResult?> = OneTimeEvent(null),
    val error: OneTimeEvent<String?> = OneTimeEvent(null)
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val chatMeta: ChatMetadataStore,
    private val pasteRepo: PasteRepository,
    private val whatsAppImportManager: WhatsAppImportManager
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
        // Re-render on any metadata change (rename from a dialog, read marker
        // written by ChatViewModel, archive toggled) without another network
        // round trip.
        viewModelScope.launch {
            chatMeta.metaFlow.collect { republish() }
        }
    }

    fun loadChats() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        pasteRepo.loadAllMessages()
            .onSuccess { messages ->
                lastMessages = messages
                val chats = groupMessagesByChat(messages, chatMeta.all())
                allChats.clear()
                allChats.addAll(chats)
                _state.update { it.copy(allMessages = messages, isLoading = false) }
                republish()
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = OneTimeEvent(e.message)) }
            }
    }

    private var lastMessages: List<Message> = emptyList()

    /** Recomputes the list the UI renders from the cached messages plus current metadata. */
    private fun republish() = viewModelScope.launch {
        val meta = chatMeta.all()
        if (lastMessages.isNotEmpty() || allChats.isNotEmpty()) {
            val regrouped = groupMessagesByChat(lastMessages, meta)
            // Chats created locally that have no message yet are not in the
            // grouping; keep them.
            val emptyLocal = allChats.filter { local -> regrouped.none { it.id == local.id } }
            allChats.clear()
            allChats.addAll(regrouped + emptyLocal)
        }
        val visible = allChats.filter { !(meta[it.id]?.hidden ?: false) }
        _state.update {
            it.copy(
                chats = filterByCategory(visible.filterNot { c -> c.isArchived }, it.selectedCategory),
                archivedChats = visible.filter { c -> c.isArchived }
            )
        }
    }

    fun toggleArchived(chatId: String) = viewModelScope.launch {
        chatMeta.update(chatId) { it.copy(archived = !it.archived) }
    }

    fun toggleMuted(chatId: String) = viewModelScope.launch {
        chatMeta.update(chatId) { it.copy(muted = !it.muted) }
    }

    fun setShowArchived(show: Boolean) {
        _state.update { it.copy(showArchived = show) }
    }

    /** Marks every message in the chat as seen, which is what clears its unread badge. */
    fun markChatRead(chatId: String) = viewModelScope.launch {
        val inChat = lastMessages.filter { it.chatId == chatId }
        if (inChat.isEmpty()) return@launch
        val ids = inChat.map { it.id }.toSet()
        val newest = inChat.maxOf { it.timestamp }
        chatMeta.update(chatId) {
            it.copy(
                readIds = ids,
                notifiedIds = it.notifiedIds intersect ids,
                lastReadTimestamp = maxOf(it.lastReadTimestamp, newest)
            )
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
        chatMeta.update(id) {
            it.copy(name = chat.name, category = category.name, avatarColor = chat.avatarColor, hidden = false)
        }
        republish()
    }

    /**
     * Imports a WhatsApp "Export chat" file (a `.txt`, or a `.zip` containing `_chat.txt`)
     * as a brand-new chat, uploading every message as a paste note with its original
     * WhatsApp timestamp preserved so the chat reconstructs in true chronological order.
     */
    fun importWhatsAppChat(
        uri: Uri,
        chatName: String,
        myDisplayName: String? = null,
        dateOrder: WhatsAppDateOrder = WhatsAppDateOrder.DAY_MONTH_YEAR,
        category: ChatCategory = ChatCategory.GENERAL
    ) = viewModelScope.launch {
        _state.update { it.copy(isImportingWhatsApp = true, whatsAppImportProgress = 0 to 0) }
        runCatching {
            val rawText = withContext(Dispatchers.IO) { readChatText(uri) }
                ?: throw IllegalArgumentException("Couldn't find a _chat.txt in that file")

            val id = UUID.randomUUID().toString().take(8)
            val result = whatsAppImportManager.import(
                rawText = rawText,
                chatId = id,
                myDisplayName = myDisplayName,
                dateOrder = dateOrder
            ) { done, total -> _state.update { it.copy(whatsAppImportProgress = done to total) } }

            allChats.add(0, ChatThread.create(id, chatName.take(30), category))
            _state.update { it.copy(chats = filterByCategory(allChats, it.selectedCategory)) }
            result
        }.onSuccess { result ->
            _state.update {
                it.copy(
                    isImportingWhatsApp = false,
                    whatsAppImportProgress = null,
                    whatsAppImportResult = OneTimeEvent(result)
                )
            }
            loadChats()
        }.onFailure { e ->
            _state.update {
                it.copy(
                    isImportingWhatsApp = false,
                    whatsAppImportProgress = null,
                    error = OneTimeEvent("WhatsApp import failed: ${e.message}")
                )
            }
        }
    }

    /** Reads `.txt` content directly, or finds and reads `_chat.txt` (or the first `.txt`
     *  entry) inside a `.zip` — WhatsApp's "Export chat" produces either shape. */
    private fun readChatText(uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val isZip = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        if (!isZip) return String(bytes, Charsets.UTF_8)

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var fallback: Pair<String, ByteArray>? = null
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".txt", ignoreCase = true)) {
                    val content = zip.readBytes()
                    if (entry.name.equals("_chat.txt", ignoreCase = true)) {
                        return String(content, Charsets.UTF_8)
                    }
                    if (fallback == null) fallback = entry.name to content
                }
                entry = zip.nextEntry
            }
            return fallback?.second?.let { String(it, Charsets.UTF_8) }
        }
    }

    fun renameChat(chatId: String, newName: String) = viewModelScope.launch {
        chatMeta.update(chatId) { it.copy(name = newName) }
    }

    fun setChatCategory(chatId: String, category: ChatCategory) = viewModelScope.launch {
        chatMeta.update(chatId) { it.copy(category = category.name) }
    }

    /**
     * Deletes the chat's pastes from the server, then forgets it locally.
     *
     * Removing it from the in-memory list alone was not a delete: the next
     * `loadChats()` re-derives every chat from the file listing, so the chat
     * came straight back. rustypaste only permits DELETE when the server has
     * a delete token configured, so a server-side failure is expected and is
     * not treated as an error - the chat is marked hidden instead, which
     * keeps it out of the list without pretending the files are gone.
     */
    fun deleteChat(chatId: String) = viewModelScope.launch {
        val fileNames = lastMessages.filter { it.chatId == chatId }.mapNotNull { it.pasteFileName }
        var failed = 0
        for (name in fileNames) {
            pasteRepo.deleteFile(name).onFailure { failed++ }
        }
        allChats.removeAll { it.id == chatId }
        if (failed > 0) {
            chatMeta.update(chatId) { it.copy(hidden = true) }
            _state.update {
                it.copy(error = OneTimeEvent("Chat hidden. $failed of ${fileNames.size} pastes could not be deleted (server has no delete token)."))
            }
        } else {
            chatMeta.remove(chatId)
        }
        lastMessages = lastMessages.filterNot { it.chatId == chatId }
        republish()
    }

    private fun filterByCategory(chats: List<ChatThread>, category: ChatCategory?): List<ChatThread> {
        return if (category == null) chats else chats.filter { it.category == category }
    }

    private fun groupMessagesByChat(messages: List<Message>, meta: Map<String, ChatMeta>): List<ChatThread> =
        buildChatThreads(messages, meta)

}

/**
 * Turns a flat message list plus the persisted per-chat metadata into the
 * threads the chat list renders.
 *
 * Top-level and pure so the unread arithmetic is testable without a
 * ViewModel, a DataStore or a server: `unreadCount` was rendered as a badge
 * for months while nothing ever computed it, and a badge nobody can test is
 * how that survives.
 */
internal fun buildChatThreads(
    messages: List<Message>,
    meta: Map<String, ChatMeta>
): List<ChatThread> {
    val grouped = messages.groupBy { it.chatId }
    return grouped.map { (chatId, msgs) ->
        val last = msgs.maxByOrNull { it.timestamp }
        val m = meta[chatId]
        val readIds = m?.readIds ?: emptySet()
        ChatThread(
            id = chatId,
            name = m?.name ?: defaultChatName(chatId),
            lastMessage = last?.text?.take(80) ?: "",
            lastTimestamp = last?.timestamp ?: 0L,
            messageCount = msgs.size,
            // Only messages the user did not write themselves can be unread.
            // Counting your own sent messages would tick the badge up every
            // time you sent something.
            unreadCount = msgs.count { !it.isOutgoing && it.id !in readIds },
            isArchived = m?.archived ?: false,
            isMuted = m?.muted ?: false,
            category = m?.categoryOrDefault ?: ChatCategory.GENERAL,
            avatarColor = m?.avatarColor ?: 0xFF1A73E8
        )
    }.sortedByDescending { it.lastTimestamp }
}

internal fun defaultChatName(id: String): String = when (id) {
    Message.DEFAULT_CHAT -> "General"
    else -> "Chat $id"
}
