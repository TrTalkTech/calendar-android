package com.example.calendar

import android.content.Context
import com.example.calendar.R
import java.util.Locale

/**
 * Краткое описание повтора для UI по [android.provider.CalendarContract.Events.RRULE].
 * Типичные правила Google/Exchange; сложные случаи — [R.string.repeat_custom].
 */
object RRuleDisplayFormatter {

    private val dayTokenToShortRu = mapOf(
        "MO" to "пн",
        "TU" to "вт",
        "WE" to "ср",
        "TH" to "чт",
        "FR" to "пт",
        "SA" to "сб",
        "SU" to "вс",
    )

    private val byDaySingleRegex =
        Regex("^([+-]?\\d*)(MO|TU|WE|TH|FR|SA|SU)$", RegexOption.IGNORE_CASE)

    fun format(context: Context, recurrenceRule: String?): String {
        val raw = recurrenceRule?.trim()?.takeIf { it.isNotEmpty() }
            ?: return context.getString(R.string.value_never)
        val parts = parseParts(raw)
        val freq = parts["FREQ"]?.uppercase(Locale.ROOT)
            ?: return context.getString(R.string.repeat_custom)
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val byDayRaw = parts["BYDAY"]
        val byDays = byDayRaw?.let { expandByDayTokens(it) }.orEmpty()

        return when (freq) {
            "DAILY" -> when (interval) {
                1 -> context.getString(R.string.repeat_every_day)
                else -> context.getString(R.string.repeat_every_n_days, interval)
            }
            "WEEKLY" -> when {
                byDays.isNotEmpty() && interval == 1 ->
                    context.getString(R.string.repeat_weekly_on_days, byDays.joinToString(", "))
                byDays.isNotEmpty() && interval > 1 ->
                    context.getString(
                        R.string.repeat_every_n_weeks_on_days,
                        interval,
                        byDays.joinToString(", "),
                    )
                interval == 1 -> context.getString(R.string.repeat_every_week)
                else -> context.getString(R.string.repeat_every_n_weeks, interval)
            }
            "MONTHLY" -> when (interval) {
                1 -> context.getString(R.string.repeat_every_month)
                else -> context.getString(R.string.repeat_every_n_months, interval)
            }
            "YEARLY" -> when (interval) {
                1 -> context.getString(R.string.repeat_every_year)
                else -> context.getString(R.string.repeat_every_n_years, interval)
            }
            else -> context.getString(R.string.repeat_custom)
        }
    }

    private fun parseParts(rrule: String): Map<String, String> {
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

    private fun expandByDayTokens(byDayValue: String): List<String> {
        return byDayValue.split(",").mapNotNull { token ->
            val m = byDaySingleRegex.matchEntire(token.trim()) ?: return@mapNotNull null
            val code = m.groupValues[2].uppercase(Locale.ROOT)
            dayTokenToShortRu[code]
        }
    }
}
