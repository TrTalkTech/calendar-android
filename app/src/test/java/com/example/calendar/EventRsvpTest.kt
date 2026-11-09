package com.example.calendar

import com.example.calendar.data.EventRsvp
import com.example.calendar.data.EventRsvpAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRsvpTest {

    @Test
    fun mapsSelfAttendeeStatus() {
        assertEquals(EventRsvpAnswer.ACCEPTED, EventRsvp.fromSelfAttendeeStatus(1))
        assertEquals(EventRsvpAnswer.DECLINED, EventRsvp.fromSelfAttendeeStatus(2))
        assertEquals(EventRsvpAnswer.TENTATIVE, EventRsvp.fromSelfAttendeeStatus(4))
        assertNull(EventRsvp.fromSelfAttendeeStatus(3))
        assertEquals(1, EventRsvp.toSelfAttendeeStatus(EventRsvpAnswer.ACCEPTED))
        assertEquals(2, EventRsvp.toSelfAttendeeStatus(EventRsvpAnswer.DECLINED))
        assertEquals(4, EventRsvp.toSelfAttendeeStatus(EventRsvpAnswer.TENTATIVE))
    }

    @Test
    fun hidesDeclinedFromAgenda() {
        assertTrue(EventRsvp.shouldHideFromAgenda(2))
        assertFalse(EventRsvp.shouldHideFromAgenda(1))
        assertFalse(EventRsvp.shouldHideFromAgenda(null))
        assertTrue(EventRsvp.notDeclinedSql("selfAttendeeStatus").contains("!= 2"))
    }

    @Test
    fun outlineForUnacceptedInvites() {
        assertTrue(EventRsvp.isNotAccepted(EventRsvp.STATUS_INVITED))
        assertTrue(EventRsvp.isNotAccepted(EventRsvp.STATUS_TENTATIVE))
        assertFalse(EventRsvp.isNotAccepted(EventRsvp.STATUS_ACCEPTED))
        assertFalse(EventRsvp.isNotAccepted(EventRsvp.STATUS_NONE))
        assertFalse(EventRsvp.isNotAccepted(EventRsvp.STATUS_DECLINED))
        assertFalse(EventRsvp.isNotAccepted(null))
    }

    @Test
    fun chooserCollapsedAfterStoredAnswer() {
        assertFalse(EventRsvp.hasAnswer(EventRsvp.STATUS_INVITED))
        assertFalse(EventRsvp.hasAnswer(EventRsvp.STATUS_NONE))
        assertTrue(EventRsvp.hasAnswer(EventRsvp.STATUS_ACCEPTED))
        assertTrue(EventRsvp.hasAnswer(EventRsvp.STATUS_TENTATIVE))
        assertTrue(EventRsvp.hasAnswer(EventRsvp.STATUS_DECLINED))
    }

    @Test
    fun canRespond_onlyForIncomingInvite() {
        assertFalse(
            EventRsvp.canRespond(
                isOrganizer = true,
                organizerEmail = "me@example.com",
                ownerAccount = "me@example.com",
                selfAttendeeStatus = EventRsvp.STATUS_INVITED,
                hasAttendeeData = true,
            ),
        )
        assertTrue(
            EventRsvp.canRespond(
                isOrganizer = false,
                organizerEmail = "boss@example.com",
                ownerAccount = "me@example.com",
                selfAttendeeStatus = EventRsvp.STATUS_INVITED,
                hasAttendeeData = true,
            ),
        )
        assertTrue(
            EventRsvp.canRespond(
                isOrganizer = false,
                organizerEmail = "boss@example.com",
                ownerAccount = "me@example.com",
                selfAttendeeStatus = EventRsvp.STATUS_NONE,
                hasAttendeeData = true,
            ),
        )
        assertFalse(
            EventRsvp.canRespond(
                isOrganizer = false,
                organizerEmail = "me@example.com",
                ownerAccount = "me@example.com",
                selfAttendeeStatus = EventRsvp.STATUS_NONE,
                hasAttendeeData = true,
            ),
        )
    }
}
