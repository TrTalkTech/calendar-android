package com.example.calendar.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.calendar.R

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AppAccentColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE,
    ;

    fun composeColor(dark: Boolean): Color = when (this) {
        RED -> if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
        ORANGE -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
        YELLOW -> if (dark) Color(0xFFFFD60A) else Color(0xFFFFCC00)
        GREEN -> if (dark) Color(0xFF30D158) else Color(0xFF34C759)
        BLUE -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
        PURPLE -> if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    }

    fun argb(): Int = when (this) {
        RED -> 0xFFFF3B30.toInt()
        ORANGE -> 0xFFFF9500.toInt()
        YELLOW -> 0xFFFFCC00.toInt()
        GREEN -> 0xFF34C759.toInt()
        BLUE -> 0xFF007AFF.toInt()
        PURPLE -> 0xFFAF52DE.toInt()
    }

    @StringRes
    fun labelRes(): Int = when (this) {
        RED -> R.string.app_settings_accent_red
        ORANGE -> R.string.app_settings_accent_orange
        YELLOW -> R.string.app_settings_accent_yellow
        GREEN -> R.string.app_settings_accent_green
        BLUE -> R.string.app_settings_accent_blue
        PURPLE -> R.string.app_settings_accent_purple
    }

    companion object {
        fun fromPersisted(raw: String?): AppAccentColor =
            entries.firstOrNull { it.name == raw } ?: RED
    }
}

class AppThemePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readMode(): AppThemeMode {
        val raw = prefs.getString(KEY_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    fun writeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).commit()
    }

    fun readAccent(): AppAccentColor =
        AppAccentColor.fromPersisted(prefs.getString(KEY_ACCENT, AppAccentColor.RED.name))

    fun writeAccent(accent: AppAccentColor) {
        prefs.edit().putString(KEY_ACCENT, accent.name).commit()
    }

    companion object {
        private const val PREFS_NAME = "calendar_app_theme"
        private const val KEY_MODE = "theme_mode"
        private const val KEY_ACCENT = "accent_color"
    }
}
