package com.example.calendar.data

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class ProductionCalendarRepository(context: Context) {

    private val appContext = context.applicationContext

    fun daysForYears(years: Set<Int>): Map<LocalDate, ProductionDay> {
        if (years.isEmpty()) return emptyMap()
        val result = LinkedHashMap<LocalDate, ProductionDay>()
        for (year in years.sorted()) {
            val json = readCached(year) ?: readAsset(year) ?: fetchYear(year)?.also { cache(year, it) }
            if (json != null) {
                result.putAll(parseYear(json, year))
            }
        }
        return result
    }

    private fun readAsset(year: Int): String? = runCatching {
        appContext.assets.open("production_calendar_$year.json").bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun cacheFile(year: Int) = appContext.filesDir.resolve(CACHE_DIR).resolve("ru-$year.json")

    private fun readCached(year: Int): String? = runCatching {
        val file = cacheFile(year)
        if (file.isFile) file.readText() else null
    }.getOrNull()

    private fun cache(year: Int, json: String) {
        runCatching {
            val file = cacheFile(year)
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    private fun fetchYear(year: Int): String? = runCatching {
        val connection = (URL("$REMOTE_BASE/$year/calendar.json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Calendar2.0")
            connect()
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.contains("\"year\"")) body else null
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "fetch $year failed", it) }.getOrNull()

    companion object {
        private const val TAG = "ProductionCalendar"
        private const val CACHE_DIR = "production-calendar"
        private const val REMOTE_BASE = "https://www.xmlcalendar.ru/data/ru"
    }
}

private val YearField = Regex("\"year\"\\s*:\\s*(\\d+)")
private val MonthDaysField = Regex("\"month\"\\s*:\\s*(\\d+)\\s*,\\s*\"days\"\\s*:\\s*\"([^\"]*)\"")

internal fun parseYear(json: String, fallbackYear: Int): Map<LocalDate, ProductionDay> {
    val year = YearField.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: fallbackYear
    val result = LinkedHashMap<LocalDate, ProductionDay>()
    MonthDaysField.findAll(json).forEach { match ->
        val month = match.groupValues[1].toIntOrNull() ?: return@forEach
        if (month !in 1..12) return@forEach
        val days = match.groupValues[2]
        if (days.isBlank()) return@forEach
        days.split(',').forEach { token ->
            val raw = token.trim()
            if (raw.isEmpty()) return@forEach
            val extraRest = raw.endsWith('+')
            val shortened = raw.endsWith('*')
            val day = raw.trimEnd('+', '*').toIntOrNull() ?: return@forEach
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return@forEach
            val kind = ProductionHolidayNames.kindForListedDay(date, extraRest, shortened)
            result[date] = ProductionDay(
                date = date,
                kind = kind,
                title = ProductionHolidayNames.title(date, kind),
            )
        }
    }
    return result
}
