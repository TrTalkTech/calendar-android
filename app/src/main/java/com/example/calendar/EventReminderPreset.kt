package com.example.calendar

import android.content.Context
import com.example.calendar.R

enum class EventReminderPreset(val minutesBefore: Int?) {
  NONE(null),
  AT_EVENT(0),
  MINUTES_5(5),
  MINUTES_10(10),
  MINUTES_15(15),
  MINUTES_30(30),
  HOUR_1(60),
  HOURS_2(120),
  DAY_1(1440),
  DAYS_2(2880),
  WEEK_1(10080),
  ;

  companion object {
    val selectable: List<EventReminderPreset> = entries

    fun fromStoredMinutes(minutes: Int): EventReminderPreset =
      entries.firstOrNull { it.minutesBefore == minutes } ?: NONE
  }
}

fun EventReminderPreset.displayLabel(context: Context): String {
  val resId = when (this) {
    EventReminderPreset.NONE -> R.string.value_none
    EventReminderPreset.AT_EVENT -> R.string.reminder_at_event
    EventReminderPreset.MINUTES_5 -> R.string.reminder_5_minutes
    EventReminderPreset.MINUTES_10 -> R.string.reminder_10_minutes
    EventReminderPreset.MINUTES_15 -> R.string.reminder_15_minutes
    EventReminderPreset.MINUTES_30 -> R.string.reminder_30_minutes
    EventReminderPreset.HOUR_1 -> R.string.reminder_1_hour
    EventReminderPreset.HOURS_2 -> R.string.reminder_2_hours
    EventReminderPreset.DAY_1 -> R.string.reminder_1_day
    EventReminderPreset.DAYS_2 -> R.string.reminder_2_days
    EventReminderPreset.WEEK_1 -> R.string.reminder_1_week
  }
  return context.getString(resId)
}

fun List<EventReminderPreset>.minutesBeforeForSave(): List<Int> {
  val primary = firstOrNull() ?: return emptyList()
  if (primary == EventReminderPreset.NONE) return emptyList()
  val primaryMinutes = primary.minutesBefore ?: return emptyList()
  val result = mutableListOf(primaryMinutes)
  val secondary = getOrNull(1) ?: return result.sortedDescending()
  if (secondary != EventReminderPreset.NONE) {
    val secondaryMinutes = secondary.minutesBefore
    if (secondaryMinutes != null && secondaryMinutes != primaryMinutes) {
      result.add(secondaryMinutes)
    }
  }
  return result.sortedDescending()
}
