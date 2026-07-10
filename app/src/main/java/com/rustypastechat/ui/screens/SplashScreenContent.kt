package com.rustypastechat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rustypastechat.ui.animations.rememberFadeInAnim
import com.rustypastechat.ui.animations.rememberFadeInScaleAnim
import com.rustypastechat.ui.animations.rememberPulseAnim
import com.rustypastechat.ui.animations.rememberRotationAnim
import com.rustypastechat.ui.components.RustyMark

/**
 * Brief branded splash shown right after the system SplashScreen API's static icon,
 * so cold start gets one real animated moment instead of a hard cut to content:
 * the mark bounces in, a dashed ring slowly orbits it, and the title fades in last.
 */
@Composable
fun RustySplashContent() {
    val iconAnim = rememberFadeInScaleAnim(initialScale = 0.6f, targetScale = 1f)
    val pulse = rememberPulseAnim(minScale = 0.98f, maxScale = 1.04f, durationMs = 1400)
    val ringRotation = rememberRotationAnim(durationMs = 7000)
    val textAnim = rememberFadeInAnim(delayMs = 250)
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val markColor = MaterialTheme.colorScheme.primary
    val markContentColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                // Slowly orbiting dashed halo — the one ambient "wow" flourish, restrained
                // to a single ring so it doesn't compete with the mark itself.
                Canvas(
                    modifier = Modifier
                        .size(132.dp)
                        .graphicsLayer { alpha = iconAnim.value }
                ) {
                    rotate(ringRotation) {
                        drawCircle(
                            color = ringColor,
                            radius = size.minDimension / 2,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
                            )
                        )
                    }
                }
                RustyMark(
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            scaleX = iconAnim.value * pulse
                            scaleY = iconAnim.value * pulse
                            alpha = iconAnim.value
                        },
                    markColor = markColor,
                    contentColor = markContentColor
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "RustyPaste Chat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAnim.value }
            )
        }
    }
}
