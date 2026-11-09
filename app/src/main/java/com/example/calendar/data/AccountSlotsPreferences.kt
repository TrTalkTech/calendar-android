package com.example.calendar.data

import android.content.Context

class AccountSlotsPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isInitialized(): Boolean = prefs.getBoolean(KEY_READY, false)

    fun read(): AccountSlotsState {
        val slots = (0 until ACCOUNT_SLOT_COUNT).map { index ->
            AccountSlot(
                eventCalendarIds = parseIds(prefs.getString(keyEvents(index), null)),
                defaultEventCalendarId = prefs.getLong(keyDefault(index), -1L).takeIf { it > 0 },
                taskCalendarId = prefs.getLong(keyTask(index), -1L).takeIf { it > 0 },
            )
        }
        val active = prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, ACCOUNT_SLOT_COUNT - 1)
        return AccountSlotsState(slots, active)
    }

    fun write(state: AccountSlotsState) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_READY, true)
        editor.putInt(KEY_ACTIVE, state.activeIndex.coerceIn(0, ACCOUNT_SLOT_COUNT - 1))
        repeat(ACCOUNT_SLOT_COUNT) { index ->
            val slot = state.slot(index)
            editor.putString(keyEvents(index), slot.eventCalendarIds.joinToString(","))
            if (slot.defaultEventCalendarId != null) {
                editor.putLong(keyDefault(index), slot.defaultEventCalendarId)
            } else {
                editor.remove(keyDefault(index))
            }
            if (slot.taskCalendarId != null) {
                editor.putLong(keyTask(index), slot.taskCalendarId)
            } else {
                editor.remove(keyTask(index))
            }
        }
        editor.commit()
    }

    fun readOrSeed(calendars: List<WritableCalendar>): AccountSlotsState {
        if (calendars.isEmpty()) return AccountSlots.normalize(read(), calendars)
        if (!isInitialized()) {
            val seeded = AccountSlots.seed(calendars)
            write(seeded)
            return seeded
        }
        val normalized = AccountSlots.normalize(read(), calendars)
        write(normalized)
        return normalized
    }

    private fun parseIds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    companion object {
        private const val PREFS_NAME = "calendar_account_slots"
        private const val KEY_READY = "ready"
        private const val KEY_ACTIVE = "active_index"

        private fun keyEvents(index: Int) = "slot_${index}_events"
        private fun keyDefault(index: Int) = "slot_${index}_default"
        private fun keyTask(index: Int) = "slot_${index}_task"
    }
}
