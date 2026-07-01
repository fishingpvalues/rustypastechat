package com.rustypastechat.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rustypastechat.ui.animations.MelanoEasing
import com.rustypastechat.ui.theme.Blue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

@Composable
fun AnimatedTypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("AI is typing", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        repeat(3) { i ->
            val infinite = rememberInfiniteTransition(label = "dot$i")
            val offset by infinite.animateFloat(
                initialValue = 0f, targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 150, easing = MelanoEasing.Bounce),
                    repeatMode = RepeatMode.Reverse
                ), label = "bounce$i"
            )
            Spacer(Modifier
                .size(5.dp)
                .offset(y = offset.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Blue.copy(alpha = 0.6f))
            )
            if (i < 2) Spacer(Modifier.width(2.dp))
        }
    }
}

fun formatDateHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        msgDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
        msgDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "Today"
        msgDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
        msgDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

fun shouldShowDateHeader(prevTimestamp: Long?, currentTimestamp: Long): Boolean {
    if (prevTimestamp == null) return true
    val prev = Calendar.getInstance().apply { timeInMillis = prevTimestamp }
    val curr = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
    return prev.get(Calendar.DAY_OF_YEAR) != curr.get(Calendar.DAY_OF_YEAR) ||
        prev.get(Calendar.YEAR) != curr.get(Calendar.YEAR)
}
