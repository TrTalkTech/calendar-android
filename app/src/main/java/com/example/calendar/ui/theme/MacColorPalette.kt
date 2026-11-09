package com.example.calendar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.calendar.data.AppAccentColor

@Immutable
data class MacColorPalette(
    val windowBackground: Color,
    val contentBackground: Color,
    val separator: Color,
    val secondaryLabel: Color,
    val primaryLabel: Color,
    val todayRed: Color,
    val segmentBackground: Color,
    val gridOutsideMonth: Color,
    val selectionFill: Color,
    val accentBlue: Color,
    val yearScreenBackground: Color,
    val yearAccent: Color,
) {
    companion object {
        val Light = MacColorPalette(
            windowBackground = Color(0xFFF2F2F7),
            contentBackground = Color(0xFFFFFFFF),
            separator = Color(0xFFD1D1D6),
            secondaryLabel = Color(0xFF8E8E93),
            primaryLabel = Color(0xFF1D1D1F),
            todayRed = Color(0xFFFF3B30),
            segmentBackground = Color(0xFFE5E5EA),
            gridOutsideMonth = Color(0xFFC7C7CC),
            selectionFill = Color(0x1A007AFF),
            accentBlue = Color(0xFF007AFF),
            yearScreenBackground = Color(0xFFFFFFFF),
            yearAccent = Color(0xFFFF3B30),
        )

        val Dark = MacColorPalette(
            windowBackground = Color(0xFF121212),
            contentBackground = Color(0xFF2C2C2E),
            separator = Color(0xFF38383A),
            secondaryLabel = Color(0xFF8E8E93),
            primaryLabel = Color(0xFFF2F2F7),
            todayRed = Color(0xFFFF453A),
            segmentBackground = Color(0xFF3A3A3C),
            gridOutsideMonth = Color(0xFF636366),
            selectionFill = Color(0x330A84FF),
            accentBlue = Color(0xFF0A84FF),
            yearScreenBackground = Color(0xFF000000),
            yearAccent = Color(0xFFFF453A),
        )
    }

    fun withAccent(accent: AppAccentColor, dark: Boolean): MacColorPalette {
        val color = accent.composeColor(dark)
        return copy(todayRed = color, yearAccent = color)
    }
}

val LocalMacColorPalette = staticCompositionLocalOf { MacColorPalette.Light }

/** Палитра в духе «Календаря» macOS; значения зависят от светлой/тёмной темы. */
object MacColors {
    val windowBackground: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.windowBackground
    val contentBackground: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.contentBackground
    val separator: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.separator
    val secondaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.secondaryLabel
    val primaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.primaryLabel
    val todayRed: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.todayRed
    val segmentBackground: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.segmentBackground
    val gridOutsideMonth: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.gridOutsideMonth
    val selectionFill: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.selectionFill
    val accentBlue: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.accentBlue
}

/** Акценты режима «Год» в духе iOS. */
object IphoneYearColors {
    val yearScreenBackground: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.yearScreenBackground
    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalMacColorPalette.current.yearAccent
}
