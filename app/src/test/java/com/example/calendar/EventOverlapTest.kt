package com.example.calendar

import com.example.calendar.data.BusyEvent
import com.example.calendar.data.EventOverlap
import com.example.calendar.data.OverlapCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventOverlapTest {

    @Test
    fun intervalsOverlap_touchingEndsDoNotConflict() {
        assertFalse(EventOverlap.intervalsOverlap(10_000, 11_000, 11_000, 12_000))
        assertTrue(EventOverlap.intervalsOverlap(10_000, 11_000, 10_999, 12_000))
        assertTrue(EventOverlap.intervalsOverlap(10_000, 12_000, 10_500, 11_000))
    }

    @Test
    fun conflicts_skipsSelfFreeAllDayAndTasks() {
        val candidate = OverlapCandidate(
            eventId = 5L,
            beginMillis = 10_000,
            endMillis = 11_000,
            allDay = false,
        )
        val occupied = listOf(
            slot(5, 10_000, 11_000),
            slot(6, 10_500, 11_500, availability = EventOverlap.AVAILABILITY_FREE),
            slot(7, 10_500, 11_500, allDay = true),
            slot(8, 10_500, 11_500, calendarName = "Задачи"),
            slot(9, 10_500, 11_500, title = "Стендап"),
        )
        val conflicts = EventOverlap.conflicts(candidate, occupied)
        assertEquals(listOf(9L), conflicts.map { it.eventId })
    }

    @Test
    fun conflicts_includesOtherCalendars() {
        val candidate = OverlapCandidate(1L, 10_000, 11_000, allDay = false)
        val occupied = listOf(
            slot(10, 10_500, 11_500, calendarName = "Локальный"),
            slot(11, 10_500, 11_500, calendarName = "Exchange"),
        )
        assertEquals(listOf(10L, 11L), EventOverlap.conflicts(candidate, occupied).map { it.eventId })
    }

    @Test
    fun conflicts_allDayCandidateNeverWarns() {
        val candidate = OverlapCandidate(1L, 0, 86_400_000, allDay = true)
        val occupied = listOf(slot(2, 10_000, 11_000))
        assertTrue(EventOverlap.conflicts(candidate, occupied).isEmpty())
    }

    private fun slot(
        id: Long,
        begin: Long,
        end: Long,
        allDay: Boolean = false,
        availability: Int = 0,
        calendarName: String? = "Work",
        title: String = "Busy",
    ) = BusyEvent(
        eventId = id,
        title = title,
        beginMillis = begin,
        endMillis = end,
        allDay = allDay,
        availability = availability,
        calendarName = calendarName,
    )
}
