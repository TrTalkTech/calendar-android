package com.example.calendar

import com.example.calendar.data.ProductionDayKind
import com.example.calendar.data.parseYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProductionCalendarParseTest {

    @Test
    fun parseYear_marksFederalHolidayExtraRestAndShortened() {
        val json = """
            {
              "year": 2026,
              "months": [
                {"month": 1, "days": "1,7,9+"},
                {"month": 4, "days": "30*"},
                {"month": 11, "days": "4"}
              ]
            }
        """.trimIndent()

        val days = parseYear(json, 2026)

        assertEquals(ProductionDayKind.HOLIDAY, days[LocalDate.of(2026, 1, 1)]?.kind)
        assertEquals("Новогодние каникулы", days[LocalDate.of(2026, 1, 1)]?.title)
        assertEquals("Рождество Христово", days[LocalDate.of(2026, 1, 7)]?.title)
        assertEquals(ProductionDayKind.EXTRA_REST, days[LocalDate.of(2026, 1, 9)]?.kind)
        assertEquals("Выходной", days[LocalDate.of(2026, 1, 9)]?.title)
        assertEquals(ProductionDayKind.SHORTENED, days[LocalDate.of(2026, 4, 30)]?.kind)
        assertEquals("Сокращённый день", days[LocalDate.of(2026, 4, 30)]?.title)
        assertTrue(days[LocalDate.of(2026, 4, 30)]?.isNonWorking == false)
        assertEquals("День народного единства", days[LocalDate.of(2026, 11, 4)]?.title)
    }
}
