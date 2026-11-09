package com.example.calendar.widget

import android.content.Context

class DateWidgetShapePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): LauncherIconShape {
        val raw = prefs.getString(KEY_SHAPE, LauncherIconShape.ROUNDED.name)
            ?: LauncherIconShape.ROUNDED.name
        return runCatching { LauncherIconShape.valueOf(raw) }.getOrDefault(LauncherIconShape.ROUNDED)
    }

    fun write(shape: LauncherIconShape) {
        prefs.edit().putString(KEY_SHAPE, shape.name).commit()
    }

    companion object {
        private const val PREFS_NAME = "calendar_date_widget"
        private const val KEY_SHAPE = "icon_shape"
    }
}
