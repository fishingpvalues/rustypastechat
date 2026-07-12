package com.rustypastechat.ui.chat

import com.rustypastechat.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for a crash where the very first message in any chat (with date
 * headers enabled) crashed the app: the synthetic header item and the message that
 * follows it were assigned the same LazyColumn key (both used the message's own id),
 * which Compose's LazyColumn/LazyRow throws on ("Key ... was already used").
 */
class ChatItemsTest {

    private fun message(id: String, timestamp: Long, text: String = "hello") = Message(
        id = id,
        text = text,
        timestamp = timestamp
    )

    @Test
    fun `single first message produces a header with a distinct key`() {
        val messages = listOf(message(id = "m1", timestamp = 1_700_000_000_000L))

        val items = buildChatItems(messages, showDateHeaders = true)
        val keys = items.map { it.key() }

        assertEquals(2, items.size) // header + the message itself
        assertEquals(keys.size, keys.toSet().size) // no duplicate keys
    }

    @Test
    fun `keys stay unique across many messages spanning multiple days`() {
        val dayMs = 24L * 60 * 60 * 1000
        val messages = (0 until 20).map { i ->
            message(id = "m$i", timestamp = 1_700_000_000_000L + i * dayMs)
        }

        val items = buildChatItems(messages, showDateHeaders = true)
        val keys = items.map { it.key() }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(items.size > messages.size) // a header was inserted per day
    }

    @Test
    fun `no headers are inserted when showDateHeaders is disabled`() {
        val messages = listOf(message(id = "m1", timestamp = 1_700_000_000_000L))

        val items = buildChatItems(messages, showDateHeaders = false)

        assertEquals(1, items.size)
        assertEquals("m1", items.single().key())
    }
}
