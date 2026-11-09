package com.example.calendar

import com.example.calendar.data.EventFormDraft
import com.example.calendar.data.EventIcs
import com.example.calendar.data.EventIcsContent
import com.example.calendar.data.EventInvitee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class EventIcsTest {

    @Test
    fun build_timedEvent_utcTimesAndEscaping() {
        val ics = EventIcs.build(
            EventIcsContent(
                eventId = 42,
                title = "Стендап, план",
                location = "Москва, офис",
                description = "https://teams.microsoft.com/l/meetup\nЗал 3",
                allDay = false,
                startEpochMillis = 1_762_167_600_000L,
                endEpochMillis = 1_762_171_200_000L,
            ),
            nowEpochMillis = 1_762_160_000_000L,
        )
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("UID:calendar2-42@laktapp.ru"))
        assertTrue(ics.contains("SUMMARY:Стендап\\, план"))
        assertTrue(ics.contains("LOCATION:Москва\\, офис"))
        assertTrue(ics.contains("DESCRIPTION:https://teams.microsoft.com/l/meetup\\nЗал 3"))
        assertTrue(ics.contains("DTSTART:"))
        assertFalse(ics.contains("DTSTART;VALUE=DATE"))
        assertTrue(ics.contains("\r\n"))
    }

    @Test
    fun build_allDay_usesExclusiveDate() {
        val start = LocalDate.of(2026, 10, 29).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val ics = EventIcs.build(
            EventIcsContent(
                eventId = 1,
                title = "Отпуск",
                location = "",
                description = "",
                allDay = true,
                startEpochMillis = start,
                endEpochMillis = end,
            ),
            nowEpochMillis = start,
        )
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261029"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261030"))
    }

    @Test
    fun foldLine_wrapsLongUtf8() {
        val line = "SUMMARY:" + "я".repeat(80)
        val folded = EventIcs.foldLine(line)
        assertTrue(folded.contains("\r\n "))
        folded.lines().forEach { part ->
            assertTrue(part.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }

    @Test
    fun emailBody_includesJoinAndTime() {
        val start = LocalDateTime.of(2026, 10, 29, 10, 0)
            .atZone(ZoneOffset.ofHours(3)).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 10, 29, 11, 0)
            .atZone(ZoneOffset.ofHours(3)).toInstant().toEpochMilli()
        val body = EventIcs.emailBody(
            EventIcsContent(
                eventId = 1,
                title = "Стендап",
                location = "Москва",
                description = "Заметки",
                allDay = false,
                startEpochMillis = start,
                endEpochMillis = end,
            ),
            zone = ZoneOffset.ofHours(3),
            joinUrl = "https://teams.microsoft.com/l/meetup",
        )
        assertTrue(body.contains("Стендап"))
        assertTrue(body.contains("10:00"))
        assertTrue(body.contains("11:00"))
        assertTrue(body.contains("Место: Москва"))
        assertTrue(body.contains("Подключиться: https://teams.microsoft.com/l/meetup"))
        assertTrue(body.contains("Заметки"))
    }

    @Test
    fun draft_shiftsDateKeepsTimeAndAttendees() {
        val draft = EventFormDraft(
            title = "Стендап",
            location = "офис",
            notes = "повестка",
            url = "https://example.com",
            allDay = false,
            start = LocalDateTime.of(2026, 10, 29, 10, 30),
            end = LocalDateTime.of(2026, 10, 29, 11, 0),
            calendarId = 7L,
            attendees = listOf(EventInvitee("a@example.com", "Ann")),
            reminderMinutesBefore = listOf(15),
        ).shiftedTo(LocalDate.of(2026, 11, 1))
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 30), draft.start)
        assertEquals(LocalDateTime.of(2026, 11, 1, 11, 0), draft.end)
        assertEquals("Стендап", draft.title)
        assertEquals(listOf("a@example.com"), draft.attendees.map { it.email })
        assertEquals(listOf(15), draft.reminderMinutesBefore)
    }
}
