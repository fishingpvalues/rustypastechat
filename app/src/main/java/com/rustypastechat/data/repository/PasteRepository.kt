package com.rustypastechat.data.repository

import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasteRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val apiClientFactory: ApiClientFactory
) {
    private var api: com.rustypastechat.data.api.RustyPasteApi? = null

    private suspend fun getApi(): com.rustypastechat.data.api.RustyPasteApi {
        val settings = preferencesManager.settingsFlow.first()
        if (api == null) {
            api = apiClientFactory.createPasteApi(settings.pasteServerUrl)
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

    suspend fun loadChatHistory(chatId: String = Message.DEFAULT_CHAT): Result<List<Message>> = withContext(Dispatchers.IO) {
        runCatching {
            val pastes = listFiles().getOrThrow()
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
        val content = getFileContent(paste.fileName).getOrNull() ?: return null
        val text = String(content, Charsets.UTF_8)

        val isMedia = parsed.isMedia || paste.fileName.let { name ->
            name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "webm")
        }

        val mediaUrl = if (isMedia) getFileUrl(settings, paste.fileName) else null
        val creationTs = parseCreationTimestamp(paste.creationDateUtc) ?: parsed.timestamp

        return Message(
            id = paste.fileName,
            text = text,
            isOutgoing = parsed.isOutgoing,
            status = MessageStatus.DELIVERED,
            timestamp = creationTs,
            mediaUrl = mediaUrl,
            mediaType = if (isMedia) MediaType.IMAGE else null,
            pasteFileName = paste.fileName,
            isLlmResponse = !parsed.isOutgoing,
            chatId = parsed.chatId
        )
    }

    private fun parseCreationTimestamp(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr)?.time
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.parse(dateStr)?.time
            } catch (_: Exception) { null }
        }
    }
}
