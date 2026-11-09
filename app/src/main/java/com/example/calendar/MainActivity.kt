package com.example.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.calendar.data.AppThemePreferences
import com.example.calendar.ui.CalendarHomeScreen
import com.example.calendar.ui.theme.CalendarTheme
import com.example.calendar.widget.CalendarWidgets

class MainActivity : ComponentActivity() {

    private val themePreferences by lazy { AppThemePreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(themePreferences.readMode()) }
            var accentColor by remember { mutableStateOf(themePreferences.readAccent()) }
            CalendarTheme(themeMode = themeMode, accentColor = accentColor) {
                CalendarHomeScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themePreferences.writeMode(mode)
                        themeMode = mode
                    },
                    accentColor = accentColor,
                    onAccentColorChange = { accent ->
                        themePreferences.writeAccent(accent)
                        accentColor = accent
                        CalendarWidgets.refreshAll(this)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CalendarWidgets.refreshAll(this)
    }
}
