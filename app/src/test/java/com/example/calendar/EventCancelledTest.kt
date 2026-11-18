package com.example.calendar

import com.example.calendar.data.EventCancelled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCancelledTest {

    @Test
    fun detectsStatusAndTitlePrefixes() {
        assertTrue(EventCancelled.isCancelledStatus(2))
        assertFalse(EventCancelled.isCancelledStatus(1))
        assertFalse(EventCancelled.isCancelledStatus(null))

        assertTrue(EventCancelled.isCancelledTitle("Canceled: Sync"))
        assertTrue(EventCancelled.isCancelledTitle("cancelled: sync"))
        assertTrue(EventCancelled.isCancelledTitle("Отменено: встреча"))
        assertTrue(EventCancelled.isCancelledTitle("отмена: план"))
        assertFalse(EventCancelled.isCancelledTitle("Олег-Полина"))
        assertFalse(EventCancelled.isCancelledTitle(null))
    }

    @Test
    fun hideRespectsShowCancelledToggle() {
        assertTrue(
            EventCancelled.shouldHideFromAgenda(
                status = 2,
                title = "Meeting",
                showCancelled = false,
            ),
        )
        assertTrue(
            EventCancelled.shouldHideFromAgenda(
                status = null,
                title = "Canceled: Meeting",
                showCancelled = false,
            ),
        )
        assertFalse(
            EventCancelled.shouldHideFromAgenda(
                status = 2,
                title = "Canceled: Meeting",
                showCancelled = true,
            ),
        )
        assertFalse(
            EventCancelled.shouldHideFromAgenda(
                status = 1,
                title = "Meeting",
                showCancelled = false,
            ),
        )
    }
}
