package com.rustypastechat.data.model

import org.junit.Assert.*
import org.junit.Test

class MessageModelTest {

    @Test
    fun `buildFileName for default chat`() {
        val ts = 1719876543210L
        val name = Message.buildFileName(Message.DEFAULT_CHAT, ts, true, "abc12345")
        assertTrue(name.startsWith(Message.CHAT_PREFIX))
        assertTrue(name.contains("_o_"))
        assertTrue(name.endsWith(".txt"))
        assertTrue(name.contains(ts.toString()))
    }

    @Test
    fun `buildFileName for custom chat`() {
        val ts = System.currentTimeMillis()
        val name = Message.buildFileName("myChat", ts, false, "xyz98765")
        assertTrue(name.startsWith("chat_myChat_"))
        assertTrue(name.contains("_i_"))
    }

    @Test
    fun `buildMediaFileName includes chatId`() {
        val name = Message.buildMediaFileName("testChat", 1000L, "img001", "jpg")
        assertTrue(name.startsWith("chat_testChat_"))
        assertTrue(name.contains("_m_"))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun `isChatFile identifies chat files`() {
        assertTrue(Message.isChatFile("chat_default_123_o_abc.txt"))
        assertTrue(Message.isChatFile("chat_myId_456_i_xyz.txt"))
        assertTrue(Message.isChatFile("c_789_o_old.txt")) // legacy
        assertFalse(Message.isChatFile("random.txt"))
        assertFalse(Message.isChatFile("msg_something.txt"))
    }

    @Test
    fun `extractChatId from custom chat`() {
        assertEquals("myChat", Message.extractChatId("chat_myChat_123_o_abc.txt"))
    }

    @Test
    fun `extractChatId from default chat`() {
        assertEquals(Message.DEFAULT_CHAT, Message.extractChatId("chat_123_o_abc.txt"))
    }

    @Test
    fun `extractChatId from legacy format`() {
        assertEquals(Message.DEFAULT_CHAT, Message.extractChatId("c_123_o_abc.txt"))
    }

    @Test
    fun `parseFromFileName for default chat`() {
        val parsed = Message.parseFromFileName("chat_1719876543210_o_abc12345.txt")
        assertNotNull(parsed)
        assertEquals(1719876543210L, parsed!!.timestamp)
        assertTrue(parsed.isOutgoing)
        assertFalse(parsed.isMedia)
        assertEquals(Message.DEFAULT_CHAT, parsed.chatId)
    }

    @Test
    fun `parseFromFileName for custom chat`() {
        val parsed = Message.parseFromFileName("chat_myId_1719876543210_i_xyz98765.txt")
        assertNotNull(parsed)
        assertEquals(1719876543210L, parsed!!.timestamp)
        assertFalse(parsed.isOutgoing)
        assertEquals("myId", parsed.chatId)
    }

    @Test
    fun `parseFromFileName for media`() {
        val parsed = Message.parseFromFileName("chat_1000_m_img001.jpg")
        assertNotNull(parsed)
        assertTrue(parsed!!.isMedia)
        assertTrue(parsed.isOutgoing)
    }

    @Test
    fun `parseFromFileName legacy format`() {
        val parsed = Message.parseFromFileName("c_1719876543210_o_abc.txt")
        assertNotNull(parsed)
        assertEquals(1719876543210L, parsed!!.timestamp)
        assertTrue(parsed.isOutgoing)
        assertEquals(Message.DEFAULT_CHAT, parsed.chatId)
    }

    @Test
    fun `parseFromFileName returns null for invalid`() {
        assertNull(Message.parseFromFileName("random.txt"))
        assertNull(Message.parseFromFileName(""))
        assertNull(Message.parseFromFileName("chat_badformat.txt"))
    }

    @Test
    fun `message default values`() {
        val msg = Message(id = "test", text = "hello")
        assertTrue(msg.isOutgoing)
        assertEquals(MessageStatus.SENDING, msg.status)
        assertNull(msg.mediaUrl)
        assertNull(msg.pasteFileName)
        assertFalse(msg.isLlmResponse)
        assertNull(msg.replyToId)
        assertFalse(msg.isOneshot)
        assertFalse(msg.isImported)
        assertEquals(Message.DEFAULT_CHAT, msg.chatId)
    }

    @Test
    fun `message with all fields`() {
        val now = System.currentTimeMillis()
        val msg = Message(
            id = "msg1", text = "test", isOutgoing = false,
            status = MessageStatus.DELIVERED, timestamp = now,
            mediaUrl = "https://paste.example.com/img.jpg",
            mediaType = MediaType.IMAGE, pasteFileName = "img.jpg",
            isLlmResponse = true, replyToId = "parent",
            replyToText = "original", replyToIsOutgoing = true,
            isOneshot = true, expiresAt = now + 60000,
            isImported = true, chatId = "custom"
        )
        assertFalse(msg.isOutgoing)
        assertEquals(MessageStatus.DELIVERED, msg.status)
        assertEquals("parent", msg.replyToId)
        assertTrue(msg.isOneshot)
        assertTrue(msg.isImported)
        assertEquals("custom", msg.chatId)
    }

    @Test
    fun `replyTarget creation`() {
        val target = ReplyTarget("msg1", "hello world", true)
        assertEquals("msg1", target.messageId)
        assertEquals("hello world", target.text)
        assertTrue(target.isOutgoing)
    }
}
