package com.example.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RecurrenceFreq {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

/** Какой пункт меню «Повтор» соответствует текущему правилу. */
enum class RepeatMenuPreset {
    NEVER,
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    YEARLY,
    /** Любое другое правило — пункт «Настройка». */
    CUSTOM,
}

/** Ограничение серии по дате в UI «Конец повтора». */
enum class RepeatEndPreset {
    NEVER,
    ON_DATE,
}

data class SimpleRecurrenceState(
    val freq: RecurrenceFreq,
    val interval: Int,
)

/**
 * Сборка и разбор простых RRULE для [android.provider.CalendarContract.Events.RRULE].
 */
object RRuleBuilder {

    private val byDayTokenRegex =
        Regex("^([+-]?\\d*)(MO|TU|WE|TH|FR|SA|SU)$", RegexOption.IGNORE_CASE)
    private val untilDateTimeRegex = Regex("^(\\d{8})T(\\d{6})Z?$", RegexOption.IGNORE_CASE)

    fun hasUntilDate(rrule: String?): Boolean =
        parseParts(rrule?.trim().orEmpty()).containsKey("UNTIL")

    fun parseUntilDate(
        rrule: String?,
        allDay: Boolean,
        zone: ZoneId,
    ): LocalDate? {
        val until = parseParts(rrule?.trim().orEmpty())["UNTIL"] ?: return null
        parseBasicDate(until.take(8))?.let { return it }
        untilDateTimeRegex.matchEntire(until)?.let { match ->
            val rawDate = match.groupValues[1]
            val rawTime = match.groupValues[2]
            val year = rawDate.substring(0, 4).toInt()
            val month = rawDate.substring(4, 6).toInt()
            val day = rawDate.substring(6, 8).toInt()
            val hour = rawTime.substring(0, 2).toInt()
            val minute = rawTime.substring(2, 4).toInt()
            val second = rawTime.substring(4, 6).toInt()
            return LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(if (allDay) ZoneId.of("UTC") else zone)
                .toLocalDate()
        }
        return null
    }

    fun withoutRepeatEnd(rrule: String): String {
        val parts = parseParts(rrule).filterKeys { key ->
            key != "UNTIL" && key != "COUNT"
        }
        return rebuildRule(parts)
    }

    fun withUntilDate(
        rrule: String,
        untilDate: LocalDate,
        allDay: Boolean,
        eventStart: LocalDateTime,
        eventEnd: LocalDateTime,
        zone: ZoneId,
    ): String {
        val parts = parseParts(withoutRepeatEnd(rrule)).toMutableMap()
        if (parts["FREQ"]?.equals("WEEKLY", ignoreCase = true) == true && !parts.containsKey("WKST")) {
            parts["WKST"] = "SU"
        }
        parts.remove("UNTIL")
        parts.remove("COUNT")
        val base = rebuildRule(parts)
        val until = formatUntil(untilDate, allDay, eventStart, eventEnd, zone)
        return "$base;UNTIL=$until"
    }

    fun normalizeForComparison(rrule: String?): String {
        val raw = rrule?.trim().orEmpty().removePrefix("RRULE:").trim()
        if (raw.isEmpty()) return ""
        val parts = parseParts(raw)
        return parts.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value}" }
    }

    private fun formatUntil(
        untilDate: LocalDate,
        allDay: Boolean,
        eventStart: LocalDateTime,
        eventEnd: LocalDateTime,
        zone: ZoneId,
    ): String {
        if (allDay) {
            return untilDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        }
        val untilTime = if (eventEnd.toLocalDate() == eventStart.toLocalDate()) {
            eventEnd.toLocalTime()
        } else {
            eventStart.toLocalTime()
        }
        val untilInstant = untilDate
            .atTime(untilTime)
            .atZone(zone)
            .toInstant()
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(untilInstant)
    }

    private fun parseBasicDate(raw: String): LocalDate? = runCatching {
        LocalDate.of(
            raw.substring(0, 4).toInt(),
            raw.substring(4, 6).toInt(),
            raw.substring(6, 8).toInt(),
        )
    }.getOrNull()

    private fun rebuildRule(parts: Map<String, String>): String =
        parts.entries.joinToString(";") { "${it.key}=${it.value}" }

    /**
     * Определяет, какой пункт пресет-меню «Повтор» соответствует [rrule] для даты начала [anchorDate]
     * (день недели для еженедельных правил).
     */
    fun classifyRepeatMenuPreset(rrule: String?, anchorDate: LocalDate): RepeatMenuPreset {
        val raw = rrule?.trim()?.takeIf { it.isNotEmpty() } ?: return RepeatMenuPreset.NEVER
        val parts = parseParts(raw)
        val freq = parts["FREQ"]?.uppercase(Locale.ROOT) ?: return RepeatMenuPreset.CUSTOM
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return when (freq) {
            "DAILY" -> if (interval == 1) RepeatMenuPreset.DAILY else RepeatMenuPreset.CUSTOM
            "MONTHLY" -> if (interval == 1) RepeatMenuPreset.MONTHLY else RepeatMenuPreset.CUSTOM
            "YEARLY" -> if (interval == 1) RepeatMenuPreset.YEARLY else RepeatMenuPreset.CUSTOM
            "WEEKLY" -> {
                if (!weeklyByDayMatchesAnchor(parts, anchorDate)) {
                    RepeatMenuPreset.CUSTOM
                } else {
                    when (interval) {
                        1 -> RepeatMenuPreset.WEEKLY
                        2 -> RepeatMenuPreset.BIWEEKLY
                        else -> RepeatMenuPreset.CUSTOM
                    }
                }
            }
            else -> RepeatMenuPreset.CUSTOM
        }
    }

    private fun weeklyByDayMatchesAnchor(parts: Map<String, String>, anchor: LocalDate): Boolean {
        val anchorCode = byDayToken(anchor).uppercase(Locale.ROOT)
        val byDay = parts["BYDAY"]?.trim() ?: return true
        val tokens = byDay.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size != 1) return false
        val code = singleByDayDayCode(tokens[0]) ?: return false
        return code == anchorCode
    }

    private fun singleByDayDayCode(token: String): String? =
        byDayTokenRegex.matchEntire(token.trim())?.groupValues?.get(2)?.uppercase(Locale.ROOT)

    fun buildCustom(freq: RecurrenceFreq, interval: Int, anchorDate: LocalDate): String {
        val n = interval.coerceIn(1, 1000)
        return when (freq) {
            RecurrenceFreq.DAILY -> "FREQ=DAILY;INTERVAL=$n"
            RecurrenceFreq.WEEKLY -> weeklyWithByDay(n, anchorDate)
            RecurrenceFreq.MONTHLY -> "FREQ=MONTHLY;INTERVAL=$n"
            RecurrenceFreq.YEARLY -> "FREQ=YEARLY;INTERVAL=$n"
        }
    }

    fun weeklyWithByDay(interval: Int, anchorDate: LocalDate): String {
        val n = interval.coerceIn(1, 1000)
        val byDay = byDayToken(anchorDate)
        return "FREQ=WEEKLY;INTERVAL=$n;BYDAY=$byDay"
    }

    fun byDayToken(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }

    /**
     * Разбор правила для экрана «Настройка»; неподдерживаемые правила — null (покажем значения по умолчанию).
     */
    fun parseForCustom(rrule: String?): SimpleRecurrenceState? {
        val raw = rrule?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = parseParts(raw)
        val freqStr = parts["FREQ"]?.uppercase(Locale.ROOT) ?: return null
        val freq = when (freqStr) {
            "DAILY" -> RecurrenceFreq.DAILY
            "WEEKLY" -> RecurrenceFreq.WEEKLY
            "MONTHLY" -> RecurrenceFreq.MONTHLY
            "YEARLY" -> RecurrenceFreq.YEARLY
            else -> return null
        }
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceIn(1, 1000)?.takeIf { it > 0 } ?: 1
        return SimpleRecurrenceState(freq, interval)
    }

    private fun parseParts(rrule: String): Map<String, String> {
        if (rrule.isBlank()) return emptyMap()
        return rrule.split(";").mapNotNull { token ->
            val t = token.trim()
            if (t.isEmpty()) return@mapNotNull null
            val eq = t.indexOf('=')
            if (eq <= 0 || eq >= t.length - 1) return@mapNotNull null
            val key = t.substring(0, eq).trim().uppercase(Locale.ROOT)
            val value = t.substring(eq + 1).trim()
            key to value
        }.toMap()
    }
}
