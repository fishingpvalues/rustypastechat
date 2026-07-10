package com.rustypastechat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Named semantic palette for RustyPasteChat, built around a signature
 * "rust/copper" accent (a nod to the app name and its rustypaste backend)
 * instead of a generic Material blue. Dark surfaces use a warm-tinted
 * near-black hierarchy rather than pure neutral gray.
 */
object RustyColors {
    // Signature accent: rust/copper. Used for outgoing bubbles, primary actions, links.
    val Rust = Color(0xFFB5651D)
    val RustLight = Color(0xFFE0A570)
    val RustDark = Color(0xFF7A3D12)
    val RustContainer = Color(0xFFFFDCC2)
    val RustContainerDark = Color(0xFF5A2E0C)
    val RustGlow = Color(0x40D9884A)

    // Secondary: patina teal (oxidized-copper association, distinct from Rust).
    val Patina = Color(0xFF3E8074)
    val PatinaDark = Color(0xFF6FBBAC)

    // Semantic status colors (kept distinct from the Rust accent so bubbles
    // and status indicators never carry ambiguous meaning).
    val Success = Color(0xFF2E8B57)
    val SuccessMuted = Color(0xFF81C784)
    val Warning = Color(0xFFCC8B00)
    val Error = Color(0xFFD32F2F)
    val ErrorMuted = Color(0xFFEF9A9A)

    // Text
    val TextPrimaryLight = Color(0xFF231A14)
    val TextSecondaryLight = Color(0xFF6B5C4F)
    val TextPrimaryDark = Color(0xFFEDE6DF)
    val TextSecondaryDark = Color(0xFFB3A499)

    // Surfaces — warm-tinted hierarchy, true OLED black at the base in dark mode.
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceElevatedLight = Color(0xFFF7F1EA)
    val SurfaceContainerLight = Color(0xFFF1E9E0)
    val BackgroundLight = Color(0xFFFFFBF7)
    val SeparatorLight = Color(0xFFE0D4C8)

    val SurfaceDark = Color(0xFF0E0B09)
    val Surface2Dark = Color(0xFF17130F)
    val SurfaceElevatedDark = Color(0xFF201A15)
    val BackgroundDark = Color(0xFF000000)
    val SeparatorDark = Color(0xFF302620)

    // Chat bubbles
    val BubbleOutgoingLight = Rust
    val BubbleOutgoingTextLight = Color(0xFFFFFFFF)
    val BubbleIncomingLight = Color(0xFFEFE6DC)
    val BubbleIncomingTextLight = TextPrimaryLight

    val BubbleOutgoingDark = RustDark
    val BubbleOutgoingTextDark = Color(0xFFFFFFFF)
    val BubbleIncomingDark = Color(0xFF2A2320)
    val BubbleIncomingTextDark = TextPrimaryDark

    // Imported-paste banner
    val ImportBgLight = Color(0xFFF5EDE3)
    val ImportBgDark = Color(0xFF231C16)
    val ImportTextLight = TextSecondaryLight
    val ImportTextDark = TextSecondaryDark

    // Links — rust-toned rather than generic blue, in both themes.
    val LinkLight = RustDark
    val LinkDark = RustLight

    // Glass/border tint for GlassCard/GlowCard
    val GlassBorderLight = Color(0x1A231A14)
    val GlassBorderDark = Color(0x33EDE6DF)
}
