package com.rustypastechat.data.whatsapp

import java.util.Calendar
import java.util.TimeZone

/** Day-first (07.07.25 = 7 July 2025) vs month-first (07.07.25 = 7 Jan... no, July 7)
 *  date order used by the exporting phone's locale. WhatsApp embeds no explicit order
 *  marker in the export, so the caller must know which their own export used —
 *  most non-US locales (and the "DD.MM.YY" dot-separated style) are day-first. */
enum class WhatsAppDateOrder { DAY_MONTH_YEAR, MONTH_DAY_YEAR }

enum class WhatsAppMediaKind { IMAGE, VIDEO, AUDIO, STICKER, DOCUMENT, GIF, CONTACT_CARD }

data class WhatsAppParsedMessage(
    val sender: String?,
    val timestampMillis: Long,
    val text: String,
    val mediaKind: WhatsAppMediaKind? = null,
    val mediaFileNameHint: String? = null,
    val isEdited: Boolean = false,
    val isSystemMessage: Boolean = false
)

/**
 * Parses a WhatsApp "Export chat" `_chat.txt` file (the format WhatsApp writes when a user
 * shares a chat as a .txt or a .zip containing `_chat.txt`).
 *
 * Line shape: `‎[DD.MM.YY, HH:MM:SS] Sender Name: message text`, where continuation
 * lines of a multi-line message have no `[...]` header at all — any line that doesn't
 * start a new timestamped entry is treated as a continuation of the previous message.
 * System messages (e.g. "Messages and calls are end-to-end encrypted...") share the same
 * timestamp header but have no `Name: ` prefix.
 */
object WhatsAppChatParser {

    // U+200E (left-to-right mark) and U+200F (RTL mark) show up as stray prefix/infix
    // characters throughout real exports depending on device language direction.
    private const val LRM = '‎'
    private const val RLM = '‏'

    private val HEADER = Regex(
        """^[‎‏]?\[(\d{1,2})[./](\d{1,2})[./](\d{2,4}),\s*(\d{1,2}):(\d{2})(?::(\d{2}))?\s?([APap][Mm])?\]\s?(.*)$"""
    )
    // The space after "Name:" is optional — a message with an empty caption (e.g. media
    // whose real content lands on a continuation line) can leave nothing after the colon.
    private val SENDER_SPLIT = Regex("""^([^\n:]+?):[ \t]?(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val EDITED_SUFFIX = Regex("""[‎‏]?<This message was edited>\s*$""")

    // "<filename> • ‎N pages ‎document omitted" or plain "image omitted" / "video omitted" / etc.
    private val OMITTED = Regex(
        """^(?:(.+?)\s*(?:•\s*[‎‏]?\d+\s*pages?\s*)?[‎‏]?)?(image|video|audio|sticker|document|GIF|Contact card) omitted$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        rawText: String,
        dateOrder: WhatsAppDateOrder = WhatsAppDateOrder.DAY_MONTH_YEAR,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<WhatsAppParsedMessage> {
        val lines = rawText.split(Regex("\r\n|\n"))
        val results = mutableListOf<WhatsAppParsedMessage>()

        var pendingSender: String? = null
        var pendingTimestamp: Long? = null
        var pendingBody: StringBuilder? = null

        fun flush() {
            val ts = pendingTimestamp ?: return
            val body = pendingBody?.toString()?.trim().orEmpty()
            results.add(buildMessage(pendingSender, ts, body))
            pendingSender = null
            pendingTimestamp = null
            pendingBody = null
        }

        for (raw in lines) {
            val line = raw.trimEnd('\r').removePrefix(LRM.toString()).removePrefix(RLM.toString())
            val match = HEADER.matchEntire(line)
            if (match != null) {
                flush()
                val g = match.groupValues
                pendingTimestamp = toEpochMillis(
                    d = g[1].toInt(), mo = g[2].toInt(), y = g[3].toInt(),
                    h = g[4].toInt(), mi = g[5].toInt(), s = g[6].toIntOrNull() ?: 0,
                    ampm = g[7].ifBlank { null }, dateOrder = dateOrder, timeZone = timeZone
                )
                val remainder = g[8]
                val senderMatch = SENDER_SPLIT.matchEntire(remainder)
                if (senderMatch != null) {
                    pendingSender = senderMatch.groupValues[1]
                    pendingBody = StringBuilder(senderMatch.groupValues[2])
                } else {
                    pendingSender = null
                    pendingBody = StringBuilder(remainder)
                }
            } else if (pendingTimestamp != null) {
                pendingBody?.append('\n')?.append(line.removePrefix(LRM.toString()).removePrefix(RLM.toString()))
            }
            // lines before the very first header (rare) are dropped — nothing to attach them to
        }
        flush()
        return results
    }

    private fun stripBidiMarks(s: String): String = s.replace(LRM.toString(), "").replace(RLM.toString(), "")

    private fun buildMessage(sender: String?, timestampMillis: Long, rawBody: String): WhatsAppParsedMessage {
        var body = stripBidiMarks(rawBody)
        val edited = EDITED_SUFFIX.containsMatchIn(body)
        if (edited) body = body.replace(EDITED_SUFFIX, "").trimEnd()

        val omittedMatch = OMITTED.matchEntire(body.trim())
        if (omittedMatch != null) {
            val kind = when (omittedMatch.groupValues[2].lowercase()) {
                "image" -> WhatsAppMediaKind.IMAGE
                "video" -> WhatsAppMediaKind.VIDEO
                "audio" -> WhatsAppMediaKind.AUDIO
                "sticker" -> WhatsAppMediaKind.STICKER
                "document" -> WhatsAppMediaKind.DOCUMENT
                "gif" -> WhatsAppMediaKind.GIF
                else -> WhatsAppMediaKind.CONTACT_CARD
            }
            return WhatsAppParsedMessage(
                sender = sender,
                timestampMillis = timestampMillis,
                text = "",
                mediaKind = kind,
                mediaFileNameHint = omittedMatch.groupValues[1].trim().ifBlank { null },
                isEdited = edited,
                isSystemMessage = sender == null
            )
        }

        return WhatsAppParsedMessage(
            sender = sender,
            timestampMillis = timestampMillis,
            text = body,
            isEdited = edited,
            isSystemMessage = sender == null
        )
    }

    private fun toEpochMillis(
        d: Int, mo: Int, y: Int, h: Int, mi: Int, s: Int, ampm: String?,
        dateOrder: WhatsAppDateOrder, timeZone: TimeZone
    ): Long {
        val year = when {
            y >= 100 -> y
            y < 70 -> 2000 + y
            else -> 1900 + y
        }
        val (day, month) = if (dateOrder == WhatsAppDateOrder.DAY_MONTH_YEAR) d to mo else mo to d
        var hour = h
        if (ampm != null) {
            val isPm = ampm.equals("PM", ignoreCase = true)
            hour = when {
                isPm && h != 12 -> h + 12
                !isPm && h == 12 -> 0
                else -> h
            }
        }
        val cal = Calendar.getInstance(timeZone)
        cal.clear()
        cal.set(year, month - 1, day, hour, mi, s)
        return cal.timeInMillis
    }
}
