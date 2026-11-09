package com.example.calendar

import com.example.calendar.data.isTaskCalendarName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCalendarsNameTest {

    @Test
    fun isTaskCalendarName_matchesExactSetOnly() {
        assertTrue("Tasks".isTaskCalendarName())
        assertTrue("task".isTaskCalendarName())
        assertTrue("Задачи".isTaskCalendarName())
        assertTrue("задача".isTaskCalendarName())
        assertTrue(" TASKS ".isTaskCalendarName())
        assertFalse("Task list".isTaskCalendarName())
        assertFalse("Мои задачи".isTaskCalendarName())
        assertFalse("Google Tasks".isTaskCalendarName())
    }
}
