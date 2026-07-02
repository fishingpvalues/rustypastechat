package com.rustypastechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rustypastechat.ui.theme.Blue

object GlassShape {
    val Card = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Button = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(100.dp)
    val Small = RoundedCornerShape(10.dp)
    val Input = RoundedCornerShape(14.dp)
    val Bubble = RoundedCornerShape(16.dp)
}

@Composable
fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    shape: RoundedCornerShape = GlassShape.Card,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderAlpha: Float = 0f,
    content: @Composable ColumnScope.() -> Unit
) {
    val elevation = if (elevated) 2.dp else 0.dp
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (borderAlpha > 0f) {
            CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha)
                    )
                )
            )
        } else null,
        content = content
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassShape.Card,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
        content = content
    )
}

@Composable
fun GlowCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassShape.Card,
    glowColor: Color = Blue,
    glowRadius: Float = 0.9f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable ColumnScope.() -> Unit
) {
    val glow = glowColor.copy(alpha = 0.15f)
    val transparent = glowColor.copy(alpha = 0f)
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val r = size.minDimension * glowRadius
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow, transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            },
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content
    )
}
