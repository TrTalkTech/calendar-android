package com.example.calendar.data

import android.content.Context

class CancelledEventsPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** По умолчанию отменённые скрыты. */
    fun readShowCancelled(): Boolean = prefs.getBoolean(KEY_SHOW_CANCELLED, false)

    fun writeShowCancelled(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CANCELLED, show).commit()
    }

    companion object {
        private const val PREFS_NAME = "calendar_cancelled_events"
        private const val KEY_SHOW_CANCELLED = "show_cancelled"
    }
}
