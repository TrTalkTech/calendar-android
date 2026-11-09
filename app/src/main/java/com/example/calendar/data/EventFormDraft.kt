package com.example.calendar.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Префил формы новой встречи: дубликат без RRULE и без вложений. */
data class EventFormDraft(
    val title: String,
    val location: String,
    val notes: String,
    val url: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val calendarId: Long?,
    val attendees: List<EventInvitee> = emptyList(),
    val reminderMinutesBefore: List<Int> = emptyList(),
) {
    fun shiftedTo(date: LocalDate): EventFormDraft {
        val days = ChronoUnit.DAYS.between(start.toLocalDate(), date)
        return copy(start = start.plusDays(days), end = end.plusDays(days))
    }
}
