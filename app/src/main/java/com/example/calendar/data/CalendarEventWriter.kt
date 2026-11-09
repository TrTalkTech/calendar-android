package com.example.calendar.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

data class WritableCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String?,
    val accountType: String?,
    val color: Int?,
    val isPrimary: Boolean,
    val accessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
    val ownerAccount: String? = null,
    val syncEvents: Boolean = true,
) {
    val canModifyEvents: Boolean
        get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
}

/** Данные события для экрана просмотра / редактирования. */
data class EditableCalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String,
    val notes: String,
    val url: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /**
     * [CalendarContract.Events.EVENT_TIMEZONE] при загрузке (для не all-day — зона интерпретации [start]/[end]).
     * При сохранении нужно передавать в [updateEvent] тот же идентификатор, иначе DTSTART/DTEND «уедут».
     */
    val eventTimeZone: ZoneId,
    /** [android.provider.CalendarContract.Events.RRULE], null/пусто — без повтора. */
    val recurrenceRule: String? = null,
    /** Приглашённые участники (e-mail), без организатора. */
    val attendees: List<EventInvitee> = emptyList(),
    /** Напоминания: минуты до начала события (0 — в момент начала). */
    val reminderMinutesBefore: List<Int> = emptyList(),
    val selfAttendeeStatus: Int = EventRsvp.STATUS_NONE,
    val isOrganizer: Boolean = true,
    val hasAttendeeData: Boolean = false,
    val organizer: String? = null,
    val ownerAccount: String? = null,
) {
    val canRespondToInvite: Boolean
        get() = EventRsvp.canRespond(
            isOrganizer = isOrganizer,
            organizerEmail = organizer,
            ownerAccount = ownerAccount,
            selfAttendeeStatus = selfAttendeeStatus,
            hasAttendeeData = hasAttendeeData,
        )
}

class CalendarEventWriter(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "CalendarEventWriter"

        /** Колонка [android.provider.CalendarContract.EventsColumns.EVENT_TYPE] — «eventType». */
        private const val COLUMN_EVENT_TYPE = "eventType"

        /** [CalendarContract.Events.TYPE_EXCEPTION] — исключение из ряда. */
        private const val EVENT_TYPE_EXCEPTION = 1

        /** [CalendarContract.Events.STATUS_CANCELLED] — отменённый экземпляр. */
        private const val EVENT_STATUS_CANCELLED = 2
    }

    fun queryVisibleCalendars(): List<WritableCalendar> = try {
        queryVisibleCalendarsUnchecked()
    } catch (e: Exception) {
        Log.e(TAG, "queryVisibleCalendars failed", e)
        emptyList()
    }

    private fun queryVisibleCalendarsUnchecked(): List<WritableCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        // Все читаемые календари на устройстве: на телефоне Google часто скрывает их через VISIBLE,
        // но события уже синхронизированы и должны попадать в список и фильтр.
        // На телефонах календарь «Дни рождения» иногда имеет только CAL_ACCESS_FREEBUSY.
        val selection = "${CalendarContract.Calendars.DELETED} = ? AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selArgs = arrayOf(
            "0",
            CalendarContract.Calendars.CAL_ACCESS_FREEBUSY.toString(),
        )
        val sortOrder =
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"

        val result = mutableListOf<WritableCalendar>()
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selArgs,
            sortOrder,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            val primaryIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            val accessIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
            val syncIdx = cursor.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)
            while (cursor.moveToNext()) {
                result.add(
                    readWritableCalendar(
                        cursor,
                        idIdx,
                        nameIdx,
                        accountIdx,
                        typeIdx,
                        colorIdx,
                        primaryIdx,
                        accessIdx,
                        ownerIdx,
                        syncIdx,
                    ),
                )
            }
        }
        return result
    }

    private fun readWritableCalendar(
        cursor: Cursor,
        idIdx: Int,
        nameIdx: Int,
        accountIdx: Int,
        typeIdx: Int,
        colorIdx: Int,
        primaryIdx: Int,
        accessIdx: Int,
        ownerIdx: Int,
        syncIdx: Int,
    ): WritableCalendar =
        WritableCalendar(
            id = cursor.getLong(idIdx),
            displayName = cursor.getString(nameIdx).orEmpty(),
            accountName = cursor.getStringOrNull(accountIdx),
            accountType = cursor.getStringOrNull(typeIdx),
            color = cursor.getIntOrNull(colorIdx),
            isPrimary = cursor.getInt(primaryIdx) == 1,
            accessLevel = if (accessIdx >= 0) {
                cursor.getIntOrNull(accessIdx) ?: CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
            } else {
                CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
            },
            ownerAccount = if (ownerIdx >= 0) cursor.getStringOrNull(ownerIdx) else null,
            syncEvents = syncIdx < 0 || cursor.getIntOrNull(syncIdx) != 0,
        )

    /**
     * Вставляет событие в выбранный календарь. Требуется [android.Manifest.permission.WRITE_CALENDAR].
     */
    fun insertEvent(
        calendarId: Long,
        title: String,
        location: String?,
        description: String?,
        url: String?,
        allDay: Boolean,
        start: LocalDateTime,
        end: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        recurrenceRule: String? = null,
        attendees: List<EventInvitee> = emptyList(),
        reminderMinutesBefore: List<Int> = emptyList(),
        availability: Int = CalendarContract.Events.AVAILABILITY_BUSY,
    ): Uri? {
        val resolvedTitle = title.trim().ifBlank { "Событие" }
        val fullDescription = buildString {
            if (!url.isNullOrBlank()) {
                append(url.trim())
                append("\n\n")
            }
            if (!description.isNullOrBlank()) append(description.trim())
        }

        return try {
            ensureCalendarSyncEnabled(calendarId)
            val target = loadCalendarSyncTarget(calendarId)
            val timeZoneId = if (allDay) "UTC" else zone.id
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, resolvedTitle)
                if (!location.isNullOrBlank()) {
                    put(CalendarContract.Events.EVENT_LOCATION, location.trim())
                }
                if (fullDescription.isNotBlank()) {
                    put(CalendarContract.Events.DESCRIPTION, fullDescription)
                }
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
                put(CalendarContract.Events.EVENT_END_TIMEZONE, timeZoneId)
                putNull(CalendarContract.Events.DURATION)
                if (allDay) {
                    val dayStart = start.toLocalDate()
                    val dayEndExclusive = end.toLocalDate().plusDays(1)
                    put(CalendarContract.Events.DTSTART, utcMidnightMillis(dayStart))
                    put(CalendarContract.Events.DTEND, utcMidnightMillis(dayEndExclusive))
                } else {
                    put(CalendarContract.Events.DTSTART, start.atZone(zone).toInstant().toEpochMilli())
                    put(CalendarContract.Events.DTEND, end.atZone(zone).toInstant().toEpochMilli())
                }
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                put(CalendarContract.Events.AVAILABILITY, availability)
                put(CalendarContract.Events.HAS_ATTENDEE_DATA, if (attendees.isEmpty()) 0 else 1)
                put(CalendarContract.Events.GUESTS_CAN_MODIFY, 0)
                put(CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS, 0)
                put(CalendarContract.Events.GUESTS_CAN_SEE_GUESTS, 0)
                val organizer = target?.organizer
                if (!organizer.isNullOrBlank()) {
                    put(CalendarContract.Events.ORGANIZER, organizer)
                    put(CalendarContract.Events.IS_ORGANIZER, 1)
                }
                recurrenceRule?.let(::normalizeRecurrenceRule)?.let { r ->
                    put(CalendarContract.Events.RRULE, r)
                }
            }
            contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.also { uri ->
                val eventId = ContentUris.parseId(uri)
                syncAttendees(eventId, attendees)
                syncReminders(eventId, reminderMinutesBefore)
                requestSyncForEvent(uri, calendarId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "insertEvent failed", e)
            null
        }
    }

    fun loadEventForEdit(eventId: Long, zone: ZoneId = ZoneId.systemDefault()): EditableCalendarEvent? {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.IS_ORGANIZER,
            CalendarContract.Events.HAS_ATTENDEE_DATA,
            CalendarContract.Events.ORGANIZER,
        )
        return contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            fun col(name: String): Int = c.getColumnIndex(name)

            val calIdx = col(CalendarContract.Events.CALENDAR_ID)
            if (calIdx < 0) return@use null
            val calId = c.getLongOrNull(calIdx) ?: return@use null

            val titleIdx = col(CalendarContract.Events.TITLE)
            val title = if (titleIdx >= 0) c.getStringOrNull(titleIdx).orEmpty() else ""

            val locIdx = col(CalendarContract.Events.EVENT_LOCATION)
            val location = if (locIdx >= 0) c.getStringOrNull(locIdx).orEmpty() else ""

            val descIdx = col(CalendarContract.Events.DESCRIPTION)
            val rawDesc = if (descIdx >= 0) c.getStringOrNull(descIdx).orEmpty() else ""
            val (url, notes) = splitDescriptionUrl(rawDesc)

            val allDayIdx = col(CalendarContract.Events.ALL_DAY)
            val allDay = allDayIdx >= 0 && c.getIntOrNull(allDayIdx) == 1

            val startIdx = col(CalendarContract.Events.DTSTART)
            if (startIdx < 0) return@use null
            val startMs = c.getLongOrNull(startIdx) ?: return@use null

            val endMs = c.resolveStoredEventEndMillis(startMs, allDay)

            val tzIdx = col(CalendarContract.Events.EVENT_TIMEZONE)
            val tzId = (if (tzIdx >= 0) c.getStringOrNull(tzIdx) else null) ?: zone.id
            val eventZone = runCatching { ZoneId.of(tzId) }.getOrDefault(zone)
            val start: LocalDateTime
            val end: LocalDateTime
            if (allDay) {
                val sDay = java.time.Instant.ofEpochMilli(startMs).atZone(ZoneId.of("UTC")).toLocalDate()
                val eDayExclusive = java.time.Instant.ofEpochMilli(endMs).atZone(ZoneId.of("UTC")).toLocalDate()
                val lastDay = eDayExclusive.minusDays(1)
                start = sDay.atStartOfDay()
                end = lastDay.atTime(23, 59)
            } else {
                start = java.time.Instant.ofEpochMilli(startMs).atZone(eventZone).toLocalDateTime()
                end = java.time.Instant.ofEpochMilli(endMs).atZone(eventZone).toLocalDateTime()
            }
            val rruleIdx = col(CalendarContract.Events.RRULE)
            val rrule = if (rruleIdx >= 0) {
                c.getStringOrNull(rruleIdx)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val selfStatusIdx = col(CalendarContract.Events.SELF_ATTENDEE_STATUS)
            val selfAttendeeStatus = if (selfStatusIdx >= 0) {
                c.getIntOrNull(selfStatusIdx) ?: EventRsvp.STATUS_NONE
            } else {
                EventRsvp.STATUS_NONE
            }
            val organizerIdx = col(CalendarContract.Events.ORGANIZER)
            val organizer = if (organizerIdx >= 0) {
                c.getStringOrNull(organizerIdx)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val ownerAccount = loadCalendarSyncTarget(calId)?.organizer
            val isOrganizer = readIsOrganizer(c, col(CalendarContract.Events.IS_ORGANIZER), organizer, ownerAccount)
            val hasAttendeeIdx = col(CalendarContract.Events.HAS_ATTENDEE_DATA)
            val hasAttendeeData = hasAttendeeIdx >= 0 && c.getIntOrNull(hasAttendeeIdx) == 1

            EditableCalendarEvent(
                eventId = eventId,
                calendarId = calId,
                title = title,
                location = location,
                notes = notes,
                url = url,
                allDay = allDay,
                start = start,
                end = end,
                eventTimeZone = eventZone,
                recurrenceRule = rrule,
                attendees = loadAttendees(eventId),
                reminderMinutesBefore = loadReminderMinutesBefore(eventId),
                selfAttendeeStatus = selfAttendeeStatus,
                isOrganizer = isOrganizer,
                hasAttendeeData = hasAttendeeData,
                organizer = organizer,
                ownerAccount = ownerAccount,
            )
        }
    }

    /**
     * Обновляет событие. Требуется [android.Manifest.permission.WRITE_CALENDAR].
     *
     * @param updateRecurrence если false, колонка [CalendarContract.Events.RRULE] не попадает в [ContentValues]
     * (повтор в календаре не меняется). Иначе задаётся [recurrenceRule] или снимается повтор через [putNull].
     * Для повторяющихся событий запись RRULE при каждом сохранении без намерения пользователя часто ломает серию.
     */
    fun updateEvent(
        eventId: Long,
        calendarId: Long,
        title: String,
        location: String?,
        description: String?,
        url: String?,
        allDay: Boolean,
        start: LocalDateTime,
        end: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        recurrenceRule: String? = null,
        updateRecurrence: Boolean = false,
        attendees: List<EventInvitee> = emptyList(),
        reminderMinutesBefore: List<Int> = emptyList(),
    ): Int {
        val resolvedTitle = title.trim().ifBlank { "Событие" }
        val fullDescription = buildString {
            if (!url.isNullOrBlank()) {
                append(url.trim())
                append("\n\n")
            }
            if (!description.isNullOrBlank()) append(description.trim())
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return try {
            val existingCalendarId = readEventCalendarId(eventId)
            val values = ContentValues().apply {
                if (existingCalendarId == null || existingCalendarId != calendarId) {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                }
                put(CalendarContract.Events.TITLE, resolvedTitle)
                val loc = location?.trim().orEmpty()
                if (loc.isNotEmpty()) {
                    put(CalendarContract.Events.EVENT_LOCATION, loc)
                } else {
                    putNull(CalendarContract.Events.EVENT_LOCATION)
                }
                if (fullDescription.isNotBlank()) {
                    put(CalendarContract.Events.DESCRIPTION, fullDescription)
                } else {
                    putNull(CalendarContract.Events.DESCRIPTION)
                }
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                val timeZoneId = if (allDay) "UTC" else zone.id
                put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
                put(CalendarContract.Events.EVENT_END_TIMEZONE, timeZoneId)
                // Иначе у ряда событий (в т.ч. из Google) остаётся DURATION при заданном DTEND — провайдер падает.
                putNull(CalendarContract.Events.DURATION)
                if (allDay) {
                    val dayStart = start.toLocalDate()
                    val dayEndExclusive = end.toLocalDate().plusDays(1)
                    put(CalendarContract.Events.DTSTART, utcMidnightMillis(dayStart))
                    put(CalendarContract.Events.DTEND, utcMidnightMillis(dayEndExclusive))
                } else {
                    put(CalendarContract.Events.DTSTART, start.atZone(zone).toInstant().toEpochMilli())
                    put(CalendarContract.Events.DTEND, end.atZone(zone).toInstant().toEpochMilli())
                }
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                if (updateRecurrence) {
                    val r = normalizeRecurrenceRule(recurrenceRule)
                    if (r == null) {
                        putNull(CalendarContract.Events.RRULE)
                    } else {
                        put(CalendarContract.Events.RRULE, r)
                    }
                }
            }
            val updated = contentResolver.update(uri, values, null, null)
            if (updated >= 0) {
                syncAttendees(eventId, attendees)
                syncReminders(eventId, reminderMinutesBefore)
                requestSyncForEvent(uri, calendarId)
            }
            updated
        } catch (e: Exception) {
            Log.e(TAG, "updateEvent failed eventId=$eventId", e)
            -1
        }
    }

    /**
     * Удаляет событие целиком (все повторы, если это мастер-событие с RRULE).
     */
    fun deleteEntireEvent(eventId: Long): Boolean {
        return try {
            val calendarId = readEventCalendarId(eventId)
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val deleted = contentResolver.delete(uri, null, null) > 0
            if (deleted && calendarId != null) {
                requestSyncForEvent(uri, calendarId)
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "deleteEntireEvent failed eventId=$eventId", e)
            false
        }
    }

    fun requestSyncForEventId(eventId: Long) {
        val calendarId = readEventCalendarId(eventId) ?: return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        requestSyncForEvent(uri, calendarId)
    }

    /**
     * Ответ на приглашение: только строка себя в [CalendarContract.Attendees].
     * Колонку Events.SELF_ATTENDEE_STATUS провайдер сам выводит из неё и обновлять запрещает.
     */
    fun updateSelfAttendeeStatus(eventId: Long, answer: EventRsvpAnswer): Boolean {
        val status = EventRsvp.toSelfAttendeeStatus(answer)
        return try {
            val calendarId = readEventCalendarId(eventId) ?: return false
            val target = loadCalendarSyncTarget(calendarId)
            val organizer = readEventOrganizer(eventId)
            val selfEmails = listOfNotNull(target?.organizer, target?.accountName)
            if (!updateSelfAttendeeRow(eventId, selfEmails, organizer, status)) return false
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            requestSyncForEvent(uri, calendarId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateSelfAttendeeStatus failed eventId=$eventId", e)
            false
        }
    }

    /**
     * Убирает один экземпляр повторения: строка-исключение (eventType = exception, status = cancelled).
     */
    fun deleteRecurringInstance(
        originalEventId: Long,
        calendarId: Long,
        instanceBeginMillis: Long,
        instanceEndMillis: Long,
        allDay: Boolean,
        zone: ZoneId,
        titleForRow: String,
    ): Boolean {
        val safeEnd = when {
            instanceEndMillis > instanceBeginMillis -> instanceEndMillis
            allDay -> instanceBeginMillis + 86400000L
            else -> instanceBeginMillis + 3600000L
        }
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.DTSTART, instanceBeginMillis)
                put(CalendarContract.Events.DTEND, safeEnd)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)
                put(CalendarContract.Events.TITLE, titleForRow.trim().ifBlank { " " })
                put(COLUMN_EVENT_TYPE, EVENT_TYPE_EXCEPTION)
                put(CalendarContract.Events.ORIGINAL_ID, originalEventId)
                put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceBeginMillis)
                put(CalendarContract.Events.STATUS, EVENT_STATUS_CANCELLED)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            }
            contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
        } catch (e: Exception) {
            Log.e(TAG, "deleteRecurringInstance failed originalId=$originalEventId", e)
            false
        }
    }

    /**
     * Конец события в БД: у Google часто [CalendarContract.Events.DTEND] = null, задан только [CalendarContract.Events.DURATION].
     */
    private fun Cursor.resolveStoredEventEndMillis(dtStart: Long, allDay: Boolean): Long {
        val dtEndIdx = getColumnIndex(CalendarContract.Events.DTEND)
        if (dtEndIdx >= 0) {
            val raw = getLongOrNull(dtEndIdx)
            if (raw != null && raw > dtStart) return raw
        }
        val durIdx = getColumnIndex(CalendarContract.Events.DURATION)
        if (durIdx >= 0) {
            val dur = getStringOrNull(durIdx)
            if (!dur.isNullOrBlank()) {
                val parsed = runCatching { Duration.parse(dur).toMillis() }.getOrElse { 0L }
                if (parsed > 0L) return dtStart + parsed
            }
        }
        return if (allDay) dtStart + 86400000L else dtStart + 3600000L
    }

    private fun readEventCalendarId(eventId: Long): Long? {
        return contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.CALENDAR_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            if (idx < 0) null else cursor.getLongOrNull(idx)
        }
    }

    private fun readIsOrganizer(
        cursor: Cursor,
        index: Int,
        organizer: String?,
        ownerAccount: String?,
    ): Boolean {
        if (index >= 0) {
            val asInt = cursor.getIntOrNull(index)
            if (asInt != null) return asInt == 1
            val asText = cursor.getStringOrNull(index)?.trim()
            if (!asText.isNullOrEmpty()) {
                return asText == "1" || asText.equals("true", ignoreCase = true)
            }
        }
        val org = organizer?.trim()?.lowercase().orEmpty()
        val owner = ownerAccount?.trim()?.lowercase().orEmpty()
        if (org.isNotEmpty() && owner.isNotEmpty()) return org == owner
        return true
    }

    private fun readEventOrganizer(eventId: Long): String? {
        return contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.ORGANIZER),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex(CalendarContract.Events.ORGANIZER)
            if (idx < 0) null else cursor.getStringOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private data class AttendeeStatusRow(
        val id: Long,
        val email: String,
        val relationship: Int?,
    )

    private fun updateSelfAttendeeRow(
        eventId: Long,
        ownerEmails: List<String>,
        organizer: String?,
        status: Int,
    ): Boolean {
        val wanted = ownerEmails.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val organizerKey = organizer?.trim()?.lowercase().orEmpty()
        val rows = mutableListOf<AttendeeStatusRow>()
        contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(
                CalendarContract.Attendees._ID,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
            ),
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Attendees._ID)
            val emailIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL)
            val relIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP)
            if (idIdx < 0) return@use
            while (cursor.moveToNext()) {
                val id = cursor.getLongOrNull(idIdx) ?: continue
                val email = if (emailIdx >= 0) {
                    cursor.getStringOrNull(emailIdx)?.trim()?.lowercase().orEmpty()
                } else {
                    ""
                }
                val relationship = if (relIdx >= 0) cursor.getIntOrNull(relIdx) else null
                rows += AttendeeStatusRow(id, email, relationship)
            }
        }
        val self = rows.firstOrNull { it.email.isNotEmpty() && it.email in wanted }
            ?: rows.firstOrNull {
                it.relationship != CalendarContract.Attendees.RELATIONSHIP_ORGANIZER &&
                    it.email.isNotEmpty() &&
                    it.email != organizerKey
            }
            ?: rows.firstOrNull { it.relationship != CalendarContract.Attendees.RELATIONSHIP_ORGANIZER }
        val attendeeId = self?.id
        if (attendeeId != null) {
            val attendeeUri = ContentUris.withAppendedId(
                CalendarContract.Attendees.CONTENT_URI,
                attendeeId,
            )
            val updated = contentResolver.update(
                attendeeUri,
                ContentValues().apply {
                    put(CalendarContract.Attendees.ATTENDEE_STATUS, status)
                },
                null,
                null,
            )
            return updated > 0
        }
        val email = wanted.firstOrNull() ?: return false
        val inserted = contentResolver.insert(
            CalendarContract.Attendees.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Attendees.EVENT_ID, eventId)
                put(CalendarContract.Attendees.ATTENDEE_EMAIL, email)
                put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_REQUIRED)
                put(
                    CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                    CalendarContract.Attendees.RELATIONSHIP_ATTENDEE,
                )
                put(CalendarContract.Attendees.ATTENDEE_STATUS, status)
            },
        )
        return inserted != null
    }

    private fun loadAttendees(eventId: Long): List<EventInvitee> {
        val projection = arrayOf(
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_NAME,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
        )
        val selection = "${CalendarContract.Attendees.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        val result = LinkedHashMap<String, EventInvitee>()
        return try {
            contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val emailIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_NAME)
                val relIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP)
                while (cursor.moveToNext()) {
                    if (relIdx >= 0) {
                        val relationship = cursor.getIntOrNull(relIdx)
                        if (relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER) continue
                    }
                    val email = if (emailIdx >= 0) {
                        cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                    } else {
                        ""
                    }
                    if (email.isEmpty()) continue
                    val name = if (nameIdx >= 0) {
                        cursor.getStringOrNull(nameIdx)?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    val key = email.lowercase()
                    val existing = result[key]
                    if (existing == null || existing.name.isNullOrBlank()) {
                        result[key] = EventInvitee(email = email, name = name)
                    }
                }
                result.values.toList()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "loadAttendees failed eventId=$eventId", e)
            emptyList()
        }
    }

    private fun syncAttendees(eventId: Long, attendees: List<EventInvitee>) {
        try {
            contentResolver.delete(
                CalendarContract.Attendees.CONTENT_URI,
                "${CalendarContract.Attendees.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
            for (attendee in attendees) {
                val values = ContentValues().apply {
                    put(CalendarContract.Attendees.EVENT_ID, eventId)
                    put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendee.email)
                    attendee.name?.let { put(CalendarContract.Attendees.ATTENDEE_NAME, it) }
                    put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_REQUIRED)
                    put(
                        CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                        CalendarContract.Attendees.RELATIONSHIP_ATTENDEE,
                    )
                    put(
                        CalendarContract.Attendees.ATTENDEE_STATUS,
                        CalendarContract.Attendees.ATTENDEE_STATUS_INVITED,
                    )
                }
                contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAttendees failed eventId=$eventId", e)
        }
    }

    private fun loadReminderMinutesBefore(eventId: Long): List<Int> {
        val projection = arrayOf(
            CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD,
        )
        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        val minutes = LinkedHashSet<Int>()
        return try {
            contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val minutesIdx = cursor.getColumnIndex(CalendarContract.Reminders.MINUTES)
                val methodIdx = cursor.getColumnIndex(CalendarContract.Reminders.METHOD)
                while (cursor.moveToNext()) {
                    if (methodIdx >= 0) {
                        val method = cursor.getIntOrNull(methodIdx)
                        if (method != null &&
                            method != CalendarContract.Reminders.METHOD_ALERT &&
                            method != CalendarContract.Reminders.METHOD_DEFAULT
                        ) {
                            continue
                        }
                    }
                    val value = if (minutesIdx >= 0) cursor.getIntOrNull(minutesIdx) else null
                    if (value != null && value >= 0) {
                        minutes.add(value)
                    }
                }
                minutes.sortedDescending().take(2)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "loadReminderMinutesBefore failed eventId=$eventId", e)
            emptyList()
        }
    }

    private fun syncReminders(eventId: Long, minutesBefore: List<Int>) {
        try {
            contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
            for (minutes in minutesBefore.distinct().sortedDescending()) {
                val values = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncReminders failed eventId=$eventId", e)
        }
    }

    private fun splitDescriptionUrl(raw: String): Pair<String, String> {
        if (raw.isBlank()) return "" to ""
        val parts = raw.split("\n\n", limit = 2)
        if (parts.size == 2) {
            val head = parts[0].trim()
            if (head.startsWith("http://") || head.startsWith("https://")) {
                return head to parts[1].trim()
            }
        }
        return "" to raw.trim()
    }

    private fun utcMidnightMillis(date: LocalDate): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun normalizeRecurrenceRule(rrule: String?): String? =
        rrule?.trim()?.removePrefix("RRULE:")?.trim()?.takeIf { it.isNotEmpty() }

    private fun ensureCalendarSyncEnabled(calendarId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        runCatching {
            contentResolver.update(
                uri,
                ContentValues().apply {
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                },
                null,
                null,
            )
        }
    }

    private data class CalendarSyncTarget(
        val accountName: String,
        val accountType: String,
        val organizer: String,
    )

    private fun loadCalendarSyncTarget(calendarId: Long): CalendarSyncTarget? {
        return contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
            ),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
            val accountName = if (nameIdx >= 0) cursor.getStringOrNull(nameIdx) else null
            val accountType = if (typeIdx >= 0) cursor.getStringOrNull(typeIdx) else null
            val owner = if (ownerIdx >= 0) cursor.getStringOrNull(ownerIdx) else null
            if (accountName.isNullOrBlank() || accountType.isNullOrBlank()) return@use null
            CalendarSyncTarget(
                accountName = accountName,
                accountType = accountType,
                organizer = owner?.takeIf { it.isNotBlank() } ?: accountName,
            )
        }
    }

    private fun requestSyncForEvent(uri: Uri, calendarId: Long) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) {
                contentResolver.notifyChange(uri, null, ContentResolver.NOTIFY_SYNC_TO_NETWORK)
            } else {
                @Suppress("DEPRECATION")
                contentResolver.notifyChange(uri, null, true)
            }
        }
        requestSyncForCalendar(calendarId)
    }

    private fun requestSyncForCalendar(calendarId: Long) {
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
            ),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            if (nameIdx < 0 || typeIdx < 0) return
            val accountName = cursor.getStringOrNull(nameIdx) ?: return
            val accountType = cursor.getStringOrNull(typeIdx) ?: return
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            runCatching {
                ContentResolver.requestSync(
                    Account(accountName, accountType),
                    CalendarContract.AUTHORITY,
                    extras,
                )
            }
        }
    }
}
