package com.rustypastechat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.LlmMessage
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.data.repository.LlmRepository
import com.rustypastechat.data.repository.PasteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val error: String? = null,
    val typingMessage: String = "",
    val isLlmTyping: Boolean = false,
    val historyLoaded: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val pasteRepository = PasteRepository(preferencesManager)
    private val llmRepository = LlmRepository(preferencesManager)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var previousServerUrl: String? = null

    init {
        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                val wasDisconnected = previousServerUrl != settings.pasteServerUrl
                previousServerUrl = settings.pasteServerUrl

                _uiState.update {
                    it.copy(settings = settings, isConnected = settings.pasteServerUrl.isNotBlank())
                }

                if (settings.pasteServerUrl.isNotBlank() &&
                    (!_uiState.value.historyLoaded || wasDisconnected)) {
                    loadChatHistory()
                }
            }
        }
    }

    fun loadChatHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            pasteRepository.loadChatHistory()
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            historyLoaded = true,
                            isRefreshing = false,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoading = false,
                            isConnected = false,
                            error = "Could not load chat history: ${e.message}"
                        )
                    }
                }
        }
    }

    fun updateTypingMessage(text: String) {
        _uiState.update { it.copy(typingMessage = text) }
    }

    fun sendTextMessage() {
        val text = _uiState.value.typingMessage.trim()
        if (text.isBlank()) return

        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val fileName = Message.buildFileName(now, true, messageId)

        val msg = Message(
            id = messageId,
            text = text,
            isOutgoing = true,
            status = MessageStatus.SENDING,
            timestamp = now,
            pasteFileName = fileName
        )

        _uiState.update { it.copy(messages = it.messages + msg, typingMessage = "") }

        viewModelScope.launch {
            val result = pasteRepository.uploadText(text, fileName)
            result.onSuccess {
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED) else m
                    }
                    state.copy(messages = updated)
                }
                checkLlmAutoReply(text)
            }.onFailure { e ->
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                    }
                    state.copy(messages = updated, error = e.message)
                }
            }
        }
    }

    fun sendMediaMessage(filePath: String, fileNameOriginal: String) {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val ext = fileNameOriginal.substringAfterLast('.', "jpg")
        val fileName = Message.buildMediaFileName(now, messageId, ext)

        val msg = Message(
            id = messageId,
            text = fileNameOriginal,
            isOutgoing = true,
            status = MessageStatus.SENDING,
            timestamp = now,
            mediaUrl = filePath,
            mediaType = com.rustypastechat.data.model.MediaType.IMAGE,
            pasteFileName = fileName
        )

        _uiState.update { it.copy(messages = it.messages + msg) }

        viewModelScope.launch {
            val result = pasteRepository.uploadFile(filePath, fileName)
            result.onSuccess {
                _uiState.update { state ->
                    val settings = state.settings
                    val url = pasteRepository.getFileUrl(settings, fileName)
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED, mediaUrl = url)
                        else m
                    }
                    state.copy(messages = updated)
                }
            }.onFailure { e ->
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                    }
                    state.copy(messages = updated, error = e.message)
                }
            }
        }
    }

    fun retryMessage(messageId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        _uiState.update { state ->
            val updated = state.messages.map { m ->
                if (m.id == messageId) m.copy(status = MessageStatus.SENDING) else m
            }
            state.copy(messages = updated)
        }

        if (msg.mediaUrl != null && msg.mediaType != null) {
            sendMediaMessage(msg.mediaUrl, msg.pasteFileName ?: "file.jpg")
        } else {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val fileName = msg.pasteFileName ?: Message.buildFileName(now, true, messageId)
                val result = pasteRepository.uploadText(msg.text, fileName)
                result.onSuccess {
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == messageId) m.copy(
                                status = MessageStatus.DELIVERED,
                                pasteFileName = fileName
                            ) else m
                        }
                        state.copy(messages = updated)
                    }
                }.onFailure { e ->
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                        }
                        state.copy(messages = updated, error = e.message)
                    }
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != messageId })
        }
    }

    private fun checkLlmAutoReply(userText: String) {
        val settings = _uiState.value.settings
        if (!settings.llmEnabled || settings.llmEndpoint.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLlmTyping = true) }
            val replyId = UUID.randomUUID().toString()
            var streamedText = ""

            val streamMsg = Message(
                id = replyId,
                text = "",
                isOutgoing = false,
                status = MessageStatus.DELIVERED,
                isLlmResponse = true
            )
            _uiState.update { it.copy(messages = it.messages + streamMsg) }

            val llmMessages = buildLlmContext(userText)
            withContext(Dispatchers.IO) {
                llmRepository.sendMessage(
                    messages = llmMessages,
                    onDelta = { delta ->
                        streamedText += delta
                        _uiState.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == replyId) m.copy(text = streamedText) else m
                            }
                            state.copy(messages = updated)
                        }
                    },
                    onComplete = {
                        _uiState.update { it.copy(isLlmTyping = false) }
                        persistLlmReply(replyId, streamedText)
                    },
                    onError = { error ->
                        _uiState.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == replyId) m.copy(
                                    text = streamedText.ifBlank { "Error: $error" },
                                    status = MessageStatus.FAILED
                                ) else m
                            }
                            state.copy(messages = updated, isLlmTyping = false, error = error)
                        }
                    }
                )
            }
        }
    }

    private fun persistLlmReply(replyId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val fileName = Message.buildFileName(now, false, replyId)
            pasteRepository.uploadText(text, fileName)
                .onSuccess {
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == replyId) m.copy(
                                pasteFileName = fileName,
                                timestamp = now
                            ) else m
                        }
                        state.copy(messages = updated)
                    }
                }
        }
    }

    private fun buildLlmContext(currentText: String): List<LlmMessage> {
        val messages = mutableListOf<LlmMessage>()
        messages.add(LlmMessage("system", "You are a helpful assistant chatting via a pastebin service. Keep responses concise and helpful."))
        val history = _uiState.value.messages.filter { it.isOutgoing || it.isLlmResponse }
        for (msg in history.takeLast(10)) {
            val role = if (msg.isOutgoing) "user" else "assistant"
            if (msg.text.isNotBlank()) {
                messages.add(LlmMessage(role, msg.text))
            }
        }
        messages.add(LlmMessage("user", currentText))
        return messages
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
