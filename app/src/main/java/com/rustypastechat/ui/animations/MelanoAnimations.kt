package com.rustypastechat.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

object MelanoEasing {
    val StandardDecelerate = CubicBezierEasing(0.12f, 0f, 0.38f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Bounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val Smooth = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
}

@Composable
fun rememberSpringScaleAnim(initialScale: Float = 0.88f, targetScale: Float = 1f): Animatable<Float, *> {
    val animatable = remember { Animatable(initialScale) }
    LaunchedEffect(Unit) {
        animatable.animateTo(
            targetValue = targetScale,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    return animatable
}

@Composable
fun rememberFadeInAnim(delayMs: Int = 0): Animatable<Float, *> {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(450, easing = MelanoEasing.EmphasizedDecelerate)
        )
    }
    return animatable
}

@Composable
fun rememberSlideUpAnim(
    initialOffset: Float = 40f,
    targetOffset: Float = 0f,
    delayMs: Int = 0
): Animatable<Float, *> {
    val animatable = remember { Animatable(initialOffset) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        animatable.animateTo(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return animatable
}

@Composable
fun rememberFadeInScaleAnim(
    initialScale: Float = 0.92f,
    targetScale: Float = 1f,
    delayMs: Int = 0
): Animatable<Float, *> {
    val animatable = remember { Animatable(initialScale) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        animatable.animateTo(
            targetValue = targetScale,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    return animatable
}

@Composable
fun Modifier.shimmerEffect(
    baseColor: Color,
    highlightColor: Color,
    durationMs: Int = 1800
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = MelanoEasing.Smooth),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    return this.drawWithContent {
        drawContent()
        val shimmerWidth = size.width * 0.25f
        val offsetX = (shimmerProgress * (size.width + shimmerWidth)) - shimmerWidth
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0f),
                    highlightColor.copy(alpha = 0.12f),
                    baseColor.copy(alpha = 0f)
                ),
                start = Offset(offsetX, 0f),
                end = Offset(offsetX + shimmerWidth, size.height)
            ),
            size = size
        )
    }
}

@Composable
fun rememberRotationAnim(durationMs: Int = 6000): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )
    return angle
}

@Composable
fun rememberPulseAnim(
    minScale: Float = 0.97f,
    maxScale: Float = 1.03f,
    durationMs: Int = 1800
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = MelanoEasing.Smooth),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    return pulse
}
