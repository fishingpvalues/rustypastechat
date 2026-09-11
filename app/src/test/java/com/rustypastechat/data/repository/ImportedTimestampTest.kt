package com.rustypastechat.data.repository

import com.rustypastechat.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * rustypaste fills `creation_date_utc` from the file's filesystem birth time
 * and returns null when it cannot read one - on the reference deployment that
 * is every entry, verified live, even though the host's ext4 does record a
 * birth time.
 *
 * The old fallback was System.currentTimeMillis(), which gave every foreign
 * paste the moment it happened to be read: 33 messages sharing one timestamp,
 * a date header reading today, and every one of them moving on the next
 * reload. These pin the replacement.
 */
class ImportedTimestampTest {

    @Test
    fun `a server-supplied date is used and is displayable`() {
        val (ts, known) = PasteRepository.importedTimestamp(1_700_000_000_000L, 7)
        assertEquals(1_700_000_000_000L, ts)
        assertTrue(known)
    }

    @Test
    fun `without a date the paste is ordered by list position, not by now`() {
        val now = System.currentTimeMillis()
        val (ts, known) = PasteRepository.importedTimestamp(null, 3)
        assertFalse("a fabricated time must not be presented as real", known)
        assertTrue("must not be anchored at the moment of reading", ts < now - 60_000)
    }

    @Test
    fun `list order is preserved and every entry is distinct`() {
        val stamps = (0 until 40).map { PasteRepository.importedTimestamp(null, it).first }
        assertEquals("no two imported pastes may collide", 40, stamps.toSet().size)
        assertEquals("order must follow the list", stamps.sorted(), stamps)
    }

    @Test
    fun `the same list reconstructs to the same timestamps`() {
        // The actual defect: reconstruction ran on every open, so "now" moved
        // the whole imported history each time.
        val first = (0 until 10).map { PasteRepository.importedTimestamp(null, it) }
        Thread.sleep(5)
        val second = (0 until 10).map { PasteRepository.importedTimestamp(null, it) }
        assertEquals(first, second)
    }

    @Test
    fun `imported pastes sort before messages this app sent`() {
        val sent = Message(id = "m", text = "x", timestamp = System.currentTimeMillis())
        val (imported, _) = PasteRepository.importedTimestamp(null, 39)
        assertTrue(imported < sent.timestamp)
    }

    @Test
    fun `a message is displayable by default`() {
        assertTrue(Message(id = "m", text = "x").hasKnownTimestamp)
    }
}
