package com.rustypastechat.ui.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.ui.theme.RustyColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

private val URL_PATTERN = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")

private fun firstUrlIn(text: String): String? {
    val matcher = URL_PATTERN.matcher(text)
    return if (matcher.find()) matcher.group() else null
}

private fun buildUrlOnlyAnnotated(text: String, linkColor: Color) = buildAnnotatedString {
    val matcher = URL_PATTERN.matcher(text)
    var lastEnd = 0
    while (matcher.find()) {
        if (matcher.start() > lastEnd) append(text.substring(lastEnd, matcher.start()))
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(matcher.group())
        }
        lastEnd = matcher.end()
    }
    if (lastEnd < text.length) append(text.substring(lastEnd))
}

@Composable
fun SwipeableMessageBubble(
    message: Message,
    isSelected: Boolean = false,
    isOneshotViewed: Boolean = false,
    isStarred: Boolean = false,
    markdownEnabled: Boolean = true,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit = {},
    onReply: (String) -> Unit = {},
    onForward: (String) -> Unit = {},
    onLongPress: (String) -> Unit = {},
    onViewOneshot: (String) -> Unit = {},
    onToggleStar: (String) -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val swipeThreshold = 120f
    var swipeDisplay by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    var hasTickedThreshold by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        // Swipe action backgrounds
        if (swipeDisplay > 30f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(RustyColors.Success.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Reply, "Reply", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (swipeDisplay < -30f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = swipeDisplay }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragCancel = {
                            swipeDisplay = 0f
                            hasTickedThreshold = false
                            scope.launch { offsetX.animateTo(0f, spring(Spring.StiffnessMedium, Spring.DampingRatioMediumBouncy)) }
                        },
                        onDragEnd = {
                            if (swipeDisplay > swipeThreshold) onReply(message.id)
                            else if (swipeDisplay < -swipeThreshold) onDelete(message.id)
                            swipeDisplay = 0f
                            hasTickedThreshold = false
                            scope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeDisplay = (swipeDisplay + dragAmount).coerceIn(-200f, 200f)
                            val pastThreshold = swipeDisplay.let { it > swipeThreshold || it < -swipeThreshold }
                            if (pastThreshold && !hasTickedThreshold) {
                                hasTickedThreshold = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (!pastThreshold) {
                                hasTickedThreshold = false
                            }
                            scope.launch {
                                offsetX.snapTo(swipeDisplay)
                            }
                        }
                    )
                }
                .clickable(enabled = !isSelected) {
                    onLongPress(message.id)
                }
                // Swipe-to-reply/delete is gesture-only and invisible to TalkBack;
                // expose the same actions as custom accessibility actions.
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Reply") { onReply(message.id); true },
                        CustomAccessibilityAction("Delete") { onDelete(message.id); true }
                    )
                }
        ) {
            MessageBubbleContent(
                message = message,
                isSelected = isSelected,
                isOneshotViewed = isOneshotViewed,
                isStarred = isStarred,
                markdownEnabled = markdownEnabled,
                onRetry = onRetry,
                onDelete = onDelete,
                onCopy = onCopy,
                onReply = onReply,
                onForward = onForward,
                onViewOneshot = onViewOneshot,
                onToggleStar = onToggleStar,
                onReact = onReact
            )
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: Message,
    isSelected: Boolean,
    isOneshotViewed: Boolean,
    isStarred: Boolean,
    markdownEnabled: Boolean,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit,
    onReply: (String) -> Unit,
    onForward: (String) -> Unit,
    onViewOneshot: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    val darkTheme = isSystemInDarkTheme()
    val isOutgoing = message.isOutgoing
    var showReactionPicker by remember { mutableStateOf(false) }

    val bubbleColor = when {
        message.isLlmResponse -> if (darkTheme) RustyColors.LlmBgDark else RustyColors.LlmBgLight
        message.isImported -> if (darkTheme) RustyColors.ImportBgDark else RustyColors.ImportBgLight
        isOutgoing -> if (darkTheme) RustyColors.BubbleOutgoingDark else RustyColors.BubbleOutgoingLight
        else -> if (darkTheme) RustyColors.BubbleIncomingDark else RustyColors.BubbleIncomingLight
    }
    val textColor = when {
        message.isLlmResponse -> if (darkTheme) RustyColors.LlmTextDark else RustyColors.LlmTextLight
        message.isImported -> if (darkTheme) RustyColors.ImportTextDark else RustyColors.ImportTextLight
        isOutgoing -> if (darkTheme) RustyColors.BubbleOutgoingTextDark else RustyColors.BubbleOutgoingTextLight
        else -> if (darkTheme) RustyColors.BubbleIncomingTextDark else RustyColors.BubbleIncomingTextLight
    }

    val timeText = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }
    val rowAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val bg = if (isSelected) bubbleColor.copy(alpha = 0.6f) else bubbleColor

    val statusLabel = if (isOutgoing) when (message.status) {
        MessageStatus.SENDING -> "sending"
        MessageStatus.SENT -> "sent"
        MessageStatus.DELIVERED -> "delivered"
        MessageStatus.READ -> "read"
        MessageStatus.FAILED -> "failed to send"
    } else null
    val bubbleDescription = buildString {
        append(if (isOutgoing) "You" else "Message")
        if (message.mediaType == com.rustypastechat.data.model.MediaType.FILE && message.text.isNotBlank()) {
            append(": file ${message.text}")
        } else if (message.text.isNotBlank()) {
            append(": ${message.text}")
        } else if (!message.mediaUrl.isNullOrBlank()) {
            append(if (message.mediaType == com.rustypastechat.data.model.MediaType.VIDEO) ": video" else ": image")
        }
        append(", $timeText")
        if (statusLabel != null) append(", $statusLabel")
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = rowAlignment
    ) {
        if (!message.replyToText.isNullOrBlank()) {
            ReplyPreview(message.replyToText, message.replyToIsOutgoing ?: false)
        }

        Box(
            modifier = Modifier
                .widthIn(min = 60.dp, max = 280.dp)
                .clip(bubbleShape)
                .background(bg)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = bubbleDescription }
        ) {
            Column {
                if (message.isOneshot && isOneshotViewed) {
                    OneshotOpenedPlaceholder(textColor)
                } else if (message.isOneshot && !isOneshotViewed) {
                    OneshotGatePlaceholder(textColor) { onViewOneshot(message.id) }
                } else {
                    if (!message.mediaUrl.isNullOrBlank()) {
                        when (message.mediaType) {
                            com.rustypastechat.data.model.MediaType.VIDEO -> {
                                VideoAttachment(message.mediaUrl)
                                Spacer(Modifier.height(4.dp))
                            }
                            com.rustypastechat.data.model.MediaType.FILE -> {
                                FileAttachment(message.mediaUrl, message.text)
                                Spacer(Modifier.height(4.dp))
                            }
                            com.rustypastechat.data.model.MediaType.AUDIO -> {
                                AudioAttachment(message.mediaUrl)
                                Spacer(Modifier.height(4.dp))
                            }
                            else -> {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Image",
                                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    val suppressCaption = message.mediaType == com.rustypastechat.data.model.MediaType.FILE ||
                        message.mediaType == com.rustypastechat.data.model.MediaType.AUDIO
                    if (!suppressCaption && message.text.isNotBlank()) {
                        RichTextContent(message.text, textColor, darkTheme, markdownEnabled)
                        if (message.mediaUrl.isNullOrBlank()) {
                            firstUrlIn(message.text)?.let { url ->
                                Spacer(Modifier.height(6.dp))
                                UrlPreview(url)
                            }
                        }
                    } else if (message.isLlmResponse && message.status != MessageStatus.FAILED) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Thinking...", color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (isStarred) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = "Starred", tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    }
                }
                if (message.isImported) {
                    Spacer(Modifier.height(2.dp))
                    Text("Imported", color = textColor.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }

                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = textColor.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { showReactionPicker = true }
                    ) {
                        Text(
                            message.reactions.joinToString(" "),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (message.isLlmResponse && message.text.isNotBlank() && message.status != MessageStatus.SENDING) {
                    Text(
                        "~${approxTokenCount(message.text)} tokens",
                        color = textColor.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (message.mediaUrl == null) {
                        Box {
                            Icon(
                                Icons.Default.AddReaction,
                                contentDescription = "Add reaction",
                                tint = textColor.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { showReactionPicker = true }
                            )
                            if (showReactionPicker) {
                                Popup(
                                    alignment = Alignment.BottomEnd,
                                    onDismissRequest = { showReactionPicker = false },
                                    properties = PopupProperties(focusable = false)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shadowElevation = 8.dp
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                                Text(
                                                    emoji,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    modifier = Modifier
                                                        .clickable {
                                                            onReact(message.id, emoji)
                                                            showReactionPicker = false
                                                        }
                                                        .padding(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (message.isEdited) {
                        Text(
                            "edited",
                            color = textColor.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(timeText, color = textColor.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    if (isOutgoing) {
                        Spacer(Modifier.width(4.dp))
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }

        if (message.status == MessageStatus.FAILED && isOutgoing) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Default.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text("Retry", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onRetry(message.id) })
                Spacer(Modifier.width(8.dp))
                Text("Delete", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { onDelete(message.id) })
            }
        }
    }
}

@Composable
private fun VideoAttachment(url: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    )
                }
            }
            .semantics { contentDescription = "Video attachment, tap to play" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun FileAttachment(url: String, fileName: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.15f))
            .clickable {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    )
                }
            }
            .padding(10.dp)
            .semantics { contentDescription = "File attachment: $fileName, tap to open" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AudioAttachment(url: String) {
    var isPlaying by remember(url) { mutableStateOf(false) }
    var durationMs by remember(url) { mutableStateOf(0) }
    var positionMs by remember(url) { mutableStateOf(0) }
    val mediaPlayer = remember(url) { android.media.MediaPlayer() }

    DisposableEffect(url) {
        onDispose { runCatching { mediaPlayer.release() } }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = runCatching { mediaPlayer.currentPosition }.getOrDefault(positionMs)
            kotlinx.coroutines.delay(200)
        }
    }

    val togglePlay: () -> Unit = togglePlay@{
        if (isPlaying) {
            runCatching { mediaPlayer.pause() }
            isPlaying = false
            return@togglePlay
        }
        if (durationMs == 0) {
            runCatching {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(url)
                mediaPlayer.setOnPreparedListener {
                    // Runs async, possibly after this bubble scrolled away and released
                    // the player (DisposableEffect) — never let a stale callback crash it.
                    runCatching {
                        durationMs = it.duration
                        it.start()
                        isPlaying = true
                    }
                }
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                }
                mediaPlayer.prepareAsync()
            }
        } else {
            runCatching { mediaPlayer.start() }
            isPlaying = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "Voice message, ${formatAudioDuration(durationMs)}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = togglePlay, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        WaveformBars(
            seed = url,
            progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            formatAudioDuration(if (isPlaying || positionMs > 0) (durationMs - positionMs).coerceAtLeast(0) else durationMs),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun WaveformBars(seed: String, progress: Float, modifier: Modifier = Modifier) {
    val bars = remember(seed) {
        val rnd = kotlin.random.Random(seed.hashCode())
        List(24) { 6 + rnd.nextInt(16) }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { index, heightDp ->
            val played = index.toFloat() / bars.size < progress
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .width(3.dp)
                    .height(heightDp.dp)
                    .background(
                        if (played) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private fun formatAudioDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun OneshotGatePlaceholder(textColor: Color, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onTap)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "View once message, tap to view" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Visibility, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Tap to view — disappears after opening", color = textColor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OneshotOpenedPlaceholder(textColor: Color) {
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "View once message, already opened" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Opened", color = textColor.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
private fun ReplyPreview(text: String, isOutgoing: Boolean) {
    val darkTheme = isSystemInDarkTheme()
    val accentColor = if (isOutgoing) {
        if (darkTheme) RustyColors.BubbleOutgoingDark else RustyColors.BubbleOutgoingLight
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
        Box(
            Modifier.width(3.dp).height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val FORMAT_PATTERN = Pattern.compile(
    "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)" + // 1: url
        "|(\\*\\*([^*]+)\\*\\*)" + // 2/3: bold
        "|(__([^_]+)__)" + // 4/5: underline
        "|(~~([^~]+)~~)" + // 6/7: strike
        "|(`([^`]+)`)" + // 8/9: inline code
        "|(\\*([^*]+)\\*)" + // 10/11: italic
        "|(#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b)" // 12: hex color code
)

private val FULL_HEX_COLOR_PATTERN = Pattern.compile("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})")

private fun parseHexColor(hex: String): Color? {
    // android.graphics.Color.parseColor only understands #RRGGBB/#AARRGGBB, not the #RGB
    // shorthand — expand 3-digit hex (#f0a -> #ff00aa) before handing it off.
    val body = hex.removePrefix("#")
    val expanded = if (body.length == 3) body.map { "$it$it" }.joinToString("") else body
    return runCatching { Color(android.graphics.Color.parseColor("#$expanded")) }.getOrNull()
}

private fun buildInlineAnnotated(text: String, linkColor: Color) = buildAnnotatedString {
    val matcher = FORMAT_PATTERN.matcher(text)
    var lastEnd = 0
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        if (start > lastEnd) append(text.substring(lastEnd, start))
        when {
            matcher.group(1) != null -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(matcher.group(1))
            }
            matcher.group(2) != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matcher.group(3))
            }
            matcher.group(4) != null -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(matcher.group(5))
            }
            matcher.group(6) != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(matcher.group(7))
            }
            matcher.group(8) != null -> withStyle(
                SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            ) {
                append(matcher.group(9))
            }
            matcher.group(10) != null -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                append(matcher.group(11))
            }
            matcher.group(12) != null -> withStyle(
                SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            ) {
                append(matcher.group(12))
            }
        }
        lastEnd = end
    }
    if (lastEnd < text.length) append(text.substring(lastEnd))
}

/**
 * Same inline formatting as [buildInlineAnnotated], plus a real color swatch rendered right next
 * to any hex color code found in the text — "integrated" per the design brief (no menu, no
 * markdown ceremony, it just lights up because the app recognized what you typed).
 */
private fun buildInlineWithSwatches(text: String, linkColor: Color): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        val matcher = FORMAT_PATTERN.matcher(text)
        var lastEnd = 0
        var swatchCount = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start > lastEnd) append(text.substring(lastEnd, start))
            when {
                matcher.group(1) != null -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(matcher.group(1))
                }
                matcher.group(2) != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(matcher.group(3)) }
                matcher.group(4) != null -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(matcher.group(5)) }
                matcher.group(6) != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(matcher.group(7)) }
                matcher.group(8) != null -> withStyle(
                    SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                ) { append(matcher.group(9)) }
                matcher.group(10) != null -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(matcher.group(11))
                }
                matcher.group(12) != null -> {
                    val hex = matcher.group(12)
                    val swatchColor = parseHexColor(hex)
                    if (swatchColor != null) {
                        val id = "swatch_${swatchCount++}"
                        inlineContent[id] = InlineTextContent(
                            Placeholder(
                                width = 14.sp, height = 14.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                            )
                        }
                        appendInlineContent(id, "●")
                        append(" ")
                        withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                            append(hex)
                        }
                    } else {
                        append(hex)
                    }
                }
            }
            lastEnd = end
        }
        if (lastEnd < text.length) append(text.substring(lastEnd))
    }
    return annotated to inlineContent
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Ordered(val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String?, val code: String) : MdBlock
    data class MermaidBlock(val code: String) : MdBlock
    data class SvgBlock(val svg: String) : MdBlock
}

private val HEADING_REGEX = Regex("^#{1,6}\\s+.*")
private val ORDERED_REGEX = Regex("^\\d+\\.\\s+.*")

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim().lowercase()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i]); i++
                }
                if (i < lines.size) i++ // consume closing fence
                val code = codeLines.joinToString("\n")
                blocks.add(
                    when (lang) {
                        "mermaid" -> MdBlock.MermaidBlock(code)
                        "svg" -> MdBlock.SvgBlock(code)
                        else -> MdBlock.Code(lang.ifBlank { null }, code)
                    }
                )
            }
            HEADING_REGEX.matches(trimmed) -> {
                val level = trimmed.takeWhile { it == '#' }.length
                blocks.add(MdBlock.Heading(level, trimmed.removePrefix("#".repeat(level)).trim()))
                i++
            }
            trimmed.startsWith("> ") -> {
                blocks.add(MdBlock.Quote(trimmed.removePrefix("> ")))
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MdBlock.Bullet(trimmed.substring(2)))
                i++
            }
            ORDERED_REGEX.matches(trimmed) -> {
                val num = trimmed.takeWhile { it.isDigit() }
                blocks.add(MdBlock.Ordered(num, trimmed.substringAfter(". ")))
                i++
            }
            trimmed.isBlank() -> i++ // blank line: block separator, no empty block emitted
            else -> {
                blocks.add(MdBlock.Paragraph(line))
                i++
            }
        }
    }
    return blocks
}

/** Real JSON syntax coloring (keys/strings/numbers/booleans/null each their own color) instead
 *  of flat monospace — used both for explicit ```json fences and auto-detected whole-message JSON. */
private fun buildJsonHighlighted(
    element: kotlinx.serialization.json.JsonElement,
    palette: JsonPalette,
    indent: Int = 0,
    builder: androidx.compose.ui.text.AnnotatedString.Builder
) {
    val pad = "  ".repeat(indent)
    val childPad = "  ".repeat(indent + 1)
    when (element) {
        is kotlinx.serialization.json.JsonObject -> {
            builder.append("{\n")
            val entries = element.entries.toList()
            entries.forEachIndexed { idx, (key, value) ->
                builder.append(childPad)
                builder.withStyle(SpanStyle(color = palette.key)) { append("\"$key\"") }
                builder.append(": ")
                buildJsonHighlighted(value, palette, indent + 1, builder)
                if (idx < entries.lastIndex) builder.append(",")
                builder.append("\n")
            }
            builder.append(pad); builder.append("}")
        }
        is kotlinx.serialization.json.JsonArray -> {
            builder.append("[\n")
            element.forEachIndexed { idx, value ->
                builder.append(childPad)
                buildJsonHighlighted(value, palette, indent + 1, builder)
                if (idx < element.lastIndex) builder.append(",")
                builder.append("\n")
            }
            builder.append(pad); builder.append("]")
        }
        is kotlinx.serialization.json.JsonNull -> builder.withStyle(SpanStyle(color = palette.nullColor)) { builder.append("null") }
        is kotlinx.serialization.json.JsonPrimitive -> when {
            element.isString -> builder.withStyle(SpanStyle(color = palette.string)) { append("\"${escapeJsonString(element.content)}\"") }
            element.content == "true" || element.content == "false" -> builder.withStyle(SpanStyle(color = palette.bool)) { append(element.content) }
            else -> builder.withStyle(SpanStyle(color = palette.number)) { append(element.content) }
        }
    }
}

private data class JsonPalette(val key: Color, val string: Color, val number: Color, val bool: Color, val nullColor: Color)

private fun escapeJsonString(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\t", "\\t")

@Composable
private fun rememberJsonPalette(): JsonPalette {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary) {
        JsonPalette(
            key = primary,
            string = RustyColors.Success,
            number = RustyColors.Patina,
            bool = RustyColors.Warning,
            nullColor = Color.Gray
        )
    }
}

@Composable
private fun JsonContent(json: String) {
    val palette = rememberJsonPalette()
    val annotated = remember(json) {
        runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(json)
            buildAnnotatedString { buildJsonHighlighted(element, palette, 0, this) }
        }.getOrNull()
    }
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            annotated ?: buildAnnotatedString { append(json) },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/** Whole message is (only) a valid JSON object/array — auto-render highlighted, no fence needed. */
/** Rough token estimate (~4 chars/token, the common rule of thumb for English text) — good
 *  enough for a "roughly how much this cost" hint, not meant to match a real tokenizer. */
private fun approxTokenCount(text: String): Int = (text.length / 4).coerceAtLeast(1)

private fun looksLikeWholeMessageJson(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 2) return false
    if (!(trimmed.startsWith("{") && trimmed.endsWith("}")) && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) return false
    return runCatching {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
        element is kotlinx.serialization.json.JsonObject || element is kotlinx.serialization.json.JsonArray
    }.getOrDefault(false)
}

/** Whole message is just a bare color code ("#ff5733" and nothing else) — show a real swatch card
 *  instead of a tiny inline dot, matching how much of the message it actually is. */
@Composable
private fun ColorSwatchCard(hex: String, textColor: Color) {
    val color = parseHexColor(hex) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(hex, color = textColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Medium)
            Text(
                "rgb(${color.red.times(255).toInt()}, ${color.green.times(255).toInt()}, ${color.blue.times(255).toInt()})",
                color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RichTextContent(text: String, textColor: Color, darkTheme: Boolean, markdownEnabled: Boolean) {
    val linkColor = if (darkTheme) RustyColors.LinkDark else RustyColors.LinkLight

    if (!markdownEnabled) {
        // Markdown formatting off: keep URL auto-linking (not really "markdown", always expected)
        // but don't parse **/__/~~/`/* markers — show them as literally typed, for users who'd
        // otherwise be confused by stray asterisks/underscores turning into styling.
        Text(buildUrlOnlyAnnotated(text, linkColor), color = textColor, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 36.dp))
        return
    }

    if (text.trim().startsWith("<svg", ignoreCase = true)) {
        SvgContent(text.trim())
        return
    }

    // Auto-detect whole-message content that deserves rich rendering with zero ceremony —
    // no ```fence, no menu, no "format as JSON" button. It just recognizes what was pasted.
    val trimmedWhole = text.trim()
    val wholeMessageHex = FULL_HEX_COLOR_PATTERN.matcher(trimmedWhole).let { if (it.matches()) trimmedWhole else null }
    if (wholeMessageHex != null && parseHexColor(wholeMessageHex) != null) {
        ColorSwatchCard(wholeMessageHex, textColor)
        return
    }
    if (looksLikeWholeMessageJson(trimmedWhole)) {
        JsonContent(trimmedWhole)
        return
    }

    val blocks = remember(text) { parseMarkdownBlocks(text) }

    // Fast path: a single plain paragraph (by far the most common message) keeps the original,
    // simpler single-Text rendering rather than wrapping every message in an extra Column.
    val onlyBlock = blocks.singleOrNull() as? MdBlock.Paragraph
    if (onlyBlock != null) {
        val (annotated, inlineContent) = remember(onlyBlock.text, linkColor) { buildInlineWithSwatches(onlyBlock.text, linkColor) }
        Text(annotated, color = textColor, style = MaterialTheme.typography.bodyLarge,
            inlineContent = inlineContent, modifier = Modifier.padding(end = 36.dp))
        return
    }

    Column(modifier = Modifier.padding(end = 36.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    buildInlineAnnotated(block.text, linkColor),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                )
                is MdBlock.Paragraph -> {
                    val (annotated, inlineContent) = remember(block.text, linkColor) { buildInlineWithSwatches(block.text, linkColor) }
                    Text(
                        annotated, color = textColor, inlineContent = inlineContent,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is MdBlock.Bullet -> Row {
                    Text("•  ", color = textColor, style = MaterialTheme.typography.bodyLarge)
                    Text(buildInlineAnnotated(block.text, linkColor), color = textColor, style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Ordered -> Row {
                    Text("${block.number}.  ", color = textColor, style = MaterialTheme.typography.bodyLarge)
                    Text(buildInlineAnnotated(block.text, linkColor), color = textColor, style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Quote -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(18.dp).background(textColor.copy(alpha = 0.35f)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildInlineAnnotated(block.text, linkColor), color = textColor.copy(alpha = 0.8f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is MdBlock.Code -> {
                    if (block.lang == "json") {
                        JsonContent(block.code)
                    } else {
                        androidx.compose.material3.Surface(
                            color = textColor.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                block.code, color = textColor, style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                is MdBlock.MermaidBlock -> MermaidDiagram(block.code)
                is MdBlock.SvgBlock -> SvgContent(block.svg)
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun SvgContent(svg: String) {
    val context = LocalContext.current
    val file = remember(svg) {
        java.io.File(context.cacheDir, "svg_${svg.hashCode()}.svg").also { f ->
            if (!f.exists()) runCatching { f.writeText(svg) }
        }
    }
    AsyncImage(
        model = file,
        contentDescription = "Diagram",
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun MermaidDiagram(code: String) {
    val darkTheme = isSystemInDarkTheme()
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // This WebView only ever loads one hardcoded local HTML string (never a remote
                // or user-supplied URL) — block any navigation attempt as defense in depth.
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean = true
                }
            }
        },
        update = { webView ->
            val theme = if (darkTheme) "dark" else "default"
            val escaped = code.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
            val html = """
                <html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <script src="file:///android_asset/mermaid.min.js"></script>
                <style>body{margin:0;padding:8px;background:transparent;}</style>
                </head><body>
                <pre class="mermaid">$escaped</pre>
                <script>
                  mermaid.initialize({ startOnLoad: true, theme: "$theme", securityLevel: "strict" });
                </script>
                </body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        }
    )
}
