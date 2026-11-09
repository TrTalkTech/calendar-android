package com.example.calendar

import com.example.calendar.data.ProductionDay
import com.example.calendar.data.ProductionDayKind
import com.example.calendar.data.ProductionHolidayNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class ProductionHolidayNamesTest {

    @Test
    fun january8IsNewYearHolidays() {
        assertEquals(
            "Новогодние каникулы",
            ProductionHolidayNames.title(LocalDate.of(2026, 1, 8), ProductionDayKind.HOLIDAY),
        )
    }

    @Test
    fun shortenedDayIsNotNonWorking() {
        val day = ProductionDay(
            date = LocalDate.of(2026, 4, 30),
            kind = ProductionDayKind.SHORTENED,
            title = ProductionHolidayNames.title(LocalDate.of(2026, 4, 30), ProductionDayKind.SHORTENED),
        )
        assertEquals("Сокращённый день", day.title)
        assertFalse(day.isNonWorking)
        assertEquals(
            ProductionDayKind.SHORTENED,
            ProductionHolidayNames.kindForListedDay(
                LocalDate.of(2026, 4, 30),
                extraRest = false,
                shortened = true,
            ),
        )
    }
}
