package com.rustypastechat.data.repository

import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.api.PasteAuthInterceptor
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.AppSettings
import com.rustypastechat.data.model.PasteItem
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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

    suspend fun listFiles(): Result<List<PasteItem>> {
        runCatching {
            val response = getApi().listFiles()
            if (response.isSuccessful) {
                return Result.success(response.body() ?: emptyList())
            }
            return Result.failure(Exception("List files failed: ${response.code()} ${response.message()}"))
        }.getOrElse {
            return Result.failure(it)
        }
    }

    suspend fun uploadFile(filePath: String, fileName: String): Result<String> {
        runCatching {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found"))
            }
            val mimeType = file.toURI().toURL().openConnection().contentType
                ?: "application/octet-stream"
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val response = getApi().uploadFile(filePart)
            if (response.isSuccessful) {
                val url = response.body()?.string()?.trim() ?: fileName
                return Result.success(url)
            }
            return Result.failure(Exception("Upload failed: ${response.code()} ${response.message()}"))
        }.getOrElse {
            return Result.failure(it)
        }
    }

    suspend fun uploadText(text: String, fileName: String): Result<String> {
        runCatching {
            val requestBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val response = getApi().uploadFile(filePart)
            if (response.isSuccessful) {
                val url = response.body()?.string()?.trim() ?: fileName
                return Result.success(url)
            }
            return Result.failure(Exception("Upload failed: ${response.code()} ${response.message()}"))
        }.getOrElse {
            return Result.failure(it)
        }
    }

    suspend fun getFileContent(filename: String): Result<ByteArray> {
        runCatching {
            val response = getApi().getFile(filename)
            if (response.isSuccessful) {
                return Result.success(response.body()?.bytes() ?: ByteArray(0))
            }
            return Result.failure(Exception("Download failed: ${response.code()} ${response.message()}"))
        }.getOrElse {
            return Result.failure(it)
        }
    }

    fun getFileUrl(settings: AppSettings, filename: String): String {
        val base = settings.pasteServerUrl.trimEnd('/')
        return "$base/$filename"
    }
}
