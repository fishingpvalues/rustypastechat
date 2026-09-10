package com.rustypastechat.ui.chatlist

import com.rustypastechat.data.local.ChatMeta
import com.rustypastechat.data.model.ChatCategory
import com.rustypastechat.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatThreadBuildingTest {

    private fun msg(
        id: String,
        chatId: String = "a",
        ts: Long,
        outgoing: Boolean = false
    ) = Message(id = id, text = "m$id", chatId = chatId, timestamp = ts, isOutgoing = outgoing)

    @Test
    fun `incoming messages the user has not seen are unread`() {
        val threads = buildChatThreads(
            listOf(msg("1", ts = 100), msg("2", ts = 200), msg("3", ts = 300)),
            mapOf("a" to ChatMeta(readIds = setOf("1")))
        )
        assertEquals(2, threads.single().unreadCount)
    }

    @Test
    fun `own messages never count as unread`() {
        val threads = buildChatThreads(
            listOf(msg("1", ts = 200, outgoing = true), msg("2", ts = 300, outgoing = true)),
            mapOf("a" to ChatMeta())
        )
        assertEquals(
            "sending a message must not make the chat look unread to the sender",
            0, threads.single().unreadCount
        )
    }

    @Test
    fun `a fully read chat has no badge`() {
        val threads = buildChatThreads(
            listOf(msg("1", ts = 100), msg("2", ts = 200)),
            mapOf("a" to ChatMeta(readIds = setOf("1", "2")))
        )
        assertEquals(0, threads.single().unreadCount)
    }

    // The bug this whole marker exists to survive: a legacy paste has no
    // timestamp anywhere, so the repository stamps it with the current clock
    // on every load. A read marker keyed on time is therefore stale one
    // refresh later - measured against the live server, the badge went 30 to
    // 31 across opening the chat instead of to 0.
    @Test
    fun `a read chat stays read when its messages are restamped`() {
        val meta = mapOf("a" to ChatMeta(readIds = setOf("1", "2"), lastReadTimestamp = 200))
        val restamped = listOf(msg("1", ts = 9_999_999), msg("2", ts = 9_999_999))
        assertEquals(0, buildChatThreads(restamped, meta).single().unreadCount)
    }

    @Test
    fun `a chat that was never opened counts every incoming message`() {
        val threads = buildChatThreads(
            listOf(msg("1", ts = 100), msg("2", ts = 200)),
            emptyMap()
        )
        assertEquals(2, threads.single().unreadCount)
    }

    @Test
    fun `metadata supplies name category archive and mute`() {
        val threads = buildChatThreads(
            listOf(msg("1", ts = 1)),
            mapOf(
                "a" to ChatMeta(
                    name = "Work notes",
                    category = ChatCategory.WORK.name,
                    archived = true,
                    muted = true,
                    avatarColor = 0xFF00FF00
                )
            )
        )
        val t = threads.single()
        assertEquals("Work notes", t.name)
        assertEquals(ChatCategory.WORK, t.category)
        assertTrue(t.isArchived)
        assertTrue(t.isMuted)
        assertEquals(0xFF00FF00, t.avatarColor)
    }

    @Test
    fun `a chat with no metadata falls back to a derived name and defaults`() {
        val threads = buildChatThreads(listOf(msg("1", chatId = "xyz", ts = 1)), emptyMap())
        val t = threads.single()
        assertEquals("Chat xyz", t.name)
        assertEquals(ChatCategory.GENERAL, t.category)
        assertFalse(t.isArchived)
        assertFalse(t.isMuted)
    }

    @Test
    fun `the default chat keeps its friendly name`() {
        val threads = buildChatThreads(listOf(msg("1", chatId = Message.DEFAULT_CHAT, ts = 1)), emptyMap())
        assertEquals("General", threads.single().name)
    }

    @Test
    fun `threads are ordered by their newest message and preview it`() {
        val threads = buildChatThreads(
            listOf(
                msg("old", chatId = "a", ts = 100),
                msg("newest", chatId = "b", ts = 900),
                msg("mid", chatId = "c", ts = 500)
            ),
            emptyMap()
        )
        assertEquals(listOf("b", "c", "a"), threads.map { it.id })
        assertEquals("mnewest", threads.first().lastMessage)
    }

    @Test
    fun `last message is the newest one even when the list is out of order`() {
        // loadAllMessages() has no ordering guarantee across pastes, so
        // taking the list's last element (the previous behavior) could show a
        // stale preview and a stale sort key.
        val threads = buildChatThreads(
            listOf(msg("2", ts = 900), msg("1", ts = 100)),
            emptyMap()
        )
        assertEquals(900L, threads.single().lastTimestamp)
        assertEquals("m2", threads.single().lastMessage)
    }
}
