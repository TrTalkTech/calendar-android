package com.example.calendar.data

import android.content.Context

enum class AttachmentOpenMode {
    IN_APP,
    EXTERNAL,
}

class AttachmentOpenPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AttachmentOpenMode {
        val raw = prefs.getString(KEY_MODE, AttachmentOpenMode.IN_APP.name)
            ?: AttachmentOpenMode.IN_APP.name
        return runCatching { AttachmentOpenMode.valueOf(raw) }.getOrDefault(AttachmentOpenMode.IN_APP)
    }

    fun write(mode: AttachmentOpenMode) {
        prefs.edit().putString(KEY_MODE, mode.name).commit()
    }

    companion object {
        private const val PREFS_NAME = "calendar_attachments"
        private const val KEY_MODE = "open_mode"
    }
}
