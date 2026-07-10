package com.novelreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    secondaryContainer = Secondary,
    tertiary = Tertiary,
    background = SurfaceDark,
    surface = SurfaceDarkElevated,
    surfaceVariant = SurfaceDarkCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = OutlineStandard,
    outlineVariant = OutlineSubtle,
    error = Error,
    errorContainer = ErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimary,
    primaryContainer = SurfaceLightCard,
    secondary = Secondary,
    tertiary = Tertiary,
    background = SurfaceLight,
    surface = SurfaceLightElevated,
    surfaceVariant = SurfaceLightCard,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = Error
)

private val AmoledColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    tertiary = Tertiary,
    background = SurfaceDeep,
    surface = SurfaceDeep,
    surfaceVariant = SurfaceDarkElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
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
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovelReaderTypography,
        shapes = NovelReaderShapes,
        content = content
    )
}
