package com.example.calendar

import com.example.calendar.data.CalendarEvent
import com.example.calendar.data.EventOverlap
import com.example.calendar.data.EventRsvp
import com.example.calendar.data.isHiddenExchangeTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenExchangeTaskTest {

    @Test
    fun freeAllDayMeetingOnExchangeIsVisibleWithoutMarker() {
        val event = event(
            title = "Offsite",
            allDay = true,
            availability = EventOverlap.AVAILABILITY_FREE,
            calendarId = 10,
        )
        assertFalse(event.isHiddenExchangeTask(setOf(10L), appTaskEventIds = emptySet()))
    }

    @Test
    fun markedAppTaskIsHidden() {
        val event = event(
            title = "Buy milk",
            allDay = true,
            availability = EventOverlap.AVAILABILITY_FREE,
            calendarId = 10,
            eventId = 55,
        )
        assertTrue(event.isHiddenExchangeTask(setOf(10L), appTaskEventIds = setOf(55L)))
    }

    @Test
    fun donePrefixIsHidden() {
        val event = event(
            title = "✓ Done item",
            allDay = true,
            availability = EventOverlap.AVAILABILITY_FREE,
            calendarId = 10,
        )
        assertTrue(event.isHiddenExchangeTask(setOf(10L), appTaskEventIds = emptySet()))
    }

    @Test
    fun busyAllDayIsNeverHiddenAsTask() {
        val event = event(
            title = "Holiday",
            allDay = true,
            availability = 0,
            calendarId = 10,
            eventId = 55,
        )
        assertFalse(event.isHiddenExchangeTask(setOf(10L), appTaskEventIds = setOf(55L)))
    }

    private fun event(
        title: String,
        allDay: Boolean,
        availability: Int,
        calendarId: Long,
        eventId: Long = 1L,
    ) = CalendarEvent(
        eventId = eventId,
        title = title,
        beginMillis = 0L,
        endMillis = 86_400_000L,
        allDay = allDay,
        calendarId = calendarId,
        calendarName = "Work",
        color = null,
        availability = availability,
        selfAttendeeStatus = EventRsvp.STATUS_NONE,
    )
}
