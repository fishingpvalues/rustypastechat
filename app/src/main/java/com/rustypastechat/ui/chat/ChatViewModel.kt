package com.rustypastechat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.LlmMessage
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.data.model.PasteItem
import com.rustypastechat.data.repository.LlmRepository
import com.rustypastechat.data.repository.PasteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val error: String? = null,
    val typingMessage: String = "",
    val isLlmTyping: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val pasteRepository = PasteRepository(preferencesManager)
    private val llmRepository = LlmRepository(preferencesManager)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings, isConnected = settings.pasteServerUrl.isNotBlank()) }
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
        val msg = Message(
            id = messageId,
            text = text,
            isOutgoing = true,
            status = MessageStatus.SENDING
        )

        _uiState.update { it.copy(messages = it.messages + msg, typingMessage = "") }

        viewModelScope.launch {
            val fileName = "msg_${messageId.take(8)}.txt"
            val result = pasteRepository.uploadText(text, fileName)
            result.onSuccess {
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED) else m
                    }
                    state.copy(messages = updated)
                }
                checkLlmAutoReply(text)
            }.onFailure {
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                    }
                    state.copy(messages = updated, error = it.message)
                }
            }
        }
    }

    fun sendMediaMessage(filePath: String, fileName: String) {
        val messageId = UUID.randomUUID().toString()
        val msg = Message(
            id = messageId,
            text = fileName,
            isOutgoing = true,
            status = MessageStatus.SENDING,
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
                        if (m.id == messageId) m.copy(
                            status = MessageStatus.DELIVERED,
                            mediaUrl = url
                        ) else m
                    }
                    state.copy(messages = updated)
                }
            }.onFailure {
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                    }
                    state.copy(messages = updated, error = it.message)
                }
            }
        }
    }

    fun retryMessage(messageId: String) {
        val msgIndex = _uiState.value.messages.indexOfFirst { it.id == messageId }
        if (msgIndex < 0) return

        val msg = _uiState.value.messages[msgIndex]
        _uiState.update { state ->
            val updated = state.messages.map { m ->
                if (m.id == messageId) m.copy(status = MessageStatus.SENDING) else m
            }
            state.copy(messages = updated)
        }

        if (msg.mediaUrl != null && msg.mediaType != null) {
            sendMediaMessage(msg.mediaUrl, msg.pasteFileName ?: "file")
        } else {
            viewModelScope.launch {
                val fileName = "msg_${messageId.take(8)}.txt"
                val result = pasteRepository.uploadText(msg.text, fileName)
                result.onSuccess {
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED) else m
                        }
                        state.copy(messages = updated)
                    }
                }.onFailure {
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                        }
                        state.copy(messages = updated, error = it.message)
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
            val streamMessageId = UUID.randomUUID().toString()
            var streamedText = ""

            val streamMsg = Message(
                id = streamMessageId,
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
                                if (m.id == streamMessageId) m.copy(text = streamedText) else m
                            }
                            state.copy(messages = updated)
                        }
                    },
                    onComplete = {
                        _uiState.update { it.copy(isLlmTyping = false) }
                    },
                    onError = { error ->
                        _uiState.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == streamMessageId) m.copy(
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

    private fun buildLlmContext(currentText: String): List<LlmMessage> {
        val messages = mutableListOf<LlmMessage>()
        messages.add(LlmMessage("system", "You are a helpful assistant chatting via a pastebin service. Keep responses concise and helpful."))

        val history = _uiState.value.messages.filter { it.isOutgoing || it.isLlmResponse }
        for (msg in history.takeLast(10)) {
            val role = if (msg.isOutgoing) "user" else "assistant"
            messages.add(LlmMessage(role, msg.text))
        }
        messages.add(LlmMessage("user", currentText))
        return messages
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
