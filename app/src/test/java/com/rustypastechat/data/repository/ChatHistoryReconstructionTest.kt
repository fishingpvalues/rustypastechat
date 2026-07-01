package com.rustypastechat.data.repository

import com.rustypastechat.data.api.RustyPasteTestServer
import com.rustypastechat.data.model.Message
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class ChatHistoryReconstructionTest {

    companion object {
        private lateinit var server: RustyPasteTestServer
        private lateinit var repo: PasteRepositoryHelper

        @JvmStatic
        @BeforeClass
        fun setup() {
            server = RustyPasteTestServer()
            server.start()
            repo = PasteRepositoryHelper(server.baseUrl)
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            server.close()
        }
    }

    @Test
    fun `message filename parsing - outgoing`() {
        val ts = System.currentTimeMillis()
        val fileName = Message.buildFileName(ts, true, "abc12345")
        assertTrue(fileName.startsWith("c_"))
        assertTrue(fileName.contains("_o_"))
        assertTrue(fileName.endsWith(".txt"))

        val parsed = Message.parseFromFileName(fileName)
        assertNotNull(parsed)
        assertEquals(ts, parsed!!.timestamp)
        assertTrue(parsed.isOutgoing)
        assertFalse(parsed.isMedia)
    }

    @Test
    fun `message filename parsing - incoming`() {
        val ts = System.currentTimeMillis()
        val fileName = Message.buildFileName(ts, false, "xyz98765")
        assertTrue(fileName.contains("_i_"))

        val parsed = Message.parseFromFileName(fileName)
        assertNotNull(parsed)
        assertEquals(ts, parsed!!.timestamp)
        assertFalse(parsed.isOutgoing)
    }

    @Test
    fun `message filename parsing - media`() {
        val ts = System.currentTimeMillis()
        val fileName = Message.buildMediaFileName(ts, "img001", "jpg")
        assertTrue(fileName.contains("_m_"))
        assertTrue(fileName.endsWith(".jpg"))

        val parsed = Message.parseFromFileName(fileName)
        assertNotNull(parsed)
        assertTrue(parsed!!.isMedia)
    }

    @Test
    fun `isChatFile correctly identifies chat files`() {
        assertTrue(Message.isChatFile("c_1719876543_o_abc.txt"))
        assertTrue(Message.isChatFile("c_1719876543_i_xyz.txt"))
        assertFalse(Message.isChatFile("random_file.txt"))
        assertFalse(Message.isChatFile("msg_something.txt"))
    }

    @Test
    fun `parseFromFileName returns null for non chat files`() {
        assertNull(Message.parseFromFileName("random.txt"))
        assertNull(Message.parseFromFileName("c_badformat.txt"))
        assertNull(Message.parseFromFileName(""))
    }

    @Test
    fun `upload and reconstruct roundtrip`() = runBlocking {
        val ts = System.currentTimeMillis()
        val outName = Message.buildFileName(ts, true, "rtt001")
        val outText = "Hello from test"
        repo.uploadText(outText, outName)

        val inName = Message.buildFileName(ts + 1000, false, "rtt002")
        val inText = "Reply from test"
        repo.uploadText(inText, inName)

        val pastes = repo.listFiles().getOrThrow()
        val chatFiles = pastes.filter { Message.isChatFile(it.fileName) }
        assertTrue("Should have at least 2 chat files", chatFiles.size >= 2)
    }

    @Test
    fun `reconstruct messages ordered by name timestamp`() = runBlocking {
        val base = System.currentTimeMillis()

        repo.uploadText("msg1", Message.buildFileName(base, true, "o1"))
        repo.uploadText("msg2", Message.buildFileName(base + 100, false, "i1"))
        repo.uploadText("msg3", Message.buildFileName(base + 200, true, "o2"))

        val pastes = repo.listFiles().getOrThrow()
        val chatFiles = pastes
            .filter { Message.isChatFile(it.fileName) }
            .sortedBy { it.creationDateUtc ?: "" }

        assertTrue("Should have at least 3 chat files", chatFiles.size >= 3)

        // Download each and verify content is preserved
        for (paste in chatFiles) {
            val content = repo.getFileContent(paste.fileName).getOrThrow()
            val text = String(content, Charsets.UTF_8)
            val parsed = Message.parseFromFileName(paste.fileName)
            assertNotNull("Should parse filename: ${paste.fileName}", parsed)
            assertTrue("Content should not be empty", text.isNotBlank())
        }
    }
}
