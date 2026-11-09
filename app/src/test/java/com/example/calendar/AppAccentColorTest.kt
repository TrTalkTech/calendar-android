package com.example.calendar

import androidx.compose.ui.graphics.Color
import com.example.calendar.data.AppAccentColor
import com.example.calendar.ui.theme.MacColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class AppAccentColorTest {

    @Test
    fun fromPersisted_unknownFallsBackToRed() {
        assertEquals(AppAccentColor.RED, AppAccentColor.fromPersisted(null))
        assertEquals(AppAccentColor.RED, AppAccentColor.fromPersisted(""))
        assertEquals(AppAccentColor.RED, AppAccentColor.fromPersisted("magenta"))
        assertEquals(AppAccentColor.BLUE, AppAccentColor.fromPersisted("BLUE"))
    }

    @Test
    fun withAccent_replacesTodayAndYearOnly() {
        val blue = MacColorPalette.Light.withAccent(AppAccentColor.BLUE, dark = false)
        assertEquals(Color(0xFF007AFF), blue.todayRed)
        assertEquals(Color(0xFF007AFF), blue.yearAccent)
        assertEquals(MacColorPalette.Light.accentBlue, blue.accentBlue)
        assertEquals(MacColorPalette.Light.windowBackground, blue.windowBackground)

        val darkGreen = MacColorPalette.Dark.withAccent(AppAccentColor.GREEN, dark = true)
        assertEquals(Color(0xFF30D158), darkGreen.todayRed)
        assertEquals(Color(0xFF30D158), darkGreen.yearAccent)
        assertEquals(MacColorPalette.Dark.accentBlue, darkGreen.accentBlue)
    }
}
