package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════
// PALETTE PRINCIPALE — Luxe sombre & accents vifs
// ═══════════════════════════════════════════════

// Neutres profonds
val SurfaceDark = Color(0xFF0A0A0F)
val SurfaceDarkElevated = Color(0xFF12121A)
val SurfaceDarkCard = Color(0xFF1A1A24)
val SurfaceDarkCardHover = Color(0xFF22222E)
val OutlineDark = Color(0xFF2A2A3C)
val OutlineDarkVariant = Color(0xFF3A3A4E)
val OnSurfaceDark = Color(0xFFECECF1)
val OnSurfaceDarkSecondary = Color(0xFF9E9EB8)
val OnSurfaceDarkTertiary = Color(0xFF6E6E8A)

// Neutres clairs
val SurfaceLight = Color(0xFFF5F5FA)
val SurfaceLightElevated = Color(0xFFFFFFFF)
val SurfaceLightCard = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFFE0E0EC)
val OnSurfaceLight = Color(0xFF0A0A0F)
val OnSurfaceLightSecondary = Color(0xFF5E5E78)
val OnSurfaceLightTertiary = Color(0xFF8E8EA8)

// Accent principal — Rouge rubis intense
val Primary = Color(0xFFE11D48)
val PrimaryVariant = Color(0xFFBE123C)
val PrimaryContainer = Color(0xFF4A0D2B)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryGradientStart = Color(0xFFE11D48)
val PrimaryGradientEnd = Color(0xFF9F1239)

// Accent secondaire — Or ambré
val Secondary = Color(0xFFF59E0B)
val SecondaryVariant = Color(0xFFD97706)
val SecondaryContainer = Color(0xFF451A03)
val OnSecondary = Color(0xFFFFFFFF)

// Accent tertiaire — Violet royal
val Tertiary = Color(0xFF8B5CF6)
val TertiaryVariant = Color(0xFF7C3AED)
val TertiaryContainer = Color(0xFF2E1065)

// Sémantique
val Error = Color(0xFFEF4444)
val ErrorContainer = Color(0xFF450A0A)
val Success = Color(0xFF22C55E)
val SuccessContainer = Color(0xFF052E16)
val Warning = Color(0xFFF59E0B)
val Info = Color(0xFF3B82F6)

// Autres
val RatingGold = Color(0xFFFBBF24)
val StatusOngoing = Color(0xFF22C55E)
val StatusCompleted = Color(0xFF3B82F6)
val StatusHiatus = Color(0xFFF59E0B)
val StatusCancelled = Color(0xFFEF4444)

// Verre / Glassmorphism
val GlassWhite = Color(0x14FFFFFF)
val GlassWhiteStrong = Color(0x1AFFFFFF)
val GlassWhiteBorder = Color(0x0DFFFFFF)
val GlassDark = Color(0x14000000)
val GlassDarkBorder = Color(0x0A000000)

// Overlay
val Scrim = Color(0x99000000)
val ScrimLight = Color(0x66000000)

// ═══════════════════════════════════════════════
// PALETTE AMOLED (noir pur)
// ═══════════════════════════════════════════════

val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF050508)
val AmoledSurfaceVariant = Color(0xFF0D0D14)
val AmoledOnSurface = Color(0xFFE8E8EA)
val AmoledOnSurfaceVariant = Color(0xFF88889C)
val AmoledOutline = Color(0xFF1A1A24)

// ═══════════════════════════════════════════════
// THÈMES DISPONIBLES
// ═══════════════════════════════════════════════

enum class AppTheme(val displayName: String, val isDark: Boolean) {
    SYSTEM("Système", true),
    DARK("Sombre", true),
    LIGHT("Clair", false),
    AMOLED("AMOLED", true)
}
