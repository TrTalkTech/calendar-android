package com.example.calendar.ui

import java.util.Locale

object EventConferenceLinks {
    private val conferenceHosts = listOf(
        "zoom.us",
        "zoom.com",
        "teams.microsoft.com",
        "teams.live.com",
        "meet.google.com",
        "webex.com",
        "skype.com",
        "join.skype",
        "whereby.com",
        "dialin.",
    )

    private val conferenceLabels = listOf(
        "skype",
        "zoom",
        "teams",
        "webex",
        "google meet",
        "skype for business",
        "собрание skype",
        "конференция zoom",
        "microsoft teams",
    )

    private val urlInText = Regex(
        """(?i)(?:<(https?://[^>\s]+)>|((?:https?://|www\.)[^\s<>"']+))""",
    )

    fun isConferenceLocation(location: String): Boolean {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (conferenceLabels.any { lower.contains(it) }) return true
        return extractUrls(trimmed).any(::isConferenceUrl)
    }

    fun joinUrl(location: String, notes: String = "", url: String = ""): String? {
        val fromLocation = locationAsUrl(location) ?: extractUrls(location).firstOrNull()
        if (fromLocation != null && (isConferenceUrl(fromLocation) || locationAsUrl(location) != null)) {
            return fromLocation
        }
        extractUrls(notes).firstOrNull(::isConferenceUrl)?.let { return it }
        locationAsUrl(url)?.let { return it }
        extractUrls(url).firstOrNull()?.let { return it }
        extractUrls(notes).firstOrNull()?.let { return it }
        return fromLocation
    }

    fun isConferenceUrl(raw: String): Boolean {
        val url = normalizeUrl(raw) ?: return false
        val host = hostOf(url) ?: return false
        if (conferenceHosts.any { host.contains(it) }) return true
        if (host.contains("meet.")) return true
        return false
    }

    internal fun hostOf(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withoutScheme = if (trimmed.contains("://")) {
            trimmed.substringAfter("://")
        } else {
            trimmed
        }
        val hostPort = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBefore(':')
        }.trim().lowercase(Locale.ROOT)
        return host.takeIf { it.isNotEmpty() }
    }

    fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return urlInText.findAll(text).mapNotNull { match ->
            val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
            normalizeUrl(raw.trim().trimEnd('.', ',', ';', ')'))
        }.distinct().toList()
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("www.", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }
}
