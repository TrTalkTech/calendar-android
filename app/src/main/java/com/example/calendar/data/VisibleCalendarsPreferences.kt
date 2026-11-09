package com.example.calendar.data

import android.content.Context

/**
 * Сохраняет, какие календари показывать в списке событий.
 * [null] — без фильтра (все календари). Непустой набор — только выбранные id.
 * Пустой набор — пользователь явно снял все флаги (событий не будет).
 */
class VisibleCalendarsPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readFilter(): Set<Long>? {
        if (!prefs.contains(KEY_IDS)) return null
        val raw = prefs.getString(KEY_IDS, null) ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    /**
     * @param selectedIds выбранные календари
     * @param allIdsOnDevice все id, которые сейчас вернул провайдер (для сравнения «выбраны все»)
     */
    fun writeFilter(selectedIds: Set<Long>, allIdsOnDevice: Set<Long>) {
        val editor = prefs.edit()
        when {
            allIdsOnDevice.isEmpty() -> editor.remove(KEY_IDS)
            selectedIds.isEmpty() -> editor.putString(KEY_IDS, "")
            selectedIds == allIdsOnDevice -> editor.remove(KEY_IDS)
            else -> editor.putString(KEY_IDS, selectedIds.joinToString(","))
        }
        editor.commit()
    }

    /**
     * Удаляет из фильтра id календарей, которых уже нет на устройстве.
     * Пустой набор (все галочки сняты) не сбрасывается.
     */
    fun repairInvalidFilter(availableCalendarIds: Set<Long>) {
        if (availableCalendarIds.isEmpty()) {
            if (prefs.contains(KEY_IDS)) {
                prefs.edit().remove(KEY_IDS).commit()
            }
            return
        }
        if (!prefs.contains(KEY_IDS)) return
        val filter = readFilter() ?: return
        if (filter.isEmpty()) return
        val valid = filter.intersect(availableCalendarIds)
        when {
            valid == filter -> Unit
            valid.isEmpty() -> prefs.edit().remove(KEY_IDS).commit()
            else -> prefs.edit().putString(KEY_IDS, valid.joinToString(",")).commit()
        }
    }

    companion object {
        private const val PREFS_NAME = "calendar_visible_calendars"
        private const val KEY_IDS = "visible_calendar_ids"
    }
}
