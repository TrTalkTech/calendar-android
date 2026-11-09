package com.example.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RRuleBuilderTest {

    private val thursday = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun weeklyWithUntilAndWithoutRepeatEnd() {
        val weekly = RRuleBuilder.weeklyWithByDay(1, thursday)
        val withUntil = RRuleBuilder.withUntilDate(
            rrule = weekly,
            untilDate = LocalDate.of(2026, 12, 1),
            allDay = false,
            eventStart = LocalDateTime.of(2026, 10, 29, 10, 0),
            eventEnd = LocalDateTime.of(2026, 10, 29, 11, 0),
            zone = zone,
        )
        assertTrue(withUntil.contains("FREQ=WEEKLY"))
        assertTrue(withUntil.contains("BYDAY=TH"))
        assertTrue(withUntil.contains("UNTIL="))
        assertTrue(RRuleBuilder.hasUntilDate(withUntil))

        val stripped = RRuleBuilder.withoutRepeatEnd(withUntil)
        assertFalse(stripped.contains("UNTIL"))
        assertFalse(stripped.contains("COUNT"))
        assertTrue(stripped.contains("FREQ=WEEKLY"))
    }

    @Test
    fun withoutRepeatEnd_stripsCount() {
        val stripped = RRuleBuilder.withoutRepeatEnd("FREQ=DAILY;COUNT=5;INTERVAL=1")
        assertFalse(stripped.contains("COUNT"))
        assertTrue(stripped.contains("FREQ=DAILY"))
        assertTrue(stripped.contains("INTERVAL=1"))
    }

    @Test
    fun normalizeForComparison_ignoresPartOrder() {
        assertEquals(
            RRuleBuilder.normalizeForComparison("FREQ=WEEKLY;INTERVAL=1;BYDAY=TH"),
            RRuleBuilder.normalizeForComparison("INTERVAL=1;BYDAY=TH;FREQ=WEEKLY"),
        )
    }
}
