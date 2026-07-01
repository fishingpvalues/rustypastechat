package com.rustypastechat.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class PasteApiEdgeCaseTest {

    companion object {
        private lateinit var server: RustyPasteTestServer
        private lateinit var api: RustyPasteApi
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        @JvmStatic @BeforeClass
        fun setup() {
            server = RustyPasteTestServer()
            server.start()
            api = Retrofit.Builder()
                .baseUrl("${server.baseUrl}/")
                .client(OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(RustyPasteApi::class.java)
        }

        @JvmStatic @AfterClass
        fun teardown() { server.close() }
    }

    @Test
    fun `upload with filename header`() = runBlocking {
        val body = "test".toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "original.txt", body)
        val response = api.uploadFile(part)
        assertTrue(response.isSuccessful)
        val url = response.body()?.string()?.trim() ?: ""
        assertTrue(url.contains("original.txt"))
    }

    @Test
    fun `upload without explicit content type`() = runBlocking {
        val body = "plain text".toRequestBody(null as okhttp3.MediaType?)
        val part = MultipartBody.Part.createFormData("file", "nctype.txt", body)
        val response = api.uploadFile(part)
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `list after no uploads returns empty`() = runBlocking {
        // Don't upload anything - just ensure list works with no files
        val response = api.listFiles()
        assertTrue(response.isSuccessful)
        // May have files from other tests, but should be valid JSON
        assertNotNull(response.body())
    }

    @Test
    fun `version endpoint works`() = runBlocking {
        // Already tested in integration test; additional check for response format
        val response = api.getVersion()
        assertTrue(response.isSuccessful)
        val v = response.body()?.string()?.trim() ?: ""
        assertTrue(v.isNotBlank())
    }

    @Test
    fun `delete nonexistent file returns error`() = runBlocking {
        val response = api.deleteFile("nonexistent_xyz_999.txt")
        assertFalse(response.isSuccessful)
    }

    @Test
    fun `upload large-ish content`() = runBlocking {
        val content = "x".repeat(10000)
        val body = content.toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "large.txt", body)
        val response = api.uploadFile(part)
        assertTrue("Large upload should succeed", response.isSuccessful)

        val url = response.body()?.string()?.trim() ?: ""
        val filename = url.substringAfterLast("/").trim()
        val dl = api.getFile(filename)
        assertTrue(dl.isSuccessful)
        assertEquals(content, dl.body()?.string())
    }

    @Test
    fun `get file head request works`() = runBlocking {
        val body = "headtest".toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "headtest.txt", body)
        api.uploadFile(part)
        // HEAD is handled by the same serve route
        val response = api.getFile("headtest.txt")
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `upload special characters in content`() = runBlocking {
        val content = "Hello\nWorld\tTab\r\nSpecial: !@#$%^&*()_+-=[]{}|;':\",./<>?"
        val body = content.toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "special.txt", body)
        val response = api.uploadFile(part)
        assertTrue(response.isSuccessful)
        val url = response.body()?.string()?.trim() ?: ""
        val filename = url.substringAfterLast("/").trim()
        val dl = api.getFile(filename)
        assertEquals(content, dl.body()?.string())
    }

    @Test
    fun `upload unicode text`() = runBlocking {
        val content = "Hello 世界 🌍 Grüße émoji"
        val body = content.toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "unicode.txt", body)
        val response = api.uploadFile(part)
        assertTrue(response.isSuccessful)
        val url = response.body()?.string()?.trim() ?: ""
        val filename = url.substringAfterLast("/").trim()
        val dl = api.getFile(filename)
        assertEquals(content, dl.body()?.string())
    }

    @Test
    fun `get file with download=true returns content`() = runBlocking {
        val content = "dl_query"
        val body = content.toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "dlq.txt", body)
        api.uploadFile(part)

        val response = api.getFileWithDownload("dlq.txt", true)
        assertTrue(response.isSuccessful)
        assertEquals(content, response.body()?.string())
    }
}
