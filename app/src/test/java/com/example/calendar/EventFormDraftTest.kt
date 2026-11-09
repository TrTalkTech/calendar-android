package com.example.calendar

import com.example.calendar.data.EventFormDraft
import com.example.calendar.data.EventInvitee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EventFormDraftTest {

    @Test
    fun shiftedTo_allDayKeepsExclusiveEnd() {
        val draft = EventFormDraft(
            title = "Отпуск",
            location = "",
            notes = "",
            url = "",
            allDay = true,
            start = LocalDateTime.of(2026, 10, 29, 0, 0),
            end = LocalDateTime.of(2026, 10, 30, 0, 0),
            calendarId = 4L,
            attendees = listOf(EventInvitee("a@example.com", "Ann")),
            reminderMinutesBefore = listOf(15),
        ).shiftedTo(LocalDate.of(2026, 11, 1))
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), draft.start)
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0), draft.end)
        assertTrue(draft.allDay)
        assertEquals(4L, draft.calendarId)
        assertEquals(listOf("a@example.com"), draft.attendees.map { it.email })
    }
}
