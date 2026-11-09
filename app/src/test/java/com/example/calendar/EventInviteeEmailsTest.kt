package com.example.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventInviteeEmailsTest {

    @Test
    fun parse_splitsCommaAndSemicolonAndDropsInvalid() {
        assertEquals(
            listOf("a@example.com", "b@example.com"),
            EventInviteeEmails.parse("a@example.com, b@example.com"),
        )
        assertEquals(
            listOf("a@example.com", "b@example.com"),
            EventInviteeEmails.parse("Ann <a@example.com>; b@example.com"),
        )
        assertEquals(
            listOf("ok@example.com"),
            EventInviteeEmails.parse("not-an-email, ok@example.com, a@"),
        )
    }

    @Test
    fun invalidEmails_keepsOnlyBroken() {
        assertEquals(
            listOf("not-an-email", "a@"),
            EventInviteeEmails.invalidEmails(listOf("not-an-email", "ok@example.com", "a@")),
        )
        assertTrue(EventInviteeEmails.isValid("ok@example.com"))
        assertFalse(EventInviteeEmails.isValid("not-an-email"))
    }
}
