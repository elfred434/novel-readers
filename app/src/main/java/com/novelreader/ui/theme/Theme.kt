package com.novelreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary, onPrimary = OnPrimary, primaryContainer = PrimaryVariant,
    secondary = Secondary, tertiary = Tertiary,
    background = Surface, surface = Surface, surfaceVariant = SurfaceVariant,
    onBackground = OnSurface, onSurface = OnSurface, onSurfaceVariant = OnSurfaceVariant,
    outline = Outline, error = Error
)

private val LightColorScheme = lightColorScheme(
    primary = Primary, onPrimary = OnPrimary, primaryContainer = PrimaryVariant,
    secondary = Secondary, tertiary = Tertiary,
    background = LightSurface, surface = LightSurface, surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnSurface, onSurface = LightOnSurface, onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline, error = Error
)

private val AmoledColorScheme = darkColorScheme(
    primary = Primary, onPrimary = OnPrimary, primaryContainer = PrimaryVariant,
    secondary = Secondary, tertiary = Tertiary,
    background = AmoledBackground, surface = AmoledSurface, surfaceVariant = AmoledSurfaceVariant,
    onBackground = AmoledOnSurface, onSurface = AmoledOnSurface, onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = Color(0xFF1E1E20),
    error = Error
)

@Composable
fun NovelReaderTheme(
    themeType: AppTheme = AppTheme.DARK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDark = when (themeType) {
        AppTheme.SYSTEM -> darkTheme
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.LIGHT -> false
    }

    val colorScheme = when (themeType) {
        AppTheme.AMOLED -> AmoledColorScheme
        AppTheme.LIGHT -> LightColorScheme
        else -> if (isDark) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = NovelReaderTypography, content = content)
}
