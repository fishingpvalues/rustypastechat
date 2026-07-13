package com.rustypastechat.data.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WhatsAppChatParserTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        val cal = Calendar.getInstance(utc)
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        return cal.timeInMillis
    }

    @Test
    fun `parses a single-line message with day-month-year 24-hour timestamp`() {
        val text = "‎[07.07.25, 22:06:35] Alex Example: hello world"
        val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)

        assertEquals(1, messages.size)
        val msg = messages.single()
        assertEquals("Alex Example", msg.sender)
        assertEquals("hello world", msg.text)
        assertEquals(epoch(2025, 7, 7, 22, 6, 35), msg.timestampMillis)
        assertFalse(msg.isSystemMessage)
    }

    @Test
    fun `joins continuation lines into the previous message`() {
        val text = listOf(
            "[09.07.25, 15:52:25] Alex Example: {",
            "  \"key\": \"value\"",
            "}"
        ).joinToString("\n")
        val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)

        assertEquals(1, messages.size)
        assertEquals("{\n  \"key\": \"value\"\n}", messages.single().text)
    }

    @Test
    fun `parses two consecutive messages independently`() {
        val text = listOf(
            "[07.07.25, 22:06:35] Alex Example: first",
            "[07.07.25, 22:07:00] Alex Example: second"
        ).joinToString("\n")
        val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)

        assertEquals(2, messages.size)
        assertEquals("first", messages[0].text)
        assertEquals("second", messages[1].text)
        assertTrue(messages[0].timestampMillis < messages[1].timestampMillis)
    }

    @Test
    fun `recognizes plain media-omitted markers`() {
        for (kind in listOf("image" to WhatsAppMediaKind.IMAGE, "video" to WhatsAppMediaKind.VIDEO,
            "audio" to WhatsAppMediaKind.AUDIO, "sticker" to WhatsAppMediaKind.STICKER)) {
            val text = "‎[07.07.25, 22:06:35] Alex Example: ‎${kind.first} omitted"
            val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)
            assertEquals(kind.second, messages.single().mediaKind)
            assertEquals("", messages.single().text)
        }
    }

    @Test
    fun `attributes an empty-caption message to its sender instead of treating it as a system message`() {
        val text = "[13.03.26, 13:41:36] Alex Example:"
        val msg = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc).single()

        assertEquals("Alex Example", msg.sender)
        assertFalse(msg.isSystemMessage)
        assertEquals("", msg.text)
    }

    @Test
    fun `recognizes a document-omitted marker with filename and page count`() {
        val text = "[09.07.25, 10:00:00] Alex Example: Rechnung_5_2026_140905986.pdf • ‎1 page ‎document omitted"
        val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)

        val msg = messages.single()
        assertEquals(WhatsAppMediaKind.DOCUMENT, msg.mediaKind)
        assertEquals("Rechnung_5_2026_140905986.pdf", msg.mediaFileNameHint)
    }

    @Test
    fun `strips the edited suffix and sets isEdited`() {
        val text = "[07.07.25, 22:06:35] Alex Example: oops <This message was edited>"
        val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)

        val msg = messages.single()
        assertTrue(msg.isEdited)
        assertEquals("oops", msg.text)
    }

    @Test
    fun `treats a header line with no sender colon as a system message`() {
        val text = "[07.07.25, 22:06:35] Messages and calls are end-to-end encrypted."
        val messages = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc)

        val msg = messages.single()
        assertTrue(msg.isSystemMessage)
        assertNull(msg.sender)
    }

    @Test
    fun `parses 12-hour AM PM timestamps correctly, including the noon and midnight edge cases`() {
        val noon = WhatsAppChatParser.parse(
            "[07/07/25, 12:00:00 PM] A: noon", WhatsAppDateOrder.MONTH_DAY_YEAR, utc
        ).single()
        val midnight = WhatsAppChatParser.parse(
            "[07/07/25, 12:00:00 AM] A: midnight", WhatsAppDateOrder.MONTH_DAY_YEAR, utc
        ).single()

        assertEquals(epoch(2025, 7, 7, 12, 0, 0), noon.timestampMillis)
        assertEquals(epoch(2025, 7, 7, 0, 0, 0), midnight.timestampMillis)
    }

    @Test
    fun `month-day-year date order is honored when requested`() {
        // 03.04.26 is 3 April 2026 read day-first, but 4 March 2026 read month-first.
        val dmy = WhatsAppChatParser.parse("[03.04.26, 09:00:00] A: x", WhatsAppDateOrder.DAY_MONTH_YEAR, utc).single()
        val mdy = WhatsAppChatParser.parse("[03.04.26, 09:00:00] A: x", WhatsAppDateOrder.MONTH_DAY_YEAR, utc).single()

        assertEquals(epoch(2026, 4, 3, 9, 0, 0), dmy.timestampMillis)
        assertEquals(epoch(2026, 3, 4, 9, 0, 0), mdy.timestampMillis)
    }

    @Test
    fun `sender name containing a colon-free display name splits correctly even with an early colon in the text`() {
        val text = "[07.07.25, 22:06:35] Alex Example: Check this: https://example.com"
        val msg = WhatsAppChatParser.parse(text, WhatsAppDateOrder.DAY_MONTH_YEAR, utc).single()

        assertEquals("Alex Example", msg.sender)
        assertEquals("Check this: https://example.com", msg.text)
    }

    @Test
    fun `remaps WhatsApp markup to this app's markup in one pass without double-converting`() {
        val input = "*bold* and _italic_ and ~strike~"
        val out = remapWhatsAppMarkupToApp(input)
        assertEquals("**bold** and *italic* and ~~strike~~", out)
    }

    @Test
    fun `empty input yields no messages`() {
        assertEquals(0, WhatsAppChatParser.parse("", WhatsAppDateOrder.DAY_MONTH_YEAR, utc).size)
    }
}
