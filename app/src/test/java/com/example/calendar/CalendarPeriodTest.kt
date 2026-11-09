package com.example.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarPeriodTest {

    @Test
    fun monthKeepsDayAndClampsShortMonth() {
        val january = YearMonth.of(2026, 1)
        assertEquals(LocalDate.of(2026, 1, 31), CalendarPeriod.dateInMonth(january, 31))
        val february = YearMonth.of(2026, 2)
        assertEquals(LocalDate.of(2026, 2, 28), CalendarPeriod.dateInMonth(february, 31))
        val march = YearMonth.of(2026, 3)
        assertEquals(LocalDate.of(2026, 3, 31), CalendarPeriod.dateInMonth(march, 31))
        assertEquals(LocalDate.of(2024, 2, 29), CalendarPeriod.dateInMonth(YearMonth.of(2024, 2), 31))
    }

    @Test
    fun weekSpanHeaderSameMonthAcrossMonthsAndYears() {
        assertEquals(
            "12–18 окт",
            RussianDateFormat.weekSpanHeader(LocalDate.of(2026, 10, 12)),
        )
        assertEquals(
            "26 окт – 1 ноя",
            RussianDateFormat.weekSpanHeader(LocalDate.of(2026, 10, 26)),
        )
        assertEquals(
            "28 дек 2026 – 3 янв 2027",
            RussianDateFormat.weekSpanHeader(LocalDate.of(2026, 12, 28)),
        )
    }

    @Test
    fun pickerMillisRoundTrip() {
        val date = LocalDate.of(2026, 10, 25)
        assertEquals(date, CalendarPeriod.dateFromPickerMillis(CalendarPeriod.pickerMillis(date)))
    }

    @Test
    fun shiftDaysAndWeeks() {
        val date = LocalDate.of(2026, 10, 25)
        assertEquals(LocalDate.of(2026, 10, 26), CalendarPeriod.shiftDays(date, 1))
        assertEquals(LocalDate.of(2026, 10, 18), CalendarPeriod.shiftWeeks(date, -1))
    }
}
