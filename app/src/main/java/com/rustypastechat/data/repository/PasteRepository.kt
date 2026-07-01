package com.rustypastechat.data.repository

import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.api.PasteAuthInterceptor
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

class PasteRepository(
    private val preferencesManager: PreferencesManager
) : PasteAuthInterceptor.TokenProvider {

    private var api: com.rustypastechat.data.api.RustyPasteApi? = null

    override fun getToken(): String? {
        val settings = kotlinx.coroutines.runBlocking {
            preferencesManager.settingsFlow.first()
        }
        return settings.authToken.ifBlank { null }
    }

    private suspend fun getApi(): com.rustypastechat.data.api.RustyPasteApi {
        val settings = preferencesManager.settingsFlow.first()
        if (api == null) {
            api = ApiClientFactory.createPasteApi(settings.pasteServerUrl, this)
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

    // ── Chat history reconstruction ─────────────────────────────────────────

    suspend fun loadChatHistory(): Result<List<Message>> = withContext(Dispatchers.IO) {
        runCatching {
            val pastes = listFiles().getOrThrow()
            val chatPastes = pastes
                .filter { Message.isChatFile(it.fileName) }
                .sortedBy { it.creationDateUtc ?: "" }

            if (chatPastes.isEmpty()) return@runCatching emptyList<Message>()

            val messages = coroutineScope {
                chatPastes.map { paste ->
                    async { pasteToMessage(paste) }
                }.awaitAll().filterNotNull()
            }
            messages.sortedBy { it.timestamp }
        }
    }

    private suspend fun pasteToMessage(paste: PasteItem): Message? {
        val parsed = Message.parseFromFileName(paste.fileName) ?: return null
        val content = getFileContent(paste.fileName).getOrNull() ?: return null
        val text = String(content, Charsets.UTF_8)

        val isMedia = parsed.isMedia || paste.fileName.let { name ->
            name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "webm")
        }

        val mediaUrl = if (isMedia) {
            val settings = kotlinx.coroutines.runBlocking { preferencesManager.settingsFlow.first() }
            getFileUrl(settings, paste.fileName)
        } else null

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
            isLlmResponse = !parsed.isOutgoing
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
