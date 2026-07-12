package com.rustypastechat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.LlmMessage
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.data.model.PasteItem
import com.rustypastechat.data.model.ReplyTarget
import com.rustypastechat.data.repository.LlmRepository
import com.rustypastechat.data.repository.PasteRepository
import com.rustypastechat.di.IoDispatcher
import com.rustypastechat.ui.common.OneTimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val error: OneTimeEvent<String?> = OneTimeEvent(null),
    val typingMessage: String = "",
    val isLlmTyping: Boolean = false,
    val historyLoaded: Boolean = false,
    val replyTarget: ReplyTarget? = null,
    val isOneshotMode: Boolean = false,
    val messageTtlSeconds: Long = 0L,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val editingMessageId: String? = null,
    val editingMessageText: String = "",
    val isRecordingVoice: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val viewedOneshotIds: Set<String> = emptySet(),
    val starredIds: Set<String> = emptySet(),
    val forwardTargetMessageId: String? = null,
    val forwardChatOptions: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesManager: PreferencesManager,
    private val pasteRepository: PasteRepository,
    private val llmRepository: LlmRepository,
    private val imageProcessor: ImageProcessor,
    private val voiceRecorder: VoiceRecorder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentChatId: String = Message.DEFAULT_CHAT
    private var previousServerUrl: String? = null
    private var draftSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val starred = preferencesManager.starredIdsFlow.first()
            _uiState.update { it.copy(starredIds = starred) }
        }
        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                val wasDisconnected = previousServerUrl != settings.pasteServerUrl
                previousServerUrl = settings.pasteServerUrl
                _uiState.update { it.copy(settings = settings, isConnected = settings.pasteServerUrl.isNotBlank()) }
                if (settings.pasteServerUrl.isNotBlank() &&
                    (!_uiState.value.historyLoaded || wasDisconnected)) {
                    loadChatHistory()
                }
            }
        }
    }

    fun setChatId(chatId: String) {
        if (currentChatId != chatId) {
            val previousChatId = currentChatId
            val draftToFlush = _uiState.value.typingMessage
            currentChatId = chatId
            draftSaveJob?.cancel()
            _uiState.update { it.copy(historyLoaded = false, messages = emptyList(), typingMessage = "") }
            loadChatHistory()
            viewModelScope.launch {
                preferencesManager.saveDraft(previousChatId, draftToFlush)
                val draft = preferencesManager.getDraft(chatId)
                if (draft.isNotEmpty() && currentChatId == chatId) {
                    _uiState.update { it.copy(typingMessage = draft) }
                }
            }
        }
    }

    fun loadChatHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = OneTimeEvent(null)) }
            pasteRepository.loadChatHistory(currentChatId)
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(messages = messages, historyLoaded = true, isRefreshing = false, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false, isConnected = false,
                            error = OneTimeEvent("Could not load chat history: ${e.message}"))
                    }
                }
        }
    }

    fun updateTypingMessage(text: String) {
        _uiState.update { it.copy(typingMessage = text) }
        val chatId = currentChatId
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(500)
            preferencesManager.saveDraft(chatId, text)
        }
    }

    fun setReplyTarget(target: ReplyTarget?) {
        _uiState.update { it.copy(replyTarget = target) }
    }

    fun toggleOneshotMode() {
        _uiState.update { it.copy(isOneshotMode = !it.isOneshotMode) }
    }

    fun setMessageTtl(seconds: Long) {
        _uiState.update { it.copy(messageTtlSeconds = if (it.messageTtlSeconds == seconds) 0L else seconds) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchMode() {
        _uiState.update { it.copy(isSearchMode = !it.isSearchMode, searchQuery = "", editingMessageId = null) }
    }

    fun startEditingMessage(messageId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!msg.isOutgoing) return
        _uiState.update { it.copy(editingMessageId = messageId, editingMessageText = msg.text) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessageId = null, editingMessageText = "") }
    }

    fun updateEditingText(text: String) {
        _uiState.update { it.copy(editingMessageText = text) }
    }

    fun saveEditedMessage() {
        val id = _uiState.value.editingMessageId ?: return
        val newText = _uiState.value.editingMessageText.trim()
        if (newText.isBlank()) return

        val msg = _uiState.value.messages.find { it.id == id } ?: return
        _uiState.update { state ->
            val updated = state.messages.map { m ->
                if (m.id == id) m.copy(text = newText, status = MessageStatus.SENDING) else m
            }
            state.copy(messages = updated, editingMessageId = null, editingMessageText = "")
        }

        viewModelScope.launch {
            val fileName = msg.pasteFileName ?: Message.buildFileName(currentChatId, System.currentTimeMillis(), true, id)
            pasteRepository.uploadText(newText, fileName)
                .onSuccess {
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == id) m.copy(status = MessageStatus.DELIVERED, pasteFileName = fileName) else m
                        }
                        state.copy(messages = updated)
                    }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == id) m.copy(status = MessageStatus.FAILED) else m
                        }
                        state.copy(messages = updated, error = OneTimeEvent(e.message))
                    }
                }
        }
    }

    fun getFilteredMessages(): List<Message> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val all = _uiState.value.messages
        if (query.isBlank()) return all
        return all.filter { it.text.lowercase().contains(query) }
    }

    /** Toggles the marker pair around the whole draft (no rich-text field means no selection
     *  to wrap, so this wraps/unwraps the entire message rather than a text range). */
    fun insertFormatting(marker: String) {
        val text = _uiState.value.typingMessage
        val alreadyWrapped = text.length >= marker.length * 2 &&
            text.startsWith(marker) && text.endsWith(marker)
        val updated = if (alreadyWrapped) {
            text.removePrefix(marker).removeSuffix(marker)
        } else {
            "$marker$text$marker"
        }
        _uiState.update { it.copy(typingMessage = updated) }
    }

    fun sendTextMessage() {
        val text = _uiState.value.typingMessage.trim()
        if (text.isBlank()) return

        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val fileName = Message.buildFileName(currentChatId, now, true, messageId)
        val reply = _uiState.value.replyTarget
        val isOneshot = _uiState.value.isOneshotMode
        val ttl = _uiState.value.messageTtlSeconds

        val msg = Message(
            id = messageId, text = text, isOutgoing = true,
            status = MessageStatus.SENDING, timestamp = now,
            pasteFileName = fileName,
            replyToId = reply?.messageId, replyToText = reply?.text, replyToIsOutgoing = reply?.isOutgoing,
            isOneshot = isOneshot,
            expiresAt = if (ttl > 0) now + ttl * 1000 else null
        )

        _uiState.update {
            it.copy(messages = it.messages + msg, typingMessage = "", replyTarget = null, isOneshotMode = false)
        }
        draftSaveJob?.cancel()
        viewModelScope.launch { preferencesManager.saveDraft(currentChatId, "") }

        viewModelScope.launch {
            val result = if (isOneshot) {
                pasteRepository.uploadOneshot(text, fileName)
            } else if (ttl > 0) {
                pasteRepository.uploadTextWithExpiry(text, fileName, ttl)
            } else {
                pasteRepository.uploadText(text, fileName)
            }
            result.onSuccess {
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED) else m
                    }
                    state.copy(messages = updated)
                }
                if (!isOneshot) checkLlmAutoReply(text)
            }.onFailure { e ->
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                    }
                    state.copy(messages = updated, error = OneTimeEvent(e.message))
                }
            }
        }
    }

    fun sendMediaMessage(
        filePath: String,
        fileNameOriginal: String,
        mediaType: com.rustypastechat.data.model.MediaType = com.rustypastechat.data.model.MediaType.IMAGE
    ) {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val ext = fileNameOriginal.substringAfterLast('.', "jpg")
        val fileName = Message.buildMediaFileName(currentChatId, now, messageId, ext)
        val reply = _uiState.value.replyTarget

        val msg = Message(
            id = messageId, text = fileNameOriginal, isOutgoing = true,
            status = MessageStatus.SENDING, timestamp = now,
            mediaUrl = filePath, mediaType = mediaType,
            pasteFileName = fileName,
            replyToId = reply?.messageId, replyToText = reply?.text, replyToIsOutgoing = reply?.isOutgoing
        )

        _uiState.update { it.copy(messages = it.messages + msg, replyTarget = null) }

        viewModelScope.launch {
            pasteRepository.uploadFile(filePath, fileName)
                .onSuccess {
                    _uiState.update { state ->
                        val url = pasteRepository.getFileUrl(state.settings, fileName)
                        val updated = state.messages.map { m ->
                            if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED, mediaUrl = url) else m
                        }
                        state.copy(messages = updated)
                    }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                        }
                        state.copy(messages = updated, error = OneTimeEvent(e.message))
                    }
                }
        }
    }

    /** Entry point for anything picked via the generic attach button — routes images through
     *  compression, copies video/other files to cache as-is, and tags the correct [MediaType]. */
    fun sendPickedMedia(uri: Uri) {
        val mime = appContext.contentResolver.getType(uri)
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.let { ext ->
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            } ?: ""
        when {
            mime.startsWith("image/") -> compressAndSendMedia(uri)
            mime.startsWith("video/") -> copyAndSendRawMedia(uri, com.rustypastechat.data.model.MediaType.VIDEO)
            else -> copyAndSendRawMedia(uri, com.rustypastechat.data.model.MediaType.FILE)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun copyAndSendRawMedia(uri: Uri, mediaType: com.rustypastechat.data.model.MediaType) {
        viewModelScope.launch(ioDispatcher) {
            val originalName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"
            val ext = originalName.substringAfterLast('.', if (mediaType == com.rustypastechat.data.model.MediaType.VIDEO) "mp4" else "bin")
            val tempFile = java.io.File(appContext.cacheDir, "upload_${System.currentTimeMillis()}.$ext")
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw java.io.IOException("Could not open file")
            }.onSuccess {
                sendMediaMessage(tempFile.absolutePath, originalName, mediaType)
            }.onFailure { e ->
                _uiState.update { it.copy(error = OneTimeEvent("Attachment error: ${e.message}")) }
            }
        }
    }

    fun compressAndSendMedia(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val tempFile = java.io.File(appContext.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            val quality = _uiState.value.settings.imageQuality
            imageProcessor.compressImage(uri, tempFile, quality.maxDimension, quality.jpegQuality)
                .onSuccess { compressed ->
                    sendMediaMessage(compressed.absolutePath, uri.lastPathSegment ?: "image.jpg")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = OneTimeEvent("Image error: ${e.message}")) }
                }
        }
    }

    private var recordingJob: Job? = null

    fun startRecordingVoice() {
        if (_uiState.value.isRecordingVoice) return
        voiceRecorder.start(_uiState.value.settings.voiceQuality)
            .onSuccess {
                _uiState.update { it.copy(isRecordingVoice = true, recordingElapsedMs = 0L) }
                val startedAt = System.currentTimeMillis()
                recordingJob = viewModelScope.launch {
                    while (true) {
                        delay(100)
                        _uiState.update { it.copy(recordingElapsedMs = System.currentTimeMillis() - startedAt) }
                    }
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = OneTimeEvent("Could not start recording: ${e.message}")) }
            }
    }

    fun stopRecordingAndSendVoice() {
        recordingJob?.cancel()
        recordingJob = null
        val recorded = voiceRecorder.stop()
        _uiState.update { it.copy(isRecordingVoice = false, recordingElapsedMs = 0L) }
        if (recorded != null) {
            val (file, _) = recorded
            sendMediaMessage(file.absolutePath, "Voice message", com.rustypastechat.data.model.MediaType.AUDIO)
        }
    }

    fun cancelRecordingVoice() {
        recordingJob?.cancel()
        recordingJob = null
        voiceRecorder.cancel()
        _uiState.update { it.copy(isRecordingVoice = false, recordingElapsedMs = 0L) }
    }

    /** WhatsApp/Signal-style burn-after-reading: the bubble hides content until this is called,
     *  then hides it again permanently — this only gates local rendering (once-only server
     *  delivery is already enforced by the paste server's own oneshot semantics). */
    fun markOneshotViewed(messageId: String) {
        _uiState.update { it.copy(viewedOneshotIds = it.viewedOneshotIds + messageId) }
    }

    fun toggleStarred(messageId: String) {
        _uiState.update { state ->
            val starred = state.starredIds
            state.copy(starredIds = if (messageId in starred) starred - messageId else starred + messageId)
        }
        viewModelScope.launch { preferencesManager.saveStarredIds(_uiState.value.starredIds) }
    }

    fun copyMessage(messageId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", msg.text))
    }

    fun replyToMessage(messageId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        _uiState.update {
            it.copy(replyTarget = ReplyTarget(msg.id, msg.text.take(80), msg.isOutgoing))
        }
    }

    fun deleteMessage(messageId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != messageId })
        }
        msg.pasteFileName?.let { fileName ->
            viewModelScope.launch { pasteRepository.deleteFile(fileName) }
        }
    }

    /** Opens the chat-picker for a real forward (re-uploads the message under the target
     *  chat's file prefix) instead of the old fake "copy text into my own composer" behavior. */
    fun forwardMessage(messageId: String) {
        _uiState.update { it.copy(forwardTargetMessageId = messageId) }
        viewModelScope.launch {
            val messages = pasteRepository.loadAllMessages().getOrNull() ?: emptyList()
            val options = messages.map { it.chatId }.distinct()
                .filter { it != currentChatId }
                .map { id -> id to (if (id == Message.DEFAULT_CHAT) "General" else "Chat $id") }
            _uiState.update { it.copy(forwardChatOptions = options) }
        }
    }

    fun cancelForward() {
        _uiState.update { it.copy(forwardTargetMessageId = null, forwardChatOptions = emptyList()) }
    }

    fun forwardTo(targetChatId: String) {
        val messageId = _uiState.value.forwardTargetMessageId ?: return
        val msg = _uiState.value.messages.find { it.id == messageId }
        _uiState.update { it.copy(forwardTargetMessageId = null, forwardChatOptions = emptyList()) }
        if (msg == null) return

        viewModelScope.launch(ioDispatcher) {
            val newId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val forwarded = if (msg.mediaUrl != null && msg.mediaType != null && msg.pasteFileName != null) {
                val ext = msg.pasteFileName.substringAfterLast('.', "bin")
                val newFileName = Message.buildMediaFileName(targetChatId, now, newId, ext)
                pasteRepository.getFileContent(msg.pasteFileName).mapCatching { bytes ->
                    val tempFile = java.io.File(appContext.cacheDir, "forward_$now.$ext")
                    tempFile.writeBytes(bytes)
                    val result = pasteRepository.uploadFile(tempFile.absolutePath, newFileName)
                    tempFile.delete()
                    result.getOrThrow()
                }
            } else if (msg.text.isNotBlank()) {
                val newFileName = Message.buildFileName(targetChatId, now, true, newId)
                pasteRepository.uploadText(msg.text, newFileName)
            } else {
                Result.failure(IllegalStateException("Nothing to forward"))
            }
            val targetName = if (targetChatId == Message.DEFAULT_CHAT) "General" else "Chat $targetChatId"
            forwarded.onSuccess {
                _uiState.update { it.copy(error = OneTimeEvent("Forwarded to $targetName")) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = OneTimeEvent("Forward failed: ${e.message}")) }
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
            sendMediaMessage(msg.mediaUrl, msg.text.ifBlank { msg.pasteFileName ?: "file" }, msg.mediaType)
        } else {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val fileName = msg.pasteFileName ?: Message.buildFileName(currentChatId, now, true, messageId)
                pasteRepository.uploadText(msg.text, fileName)
                    .onSuccess {
                        _uiState.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == messageId) m.copy(status = MessageStatus.DELIVERED, pasteFileName = fileName) else m
                            }
                            state.copy(messages = updated)
                        }
                    }
                    .onFailure { e ->
                        _uiState.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == messageId) m.copy(status = MessageStatus.FAILED) else m
                            }
                            state.copy(messages = updated, error = OneTimeEvent(e.message))
                        }
                    }
            }
        }
    }

    private fun checkLlmAutoReply(userText: String) {
        val settings = _uiState.value.settings
        if (!settings.llmEnabled || settings.llmEndpoint.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLlmTyping = true) }
            val replyId = UUID.randomUUID().toString()
            var streamedText = ""

            val streamMsg = Message(id = replyId, text = "", isOutgoing = false,
                status = MessageStatus.DELIVERED, isLlmResponse = true)
            _uiState.update { it.copy(messages = it.messages + streamMsg) }

            val llmMessages = buildLlmContext(userText)
            withContext(ioDispatcher) {
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
                            state.copy(messages = updated, isLlmTyping = false, error = OneTimeEvent(error))
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
            val fileName = Message.buildFileName(currentChatId, now, false, replyId)
            pasteRepository.uploadText(text, fileName)
                .onSuccess {
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == replyId) m.copy(pasteFileName = fileName, timestamp = now) else m
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
            if (msg.text.isNotBlank()) messages.add(LlmMessage(role, msg.text))
        }
        messages.add(LlmMessage("user", currentText))
        return messages
    }

    override fun onCleared() {
        super.onCleared()
        // voiceRecorder is a Hilt singleton, independent of this ViewModel's lifecycle —
        // navigating away mid-recording must not leave the mic held open indefinitely.
        if (_uiState.value.isRecordingVoice) voiceRecorder.cancel()
    }
}
