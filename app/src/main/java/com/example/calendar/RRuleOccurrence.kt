package com.example.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Проверка, попадает ли [candidate] в простые RRULE (DAILY/WEEKLY/MONTHLY/YEARLY).
 * Нужна как fallback, когда OEM не развернул [android.provider.CalendarContract.Instances].
 */
object RRuleOccurrence {

    fun occursOn(
        rrule: String?,
        seriesStart: LocalDate,
        candidate: LocalDate,
        exdates: Set<LocalDate> = emptySet(),
    ): Boolean {
        if (candidate.isBefore(seriesStart)) return false
        if (candidate in exdates) return false
        val raw = rrule?.trim()?.removePrefix("RRULE:")?.trim().orEmpty()
        if (raw.isEmpty()) return candidate == seriesStart
        val parts = parseParts(raw)
        val freq = parts["FREQ"]?.uppercase(Locale.ROOT) ?: return false
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val until = parseUntilDate(parts["UNTIL"])
        if (until != null && candidate.isAfter(until)) return false
        val count = parts["COUNT"]?.toIntOrNull()
        if (count != null && count <= 0) return false

        val matches = when (freq) {
            "DAILY" -> occursDaily(seriesStart, candidate, interval)
            "WEEKLY" -> occursWeekly(parts, seriesStart, candidate, interval)
            "MONTHLY" -> occursMonthly(seriesStart, candidate, interval)
            "YEARLY" -> occursYearly(seriesStart, candidate, interval)
            else -> false
        }
        if (!matches) return false
        if (count == null) return true
        return occurrenceIndex(freq, parts, seriesStart, candidate, interval) < count
    }

    private fun occursDaily(start: LocalDate, candidate: LocalDate, interval: Int): Boolean {
        val days = ChronoUnit.DAYS.between(start, candidate)
        return days >= 0 && days % interval == 0L
    }

    private fun occursWeekly(
        parts: Map<String, String>,
        start: LocalDate,
        candidate: LocalDate,
        interval: Int,
    ): Boolean {
        val byDays = parseByDays(parts["BYDAY"]).ifEmpty {
            setOf(start.dayOfWeek)
        }
        if (candidate.dayOfWeek !in byDays) return false
        val weeks = ChronoUnit.WEEKS.between(start, candidate)
        return weeks >= 0 && weeks % interval == 0L
    }

    private fun occursMonthly(start: LocalDate, candidate: LocalDate, interval: Int): Boolean {
        if (candidate.dayOfMonth != start.dayOfMonth) {
            // Короткий месяц: 31-е → последний день, если старт был 31-го.
            if (start.dayOfMonth <= candidate.lengthOfMonth()) return false
            if (candidate.dayOfMonth != candidate.lengthOfMonth()) return false
        }
        val months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), candidate.withDayOfMonth(1))
        return months >= 0 && months % interval == 0L
    }

    private fun occursYearly(start: LocalDate, candidate: LocalDate, interval: Int): Boolean {
        if (candidate.month != start.month) return false
        val dayOk = candidate.dayOfMonth == start.dayOfMonth ||
            (start.monthValue == 2 && start.dayOfMonth == 29 &&
                candidate.dayOfMonth == candidate.lengthOfMonth())
        if (!dayOk) return false
        val years = candidate.year - start.year
        return years >= 0 && years % interval == 0
    }

    private fun occurrenceIndex(
        freq: String,
        parts: Map<String, String>,
        start: LocalDate,
        candidate: LocalDate,
        interval: Int,
    ): Int {
        var index = 0
        var cursor = start
        var guard = 0
        while (!cursor.isAfter(candidate) && guard < 4000) {
            if (occursOnWithoutCount(freq, parts, start, cursor, interval)) {
                if (cursor == candidate) return index
                index++
            }
            cursor = cursor.plusDays(1)
            guard++
        }
        return Int.MAX_VALUE
    }

    private fun occursOnWithoutCount(
        freq: String,
        parts: Map<String, String>,
        start: LocalDate,
        candidate: LocalDate,
        interval: Int,
    ): Boolean = when (freq) {
        "DAILY" -> occursDaily(start, candidate, interval)
        "WEEKLY" -> occursWeekly(parts, start, candidate, interval)
        "MONTHLY" -> occursMonthly(start, candidate, interval)
        "YEARLY" -> occursYearly(start, candidate, interval)
        else -> false
    }

    private fun parseByDays(raw: String?): Set<DayOfWeek> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { token ->
                val code = token.trim().takeLast(2).uppercase(Locale.ROOT)
                when (code) {
                    "MO" -> DayOfWeek.MONDAY
                    "TU" -> DayOfWeek.TUESDAY
                    "WE" -> DayOfWeek.WEDNESDAY
                    "TH" -> DayOfWeek.THURSDAY
                    "FR" -> DayOfWeek.FRIDAY
                    "SA" -> DayOfWeek.SATURDAY
                    "SU" -> DayOfWeek.SUNDAY
                    else -> null
                }
            }
            .toSet()
    }

    private fun parseUntilDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.take(8)
        if (digits.length < 8) return null
        return runCatching {
            LocalDate.of(
                digits.substring(0, 4).toInt(),
                digits.substring(4, 6).toInt(),
                digits.substring(6, 8).toInt(),
            )
        }.getOrNull()
    }

    private fun parseParts(rrule: String): Map<String, String> {
        return rrule.split(";").mapNotNull { token ->
            val t = token.trim()
            if (t.isEmpty()) return@mapNotNull null
            val eq = t.indexOf('=')
            if (eq <= 0 || eq >= t.length - 1) return@mapNotNull null
            t.substring(0, eq).trim().uppercase(Locale.ROOT) to t.substring(eq + 1).trim()
        }.toMap()
    }
}
