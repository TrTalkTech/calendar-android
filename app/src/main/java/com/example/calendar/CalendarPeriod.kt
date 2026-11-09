package com.example.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

object CalendarPeriod {

    fun shiftDays(date: LocalDate, dayDelta: Long): LocalDate = date.plusDays(dayDelta)

    fun shiftWeeks(date: LocalDate, weekDelta: Long): LocalDate = date.plusWeeks(weekDelta)

    /** Сохраняет число месяца. В коротком месяце берётся последний день, якорь не затирается. */
    fun dateInMonth(month: YearMonth, dayAnchor: Int): LocalDate {
        val day = dayAnchor.coerceIn(1, month.lengthOfMonth())
        return month.atDay(day)
    }

    fun pickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun dateFromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
