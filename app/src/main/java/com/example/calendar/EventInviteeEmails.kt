package com.example.calendar

import com.example.calendar.data.EventInvitee
import java.util.Locale

/** Разбор и проверка адресов участников события. */
object EventInviteeEmails {

    private val emailInBrackets = Regex("<([^>]+)>")
    private val emailPattern = Regex(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
    )

    fun parse(input: String): List<String> =
        parseInvitees(input).map { it.email }

    fun parseInvitees(input: String): List<EventInvitee> {
        val result = mutableListOf<EventInvitee>()
        val seen = HashSet<String>()
        for (token in input.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }) {
            val email = extractEmail(token)
            if (email.isEmpty()) continue
            val key = email.lowercase(Locale.ROOT)
            if (!seen.add(key)) continue
            val inlineName = token.substringBefore('<').trim().takeIf { part ->
                part.isNotEmpty() && !part.contains('@')
            }
            result += EventInvitee(email = email, name = inlineName)
        }
        return result
    }

    fun trailingQuery(input: String): String {
        val lastDelimiter = input.lastIndexOfAny(charArrayOf(',', ';', '\n'))
        return if (lastDelimiter < 0) {
            input.trim()
        } else {
            input.substring(lastDelimiter + 1).trim()
        }
    }

    fun appendInvitee(value: String, displayName: String, email: String): String {
        val token = formatToken(displayName, email)
        val lastDelimiter = value.lastIndexOfAny(charArrayOf(',', ';', '\n'))
        val prefix = if (lastDelimiter < 0) {
            ""
        } else {
            value.substring(0, lastDelimiter + 1) + " "
        }
        return prefix + token + ", "
    }

    fun formatToken(displayName: String, email: String): String {
        val name = displayName.trim()
        return if (name.isEmpty()) {
            email
        } else {
            "$name <$email>"
        }
    }

    fun displayLabel(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return ""
        val name = trimmed.substringBefore('<').trim()
        if (name.isNotEmpty()) return name
        return extractEmail(trimmed)
    }

    fun displayValues(input: String): List<String> =
        input.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { displayLabel(it) }
            .filter { it.isNotEmpty() }

    fun extractEmail(token: String): String {
        val bracketed = emailInBrackets.find(token)?.groupValues?.getOrNull(1)?.trim()
        if (!bracketed.isNullOrEmpty() && isValid(bracketed)) return bracketed
        val trimmed = token.trim()
        return if (isValid(trimmed)) trimmed else ""
    }

    fun isValid(email: String): Boolean =
        emailPattern.matches(email.trim())

    fun invalidEmails(emails: List<String>): List<String> =
        emails.filterNot { isValid(it) }
}
