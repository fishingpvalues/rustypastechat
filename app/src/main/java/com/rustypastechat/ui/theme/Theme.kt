package com.rustypastechat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val RustyPasteShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BlueDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Teal.copy(alpha = 0.12f),
    onSecondaryContainer = TealDark,
    tertiary = Orange,
    onTertiary = Color.White,
    tertiaryContainer = Orange.copy(alpha = 0.10f),
    onTertiaryContainer = Orange,
    error = Red,
    onError = Color.White,
    errorContainer = Red.copy(alpha = 0.10f),
    onErrorContainer = Red,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceContainerLight,
    surfaceContainerHighest = SurfaceElevatedLight,
    outline = SeparatorLight,
    outlineVariant = Color(0xFFDDDDE0),
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    inversePrimary = BlueDark,
    scrim = Color(0xCC000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.Black,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = Blue,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Teal.copy(alpha = 0.15f),
    onSecondaryContainer = TealDark,
    tertiary = Orange,
    onTertiary = Color.Black,
    tertiaryContainer = Orange.copy(alpha = 0.12f),
    onTertiaryContainer = Orange,
    error = RedMuted,
    onError = Color.Black,
    errorContainer = Red.copy(alpha = 0.15f),
    onErrorContainer = RedMuted,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = BackgroundDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Surface2Dark,
    surfaceContainerHighest = SurfaceElevatedDark,
    outline = SeparatorDark,
    outlineVariant = Color(0xFF252528),
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = BackgroundLight,
    inversePrimary = Blue,
    scrim = Color(0xCC000000)
)

@Composable
fun RustyPasteChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RustyPasteShapes,
        content = content
    )
}
