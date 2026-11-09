package com.example.calendar

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Названия месяцев для заголовка сетки (именительный падеж). */
private val monthsNominative = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

/** Родительный падеж после числа («12 мая»). */
private val monthsGenitive = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

/** Короткий месяц в диапазоне недели («14–20 окт»). */
private val monthsShort = listOf(
    "янв", "фев", "мар", "апр", "май", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)

object RussianDateFormat {

    fun monthYearHeader(yearMonth: YearMonth): String {
        val m = monthsNominative[yearMonth.monthValue - 1]
        return "$m ${yearMonth.year}"
    }

    /** Диапазон семи дней, начиная с [weekStart]: «14–20 окт», через месяц — «26 окт – 1 ноя». */
    fun weekSpanHeader(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        val startMonth = monthsShort[weekStart.monthValue - 1]
        val endMonth = monthsShort[end.monthValue - 1]
        return when {
            weekStart.year == end.year && weekStart.month == end.month ->
                "${weekStart.dayOfMonth}–${end.dayOfMonth} $startMonth"
            weekStart.year == end.year ->
                "${weekStart.dayOfMonth} $startMonth – ${end.dayOfMonth} $endMonth"
            else ->
                "${weekStart.dayOfMonth} $startMonth ${weekStart.year} – ${end.dayOfMonth} $endMonth ${end.year}"
        }
    }

    fun monthNominative(date: LocalDate): String = monthsNominative[date.monthValue - 1]

    /** День и месяц в родительном падеже, напр. «12 мая». */
    fun dayAndMonthGenitive(date: LocalDate): String {
        val gen = monthsGenitive[date.monthValue - 1]
        return "${date.dayOfMonth} $gen"
    }

    /** «20 нед., 137 день» — номер недели и день года. */
    fun weekAndDayCaption(date: LocalDate, locale: Locale = AppLocale.display): String {
        val weekOfYear = date.get(WeekFields.of(locale).weekOfWeekBasedYear())
        return "$weekOfYear нед., ${date.dayOfYear} день"
    }

    /**
     * Подпись дня в списке событий: «Воскресенье, 17 мая, 20 нед., 137 день.»
     */
    fun eventsDayTitle(date: LocalDate, locale: Locale = AppLocale.display): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        return "$weekday, ${dayAndMonthGenitive(date)}, ${weekAndDayCaption(date, locale)}."
    }
}
