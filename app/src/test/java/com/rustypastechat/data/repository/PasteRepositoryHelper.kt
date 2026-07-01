package com.rustypastechat.data.repository

import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.api.PasteAuthInterceptor
import com.rustypastechat.data.api.RustyPasteApi
import com.rustypastechat.data.model.PasteItem
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PasteRepositoryHelper(
    private val baseUrl: String,
    private val token: String? = null
) : PasteAuthInterceptor.TokenProvider {

    private val api: RustyPasteApi by lazy {
        ApiClientFactory.createPasteApi(baseUrl, this)
    }

    override fun getToken(): String? = token

    suspend fun uploadText(text: String, fileName: String): Result<String> = runCatching {
        val body = text.toRequestBody("text/plain".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val response = api.uploadFile(part)
        if (response.isSuccessful) {
            response.body()?.string()?.trim() ?: fileName
        } else {
            throw Exception("Upload failed: ${response.code()}")
        }
    }

    suspend fun getFileContent(filename: String): Result<ByteArray> = runCatching {
        val response = api.getFile(filename)
        if (response.isSuccessful) {
            response.body()?.bytes() ?: ByteArray(0)
        } else {
            throw Exception("Download failed: ${response.code()}")
        }
    }

    suspend fun listFiles(): Result<List<PasteItem>> = runCatching {
        val response = api.listFiles()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("List failed: ${response.code()}")
        }
    }

    fun buildUrl(serverUrl: String, filename: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base/$filename"
    }
}
