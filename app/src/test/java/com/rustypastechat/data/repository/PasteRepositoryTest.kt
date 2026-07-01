package com.rustypastechat.data.repository

import com.rustypastechat.data.api.RustyPasteTestServer
import com.rustypastechat.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class PasteRepositoryTest {

    companion object {
        private lateinit var server: RustyPasteTestServer
        private lateinit var repo: PasteRepositoryHelper
        private val settingsHolder = MutableStateFlow(AppSettings())

        @JvmStatic
        @BeforeClass
        fun setup() {
            server = RustyPasteTestServer()
            server.start()
            settingsHolder.value = AppSettings(pasteServerUrl = server.baseUrl)
            repo = PasteRepositoryHelper(server.baseUrl)
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            server.close()
        }
    }

    @Test
    fun `upload text returns success`() = runBlocking {
        val result = repo.uploadText("repo test content", "repo_test.txt")
        assertTrue("Upload should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val url = result.getOrThrow()
        assertTrue("URL should contain repo_test", url.contains("repo_test"))
    }

    @Test
    fun `upload and download roundtrip`() = runBlocking {
        val content = "roundtrip_content_xyz"
        val result = repo.uploadText(content, "roundtrip.txt")
        val filename = result.getOrThrow().substringAfterLast("/").trim()

        val downloadResult = repo.getFileContent(filename)
        assertTrue("Download should succeed", downloadResult.isSuccess)
        assertEquals(content, String(downloadResult.getOrThrow()))
    }

    @Test
    fun `list files returns items`() = runBlocking {
        repo.uploadText("a", "list_a.txt")
        repo.uploadText("b", "list_b.txt")

        val result = repo.listFiles()
        assertTrue("List should succeed", result.isSuccess)
        val items = result.getOrThrow()
        assertTrue("Should have items", items.size >= 2)
    }

    @Test
    fun `file url construction`() {
        assertEquals(
            "https://paste.example.com/file.txt",
            repo.buildUrl("https://paste.example.com", "file.txt")
        )
        assertEquals(
            "https://paste.example.com/file.txt",
            repo.buildUrl("https://paste.example.com/", "file.txt")
        )
    }
}
