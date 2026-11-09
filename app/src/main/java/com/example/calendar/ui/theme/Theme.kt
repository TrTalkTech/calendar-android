package com.example.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.calendar.data.AppAccentColor
import com.example.calendar.data.AppThemeMode

@Composable
fun CalendarTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentColor: AppAccentColor = AppAccentColor.RED,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val palette = (if (darkTheme) MacColorPalette.Dark else MacColorPalette.Light)
        .withAccent(accentColor, darkTheme)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1A2A44),
            onPrimaryContainer = palette.primaryLabel,
            secondary = palette.secondaryLabel,
            onSecondary = Color.White,
            tertiary = palette.todayRed,
            background = palette.windowBackground,
            onBackground = palette.primaryLabel,
            surface = palette.contentBackground,
            onSurface = palette.primaryLabel,
            surfaceVariant = palette.segmentBackground,
            onSurfaceVariant = palette.secondaryLabel,
            outline = palette.separator,
        )
    } else {
        lightColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE8F1FF),
            onPrimaryContainer = palette.primaryLabel,
            secondary = palette.secondaryLabel,
            onSecondary = Color.White,
            tertiary = palette.todayRed,
            background = palette.windowBackground,
            onBackground = palette.primaryLabel,
            surface = palette.contentBackground,
            onSurface = palette.primaryLabel,
            surfaceVariant = palette.segmentBackground,
            onSurfaceVariant = palette.secondaryLabel,
            outline = palette.separator,
        )
    }

    CompositionLocalProvider(LocalMacColorPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MacTypography,
            content = content,
        )
    }
}
