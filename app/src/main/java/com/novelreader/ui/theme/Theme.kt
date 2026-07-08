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
import androidx.core.view.WindowInsetsControllerCompat

private val DarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    tertiary = Tertiary,
    tertiaryContainer = TertiaryContainer,
    error = Error,
    errorContainer = ErrorContainer,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDarkElevated,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDarkCard,
    onSurfaceVariant = OnSurfaceDarkSecondary,
    outline = OutlineDark,
    outlineVariant = OutlineDarkVariant
)

private val LightScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFFFFE5E5),
    secondary = SecondaryVariant,
    onSecondary = OnSecondary,
    tertiary = TertiaryVariant,
    error = Error,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLightElevated,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceLightCard,
    onSurfaceVariant = OnSurfaceLightSecondary,
    outline = OutlineLight
)

private val AmoledScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    tertiary = Tertiary,
    error = Error,
    background = AmoledBackground,
    onBackground = AmoledOnSurface,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = AmoledOutline
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
        AppTheme.AMOLED -> AmoledScheme
        AppTheme.LIGHT -> LightScheme
        else -> if (isDark) DarkScheme else LightScheme
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
