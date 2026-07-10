package com.rustypastechat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * The app's brand mark: a note with one folded corner (paste) fused with a
 * speech-bubble tail (chat) — same silhouette as the launcher icon
 * (see app-icon-mono.svg / ic_launcher_foreground.xml), drawn as a vector
 * path here so it stays crisp and theme-tintable everywhere it's used
 * (splash screen, empty states, About page) instead of a raster asset.
 */
@Composable
fun RustyMark(
    modifier: Modifier = Modifier,
    markColor: Color = MaterialTheme.colorScheme.onSurface,
    contentColor: Color = MaterialTheme.colorScheme.surface
) {
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 108f
        val ox = (size.width - 108f * scale) / 2f
        val oy = (size.height - 108f * scale) / 2f
        fun pt(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)

        val tail = Path().apply {
            moveTo(pt(27f, 72.56f).x, pt(27f, 72.56f).y)
            lineTo(pt(27f, 85.22f).x, pt(27f, 85.22f).y)
            cubicTo(
                pt(27f, 86.17f).x, pt(27f, 86.17f).y,
                pt(28.31f, 86.08f).x, pt(28.31f, 86.08f).y,
                pt(28.81f, 86.08f).x, pt(28.81f, 86.08f).y
            )
            lineTo(pt(36.28f, 79.31f).x, pt(36.28f, 79.31f).y)
            lineTo(pt(27f, 79.31f).x, pt(27f, 79.31f).y)
            close()
        }
        drawPath(tail, markColor)

        val body = Path().apply {
            moveTo(pt(38.81f, 27f).x, pt(38.81f, 27f).y)
            lineTo(pt(67.5f, 27f).x, pt(67.5f, 27f).y)
            lineTo(pt(81f, 40.5f).x, pt(81f, 40.5f).y)
            lineTo(pt(81f, 69.19f).x, pt(81f, 69.19f).y)
            cubicTo(
                pt(81f, 75.9f).x, pt(81f, 75.9f).y,
                pt(75.56f, 81f).x, pt(75.56f, 81f).y,
                pt(69.19f, 81f).x, pt(69.19f, 81f).y
            )
            lineTo(pt(38.81f, 81f).x, pt(38.81f, 81f).y)
            cubicTo(
                pt(32.1f, 81f).x, pt(32.1f, 81f).y,
                pt(27f, 75.9f).x, pt(27f, 75.9f).y,
                pt(27f, 69.19f).x, pt(27f, 69.19f).y
            )
            lineTo(pt(27f, 38.81f).x, pt(27f, 38.81f).y)
            cubicTo(
                pt(27f, 32.1f).x, pt(27f, 32.1f).y,
                pt(32.1f, 27f).x, pt(32.1f, 27f).y,
                pt(38.81f, 27f).x, pt(38.81f, 27f).y
            )
            close()
        }
        drawPath(body, markColor)

        val foldUnder = Path().apply {
            moveTo(pt(67.5f, 27f).x, pt(67.5f, 27f).y)
            lineTo(pt(81f, 40.5f).x, pt(81f, 40.5f).y)
            lineTo(pt(67.5f, 40.5f).x, pt(67.5f, 40.5f).y)
            close()
        }
        drawPath(foldUnder, markColor.copy(alpha = markColor.alpha * 0.18f))
        drawLine(contentColor, pt(67.5f, 27f), pt(81f, 40.5f), strokeWidth = scale)

        drawRect(contentColor, topLeft = pt(35.44f, 45.56f), size = Size(30.38f * scale, 3.38f * scale))
        drawRect(contentColor.copy(alpha = 0.7f), topLeft = pt(35.44f, 53.16f), size = Size(21.94f * scale, 3.38f * scale))
        drawRect(contentColor.copy(alpha = 0.45f), topLeft = pt(35.44f, 60.75f), size = Size(15.19f * scale, 3.38f * scale))
    }
}
