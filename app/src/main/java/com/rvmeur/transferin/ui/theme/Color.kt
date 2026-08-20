package com.rvmeur.transferin.ui.theme

import androidx.compose.ui.graphics.Color

// RVM Quiet Luxury Color System (rvmeur.com)
val Ground           = Color(0xFFFAF8F3)   // Warm Off-White Canvas
val CardSurface      = Color(0xFFF6F3EB)   // Elevated Alabaster Card Surface
val SurfaceVariant   = Color(0xFFF0EBE0)   // Soft Beige Outer Shell Surface
val TextPrimary      = Color(0xFF26170C)   // Deep Espresso Primary Typography
val TextMuted        = Color(0xFF4F453F)   // Medium Espresso Secondary Copy
val TextMutedLight   = Color(0xFF6E625B)   // Darkened Taupe Monospace Captions (WCAG AAA 5.8:1)
val Border           = Color(0xFFD2C4BC)   // Primary Muted Taupe Border
val LightBorder      = Color(0xFFE8E0D8)   // Hairline Divider
val AccentGreen      = Color(0xFF2D4636)   // Forest Emerald Primary Accent
val AccentOlive      = Color(0xFF56642B)   // Warm Olive Active Status
val AccentBurgundy   = Color(0xFF7A1A1A)   // Burgundy Pulse Alert Accent
val AccentGreenSubtle= Color(0x142D4636)   // 8% Emerald Background Wash

// Compatibility Aliases
val CreamBackground  = Ground
val TextSecondary    = TextMuted
val TextTertiary     = TextMutedLight
val BorderSoft       = LightBorder
val BorderMedium     = Border
val AccentGold       = AccentGreen
val AccentGoldDark   = Color(0xFF1E3024)
val AccentGoldLight  = Color(0xFF436951)

// Status & Utility Colors
val SuccessGreen     = Color(0xFF4CAF50)
val SuccessGreenSoft = Color(0xFFE8F5E9)
val WarningOrange    = Color(0xFFFF9800)
val WarningOrangeSoft= Color(0xFFFFF3E0)
val ErrorRedSoft     = Color(0xFFE53935)
val ErrorRedSoftBg   = Color(0xFFFFEBEE)

// Culling & Modal Tokens
val BadgeWarmAmber      = Color(0xFFB8860B)   // Flash misfire / underexposure alert
val BadgeDeepBurgundy   = Color(0xFF8B0000)   // Blown highlight / overexposure alert
val AccentAmber         = BadgeWarmAmber
val ModalDarkBackground = Color(0xEE12100E)   // Inspection modal dark canvas
val ModalScrim          = ModalDarkBackground

// Overlays
val OverlayDark      = Color(0x99000000)
val OverlayLight     = Color(0x4DFFFFFF)