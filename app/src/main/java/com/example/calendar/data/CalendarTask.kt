package com.example.calendar.data

import java.time.LocalDate

data class CalendarTask(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val date: LocalDate,
    val done: Boolean,
    val notes: String = "",
    val color: Int?,
    val calendarName: String?,
    val reminderMinutesBefore: List<Int> = emptyList(),
)
