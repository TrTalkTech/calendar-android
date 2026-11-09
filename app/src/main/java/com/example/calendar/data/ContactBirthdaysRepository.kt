package com.example.calendar.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.example.calendar.AppLocale
import com.example.calendar.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * Дни рождения из [ContactsContract]: на части устройств календарь «Дни рождения»
 * в провайдере пустой или отфильтрован, хотя в контактах даты есть.
 */
class ContactBirthdaysRepository(
    private val contentResolver: ContentResolver,
    private val context: Context,
) {
    companion object {
        val birthdayDotColorArgb: Int = 0xFFFF2D55.toInt()
        private const val SYNTHETIC_EVENT_ID_BASE = -1_000_000_000_000L
    }

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun queryBirthdaysForDay(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> {
        if (!hasReadContactsPermission()) return emptyList()
        val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return loadBirthdayRows()
            .filter { it.month == date.monthValue && it.day == date.dayOfMonth }
            .map { row ->
                CalendarEvent(
                    eventId = SYNTHETIC_EVENT_ID_BASE - row.contactId,
                    title = row.displayName,
                    beginMillis = begin,
                    endMillis = end,
                    allDay = true,
                    calendarId = null,
                    calendarName = context.getString(R.string.contact_birthday_calendar_label),
                    color = birthdayDotColorArgb,
                )
            }
    }

    fun searchPastBirthdays(
        query: String,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> {
        val needle = query.trim()
        if (needle.isEmpty() || !hasReadContactsPermission()) return emptyList()
        return loadBirthdayRows()
            .filter { it.displayName.contains(needle, ignoreCase = true) }
            .mapNotNull { row ->
                val date = mostRecentBirthdayOnOrBefore(row.month, row.day, today) ?: return@mapNotNull null
                val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
                CalendarEvent(
                    eventId = SYNTHETIC_EVENT_ID_BASE - row.contactId,
                    title = row.displayName,
                    beginMillis = begin,
                    endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    allDay = true,
                    calendarId = null,
                    calendarName = context.getString(R.string.contact_birthday_calendar_label),
                    color = birthdayDotColorArgb,
                )
            }
    }

    private fun mostRecentBirthdayOnOrBefore(month: Int, day: Int, today: LocalDate): LocalDate? {
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && !thisYear.isAfter(today)) return thisYear
        return runCatching { LocalDate.of(today.year - 1, month, day) }.getOrNull()
    }

    fun queryBirthdayDatesForMonthGrid(yearMonth: YearMonth): Set<LocalDate> {
        if (!hasReadContactsPermission()) return emptySet()
        val weekFields = WeekFields.of(AppLocale.display)
        val firstGridDay = yearMonth.atDay(1)
            .with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
        val gridEndInclusive = firstGridDay.plusDays(41)
        return queryBirthdayDatesBetween(firstGridDay, gridEndInclusive)
    }

    private fun queryBirthdayDatesBetween(start: LocalDate, endInclusive: LocalDate): Set<LocalDate> {
        val result = mutableSetOf<LocalDate>()
        for (row in loadBirthdayRows()) {
            var candidate = LocalDate.of(start.year, row.month, row.day)
            if (candidate.isBefore(start)) {
                candidate = candidate.plusYears(1)
            }
            while (!candidate.isAfter(endInclusive)) {
                result.add(candidate)
                candidate = candidate.plusYears(1)
            }
        }
        return result
    }

    private data class BirthdayRow(
        val contactId: Long,
        val displayName: String,
        val month: Int,
        val day: Int,
    )

    private fun loadBirthdayRows(): List<BirthdayRow> {
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.Data.DISPLAY_NAME,
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND " +
            "${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
        )
        val rows = mutableListOf<BirthdayRow>()
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val contactIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val dateIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
            if (contactIdx < 0 || dateIdx < 0) return@use
            while (cursor.moveToNext()) {
                val contactId = cursor.getLongOrNull(contactIdx) ?: continue
                val startDate = cursor.getStringOrNull(dateIdx) ?: continue
                val (month, day) = parseBirthdayMonthDay(startDate) ?: continue
                val name = if (nameIdx >= 0) {
                    cursor.getStringOrNull(nameIdx)?.trim().orEmpty()
                } else {
                    ""
                }
                rows.add(
                    BirthdayRow(
                        contactId = contactId,
                        displayName = name.ifBlank { context.getString(R.string.event_no_title) },
                        month = month,
                        day = day,
                    ),
                )
            }
        }
        return rows.distinctBy { "${it.contactId}-${it.month}-${it.day}" }
    }

    private fun parseBirthdayMonthDay(startDate: String): Pair<Int, Int>? {
        val trimmed = startDate.trim()
        val md = when {
            trimmed.startsWith("--") -> trimmed.removePrefix("--")
            trimmed.length == 5 && trimmed[2] == '-' -> trimmed
            trimmed.length >= 10 && trimmed[4] == '-' -> trimmed.substring(5)
            else -> return null
        }
        val parts = md.split('-')
        if (parts.size != 2) return null
        val month = parts[0].toIntOrNull() ?: return null
        val day = parts[1].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return month to day
    }
}

fun CalendarDayEventsQuery.withMergedContactBirthdays(
    contactBirthdays: List<CalendarEvent>,
): CalendarDayEventsQuery {
    if (contactBirthdays.isEmpty()) return this
    val extra = contactBirthdays.filter { contact ->
        !events.any { provider -> providerBirthdayMatchesContact(provider, contact.title) }
    }
    if (extra.isEmpty()) return this
    return copy(events = (events + extra).sortedBy { it.beginMillis })
}

fun mergeContactBirthdayDots(
    dots: Map<LocalDate, List<Int>>,
    birthdayDates: Set<LocalDate>,
): Map<LocalDate, List<Int>> {
    if (birthdayDates.isEmpty()) return dots
    val merged = dots.toMutableMap()
    for (date in birthdayDates) {
        val colors = merged[date]?.toMutableList() ?: mutableListOf()
        if (!colors.contains(ContactBirthdaysRepository.birthdayDotColorArgb)) {
            colors.add(ContactBirthdaysRepository.birthdayDotColorArgb)
            merged[date] = colors
        }
    }
    return merged
}

private fun providerBirthdayMatchesContact(event: CalendarEvent, contactName: String): Boolean {
    if (!event.allDay) return false
    val title = event.title.trim()
    if (title.equals(contactName, ignoreCase = true)) return true
    val lower = title.lowercase()
    val looksLikeBirthday = lower.contains("birthday") ||
        lower.contains("день рождения") ||
        lower.contains("birthdays") ||
        lower.contains("дни рождения")
    return looksLikeBirthday && title.contains(contactName, ignoreCase = true)
}
