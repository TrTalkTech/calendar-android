package com.example.calendar.data

import java.time.LocalDate
import java.time.MonthDay

enum class ProductionDayKind {
    HOLIDAY,
    WEEKEND,
    EXTRA_REST,
    SHORTENED,
}

data class ProductionDay(
    val date: LocalDate,
    val kind: ProductionDayKind,
    val title: String,
) {
    val isNonWorking: Boolean get() = kind != ProductionDayKind.SHORTENED
}

internal object ProductionHolidayNames {

    fun title(date: LocalDate, kind: ProductionDayKind): String {
        federalTitle(date)?.let { return it }
        return when (kind) {
            ProductionDayKind.SHORTENED -> "Сокращённый день"
            ProductionDayKind.EXTRA_REST,
            ProductionDayKind.WEEKEND,
            ProductionDayKind.HOLIDAY,
            -> "Выходной"
        }
    }

    fun kindForListedDay(date: LocalDate, extraRest: Boolean, shortened: Boolean): ProductionDayKind {
        if (shortened) return ProductionDayKind.SHORTENED
        if (federalTitle(date) != null) return ProductionDayKind.HOLIDAY
        if (extraRest) return ProductionDayKind.EXTRA_REST
        return ProductionDayKind.WEEKEND
    }

    private fun federalTitle(date: LocalDate): String? {
        val md = MonthDay.from(date)
        return when {
            date.monthValue == 1 && date.dayOfMonth in 1..6 -> "Новогодние каникулы"
            md == MonthDay.of(1, 7) -> "Рождество Христово"
            md == MonthDay.of(1, 8) -> "Новогодние каникулы"
            md == MonthDay.of(2, 23) -> "День защитника Отечества"
            md == MonthDay.of(3, 8) -> "Международный женский день"
            md == MonthDay.of(5, 1) -> "Праздник Весны и Труда"
            md == MonthDay.of(5, 9) -> "День Победы"
            md == MonthDay.of(6, 12) -> "День России"
            md == MonthDay.of(11, 4) -> "День народного единства"
            else -> null
        }
    }
}
