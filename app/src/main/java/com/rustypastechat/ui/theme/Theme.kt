package com.rustypastechat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RustyColors.Rust,
    onPrimary = Color.White,
    primaryContainer = RustyColors.RustContainer,
    onPrimaryContainer = RustyColors.RustDark,
    secondary = RustyColors.Patina,
    onSecondary = Color.White,
    secondaryContainer = RustyColors.Patina.copy(alpha = 0.12f),
    onSecondaryContainer = RustyColors.Patina,
    tertiary = RustyColors.Warning,
    onTertiary = Color.White,
    tertiaryContainer = RustyColors.Warning.copy(alpha = 0.10f),
    onTertiaryContainer = RustyColors.Warning,
    error = RustyColors.Error,
    onError = Color.White,
    errorContainer = RustyColors.Error.copy(alpha = 0.10f),
    onErrorContainer = RustyColors.Error,
    background = RustyColors.BackgroundLight,
    onBackground = RustyColors.TextPrimaryLight,
    surface = RustyColors.SurfaceLight,
    onSurface = RustyColors.TextPrimaryLight,
    surfaceVariant = RustyColors.SurfaceElevatedLight,
    onSurfaceVariant = RustyColors.TextSecondaryLight,
    surfaceContainerLow = RustyColors.BackgroundLight,
    surfaceContainer = RustyColors.SurfaceLight,
    surfaceContainerHigh = RustyColors.SurfaceContainerLight,
    surfaceContainerHighest = RustyColors.SurfaceElevatedLight,
    outline = RustyColors.SeparatorLight,
    outlineVariant = Color(0xFFE8DCD0),
    inverseSurface = RustyColors.TextPrimaryDark,
    inverseOnSurface = RustyColors.BackgroundDark,
    inversePrimary = RustyColors.RustLight,
    scrim = Color(0xCC000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = RustyColors.RustLight,
    onPrimary = Color.Black,
    primaryContainer = RustyColors.RustContainerDark,
    onPrimaryContainer = RustyColors.RustLight,
    secondary = RustyColors.PatinaDark,
    onSecondary = Color.Black,
    secondaryContainer = RustyColors.Patina.copy(alpha = 0.20f),
    onSecondaryContainer = RustyColors.PatinaDark,
    tertiary = RustyColors.Warning,
    onTertiary = Color.Black,
    tertiaryContainer = RustyColors.Warning.copy(alpha = 0.15f),
    onTertiaryContainer = RustyColors.Warning,
    error = RustyColors.ErrorMuted,
    onError = Color.Black,
    errorContainer = RustyColors.Error.copy(alpha = 0.20f),
    onErrorContainer = RustyColors.ErrorMuted,
    background = RustyColors.BackgroundDark,
    onBackground = RustyColors.TextPrimaryDark,
    surface = RustyColors.SurfaceDark,
    onSurface = RustyColors.TextPrimaryDark,
    surfaceVariant = RustyColors.Surface2Dark,
    onSurfaceVariant = RustyColors.TextSecondaryDark,
    surfaceContainerLow = RustyColors.BackgroundDark,
    surfaceContainer = RustyColors.SurfaceDark,
    surfaceContainerHigh = RustyColors.Surface2Dark,
    surfaceContainerHighest = RustyColors.SurfaceElevatedDark,
    outline = RustyColors.SeparatorDark,
    outlineVariant = Color(0xFF3A2E26),
    inverseSurface = RustyColors.TextPrimaryLight,
    inverseOnSurface = RustyColors.BackgroundLight,
    inversePrimary = RustyColors.Rust,
    scrim = Color(0xCC000000)
)

@Composable
fun RustyPasteChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: Material You derives colors from the user's wallpaper, which would
    // override the app's signature rust/copper identity (sent-bubble color, links, accents).
    dynamicColor: Boolean = false,
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
