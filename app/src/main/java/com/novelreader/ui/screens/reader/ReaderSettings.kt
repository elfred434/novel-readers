package com.novelreader.ui.screens.reader

import androidx.compose.ui.graphics.Color

data class ReaderSettings(
    val fontSizeSp: Int = 18,
    val lineHeightMultiplier: Float = 1.6f,
    val fontFamily: ReaderFont = ReaderFont.DEFAULT,
    val readerTheme: ReaderTheme = ReaderTheme.DARK,
    val horizontalPaddingDp: Int = 20,
    val verticalParagraphSpacingDp: Int = 8,
    val paginationMode: Boolean = false
)

enum class ReaderFont(val displayName: String) {
    DEFAULT("Par défaut"), SERIF("Serif"), SANS_SERIF("Sans-serif"), MONOSPACE("Monospace")
}

enum class ReaderTheme(val displayName: String) {
    DARK("Sombre"), LIGHT("Clair"), SEPIA("Sépia")
}

data class ReaderColors(val background: Color, val text: Color, val accent: Color) {
    companion object {
        // Couleurs alignées sur le design system Studio Noir (ui/theme/Color.kt) :
        // DARK = ReaderBackground #131313 / ReaderText #E0DED8, accent = Primary #CC3344.
        fun fromTheme(theme: ReaderTheme): ReaderColors = when (theme) {
            ReaderTheme.DARK -> ReaderColors(Color(0xFF131313), Color(0xFFE0DED8), Color(0xFFCC3344))
            ReaderTheme.LIGHT -> ReaderColors(Color(0xFFF8F8FA), Color(0xFF1A1B1E), Color(0xFFB82A3A))
            ReaderTheme.SEPIA -> ReaderColors(Color(0xFFF8F4E8), Color(0xFF3E3A35), Color(0xFF8B4513))
        }
    }
}
