package com.rustypastechat.ui.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.ui.theme.Blue
import com.rustypastechat.ui.theme.BubbleIncomingDark
import com.rustypastechat.ui.theme.BubbleIncomingLight
import com.rustypastechat.ui.theme.BubbleIncomingTextDark
import com.rustypastechat.ui.theme.BubbleIncomingTextLight
import com.rustypastechat.ui.theme.BubbleOutgoingDark
import com.rustypastechat.ui.theme.BubbleOutgoingLight
import com.rustypastechat.ui.theme.BubbleOutgoingTextDark
import com.rustypastechat.ui.theme.BubbleOutgoingTextLight
import com.rustypastechat.ui.theme.Green
import com.rustypastechat.ui.theme.ImportBgDark
import com.rustypastechat.ui.theme.ImportBgLight
import com.rustypastechat.ui.theme.ImportTextDark
import com.rustypastechat.ui.theme.ImportTextLight
import com.rustypastechat.ui.theme.LinkBlueDark
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

private val URL_PATTERN = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")

@Composable
fun SwipeableMessageBubble(
    message: Message,
    isSelected: Boolean = false,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit = {},
    onReply: (String) -> Unit = {},
    onForward: (String) -> Unit = {},
    onLongPress: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val swipeThreshold = 120f
    var swipeDisplay by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(offsetX) {
        snapshotFlow { offsetX.value }.collect { swipeDisplay = it }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Swipe action backgrounds
        if (swipeDisplay > 30f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Green.copy(alpha = 0.9f)),
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
                            scope.launch { offsetX.animateTo(0f, spring(Spring.StiffnessMedium, Spring.DampingRatioMediumBouncy)) }
                        },
                        onDragEnd = {
                            if (swipeDisplay > swipeThreshold) onReply(message.id)
                            else if (swipeDisplay < -swipeThreshold) onDelete(message.id)
                            scope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                offsetX.snapTo((swipeDisplay + dragAmount).coerceIn(-200f, 200f))
                            }
                        }
                    )
                }
                .clickable(enabled = !isSelected) {
                    onLongPress(message.id)
                }
        ) {
            MessageBubbleContent(
                message = message,
                isSelected = isSelected,
                onRetry = onRetry,
                onDelete = onDelete,
                onCopy = onCopy,
                onReply = onReply,
                onForward = onForward
            )
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: Message,
    isSelected: Boolean,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit,
    onReply: (String) -> Unit,
    onForward: (String) -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val isOutgoing = message.isOutgoing

    val bubbleColor = when {
        message.isImported -> if (darkTheme) ImportBgDark else ImportBgLight
        isOutgoing -> if (darkTheme) BubbleOutgoingDark else BubbleOutgoingLight
        else -> if (darkTheme) BubbleIncomingDark else BubbleIncomingLight
    }
    val textColor = when {
        message.isImported -> if (darkTheme) ImportTextDark else ImportTextLight
        isOutgoing -> if (darkTheme) BubbleOutgoingTextDark else BubbleOutgoingTextLight
        else -> if (darkTheme) BubbleIncomingTextDark else BubbleIncomingTextLight
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
        ) {
            Column {
                if (!message.mediaUrl.isNullOrBlank() && message.mediaType != com.rustypastechat.data.model.MediaType.FILE) {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = "Image",
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (message.text.isNotBlank()) {
                    RichTextContent(message.text, textColor, darkTheme)
                } else if (message.isLlmResponse && message.status != MessageStatus.FAILED) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking...", color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (message.isOneshot) {
                    Spacer(Modifier.height(2.dp))
                    Text("View once", color = textColor.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                if (message.isImported) {
                    Spacer(Modifier.height(2.dp))
                    Text("Imported", color = textColor.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(timeText, color = textColor.copy(alpha = 0.5f), fontSize = 11.sp)
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
                Text("Failed", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text("Retry", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onRetry(message.id) })
                Spacer(Modifier.width(8.dp))
                Text("Delete", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp,
                    modifier = Modifier.clickable { onDelete(message.id) })
            }
        }
    }
}

@Composable
private fun ReplyPreview(text: String, isOutgoing: Boolean) {
    val darkTheme = isSystemInDarkTheme()
    val accentColor = if (isOutgoing) {
        if (darkTheme) BubbleOutgoingDark else BubbleOutgoingLight
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

@Composable
private fun RichTextContent(text: String, textColor: Color, darkTheme: Boolean) {
    val linkColor = if (darkTheme) LinkBlueDark else Blue
    val matcher = URL_PATTERN.matcher(text)
    val annotated = buildAnnotatedString {
        var lastEnd = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start > lastEnd) append(text.substring(lastEnd, start))
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(matcher.group())
            }
            lastEnd = end
        }
        if (lastEnd < text.length) append(text.substring(lastEnd))
    }
    Text(annotated, color = textColor, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(end = 36.dp))
}
