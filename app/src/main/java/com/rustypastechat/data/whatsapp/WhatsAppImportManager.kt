package com.rustypastechat.data.whatsapp

import com.rustypastechat.data.model.Message
import com.rustypastechat.data.repository.PasteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class WhatsAppImportResult(
    val totalMessages: Int,
    val uploaded: Int,
    val failed: Int,
    val mediaPlaceholders: Int
)

/**
 * Converts WhatsApp's `*bold*` / `_italic_` / `~strikethrough~` into this app's own
 * `**bold**` / `*italic*` / `~~strikethrough~~` markup in a single linear pass — doing
 * these as three sequential .replace() calls would corrupt the result, since the
 * underscore->single-asterisk conversion's own output would then get re-wrapped by the
 * asterisk->double-asterisk pass.
 */
private val WHATSAPP_MARKUP = Regex("""\*([^*\n]+)\*|_([^_\n]+)_|~([^~\n]+)~""")

fun remapWhatsAppMarkupToApp(text: String): String = WHATSAPP_MARKUP.replace(text) { m ->
    when {
        m.groups[1] != null -> "**${m.groupValues[1]}**"
        m.groups[2] != null -> "*${m.groupValues[2]}*"
        m.groups[3] != null -> "~~${m.groupValues[3]}~~"
        else -> m.value
    }
}

@Singleton
class WhatsAppImportManager @Inject constructor(
    private val pasteRepository: PasteRepository
) {
    /**
     * Uploads every message in a parsed WhatsApp export as a paste note under [chatId],
     * preserving each message's original date/time via the historical timestamp embedded
     * in its filename (same mechanism [Message.buildFileName] always uses) so the chat
     * reconstructs in true chronological order regardless of upload order.
     *
     * Media that WhatsApp's own export omits (image/video/audio/sticker/document bytes
     * are never included in a `_chat.txt`-only export) is uploaded as a text placeholder
     * noting what was there — there is no way to recover bytes WhatsApp never gave us.
     */
    suspend fun import(
        rawText: String,
        chatId: String,
        myDisplayName: String? = null,
        dateOrder: WhatsAppDateOrder = WhatsAppDateOrder.DAY_MONTH_YEAR,
        concurrency: Int = 5,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): WhatsAppImportResult = coroutineScope {
        val parsed = WhatsAppChatParser.parse(rawText, dateOrder).filterNot { it.isSystemMessage }
        var uploaded = 0
        var failed = 0
        var mediaPlaceholders = 0
        var completed = 0

        parsed.chunked(concurrency).forEach { chunk ->
            val outcomes = chunk.map { msg ->
                async {
                    val isOutgoing = myDisplayName == null ||
                        msg.sender?.equals(myDisplayName, ignoreCase = true) == true
                    val id = UUID.randomUUID().toString()
                    val fileName = Message.buildFileName(chatId, msg.timestampMillis, isOutgoing, id)
                    val text = buildUploadText(msg)
                    pasteRepository.uploadText(text, fileName).isSuccess to (msg.mediaKind != null)
                }
            }.map { it.await() }

            outcomes.forEach { (success, isMedia) ->
                if (success) uploaded++ else failed++
                if (isMedia) mediaPlaceholders++
            }
            completed += chunk.size
            onProgress(completed, parsed.size)
        }

        WhatsAppImportResult(
            totalMessages = parsed.size,
            uploaded = uploaded,
            failed = failed,
            mediaPlaceholders = mediaPlaceholders
        )
    }

    private fun buildUploadText(msg: WhatsAppParsedMessage): String {
        val body = when {
            msg.mediaKind != null -> {
                val label = msg.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
                val hint = msg.mediaFileNameHint?.let { " ($it)" }.orEmpty()
                "[$label omitted$hint — original not included in WhatsApp's export]"
            }
            else -> remapWhatsAppMarkupToApp(msg.text)
        }
        // Use the same invisible sentinel the app's own edit feature relies on, so an
        // imported edited message gets the identical "edited" badge instead of a visible,
        // permanent "(edited)" baked into the text.
        return if (msg.isEdited) "$body${Message.EDITED_SENTINEL}" else body
    }
}
