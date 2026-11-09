package com.example.calendar.data

import android.content.Context

class ProductionCalendarPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun writeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    companion object {
        private const val PREFS_NAME = "calendar_production"
        private const val KEY_ENABLED = "enabled"
    }
}
