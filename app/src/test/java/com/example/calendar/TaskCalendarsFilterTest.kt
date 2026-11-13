package com.example.calendar

import com.example.calendar.data.CalendarEvent
import com.example.calendar.data.EventOverlap
import com.example.calendar.data.WritableCalendar
import com.example.calendar.data.filterIdsForEventLists
import com.example.calendar.data.isHiddenExchangeTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCalendarsFilterTest {

    @Test
    fun filterIdsForEventLists_excludesTasksWhenFilterIsNull() {
        val event = calendar(1, "Работа")
        val tasks = calendar(2, "Tasks")
        assertEquals(setOf(1L), filterIdsForEventLists(null, listOf(event, tasks)))
    }

    @Test
    fun filterIdsForEventLists_returnsNullWhenNoTaskCalendar() {
        val event = calendar(1, "Работа")
        assertNull(filterIdsForEventLists(null, listOf(event)))
    }

    @Test
    fun filterIdsForEventLists_intersectsVisibleFilterWithEventIds() {
        val event = calendar(1, "Работа")
        val other = calendar(3, "Личное")
        val tasks = calendar(2, "Задачи")
        assertEquals(
            setOf(1L),
            filterIdsForEventLists(setOf(1L, 2L), listOf(event, other, tasks)),
        )
    }

    @Test
    fun allDayFreeOnExchangeWriteCalendar_hiddenOnlyWhenAppTask() {
        val task = CalendarEvent(
            eventId = 10,
            title = "Задача",
            beginMillis = 0,
            endMillis = 86_400_000,
            allDay = true,
            calendarId = 1,
            calendarName = "Work",
            color = null,
            availability = EventOverlap.AVAILABILITY_FREE,
        )
        assertFalse(task.isHiddenExchangeTask(setOf(1L), appTaskEventIds = emptySet()))
        assertTrue(task.isHiddenExchangeTask(setOf(1L), appTaskEventIds = setOf(10L)))
        assertFalse(task.copy(allDay = false).isHiddenExchangeTask(setOf(1L), setOf(10L)))
        assertFalse(task.copy(availability = 0).isHiddenExchangeTask(setOf(1L), setOf(10L)))
        assertFalse(task.isHiddenExchangeTask(setOf(9L), setOf(10L)))
    }

    private fun calendar(id: Long, name: String) = WritableCalendar(
        id = id,
        displayName = name,
        accountName = "local",
        accountType = "LOCAL",
        color = null,
        isPrimary = id == 1L,
        accessLevel = 700,
    )
}
