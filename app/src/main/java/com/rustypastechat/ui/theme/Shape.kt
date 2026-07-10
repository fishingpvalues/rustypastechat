package com.rustypastechat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single source of truth for corner radii — both the M3 role-based [Shapes] and the
 *  named component tokens (formerly duplicated as a separate `GlassShape` object). */
val RustyPasteShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object RustyShape {
    val Card = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Button = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(100.dp)
    val Small = RoundedCornerShape(10.dp)
    val Input = RoundedCornerShape(14.dp)
    val Bubble = RoundedCornerShape(16.dp)
}

/** Single spacing scale — replaces ad hoc `8.dp`/`12.dp`/`16.dp` literals scattered per-screen. */
object RustySpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
