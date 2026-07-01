package com.rustypastechat.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.model.MessageStatus
import com.rustypastechat.ui.theme.BubbleIncoming
import com.rustypastechat.ui.theme.BubbleIncomingText
import com.rustypastechat.ui.theme.BubbleOutgoing
import com.rustypastechat.ui.theme.BubbleOutgoingText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing
    val bubbleColor = if (isOutgoing) BubbleOutgoing else BubbleIncoming
    val textColor = if (isOutgoing) BubbleOutgoingText else BubbleIncomingText
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeText = timeFormatter.format(Date(message.timestamp))
    val rowAlignment = if (isOutgoing) Alignment.End else Alignment.Start

    // Asymmetric WhatsApp-style bubble shapes
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(
            topStart = 16.dp, topEnd = 4.dp,
            bottomStart = 16.dp, bottomEnd = 16.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp, topEnd = 16.dp,
            bottomStart = 16.dp, bottomEnd = 16.dp
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = rowAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 60.dp, max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Column {
                if (!message.mediaUrl.isNullOrBlank() && message.mediaType != com.rustypastechat.data.model.MediaType.FILE) {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 36.dp)
                    )
                } else if (message.isLlmResponse && message.status != MessageStatus.FAILED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BubbleIncomingText
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Thinking...",
                            color = BubbleIncomingText.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = timeText,
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    if (isOutgoing) {
                        Spacer(Modifier.width(4.dp))
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }

        if (message.status == MessageStatus.FAILED && isOutgoing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Failed to send",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Retry",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onRetry(message.id) }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onDelete(message.id) }
                )
            }
        }
    }
}
