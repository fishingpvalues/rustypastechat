package com.rustypastechat.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.rustypastechat.data.model.Message

@Composable
fun MediaGalleryGrid(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
    maxHeight: Dp = 360.dp,
    imageRadius: Dp = 4.dp
) {
    if (messages.isEmpty()) return
    val count = messages.size

    Column(modifier = modifier.widthIn(max = maxWidth)) {
        when (count) {
            1 -> SingleImageView(messages[0], maxWidth, maxHeight, imageRadius)
            2 -> TwoImagesView(messages, maxWidth, imageRadius)
            3 -> ThreeImagesView(messages, maxWidth, imageRadius)
            4 -> FourImagesView(messages, maxWidth, imageRadius)
            else -> ManyImagesView(messages, maxWidth, imageRadius)
        }
    }
}

@Composable
private fun SingleImageView(msg: Message, maxWidth: Dp, maxHeight: Dp, radius: Dp) {
    var showFullScreen by remember { mutableStateOf(false) }

    AsyncImage(
        model = msg.mediaUrl,
        contentDescription = "Image",
        modifier = Modifier
            .widthIn(max = maxWidth)
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(radius))
            .clickable { showFullScreen = true },
        contentScale = ContentScale.FillWidth
    )

    if (showFullScreen) {
        FullScreenImageViewer(
            imageUrl = msg.mediaUrl ?: return,
            onDismiss = { showFullScreen = false }
        )
    }
}

@Composable
private fun TwoImagesView(msgs: List<Message>, maxWidth: Dp, radius: Dp) {
    val halfWidth = maxWidth / 2 - 1.dp
    val height = halfWidth

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        msgs.take(2).forEach { msg ->
            AsyncImage(
                model = msg.mediaUrl,
                contentDescription = "Image",
                modifier = Modifier
                    .size(width = halfWidth, height = height)
                    .clip(RoundedCornerShape(radius)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ThreeImagesView(msgs: List<Message>, maxWidth: Dp, radius: Dp) {
    val largeHeight = maxWidth * 0.7f
    val smallHeight = largeHeight / 2 - 1.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AsyncImage(
            model = msgs[0].mediaUrl,
            contentDescription = "Image",
            modifier = Modifier
                .width(maxWidth * 0.6f)
                .height(largeHeight)
                .clip(RoundedCornerShape(topStart = radius, bottomStart = radius)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            msgs.drop(1).take(2).forEachIndexed { index, msg ->
                val shape = when {
                    index == 1 -> RoundedCornerShape(bottomEnd = radius)
                    else -> RoundedCornerShape(topEnd = radius)
                }
                AsyncImage(
                    model = msg.mediaUrl,
                    contentDescription = "Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(smallHeight)
                        .clip(shape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun FourImagesView(msgs: List<Message>, maxWidth: Dp, radius: Dp) {
    val halfWidth = maxWidth / 2 - 1.dp
    val halfHeight = halfWidth

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            msgs.take(2).forEach { msg ->
                AsyncImage(
                    model = msg.mediaUrl,
                    contentDescription = "Image",
                    modifier = Modifier
                        .size(width = halfWidth, height = halfHeight)
                        .clip(RoundedCornerShape(radius)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            msgs.drop(2).take(2).forEach { msg ->
                AsyncImage(
                    model = msg.mediaUrl,
                    contentDescription = "Image",
                    modifier = Modifier
                        .size(width = halfWidth, height = halfHeight)
                        .clip(RoundedCornerShape(radius)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ManyImagesView(msgs: List<Message>, maxWidth: Dp, radius: Dp) {
    val remaining = msgs.size - 3

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AsyncImage(
            model = msgs[0].mediaUrl,
            contentDescription = "Image",
            modifier = Modifier
                .weight(0.6f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = radius, bottomStart = radius)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(0.4f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AsyncImage(
                model = msgs[1].mediaUrl,
                contentDescription = "Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topEnd = radius)),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = msgs[2].mediaUrl,
                    contentDescription = "Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomEnd = radius)),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(bottomEnd = radius)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "+$remaining",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full screen image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
