package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color

// ======================== THÈME SOMBRE ========================
val Surface = Color(0xFF0E0E10)
val SurfaceVariant = Color(0xFF1A1B1E)
val SurfaceContainer = Color(0xFF242529)
val OnSurface = Color(0xFFE8E8EA)
val OnSurfaceVariant = Color(0xFFA1A1A6)
val Outline = Color(0xFF3C3C42)

val Primary = Color(0xFFE11D48)
val PrimaryVariant = Color(0xFFBE123C)
val OnPrimary = Color(0xFFFFFFFF)

val Secondary = Color(0xFF60A5FA)
val SecondaryVariant = Color(0xFF3B82F6)
val Tertiary = Color(0xFFA78BFA)
val Error = Color(0xFFFB7185)

val RatingGold = Color(0xFFFBBF24)
val StatusOngoing = Color(0xFF34D399)
val StatusCompleted = Color(0xFF60A5FA)

// ======================== THÈME CLAIR ========================
val LightSurface = Color(0xFFF8F8FA)
val LightSurfaceVariant = Color(0xFFEEEEF0)
val LightSurfaceContainer = Color(0xFFE4E4E6)
val LightOnSurface = Color(0xFF1A1B1E)
val LightOnSurfaceVariant = Color(0xFF6B6B70)
val LightOutline = Color(0xFFC8C8CC)

// ======================== THÈME AMOLED (noir pur) ========================
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0B)
val AmoledSurfaceVariant = Color(0xFF121214)
val AmoledOnSurface = Color(0xFFE8E8EA)
val AmoledOnSurfaceVariant = Color(0xFF88888C)

/**
 * Types de thèmes supportés par l'application.
 * L'utilisateur peut choisir entre suivre le système, forcer sombre/clair,
 * ou utiliser le noir AMOLED (économise la batterie sur écrans OLED).
 */
enum class AppTheme(val displayName: String, val isDark: Boolean) {
    SYSTEM("Système", true),     // Suit les préférences système
    DARK("Sombre", true),        // Force sombre
    LIGHT("Clair", false),       // Force clair
    AMOLED("AMOLED", true)       // Noir pur (#000000)
}
