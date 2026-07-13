package com.rustypastechat.data.repository

import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.ChatHistoryPage
import com.rustypastechat.data.model.MediaType
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.data.model.ParsedFileName
import com.rustypastechat.data.model.PasteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasteRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val apiClientFactory: ApiClientFactory
) {
    private var api: com.rustypastechat.data.api.RustyPasteApi? = null
    private var apiBaseUrl: String? = null

    private suspend fun getApi(): com.rustypastechat.data.api.RustyPasteApi {
        val settings = preferencesManager.settingsFlow.first()
        if (settings.pasteServerUrl.isBlank()) {
            throw IllegalStateException("Paste server is not configured. Set a server URL in Settings.")
        }
        if (api == null || apiBaseUrl != settings.pasteServerUrl) {
            api = apiClientFactory.createPasteApi(settings.pasteServerUrl)
            apiBaseUrl = settings.pasteServerUrl
        }
        return api!!
    }

    suspend fun listFiles(): Result<List<PasteItem>> = runCatching {
        val response = getApi().listFiles()
        if (response.isSuccessful) response.body() ?: emptyList()
        else throw Exception("List failed: ${response.code()}")
    }

    suspend fun uploadFile(filePath: String, fileName: String): Result<String> = runCatching {
        val file = File(filePath)
        if (!file.exists()) throw Exception("File not found")
        val mimeType = file.toURI().toURL().openConnection().contentType
            ?: "application/octet-stream"
        val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val response = getApi().uploadFile(filePart)
        if (response.isSuccessful) response.body()?.string()?.trim() ?: fileName
        else throw Exception("Upload failed: ${response.code()}")
    }

    suspend fun uploadText(text: String, fileName: String): Result<String> = runCatching {
        val requestBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val response = getApi().uploadFile(filePart)
        if (response.isSuccessful) response.body()?.string()?.trim() ?: fileName
        else throw Exception("Upload failed: ${response.code()}")
    }

    suspend fun uploadTextWithExpiry(text: String, fileName: String, ttlSeconds: Long): Result<String> = runCatching {
        val requestBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val expireHeader = "${ttlSeconds}s"
        val response = getApi().uploadFile(filePart, expire = expireHeader)
        if (response.isSuccessful) response.body()?.string()?.trim() ?: fileName
        else throw Exception("Upload with expiry failed: ${response.code()}")
    }

    suspend fun uploadOneshot(text: String, fileName: String): Result<String> = runCatching {
        val requestBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("oneshot", fileName, requestBody)
        val oneshotField = fileName.toRequestBody("text/plain".toMediaTypeOrNull())
        val response = getApi().uploadOneshot(filePart, oneshotField)
        if (response.isSuccessful) response.body()?.string()?.trim() ?: fileName
        else throw Exception("Oneshot upload failed: ${response.code()}")
    }

    /** Updates a text paste's content in place. rustypaste refuses to re-upload an existing
     *  filename ("file already exists"), so this uploads the new content under a fresh
     *  filename, marks [oldFileName] as superseded (so it's filtered out of reconstruction
     *  even if the best-effort delete below fails, e.g. no delete token configured), and
     *  returns the new filename to store as the message's `pasteFileName` going forward. */
    suspend fun updatePasteContent(
        oldFileName: String,
        newContent: String,
        chatId: String,
        timestamp: Long,
        isOutgoing: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val newFileName = Message.buildFileName(chatId, timestamp, isOutgoing, UUID.randomUUID().toString())
            uploadText(newContent, newFileName).getOrThrow()
            preferencesManager.addSupersededFileName(oldFileName)
            deleteFile(oldFileName)
            newFileName
        }
    }

    suspend fun deleteFile(filename: String): Result<Unit> = runCatching {
        val response = getApi().deleteFile(filename)
        if (response.isSuccessful) Unit
        else throw Exception("Delete failed: ${response.code()}")
    }

    suspend fun getFileContent(filename: String): Result<ByteArray> = runCatching {
        val response = getApi().getFile(filename)
        if (response.isSuccessful) response.body()?.bytes() ?: ByteArray(0)
        else throw Exception("Download failed: ${response.code()}")
    }

    fun getFileUrl(settings: AppSettings, filename: String): String {
        val base = settings.pasteServerUrl.trimEnd('/')
        return "$base/$filename"
    }

    private fun mediaTypeForExtension(fileName: String): MediaType? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaType.IMAGE
            "mp4", "webm", "mkv", "mov", "3gp" -> MediaType.VIDEO
            "m4a", "aac", "mp3", "ogg", "opus", "wav" -> MediaType.AUDIO
            else -> null
        }
    }

    /** Reconstructs every message on the server, across every chat. Used by the chat list
     *  overview, which needs to see all chats at once — for a single chat's messages, use
     *  [loadChatHistory] instead. */
    suspend fun loadAllMessages(): Result<List<Message>> = withContext(Dispatchers.IO) {
        runCatching {
            val superseded = preferencesManager.supersededFileNamesFlow.first()
            val pastes = listFiles().getOrThrow().filter { it.fileName !in superseded }
            val settings = preferencesManager.settingsFlow.first()

            val chatPastes = pastes.filter { Message.isChatFile(it.fileName) }
            val otherPastes = pastes.filter { !Message.isChatFile(it.fileName) }

            val chatContentSet = mutableSetOf<String>()

            val chatOnlyPastes = chatPastes.sortedBy { it.creationDateUtc ?: "" }
            val chatMessages = coroutineScope {
                chatOnlyPastes.map { paste ->
                    async { pasteToMessage(paste, settings) }
                }.awaitAll().filterNotNull().also { messages ->
                    messages.forEach { msg -> msg.text.let { chatContentSet.add(it) } }
                }
            }

            val importedPastes = otherPastes.sortedBy { it.creationDateUtc ?: "" }
            val importedMessages = coroutineScope {
                importedPastes.map { paste ->
                    async { pasteToImportedMessage(paste, settings, chatContentSet) }
                }.awaitAll().filterNotNull()
            }

            (chatMessages + importedMessages).sortedBy { it.timestamp }
        }
    }

    /**
     * Reconstructs one page of a single chat's messages, newest-first-then-reversed, without
     * downloading content for every other chat's messages too — [loadAllMessages] does that,
     * which is fine once for the chat-list overview but would make opening a single chat pay
     * for every message on the whole server.
     *
     * Paging works off each filename's own embedded timestamp (cheap — no network needed to
     * sort), so only the [pageSize] messages actually being shown ever get downloaded. Pass
     * the oldest timestamp already loaded as [beforeTimestamp] to fetch the next page back.
     *
     * Legacy/imported pastes have no embedded timestamp of their own and predate multi-chat
     * support, so they only ever surface on the default chat's first page (dedup against
     * already-superseded content is scoped to that page, not the whole chat — an accepted
     * tradeoff for not downloading the whole server just to page one chat).
     */
    suspend fun loadChatHistory(
        chatId: String = Message.DEFAULT_CHAT,
        beforeTimestamp: Long? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<ChatHistoryPage> = withContext(Dispatchers.IO) {
        runCatching {
            val superseded = preferencesManager.supersededFileNamesFlow.first()
            val pastes = listFiles().getOrThrow().filter { it.fileName !in superseded }
            val settings = preferencesManager.settingsFlow.first()

            val chatPastesForThisChat = pastes.filter {
                Message.isChatFile(it.fileName) && Message.extractChatId(it.fileName) == chatId
            }
            val sortedNewestFirst = chatPastesForThisChat.sortedByDescending { embeddedTimestamp(it.fileName) }
            val windowed = if (beforeTimestamp == null) sortedNewestFirst
                else sortedNewestFirst.filter { embeddedTimestamp(it.fileName) < beforeTimestamp }
            val page = windowed.take(pageSize)
            val hasMore = windowed.size > pageSize

            val chatMessages = coroutineScope {
                page.map { paste -> async { pasteToMessage(paste, settings) } }.awaitAll().filterNotNull()
            }

            val importedMessages = if (chatId == Message.DEFAULT_CHAT && beforeTimestamp == null) {
                val otherPastes = pastes.filter { !Message.isChatFile(it.fileName) }
                val chatContentSet = chatMessages.mapTo(mutableSetOf()) { it.text }
                coroutineScope {
                    otherPastes.sortedBy { it.creationDateUtc ?: "" }.map { paste ->
                        async { pasteToImportedMessage(paste, settings, chatContentSet) }
                    }.awaitAll().filterNotNull()
                }
            } else {
                emptyList()
            }

            ChatHistoryPage(
                messages = (chatMessages + importedMessages).sortedBy { it.timestamp },
                hasMore = hasMore
            )
        }
    }

    private fun embeddedTimestamp(fileName: String): Long = Message.parseFromFileName(fileName)?.timestamp ?: 0L

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }

    private suspend fun pasteToImportedMessage(
        paste: PasteItem,
        settings: AppSettings,
        existingContent: Set<String>
    ): Message? {
        if (paste.fileSize <= 0) return null
        if (paste.fileName.contains(".[0-9]".toRegex()) && paste.fileName.length > 20) return null

        val content = getFileContent(paste.fileName).getOrNull() ?: return null
        val text = String(content, Charsets.UTF_8)

        if (text in existingContent) return null
        if (paste.fileSize > 100_000) return null
        if (!text.all { it.isLetterOrDigit() || it.isWhitespace() || it in "!@#$%^&*()_+-=[]{}|;':\",./<>?`~" }) {
            return null
        }

        val creationTs = parseCreationTimestamp(paste.creationDateUtc) ?: System.currentTimeMillis()
        val isMedia = paste.fileName.substringAfterLast('.', "").lowercase() in
            listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "webm")

        return Message(
            id = "imported_${paste.fileName}",
            text = if (isMedia) "[Image: ${paste.fileName}]" else text.take(500),
            isOutgoing = false,
            status = MessageStatus.DELIVERED,
            timestamp = creationTs,
            mediaUrl = if (isMedia) getFileUrl(settings, paste.fileName) else null,
            mediaType = if (isMedia) MediaType.IMAGE else null,
            pasteFileName = paste.fileName,
            isImported = true
        )
    }

    private suspend fun pasteToMessage(paste: PasteItem, settings: AppSettings): Message? {
        val parsed = Message.parseFromFileName(paste.fileName) ?: return null
        val extensionType = mediaTypeForExtension(paste.fileName)
        val isMedia = parsed.isMedia || extensionType != null
        val content = getFileContent(paste.fileName).getOrNull() ?: return null
        val rawText = String(content, Charsets.UTF_8)
        val (withoutReactions, reactions) = Message.stripReactionsSentinel(rawText)
        val (withoutEdited, isEdited) = Message.stripEditedSentinel(withoutReactions)
        val (withoutThread, threadRootId) = Message.stripThreadSentinel(withoutEdited)
        val (text, isLlm) = Message.stripLlmSentinel(withoutThread)

        val mediaUrl = if (isMedia) getFileUrl(settings, paste.fileName) else null
        // The filename's embedded timestamp — not the server's upload time — is the
        // authoritative send time: it's what this app itself chose when uploading,
        // whether for a live message or a backdated import (e.g. a WhatsApp import
        // preserving each message's original date). The server's creation_date_utc only
        // ever matters for legacy/imported pastes with no embedded timestamp of their own
        // (see pasteToImportedMessage).
        // Any media attachment's downloaded bytes are binary, not UTF-8 text — fall back to
        // the server filename instead of showing decoded garbage as the caption/name.
        val displayText = if (isMedia) paste.fileName else text

        return Message(
            id = paste.fileName,
            text = displayText,
            isOutgoing = parsed.isOutgoing,
            status = MessageStatus.DELIVERED,
            timestamp = parsed.timestamp,
            mediaUrl = mediaUrl,
            mediaType = if (isMedia) (extensionType ?: MediaType.FILE) else null,
            pasteFileName = paste.fileName,
            isEdited = isEdited && !isMedia,
            reactions = if (isMedia) emptyList() else reactions,
            isLlmResponse = isLlm && !isMedia,
            threadRootId = threadRootId,
            chatId = parsed.chatId
        )
    }

    private fun parseCreationTimestamp(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        // rustypaste's actual API returns space-separated dates ("2026-07-12 12:50:26"),
        // not ISO 'T'-separated ones — try that format first since it's what real
        // servers send; the ISO variants are kept as a fallback for other server versions.
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.isLenient = false
                fmt.parse(dateStr)?.time?.let { return it }
            } catch (_: Exception) {
                // try the next pattern
            }
        }
        return null
    }
}
