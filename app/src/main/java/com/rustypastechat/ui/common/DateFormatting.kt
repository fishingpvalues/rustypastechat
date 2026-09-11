package com.rustypastechat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A date formatter bound to the locale Compose is currently rendering in.
 *
 * Calling Locale.getDefault() inside a composable reads the locale in a way
 * Compose cannot observe, so the UI keeps the formatting it was first composed
 * with after the user changes their language or region - timestamps stay in
 * the old locale until the process restarts. Lint flags it as
 * NonObservableLocale.
 *
 * Reading LocalConfiguration is what makes the read observable: a locale change
 * is a configuration change, so every caller recomposes and reformats.
 */
@Composable
fun rememberDateFormatter(pattern: String): SimpleDateFormat {
    val locales = LocalConfiguration.current.locales
    return remember(pattern, locales) { SimpleDateFormat(pattern, locales[0]) }
}

/** Convenience for the common "format this epoch millis" case. */
@Composable
fun rememberFormattedDate(pattern: String, timestamp: Long): String {
    val formatter = rememberDateFormatter(pattern)
    return remember(formatter, timestamp) { formatter.format(Date(timestamp)) }
}

/** The locale Compose is rendering in, observed rather than read globally. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
