package com.rustypastechat.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rustypastechat.data.model.PasteItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class PasteApiIntegrationTest {

    companion object {
        private lateinit var server: RustyPasteTestServer
        private lateinit var api: RustyPasteApi
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = RustyPasteTestServer()
            server.start()
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            api = Retrofit.Builder()
                .baseUrl("${server.baseUrl}/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RustyPasteApi::class.java)
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            if (::server.isInitialized) server.close()
        }
    }

    // ── Upload text file ─────────────────────────────────────────────────────
    @Test
    fun `upload text file returns URL`() = runBlocking {
        val content = "Hello, rustypaste test!"
        val body = content.toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "test_upload.txt", body)
        val response = api.uploadFile(part)
        assertTrue("Upload should succeed: ${response.code()}", response.isSuccessful)
        val url = response.body()?.string()?.trim() ?: ""
        assertTrue("URL should contain filename", url.contains("test_upload"))
    }

    // ── Download uploaded file ───────────────────────────────────────────────
    @Test
    fun `get uploaded file returns content`() = runBlocking {
        val content = "test_download_content"
        val body = content.toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "download_test.txt", body)
        val uploadResp = api.uploadFile(part)
        assertTrue(uploadResp.isSuccessful)
        val url = uploadResp.body()?.string()?.trim() ?: ""
        val filename = url.substringAfterLast("/").trim()

        val downloadResp = api.getFile(filename)
        assertTrue("Download should succeed", downloadResp.isSuccessful)
        val downloaded = downloadResp.body()?.string() ?: ""
        assertEquals(content, downloaded)
    }

    // ── List files ───────────────────────────────────────────────────────────
    @Test
    fun `list files returns array of pastes`() = runBlocking {
        // Upload a file first
        val content = "list_test_content"
        val part = MultipartBody.Part.createFormData(
            "file", "list_test.txt",
            content.toRequestBody("text/plain".toMediaType())
        )
        api.uploadFile(part)

        val response = api.listFiles()
        assertTrue("List should succeed", response.isSuccessful)
        val items = response.body() ?: emptyList()
        assertTrue("Should have at least one file", items.isNotEmpty())
        assertTrue("Should contain our uploaded file",
            items.any { it.fileName.contains("list_test") })
    }

    // ── List files has correct types ─────────────────────────────────────────
    @Test
    fun `list files entries have correct fields`() = runBlocking {
        val content = "field_test"
        val part = MultipartBody.Part.createFormData(
            "file", "field_test.txt",
            content.toRequestBody("text/plain".toMediaType())
        )
        api.uploadFile(part)

        val response = api.listFiles()
        assertTrue(response.isSuccessful)
        val items = response.body() ?: emptyList()
        val item = items.firstOrNull { it.fileName.contains("field_test") }
        assertNotNull("Should find field_test file", item)
        assertTrue("File size should be > 0", item!!.fileSize > 0)
    }

    // ── Download with download=true query ────────────────────────────────────
    @Test
    fun `get file with download query works`() = runBlocking {
        val content = "download_query_test"
        val part = MultipartBody.Part.createFormData(
            "file", "dlquery.txt",
            content.toRequestBody("text/plain".toMediaType())
        )
        val uploadResp = api.uploadFile(part)
        assertTrue(uploadResp.isSuccessful)
        val url = uploadResp.body()?.string()?.trim() ?: ""
        val filename = url.substringAfterLast("/").trim()

        val response = api.getFileWithDownload(filename, true)
        assertTrue(response.isSuccessful)
        assertTrue(response.body()?.bytes()?.isNotEmpty() ?: false)
    }

    // ── Upload file with auth header (without token, should still work if none configured) ──
    @Test
    fun `upload without auth works when no auth configured`() = runBlocking {
        val content = "no_auth_test_content"
        val part = MultipartBody.Part.createFormData(
            "file", "noauth.txt",
            content.toRequestBody("text/plain".toMediaType())
        )
        val response = api.uploadFile(part)
        assertTrue("Upload without auth should succeed when server has no auth",
            response.isSuccessful)
    }

    // ── Get version endpoint ─────────────────────────────────────────────────
    @Test
    fun `version endpoint returns version`() = runBlocking {
        val response = api.getVersion()
        assertTrue("Version endpoint should succeed", response.isSuccessful)
        val version = response.body()?.string()?.trim() ?: ""
        assertTrue("Version should not be empty", version.isNotBlank())
        assertTrue("Version should look like semver", version.matches(Regex("\\d+\\.\\d+\\.\\d+")))
    }

    // ── Multiple uploads in sequence ─────────────────────────────────────────
    @Test
    fun `multiple sequential uploads all succeed`() = runBlocking {
        for (i in 1..5) {
            val content = "multi_upload_$i"
            val part = MultipartBody.Part.createFormData(
                "file", "multi_$i.txt",
                content.toRequestBody("text/plain".toMediaType())
            )
            val response = api.uploadFile(part)
            assertTrue("Upload $i should succeed", response.isSuccessful)
        }
        val listResponse = api.listFiles()
        assertTrue(listResponse.isSuccessful)
        val items = listResponse.body() ?: emptyList()
        val multiFiles = items.filter { it.fileName.contains("multi_") }
        assertTrue("Should have at least 5 multi_ files", multiFiles.size >= 5)
    }

    // ── Empty file upload ────────────────────────────────────────────────────
    @Test
    fun `empty file upload returns error`() = runBlocking {
        val part = MultipartBody.Part.createFormData(
            "file", "empty.txt",
            "".toRequestBody("text/plain".toMediaType())
        )
        val response = api.uploadFile(part)
        assertFalse("Empty upload should fail", response.isSuccessful)
    }

    // ── File not found ───────────────────────────────────────────────────────
    @Test
    fun `nonexistent file returns 404`() = runBlocking {
        val response = api.getFile("nonexistent_file_xyz_12345.txt")
        assertFalse("Nonexistent file should return error", response.isSuccessful)
        assertTrue("Should be 404 or similar",
            response.code() in 400..499)
    }

    // ── Content type preservation ────────────────────────────────────────────
    @Test
    fun `uploaded content type is preserved`() = runBlocking {
        val html = "<html><body>Hello</body></html>"
        val part = MultipartBody.Part.createFormData(
            "file", "test.html",
            html.toRequestBody("text/html".toMediaType())
        )
        val uploadResp = api.uploadFile(part)
        assertTrue(uploadResp.isSuccessful)
        val url = uploadResp.body()?.string()?.trim() ?: ""
        val filename = url.substringAfterLast("/").trim()

        val response = api.getFile(filename)
        assertTrue(response.isSuccessful)
        val body = response.body()?.string() ?: ""
        assertEquals(html, body)
    }
}
