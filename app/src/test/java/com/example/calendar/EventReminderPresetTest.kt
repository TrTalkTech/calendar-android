package com.example.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class EventReminderPresetTest {

    @Test
    fun noneSavesEmptyList() {
        assertEquals(emptyList<Int>(), listOf(EventReminderPreset.NONE).minutesBeforeForSave())
        assertEquals(emptyList<Int>(), emptyList<EventReminderPreset>().minutesBeforeForSave())
    }

    @Test
    fun duplicateMinutesAreCollapsed() {
        assertEquals(
            listOf(15),
            listOf(EventReminderPreset.MINUTES_15).minutesBeforeForSave(),
        )
        assertEquals(
            listOf(15),
            listOf(
                EventReminderPreset.MINUTES_15,
                EventReminderPreset.MINUTES_15,
            ).minutesBeforeForSave(),
        )
    }

    @Test
    fun minutesSortedDescending() {
        assertEquals(
            listOf(60, 5),
            listOf(
                EventReminderPreset.MINUTES_5,
                EventReminderPreset.HOUR_1,
            ).minutesBeforeForSave(),
        )
    }
}
