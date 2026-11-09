package com.example.calendar

import com.example.calendar.data.WritableCalendar
import com.example.calendar.data.preferredAccountNames
import com.example.calendar.data.preferredTaskCalendar
import com.example.calendar.data.taskCalendarsForWrite
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCalendarsTest {

    @Test
    fun preferredTaskCalendar_picksWritableExchangeOfSelectedAccount() {
        val personal = taskCalendar(
            id = 1,
            account = "home@example.com",
            type = "com.google",
            writable = true,
        )
        val workReadOnly = taskCalendar(
            id = 2,
            account = "work@example.com",
            type = "com.google.android.gm.exchange",
            writable = false,
        )
        val workWritable = taskCalendar(
            id = 3,
            account = "work@example.com",
            type = "com.google.android.gm.exchange",
            writable = true,
        )
        val calendars = listOf(personal, workReadOnly, workWritable)

        val preferred = calendars.preferredTaskCalendar(setOf("work@example.com"))
        assertEquals(3L, preferred?.id)

        assertEquals(
            listOf(3L, 2L, 1L),
            calendars.taskCalendarsForWrite(setOf("work@example.com")).map { it.id },
        )
    }

    @Test
    fun preferredAccountNames_skipsTaskCalendarsAndPicksExchangeTasks() {
        val exchangeMeetings = WritableCalendar(
            id = 6,
            displayName = "work@example.com",
            accountName = "work@example.com",
            accountType = "com.google.android.gm.exchange",
            color = null,
            isPrimary = true,
            accessLevel = 700,
        )
        val exchangeTasks = taskCalendar(
            id = 2,
            account = "work@example.com",
            type = "com.google.android.gm.exchange",
            writable = true,
        )
        val localTasks = WritableCalendar(
            id = 5,
            displayName = "Задачи",
            accountName = "Zadachi",
            accountType = "LOCAL",
            color = null,
            isPrimary = false,
            accessLevel = 700,
        )
        val calendars = listOf(exchangeMeetings, exchangeTasks, localTasks)

        assertEquals(
            setOf("work@example.com"),
            calendars.preferredAccountNames(null),
        )
        assertEquals(2L, calendars.preferredTaskCalendar(calendars.preferredAccountNames(null))?.id)
        assertEquals(
            listOf(2L, 5L),
            calendars.taskCalendarsForWrite(calendars.preferredAccountNames(null)).map { it.id },
        )
    }

    private fun taskCalendar(
        id: Long,
        account: String,
        type: String,
        writable: Boolean,
    ) = WritableCalendar(
        id = id,
        displayName = "Tasks",
        accountName = account,
        accountType = type,
        color = null,
        isPrimary = false,
        accessLevel = if (writable) 700 else 200,
    )
}
