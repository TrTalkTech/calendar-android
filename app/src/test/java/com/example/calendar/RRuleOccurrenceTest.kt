package com.example.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RRuleOccurrenceTest {

    @Test
    fun weeklyOccursOnMatchingWeekday() {
        val start = LocalDate.of(2026, 11, 9) // Monday
        val rule = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO"
        assertTrue(RRuleOccurrence.occursOn(rule, start, start))
        assertTrue(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 16)))
        assertFalse(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 10)))
        assertFalse(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun dailyRespectsUntil() {
        val start = LocalDate.of(2026, 11, 1)
        val rule = "FREQ=DAILY;INTERVAL=1;UNTIL=20261103"
        assertTrue(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 3)))
        assertFalse(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun biweeklySkipsAlternateWeeks() {
        val start = LocalDate.of(2026, 11, 9)
        val rule = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO"
        assertTrue(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 23)))
        assertFalse(RRuleOccurrence.occursOn(rule, start, LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun parseExdatesReadsCommaSeparatedAndValueDate() {
        val dates = RRuleOccurrence.parseExdates(
            "20261118T150000Z,VALUE=DATE:20261125,20261202",
        )
        assertTrue(dates.contains(LocalDate.of(2026, 11, 18)))
        assertTrue(dates.contains(LocalDate.of(2026, 11, 25)))
        assertTrue(dates.contains(LocalDate.of(2026, 12, 2)))
    }

    @Test
    fun weeklyHonorsExdates() {
        val start = LocalDate.of(2026, 5, 20)
        val rule = "FREQ=WEEKLY;BYDAY=WE;UNTIL=20261231T235959Z;WKST=MO"
        val day = LocalDate.of(2026, 11, 18)
        assertTrue(RRuleOccurrence.occursOn(rule, start, day))
        assertFalse(
            RRuleOccurrence.occursOn(
                rule,
                start,
                day,
                exdates = setOf(LocalDate.of(2026, 11, 18)),
            ),
        )
    }
}
