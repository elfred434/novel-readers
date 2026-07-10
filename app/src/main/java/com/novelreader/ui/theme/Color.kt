package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// STUDIO NOIR — Palette design system v1.0
// Usage : une seule couleur d'accent (Primary), utilisée avec parcimonie.
// Le reste de la hiérarchie repose sur 5 niveaux de gris.
// ═══════════════════════════════════════════════════════════════

// ── Surfaces (dark) ──────────────────────────────────────────
val SurfaceDeep = Color(0xFF000000)        // Fond racine — noir absolu
val SurfaceDark = Color(0xFF050505)         // Arrière-plan écrans
val SurfaceDarkElevated = Color(0xFF0D0D0D) // Navigation, TopBar
val SurfaceDarkCard = Color(0xFF141414)     // Cartes, sections
val SurfaceDarkCardHover = Color(0xFF1C1C1C)
val SurfaceDarkSheet = Color(0xFF1A1A1A)   // Bottom sheets, dialogues

// ── Textes (dark) ────────────────────────────────────────────
val TextPrimary = Color(0xFFE8E8E8)        // Corps, titres importants
val TextSecondary = Color(0xFF999999)       // Auteurs, métadonnées
val TextTertiary = Color(0xFF666666)       // Timestamps, notes, labels discrets
val TextDisabled = Color(0xFF3A3A3A)

// ── Liserés / Outlines (dark) ────────────────────────────────
val OutlineStandard = Color(0xFF2A2A2A)
val OutlineSubtle = Color(0xFF1E1E1E)

// ── Accent unique — Rubis atténué ────────────────────────────
// Usage strict : un seul élément par écran, UNIQuement les CTAs principaux.
val Primary = Color(0xFFCC3344)
val PrimaryVariant = Color(0xFFB82A3A)
val PrimaryContainer = Color(0xFF2E0A0E)    // Fond des boutons primaires
val OnPrimary = Color(0xFFFFFFFF)

// ── Accents secondaires (usage très limité) ──────────────────
val Secondary = Color(0xFF6B6B6B)           // Pour éléments non-interactifs mis en avant
val SecondaryVariant = Color(0xFF555555)

// ── Accent tertiaire (presque jamais utilisé) ────────────────
val Tertiary = Color(0xFF4A6FA5)            // Bleu-gris discret

// ── Couleurs sémantiques ─────────────────────────────────────
val Error = Color(0xFFE04848)
val ErrorContainer = Color(0xFF2E0A0A)
val Success = Color(0xFF3CB371)            // Vert doux
val SuccessContainer = Color(0xFF0A2E14)
val Warning = Color(0xFFD4A853)            // Ambre doux
val RatingGold = Color(0xFFD4A853)

// ── Statuts ──────────────────────────────────────────────────
val StatusOngoing = Success
val StatusCompleted = Color(0xFF4A6FA5)    // Bleu-gris discret
val StatusHiatus = Warning
val StatusCancelled = Error

// ── Fond dédié lecteur ───────────────────────────────────────
// #121212 avec 2% de teinte rouge pour la chaleur — scientifique
val ReaderBackground = Color(0xFF131313)
val ReaderText = Color(0xFFE0DED8)
val ReaderSepia = Color(0xFFF5ECD7)
val ReaderSepiaBg = Color(0xFFF8F4E8)

// ── Surfaces (light) ─────────────────────────────────────────
val SurfaceLight = Color(0xFFF5F5F0)
val SurfaceLightElevated = Color(0xFFFFFFFF)
val SurfaceLightCard = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF6B6B6B)
val TextTertiaryLight = Color(0xFF999999)
val OutlineLight = Color(0xFFE0E0D8)
val PrimaryLight = Color(0xFFB82A3A)

// ── Overlay ──────────────────────────────────────────────────
val Scrim = Color(0xCC000000)
val ScrimLight = Color(0x80000000)

// ═══════════════════════════════════════════════════════════════
// THÈMES
// ═══════════════════════════════════════════════════════════════

enum class AppTheme(val displayName: String, val isDark: Boolean) {
    SYSTEM("Système", true),
    DARK("Sombre", true),
    LIGHT("Clair", false),
    AMOLED("AMOLED", true)
}
