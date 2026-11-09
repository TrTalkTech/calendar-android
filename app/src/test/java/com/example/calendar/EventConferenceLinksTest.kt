package com.example.calendar

import com.example.calendar.ui.EventConferenceLinks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventConferenceLinksTest {

    @Test
    fun zoomUrlIsConference() {
        assertTrue(EventConferenceLinks.isConferenceUrl("https://zoom.us/j/123"))
        assertTrue(EventConferenceLinks.isConferenceLocation("https://zoom.us/j/123"))
    }

    @Test
    fun streetAddressIsNotConference() {
        assertFalse(EventConferenceLinks.isConferenceLocation("Москва, Тверская 1"))
        assertFalse(EventConferenceLinks.isConferenceUrl("Москва, Тверская 1"))
    }

    @Test
    fun meetHostIsConference() {
        assertTrue(EventConferenceLinks.isConferenceUrl("https://meet.example.com/abc"))
        assertTrue(EventConferenceLinks.isConferenceUrl("https://meet.google.com/xyz-abcd-efg"))
    }
}
