package com.rustypastechat.ui.chat

import com.rustypastechat.data.model.MediaType
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.ui.chat.components.messageAccessibilityLabel
import com.rustypastechat.ui.chat.components.statusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat bubble merges its descendants, so one string is everything a
 * TalkBack user gets for a message: the icons, the tick marks and the
 * timestamp are never announced separately. These pin that string.
 *
 * WCAG 1.4.1 is the reason the delivery state is in the text at all - sent,
 * delivered and read differ only by tick shape and colour on screen, which is
 * information conveyed by appearance alone unless it is also spoken.
 */
class MessageAccessibilityTest {

    private fun msg(
        text: String = "hello",
        outgoing: Boolean = true,
        status: MessageStatus = MessageStatus.DELIVERED,
        mediaUrl: String? = null,
        mediaType: MediaType? = null
    ) = Message(
        id = "m1", text = text, isOutgoing = outgoing, status = status,
        mediaUrl = mediaUrl, mediaType = mediaType
    )

    @Test
    fun `an outgoing message announces sender, text, time and delivery state`() {
        assertEquals(
            "You: hello, 16:56, delivered",
            messageAccessibilityLabel(msg(), "16:56")
        )
    }

    @Test
    fun `an incoming message has no delivery state`() {
        // The ticks are only drawn on your own messages. Announcing
        // "delivered" for a message someone sent you states the opposite of
        // what happened.
        assertEquals("Message: hi there, 09:01", messageAccessibilityLabel(msg("hi there", outgoing = false), "09:01"))
        assertNull(statusLabel(msg(outgoing = false)))
    }

    @Test
    fun `every delivery state is spoken, not only shown`() {
        val spoken = MessageStatus.entries.map { statusLabel(msg(status = it)) }
        assertTrue("every status needs a word", spoken.all { !it.isNullOrBlank() })
        assertEquals("each status must be distinguishable", spoken.size, spoken.toSet().size)
        assertEquals("failed to send", statusLabel(msg(status = MessageStatus.FAILED)))
    }

    @Test
    fun `a message with no known time does not announce a dangling separator`() {
        // Foreign pastes carry no timestamp - see PasteRepository.importedTimestamp.
        // The bubble renders an empty time string for those, and appending it
        // unconditionally produced "Message: hello, " which TalkBack reads as
        // a pause leading nowhere.
        val label = messageAccessibilityLabel(msg("hello", outgoing = false), "")
        assertEquals("Message: hello", label)
        assertFalse(label.endsWith(", "))
        assertFalse(label.contains(",,"))
    }

    @Test
    fun `media without text is described by kind rather than announced as empty`() {
        assertEquals(
            "You: image, 16:56, sent",
            messageAccessibilityLabel(
                msg(text = "", status = MessageStatus.SENT, mediaUrl = "http://h/a.png", mediaType = MediaType.IMAGE),
                "16:56"
            )
        )
        assertEquals(
            "You: video, 16:56, sent",
            messageAccessibilityLabel(
                msg(text = "", status = MessageStatus.SENT, mediaUrl = "http://h/a.mp4", mediaType = MediaType.VIDEO),
                "16:56"
            )
        )
    }

    @Test
    fun `a file attachment says it is a file`() {
        assertEquals(
            "You: file report.pdf, 16:56, delivered",
            messageAccessibilityLabel(msg("report.pdf", mediaType = MediaType.FILE), "16:56")
        )
    }

    @Test
    fun `no bubble is ever announced as an empty or punctuation-only string`() {
        val awkward = listOf(
            msg(text = ""),
            msg(text = "", outgoing = false),
            msg(text = "", outgoing = false, mediaUrl = null, mediaType = null)
        )
        awkward.forEach { m ->
            val label = messageAccessibilityLabel(m, "")
            assertTrue("label was '$label'", label.any { it.isLetterOrDigit() })
        }
    }
}
