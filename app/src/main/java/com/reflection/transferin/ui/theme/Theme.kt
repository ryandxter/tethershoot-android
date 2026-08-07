package com.reflection.transferin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    // Primary - Gold accent
    primary = AccentGold,
    onPrimary = Color.White,
    primaryContainer = AccentGoldLight,
    onPrimaryContainer = AccentGoldDark,

    // Secondary - Same gold family for consistency
    secondary = AccentGold,
    onSecondary = Color.White,
    secondaryContainer = AccentGoldLight,
    onSecondaryContainer = AccentGoldDark,

    // Tertiary
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = SuccessGreenSoft,
    onTertiaryContainer = SuccessGreen,

    // Background & Surface
    background = CreamBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,

    // Error
    error = ErrorRedSoft,
    onError = Color.White,
    errorContainer = ErrorRedSoftBg,
    onErrorContainer = ErrorRedSoft,

    // Outline & Borders
    outline = BorderSoft,
    outlineVariant = BorderMedium,

    // Inverse colors
    inverseSurface = TextPrimary,
    inverseOnSurface = CardSurface,
    inversePrimary = AccentGoldLight
)

@Composable
fun TransferInTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}