package com.example.calendar.data

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class EventIcsContent(
    val eventId: Long,
    val title: String,
    val location: String,
    val description: String,
    val allDay: Boolean,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val uid: String? = null,
)

object EventIcs {
    private val utcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val utcDate = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC)

    fun uid(eventId: Long): String = "calendar2-$eventId@laktapp.ru"

    fun build(content: EventIcsContent, nowEpochMillis: Long): String {
        val uid = content.uid?.takeIf { it.isNotBlank() } ?: uid(content.eventId)
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Laktapp//Calendar 2.0//RU",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "BEGIN:VEVENT",
            "UID:$uid",
            "DTSTAMP:${utcStamp.format(Instant.ofEpochMilli(nowEpochMillis))}",
        )
        if (content.allDay) {
            val startDay = utcDateDay(content.startEpochMillis)
            val endExclusive = utcDateDay(content.endEpochMillis).let { end ->
                if (!end.isAfter(startDay)) startDay.plusDays(1) else end
            }
            lines += "DTSTART;VALUE=DATE:${startDay.format(DateTimeFormatter.BASIC_ISO_DATE)}"
            lines += "DTEND;VALUE=DATE:${endExclusive.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        } else {
            lines += "DTSTART:${utcStamp.format(Instant.ofEpochMilli(content.startEpochMillis))}"
            lines += "DTEND:${utcStamp.format(Instant.ofEpochMilli(content.endEpochMillis))}"
        }
        lines += "SUMMARY:${escape(content.title.ifBlank { "Событие" })}"
        if (content.location.isNotBlank()) {
            lines += "LOCATION:${escape(content.location)}"
        }
        if (content.description.isNotBlank()) {
            lines += "DESCRIPTION:${escape(content.description)}"
        }
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", transform = ::foldLine) + "\r\n"
    }

    fun emailBody(
        content: EventIcsContent,
        zone: ZoneId,
        joinUrl: String? = null,
        locale: Locale = Locale.forLanguageTag("ru-RU"),
    ): String {
        val whenLine = formatWhen(content, zone, locale)
        return buildString {
            append(content.title.ifBlank { "Событие" })
            append('\n')
            append(whenLine)
            if (content.location.isNotBlank()) {
                append("\nМесто: ")
                append(content.location.trim())
            }
            val join = joinUrl?.trim().orEmpty()
            if (join.isNotEmpty()) {
                append("\nПодключиться: ")
                append(join)
            }
            val notes = content.description.trim()
            if (notes.isNotEmpty() && (join.isEmpty() || !notes.contains(join))) {
                append("\n\n")
                append(notes)
            }
        }
    }

    internal fun escape(text: String): String =
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")

    internal fun foldLine(line: String): String {
        val raw = line.toByteArray(StandardCharsets.UTF_8)
        if (raw.size <= 75) return line
        val out = StringBuilder()
        var index = 0
        var limit = 75
        while (index < raw.size) {
            var end = minOf(index + limit, raw.size)
            while (end > index && !utf8CharComplete(raw, index, end)) {
                end--
            }
            if (end <= index) end = minOf(index + limit, raw.size)
            out.append(String(raw, index, end - index, StandardCharsets.UTF_8))
            index = end
            if (index < raw.size) {
                out.append("\r\n ")
                limit = 74
            }
        }
        return out.toString()
    }

    private fun utf8CharComplete(raw: ByteArray, start: Int, end: Int): Boolean {
        if (end <= start) return false
        var i = end - 1
        while (i > start && (raw[i].toInt() and 0xC0) == 0x80) i--
        val lead = raw[i].toInt() and 0xFF
        val extra = when {
            lead and 0x80 == 0 -> 0
            lead and 0xE0 == 0xC0 -> 1
            lead and 0xF0 == 0xE0 -> 2
            lead and 0xF8 == 0xF0 -> 3
            else -> 0
        }
        return i + extra + 1 == end
    }

    private fun utcDateDay(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()

    private fun formatWhen(content: EventIcsContent, zone: ZoneId, locale: Locale): String {
        val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'г.'", locale)
        if (content.allDay) {
            val start = utcDateDay(content.startEpochMillis)
            val endExclusive = utcDateDay(content.endEpochMillis)
            val last = if (!endExclusive.isAfter(start)) start else endExclusive.minusDays(1)
            return if (start == last) {
                dateFmt.format(start)
            } else {
                "${dateFmt.format(start)} — ${dateFmt.format(last)}"
            }
        }
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val start = Instant.ofEpochMilli(content.startEpochMillis).atZone(zone)
        val end = Instant.ofEpochMilli(content.endEpochMillis).atZone(zone)
        val startDate = dateFmt.format(start)
        return if (start.toLocalDate() == end.toLocalDate()) {
            "$startDate, ${timeFmt.format(start)} — ${timeFmt.format(end)}"
        } else {
            "$startDate, ${timeFmt.format(start)} — ${dateFmt.format(end)}, ${timeFmt.format(end)}"
        }
    }
}
