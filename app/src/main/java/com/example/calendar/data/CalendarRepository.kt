package com.example.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.example.calendar.AppLocale
import com.example.calendar.R
import com.example.calendar.RRuleOccurrence
import com.example.calendar.ui.EventConferenceLinks
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/** Значение [CalendarContract.EventsColumns.STATUS_CANCELLED] (2) для SQL без зависимости от того, где объявлена константа. */
private const val CALENDAR_EVENT_STATUS_CANCELLED = 2

private val notCancelledSql =
    "(${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != $CALENDAR_EVENT_STATUS_CANCELLED)"

private val notDeclinedSql = EventRsvp.notDeclinedSql(CalendarContract.Events.SELF_ATTENDEE_STATUS)

private val visibleEventSql = "$notCancelledSql AND $notDeclinedSql"

private const val REPO_TAG = "CalendarRepository"

class CalendarRepository(
    private val contentResolver: ContentResolver,
    context: Context,
) {
    private val untitledLabel = context.getString(R.string.event_no_title)
    private val attachmentStore = EventAttachmentStore(context)

    /** Цвет точки в сетке, если у инстанса нет DISPLAY_COLOR. */
    private val fallbackDotColorArgb = 0xFF3478F6.toInt()

    fun queryEventsForDay(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        visibleCalendarIds: Set<Long>? = null,
        fallbackEvents: List<CalendarEvent> = emptyList(),
    ): CalendarDayEventsQuery {
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        if (visibleCalendarIds != null && visibleCalendarIds.isEmpty()) {
            val fallbackOnly = LinkedHashMap<String, CalendarEvent>()
            mergeFallbackEventsForDay(
                dayStartMillis = startMillis,
                dayEndMillis = endMillis,
                zone = zone,
                results = fallbackOnly,
                visibleCalendarIds = visibleCalendarIds,
                fallbackEvents = fallbackEvents,
            )
            return CalendarDayEventsQuery(
                events = attachJoinLinks(fallbackOnly.values.sortedBy { it.beginMillis }),
                providerEventIds = emptySet(),
            )
        }

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMillis)
            ContentUris.appendId(this, endMillis)
        }.build()

        val projection = instanceEventProjection()

        val sort = "${CalendarContract.Instances.BEGIN} ASC"

        val results = LinkedHashMap<String, CalendarEvent>()
        val calendarIdCache = mutableMapOf<Long, Long?>()

        contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
            while (cursor.moveToNext()) {
                var event = cursor.readEvent() ?: continue
                val resolvedCalId = event.calendarId
                    ?: resolveCalendarIdForEvent(contentResolver, event.eventId, calendarIdCache)
                if (resolvedCalId != null && event.calendarId == null) {
                    event = event.copy(calendarId = resolvedCalId)
                }
                if (!matchesVisibleCalendar(event.calendarId, visibleCalendarIds)) continue
                val key = "${event.eventId}-${event.beginMillis}"
                results[key] = event
            }
        }

        // На части устройств (особенно с Google Sync) дневная выборка Instances неполная,
        // хотя другие события в тот же день уже есть — нельзя ограничивать расширенный поиск
        // только пустым днём.
        mergeInstancesFromWiderWindow(
            eventIds = null,
            dayStartMillis = startMillis,
            dayEndMillis = endMillis,
            zone = zone,
            results = results,
            visibleCalendarIds = visibleCalendarIds,
            calendarIdCache = calendarIdCache,
        )

        mergeNonRecurringOverlappingEventsFromEventsTable(
            dayStartMillis = startMillis,
            dayEndMillis = endMillis,
            zone = zone,
            results = results,
            visibleCalendarIds = visibleCalendarIds,
            calendarIdCache = calendarIdCache,
        )

        val recurringRecovery = mergeRecurringMastersMissingInstances(
            day = date,
            dayStartMillis = startMillis,
            dayEndMillis = endMillis,
            zone = zone,
            results = results,
            visibleCalendarIds = visibleCalendarIds,
            calendarIdCache = calendarIdCache,
        )

        val missingEventIds = fallbackEvents
            .map { it.eventId }
            .filter { eventId -> results.values.none { it.eventId == eventId } }
            .distinct()
        if (missingEventIds.isNotEmpty()) {
            mergeInstancesFromWiderWindow(
                eventIds = missingEventIds,
                dayStartMillis = startMillis,
                dayEndMillis = endMillis,
                zone = zone,
                results = results,
                visibleCalendarIds = visibleCalendarIds,
                calendarIdCache = calendarIdCache,
            )
        }

        val stillMissingEventIds = fallbackEvents
            .map { it.eventId }
            .filter { eventId -> results.values.none { it.eventId == eventId } }
            .distinct()
        if (stillMissingEventIds.isNotEmpty()) {
            mergeEventsByIdFromEventsTable(
                eventIds = stillMissingEventIds,
                dayStartMillis = startMillis,
                dayEndMillis = endMillis,
                zone = zone,
                results = results,
                visibleCalendarIds = visibleCalendarIds,
                calendarIdCache = calendarIdCache,
            )
        }

        val providerEventIds = results.values.map { it.eventId }.toSet() -
            recurringRecovery.synthesizedIds

        mergeFallbackEventsForDay(
            dayStartMillis = startMillis,
            dayEndMillis = endMillis,
            zone = zone,
            results = results,
            visibleCalendarIds = visibleCalendarIds,
            fallbackEvents = fallbackEvents,
        )

        if (recurringRecovery.synthesizedIds.isNotEmpty()) {
            Log.i(
                REPO_TAG,
                "synthesized recurring occurrences ids=${recurringRecovery.synthesizedIds}",
            )
        }

        return CalendarDayEventsQuery(
            events = attachJoinLinks(attachAttendees(results.values.sortedBy { it.beginMillis })),
            providerEventIds = providerEventIds,
            needsInstancesRecovery = recurringRecovery.needsRecovery,
            synthesizedRecurringEventIds = recurringRecovery.synthesizedIds,
        )
    }

    private data class RecurringMergeResult(
        val synthesizedIds: Set<Long>,
        val needsRecovery: Boolean,
    )

    /**
     * OEM иногда не разворачивает Instances для RRULE. Тогда мастер есть в Events,
     * а день пустой. Синтезируем occurrence по простому RRULE и просим ViewModel сделать sync/warmup.
     */
    private fun mergeRecurringMastersMissingInstances(
        day: LocalDate,
        dayStartMillis: Long,
        dayEndMillis: Long,
        zone: ZoneId,
        results: LinkedHashMap<String, CalendarEvent>,
        visibleCalendarIds: Set<Long>?,
        calendarIdCache: MutableMap<Long, Long?>,
    ): RecurringMergeResult {
        val selection = "${CalendarContract.Events.DELETED} = ? AND " +
            "$visibleEventSql AND " +
            "${CalendarContract.Events.RRULE} IS NOT NULL AND ${CalendarContract.Events.RRULE} != '' AND " +
            "${CalendarContract.Events.DTSTART} < ?"
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.RRULE,
        )
        val synthesized = mutableSetOf<Long>()
        var needsRecovery = false
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf("0", dayEndMillis.toString()),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            val presentIds = results.values.map { it.eventId }.toHashSet()
            while (cursor.moveToNext()) {
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val rruleIdx = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val tzIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                if (idIdx < 0 || startIdx < 0 || rruleIdx < 0) continue
                val eventId = cursor.getLongOrNull(idIdx) ?: continue
                if (eventId in presentIds) continue
                val dtStart = cursor.getLongOrNull(startIdx) ?: continue
                val rrule = cursor.getStringOrNull(rruleIdx)?.trim().orEmpty()
                if (rrule.isEmpty()) continue
                val allDay = allDayIdx >= 0 && cursor.getIntOrNull(allDayIdx) == 1
                val eventZone = if (allDay) {
                    ZoneId.of("UTC")
                } else {
                    val tzId = if (tzIdx >= 0) cursor.getStringOrNull(tzIdx) else null
                    runCatching { ZoneId.of(tzId ?: zone.id) }.getOrDefault(zone)
                }
                val seriesStart = Instant.ofEpochMilli(dtStart).atZone(eventZone).toLocalDate()
                if (!RRuleOccurrence.occursOn(rrule, seriesStart, day)) continue

                val calIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                var calendarId = if (calIdx >= 0) cursor.getLongOrNull(calIdx) else null
                if (calendarId == null) {
                    calendarId = resolveCalendarIdForEvent(contentResolver, eventId, calendarIdCache)
                }
                if (!matchesVisibleCalendar(calendarId, visibleCalendarIds)) {
                    Log.d(REPO_TAG, "drop recurring master eventId=$eventId reason=filter calendarId=$calendarId")
                    continue
                }
                val selfIdx = cursor.getColumnIndex(CalendarContract.Events.SELF_ATTENDEE_STATUS)
                val selfStatus = if (selfIdx >= 0) {
                    cursor.getIntOrNull(selfIdx) ?: EventRsvp.STATUS_NONE
                } else {
                    EventRsvp.STATUS_NONE
                }
                if (EventRsvp.shouldHideFromAgenda(selfStatus)) {
                    Log.d(REPO_TAG, "drop recurring master eventId=$eventId reason=declined")
                    continue
                }

                val fromInstances = LinkedHashMap<String, CalendarEvent>()
                mergeInstancesFromWiderWindow(
                    eventIds = listOf(eventId),
                    dayStartMillis = dayStartMillis,
                    dayEndMillis = dayEndMillis,
                    zone = zone,
                    results = fromInstances,
                    visibleCalendarIds = visibleCalendarIds,
                    calendarIdCache = calendarIdCache,
                )
                if (fromInstances.isNotEmpty()) {
                    fromInstances.forEach { (key, event) -> results.putIfAbsent(key, event) }
                    presentIds += eventId
                    continue
                }

                needsRecovery = true
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val title = if (titleIdx >= 0) {
                    cursor.getStringOrNull(titleIdx)?.takeIf { it.isNotBlank() }
                } else {
                    null
                } ?: untitledLabel
                val colorIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val color = if (colorIdx >= 0) cursor.getIntOrNull(colorIdx) else null
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val location = if (locIdx >= 0) cursor.getStringOrNull(locIdx)?.trim().orEmpty() else ""
                val availabilityIdx = cursor.getColumnIndex(CalendarContract.Events.AVAILABILITY)
                val availability = if (availabilityIdx >= 0) cursor.getIntOrNull(availabilityIdx) ?: 0 else 0
                val durationMs = cursor.eventEndMillisForOverlap(dtStart, allDay) - dtStart
                val (begin, end) = occurrenceBoundsForDay(
                    day = day,
                    allDay = allDay,
                    masterStartMillis = dtStart,
                    durationMillis = durationMs.coerceAtLeast(if (allDay) 86_400_000L else 3_600_000L),
                    eventZone = eventZone,
                )
                if (!eventOverlapsLocalDay(begin, end, allDay, dayStartMillis, dayEndMillis, zone)) {
                    continue
                }
                val synthesizedEvent = CalendarEvent(
                    eventId = eventId,
                    title = title,
                    beginMillis = begin,
                    endMillis = end,
                    allDay = allDay,
                    calendarId = calendarId,
                    calendarName = null,
                    color = color,
                    location = location,
                    availability = availability,
                    selfAttendeeStatus = selfStatus,
                )
                val key = "${synthesizedEvent.eventId}-${synthesizedEvent.beginMillis}"
                if (results.putIfAbsent(key, synthesizedEvent) == null) {
                    synthesized += eventId
                    presentIds += eventId
                    Log.i(REPO_TAG, "synthesized recurring eventId=$eventId day=$day")
                }
            }
        }
        return RecurringMergeResult(
            synthesizedIds = synthesized,
            needsRecovery = needsRecovery || synthesized.isNotEmpty(),
        )
    }

    private fun occurrenceBoundsForDay(
        day: LocalDate,
        allDay: Boolean,
        masterStartMillis: Long,
        durationMillis: Long,
        eventZone: ZoneId,
    ): Pair<Long, Long> {
        if (allDay) {
            val start = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            return start to start + 86_400_000L
        }
        val masterLocal = Instant.ofEpochMilli(masterStartMillis).atZone(eventZone).toLocalDateTime()
        val begin = day.atTime(masterLocal.toLocalTime()).atZone(eventZone).toInstant().toEpochMilli()
        return begin to begin + durationMillis
    }

    private fun attachAttendees(events: List<CalendarEvent>): List<CalendarEvent> {
        val ids = events.map { it.eventId }.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return events
        val grouped = mutableMapOf<Long, MutableList<EventInvitee>>()
        ids.chunked(40).forEach { chunk ->
            val selection = "${CalendarContract.Attendees.EVENT_ID} IN (${chunk.joinToString(",") { "?" }})"
            contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                arrayOf(
                    CalendarContract.Attendees.EVENT_ID,
                    CalendarContract.Attendees.ATTENDEE_EMAIL,
                    CalendarContract.Attendees.ATTENDEE_NAME,
                    CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                ),
                selection,
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { cursor ->
                val eventIdx = cursor.getColumnIndex(CalendarContract.Attendees.EVENT_ID)
                val emailIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_NAME)
                val relIdx = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP)
                if (eventIdx < 0 || emailIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val relationship = if (relIdx >= 0) cursor.getIntOrNull(relIdx) else null
                    if (relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER) continue
                    val eventId = cursor.getLongOrNull(eventIdx) ?: continue
                    val email = cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                    if (email.isBlank()) continue
                    val name = if (nameIdx >= 0) cursor.getStringOrNull(nameIdx)?.trim() else null
                    grouped.getOrPut(eventId) { mutableListOf() }
                        .add(EventInvitee(email = email, name = name?.takeIf { it.isNotBlank() }))
                }
            }
        }
        return events.map { event ->
            val people = grouped[event.eventId].orEmpty().distinctBy { it.email.lowercase() }
            if (people.isEmpty()) event else event.copy(attendees = people)
        }
    }

    /**
     * Join-ссылка Skype/Teams часто лежит в DESCRIPTION, а в месте — только подпись
     * «Собрание Skype». На первый экран тащим только найденный URL, не весь текст заметок.
     */
    private fun attachJoinLinks(events: List<CalendarEvent>): List<CalendarEvent> {
        val ids = events
            .filter { event ->
                event.eventId > 0 &&
                    EventConferenceLinks.joinUrl(event.location, event.notes).isNullOrBlank()
            }
            .map { it.eventId }
            .distinct()
        if (ids.isEmpty()) return events
        val notesById = mutableMapOf<Long, String>()
        ids.chunked(40).forEach { chunk ->
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.DESCRIPTION,
                ),
                "${CalendarContract.Events._ID} IN (${chunk.joinToString(",") { "?" }})",
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                if (idIdx < 0 || descIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val id = cursor.getLongOrNull(idIdx) ?: continue
                    val desc = cursor.getStringOrNull(descIdx)?.trim().orEmpty()
                    if (desc.isNotEmpty()) notesById[id] = desc
                }
            }
        }
        if (notesById.isEmpty()) return events
        return events.map { event ->
            val desc = notesById[event.eventId] ?: return@map event
            val join = EventConferenceLinks.joinUrl(event.location, desc) ?: return@map event
            event.copy(notes = join)
        }
    }

    /**
     * Дополнение к [CalendarContract.Instances]: у Google/других провайдеров иногда пустая выборка
     * или неполные строки, при этом [CalendarContract.Events] содержит те же нерекуррентные события.
     */
    private fun mergeNonRecurringOverlappingEventsFromEventsTable(
        dayStartMillis: Long,
        dayEndMillis: Long,
        zone: ZoneId,
        results: LinkedHashMap<String, CalendarEvent>,
        visibleCalendarIds: Set<Long>?,
        calendarIdCache: MutableMap<Long, Long?>,
    ) {
        val selection = "${CalendarContract.Events.DELETED} = ? AND " +
            "$visibleEventSql AND " +
            "(${CalendarContract.Events.RRULE} IS NULL OR ${CalendarContract.Events.RRULE} = '') AND " +
            "${CalendarContract.Events.DTSTART} < ? AND " +
            "(${CalendarContract.Events.DTEND} IS NULL OR ${CalendarContract.Events.DTEND} > ?)"
        val selArgs = arrayOf(
            "0",
            dayEndMillis.toString(),
            dayStartMillis.toString(),
        )
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.AVAILABILITY,
        )
        val sort = "${CalendarContract.Events.DTSTART} ASC"

        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selArgs,
            sort,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val event = cursor.readCalendarEventFromEventsRow() ?: continue
                val resolvedCalId = event.calendarId
                    ?: resolveCalendarIdForEvent(contentResolver, event.eventId, calendarIdCache)
                val withCal = if (resolvedCalId != null && event.calendarId == null) {
                    event.copy(calendarId = resolvedCalId)
                } else {
                    event
                }
                if (!matchesVisibleCalendar(withCal.calendarId, visibleCalendarIds)) continue
                if (!eventOverlapsLocalDay(
                        beginMillis = withCal.beginMillis,
                        endMillis = withCal.endMillis,
                        allDay = withCal.allDay,
                        dayStartMillis = dayStartMillis,
                        dayEndMillis = dayEndMillis,
                        zone = zone,
                    )
                ) {
                    continue
                }
                val key = "${withCal.eventId}-${withCal.beginMillis}"
                if (!results.containsKey(key)) {
                    results[key] = withCal
                }
            }
        }
    }

    private fun mergeInstancesFromWiderWindow(
        eventIds: List<Long>?,
        dayStartMillis: Long,
        dayEndMillis: Long,
        zone: ZoneId,
        results: LinkedHashMap<String, CalendarEvent>,
        visibleCalendarIds: Set<Long>?,
        calendarIdCache: MutableMap<Long, Long?>,
    ) {
        if (eventIds != null && eventIds.isEmpty()) return
        val wanted = eventIds?.toSet()
        val windowStart = dayStartMillis - 42 * 86_400_000L
        val windowEnd = dayEndMillis + 42 * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, windowStart)
            ContentUris.appendId(this, windowEnd)
        }.build()
        val projection = instanceEventProjection()
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                var event = cursor.readEvent() ?: continue
                if (wanted != null && event.eventId !in wanted) continue
                val instanceKey = "${event.eventId}-${event.beginMillis}"
                if (results.containsKey(instanceKey)) continue
                if (!eventOverlapsLocalDay(
                        beginMillis = event.beginMillis,
                        endMillis = event.endMillis,
                        allDay = event.allDay,
                        dayStartMillis = dayStartMillis,
                        dayEndMillis = dayEndMillis,
                        zone = zone,
                    )
                ) {
                    continue
                }
                val resolvedCalId = event.calendarId
                    ?: resolveCalendarIdForEvent(contentResolver, event.eventId, calendarIdCache)
                if (resolvedCalId != null && event.calendarId == null) {
                    event = event.copy(calendarId = resolvedCalId)
                }
                if (!matchesVisibleCalendar(event.calendarId, visibleCalendarIds)) continue
                val key = "${event.eventId}-${event.beginMillis}"
                results.putIfAbsent(key, event)
            }
        }
    }

    private fun mergeEventsByIdFromEventsTable(
        eventIds: List<Long>,
        dayStartMillis: Long,
        dayEndMillis: Long,
        zone: ZoneId,
        results: LinkedHashMap<String, CalendarEvent>,
        visibleCalendarIds: Set<Long>?,
        calendarIdCache: MutableMap<Long, Long?>,
    ) {
        for (eventId in eventIds) {
            if (results.values.any { it.eventId == eventId }) continue
            val event = readCalendarEventFromEventsUri(eventId) ?: continue
            val resolvedCalId = event.calendarId
                ?: resolveCalendarIdForEvent(contentResolver, event.eventId, calendarIdCache)
            val withCal = if (resolvedCalId != null && event.calendarId == null) {
                event.copy(calendarId = resolvedCalId)
            } else {
                event
            }
            if (!matchesVisibleCalendar(withCal.calendarId, visibleCalendarIds)) continue
            if (!eventOverlapsLocalDay(
                    beginMillis = withCal.beginMillis,
                    endMillis = withCal.endMillis,
                    allDay = withCal.allDay,
                    dayStartMillis = dayStartMillis,
                    dayEndMillis = dayEndMillis,
                    zone = zone,
                )
            ) {
                continue
            }
            val key = "${withCal.eventId}-${withCal.beginMillis}"
            results.putIfAbsent(key, withCal)
        }
    }

    private fun readCalendarEventFromEventsUri(eventId: Long): CalendarEvent? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.AVAILABILITY,
        )
        return contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.readCalendarEventFromEventsRow()
        }
    }

    private fun mergeFallbackEventsForDay(
        dayStartMillis: Long,
        dayEndMillis: Long,
        zone: ZoneId,
        results: LinkedHashMap<String, CalendarEvent>,
        visibleCalendarIds: Set<Long>?,
        fallbackEvents: List<CalendarEvent>,
    ) {
        for (fallback in fallbackEvents) {
            if (results.values.any { event ->
                event.eventId == fallback.eventId &&
                    eventOverlapsLocalDay(
                        beginMillis = event.beginMillis,
                        endMillis = event.endMillis,
                        allDay = event.allDay,
                        dayStartMillis = dayStartMillis,
                        dayEndMillis = dayEndMillis,
                        zone = zone,
                    )
            }) {
                continue
            }
            if (!eventOverlapsLocalDay(
                    beginMillis = fallback.beginMillis,
                    endMillis = fallback.endMillis,
                    allDay = fallback.allDay,
                    dayStartMillis = dayStartMillis,
                    dayEndMillis = dayEndMillis,
                    zone = zone,
                )
            ) {
                continue
            }
            val key = "${fallback.eventId}-${fallback.beginMillis}"
            results.putIfAbsent(key, fallback)
        }
    }

    private fun eventOverlapsLocalDay(
        beginMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        dayStartMillis: Long,
        dayEndMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (allDay) {
            val startDay = Instant.ofEpochMilli(beginMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            val endExclusive = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            val lastDay = endExclusive.minusDays(1)
            val day = Instant.ofEpochMilli(dayStartMillis).atZone(zone).toLocalDate()
            return !day.isBefore(startDay) && !day.isAfter(lastDay)
        }
        return beginMillis < dayEndMillis && endMillis > dayStartMillis
    }

    private fun Cursor.eventEndMillisForOverlap(dtStart: Long, allDay: Boolean): Long {
        val dtEndIdx = getColumnIndex(CalendarContract.Events.DTEND)
        if (dtEndIdx >= 0) {
            val raw = getLongOrNull(dtEndIdx)
            if (raw != null && raw > dtStart) return raw
        }
        val durIdx = getColumnIndex(CalendarContract.Events.DURATION)
        if (durIdx >= 0) {
            val dur = getStringOrNull(durIdx)
            if (!dur.isNullOrBlank()) {
                val parsed = parseDurationMillisSafe(dur)
                if (parsed > 0L) return dtStart + parsed
            }
        }
        return if (allDay) dtStart + 86400000L else dtStart + 3600000L
    }

    private fun parseDurationMillisSafe(isoDuration: String): Long =
        runCatching { Duration.parse(isoDuration).toMillis() }.getOrElse { 0L }

    private fun Cursor.readCalendarEventFromEventsRow(): CalendarEvent? {
        val idIdx = getColumnIndex(CalendarContract.Events._ID)
        val calIdx = getColumnIndex(CalendarContract.Events.CALENDAR_ID)
        val titleIdx = getColumnIndex(CalendarContract.Events.TITLE)
        val startIdx = getColumnIndex(CalendarContract.Events.DTSTART)
        val allDayIdx = getColumnIndex(CalendarContract.Events.ALL_DAY)
        val colorIdx = getColumnIndex(CalendarContract.Events.EVENT_COLOR)
        val locationIdx = getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
        if (idIdx < 0 || startIdx < 0) return null
        val id = getLongOrNull(idIdx) ?: return null
        val begin = getLongOrNull(startIdx) ?: return null
        val calId = if (calIdx >= 0) getLongOrNull(calIdx) else null
        val title = if (titleIdx >= 0) {
            getStringOrNull(titleIdx)?.takeIf { it.isNotBlank() }
        } else {
            null
        } ?: untitledLabel
        val allDay = allDayIdx >= 0 && getIntOrNull(allDayIdx) == 1
        val color = if (colorIdx >= 0) getIntOrNull(colorIdx) else null
        val location = if (locationIdx >= 0) getStringOrNull(locationIdx)?.trim().orEmpty() else ""
        val selfIdx = getColumnIndex(CalendarContract.Events.SELF_ATTENDEE_STATUS)
        val selfAttendeeStatus = if (selfIdx >= 0) {
            getIntOrNull(selfIdx) ?: EventRsvp.STATUS_NONE
        } else {
            EventRsvp.STATUS_NONE
        }
        if (EventRsvp.shouldHideFromAgenda(selfAttendeeStatus)) return null
        val availabilityIdx = getColumnIndex(CalendarContract.Events.AVAILABILITY)
        val availability = if (availabilityIdx >= 0) getIntOrNull(availabilityIdx) ?: 0 else 0
        val end = eventEndMillisForOverlap(begin, allDay)
        return CalendarEvent(
            eventId = id,
            title = title,
            beginMillis = begin,
            endMillis = end,
            allDay = allDay,
            calendarId = calId,
            calendarName = null,
            color = color,
            location = location,
            availability = availability,
            selfAttendeeStatus = selfAttendeeStatus,
        )
    }

    /**
     * У части провайдеров [CalendarContract.Instances.CALENDAR_ID] пустой, хотя в [CalendarContract.Events]
     * календарь задан — без этого фильтр «видимых календарей» отрезает синхронные аккаунты.
     */
    private fun matchesVisibleCalendar(calendarId: Long?, visibleCalendarIds: Set<Long>?): Boolean {
        if (visibleCalendarIds == null) return true
        if (calendarId == null) return true
        return calendarId in visibleCalendarIds
    }

    private fun resolveCalendarIdForEvent(
        contentResolver: ContentResolver,
        eventId: Long,
        cache: MutableMap<Long, Long?>,
    ): Long? {
        if (eventId in cache) return cache[eventId]
        val rowCalId = contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.CALENDAR_ID),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val idx = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            if (idx < 0) null else c.getLongOrNull(idx)
        }
        cache[eventId] = rowCalId
        return rowCalId
    }

    /**
     * Для каждого дня 6-недельной сетки месяца — список цветов календарей (по одному на календарь),
     * в которых есть событие в этот день (включая хвосты соседних месяцев в сетке).
     */
    fun queryCalendarDotColorsForMonthGrid(
        yearMonth: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
        visibleCalendarIds: Set<Long>? = null,
        skipAllDayFreeOnCalendarIds: Set<Long> = emptySet(),
    ): Map<LocalDate, List<Int>> {
        if (visibleCalendarIds != null && visibleCalendarIds.isEmpty()) {
            return emptyMap()
        }

        val locale = AppLocale.display
        val weekFields = WeekFields.of(locale)
        val firstGridDay = yearMonth.atDay(1)
            .with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
        val gridEndInclusive = firstGridDay.plusDays(41)

        val startMillis = firstGridDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = firstGridDay.plusDays(42).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMillis)
            ContentUris.appendId(this, endMillis)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY,
        )

        val sort = "${CalendarContract.Instances.BEGIN} ASC"
        val byDay = mutableMapOf<LocalDate, LinkedHashMap<Long, Int>>()
        val calendarIdCache = mutableMapOf<Long, Long?>()

        fun resolvedColorArgb(raw: Int?): Int {
            val c = raw ?: return fallbackDotColorArgb
            return if (c == 0) fallbackDotColorArgb else c
        }

        contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
            while (cursor.moveToNext()) {
                val declinedIdx = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
                if (declinedIdx >= 0 &&
                    EventRsvp.shouldHideFromAgenda(cursor.getIntOrNull(declinedIdx))
                ) {
                    continue
                }
                val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val eventId = if (eventIdIdx >= 0) cursor.getLongOrNull(eventIdIdx) else null

                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                if (beginIdx < 0 || endIdx < 0) continue
                val begin = cursor.getLongOrNull(beginIdx) ?: continue
                val end = cursor.getLongOrNull(endIdx) ?: continue

                val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                var calendarId = if (calIdIdx >= 0) cursor.getLongOrNull(calIdIdx) else null
                if (calendarId == null && eventId != null) {
                    calendarId = resolveCalendarIdForEvent(contentResolver, eventId, calendarIdCache)
                }
                if (!matchesVisibleCalendar(calendarId, visibleCalendarIds)) continue
                if (calendarId != null && calendarId in skipAllDayFreeOnCalendarIds) {
                    val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val freeIdx = cursor.getColumnIndex(CalendarContract.Instances.AVAILABILITY)
                    val allDay = allDayIdx >= 0 && cursor.getIntOrNull(allDayIdx) == 1
                    val free = freeIdx >= 0 &&
                        cursor.getIntOrNull(freeIdx) == EventOverlap.AVAILABILITY_FREE
                    if (allDay && free) continue
                }

                val colorIdx = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val colorRaw = if (colorIdx >= 0) cursor.getIntOrNull(colorIdx) else null
                val dotArgb = resolvedColorArgb(colorRaw)

                val calKey = calendarId ?: Long.MIN_VALUE

                for (day in instanceLocalDays(begin, end, zone)) {
                    if (day.isBefore(firstGridDay) || day.isAfter(gridEndInclusive)) continue
                    val forDay = byDay.getOrPut(day) { LinkedHashMap() }
                    forDay.putIfAbsent(calKey, dotArgb)
                }
            }
        }

        return byDay.mapValues { (_, calMap) -> calMap.values.toList() }
    }

    /** Локальные даты, которые пересекает инстанс (конец обычно exclusive). */
    private fun instanceLocalDays(
        beginMillis: Long,
        endMillis: Long,
        zone: ZoneId,
    ): List<LocalDate> {
        val startDay = Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate()
        if (endMillis <= beginMillis) return listOf(startDay)
        val lastDay = Instant.ofEpochMilli(endMillis - 1).atZone(zone).toLocalDate()
        val out = ArrayList<LocalDate>()
        var d = startDay
        var guard = 0
        while (!d.isAfter(lastDay) && guard++ < 400) {
            out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    private fun Cursor.readEvent(): CalendarEvent? {
        fun index(name: String): Int {
            val i = getColumnIndex(name)
            return if (i >= 0) i else -1
        }

        val idIdx = index(CalendarContract.Instances.EVENT_ID)
        val beginIdx = index(CalendarContract.Instances.BEGIN)
        val endIdx = index(CalendarContract.Instances.END)
        if (idIdx < 0 || beginIdx < 0 || endIdx < 0) return null

        val id = getLongOrNull(idIdx) ?: return null
        val begin = getLongOrNull(beginIdx) ?: return null
        val end = getLongOrNull(endIdx) ?: return null

        val titleIdx = index(CalendarContract.Instances.TITLE)
        val title = if (titleIdx >= 0) {
            getStringOrNull(titleIdx)?.takeIf { it.isNotBlank() }
        } else {
            null
        } ?: untitledLabel

        val allDayIdx = index(CalendarContract.Instances.ALL_DAY)
        val allDay = allDayIdx >= 0 && getIntOrNull(allDayIdx) == 1

        val colorIdx = index(CalendarContract.Instances.DISPLAY_COLOR)
        val color = if (colorIdx >= 0) getIntOrNull(colorIdx) else null

        val calIdx = index(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
        val calName = if (calIdx >= 0) getStringOrNull(calIdx) else null

        val calIdIdx = index(CalendarContract.Instances.CALENDAR_ID)
        val calId = if (calIdIdx >= 0) getLongOrNull(calIdIdx) else null

        val locIdx = index(CalendarContract.Instances.EVENT_LOCATION)
        val location = if (locIdx >= 0) getStringOrNull(locIdx)?.trim().orEmpty() else ""

        val selfIdx = index(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
        val selfAttendeeStatus = if (selfIdx >= 0) {
            getIntOrNull(selfIdx) ?: EventRsvp.STATUS_NONE
        } else {
            EventRsvp.STATUS_NONE
        }
        if (EventRsvp.shouldHideFromAgenda(selfAttendeeStatus)) return null

        val availabilityIdx = index(CalendarContract.Instances.AVAILABILITY)
        val availability = if (availabilityIdx >= 0) getIntOrNull(availabilityIdx) ?: 0 else 0

        return CalendarEvent(
            eventId = id,
            title = title,
            beginMillis = begin,
            endMillis = end,
            allDay = allDay,
            calendarId = calId,
            calendarName = calName,
            color = color,
            location = location,
            availability = availability,
            selfAttendeeStatus = selfAttendeeStatus,
        )
    }

    private fun instanceEventProjection(): Array<String> = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        CalendarContract.Instances.AVAILABILITY,
    )

    /**
     * События выбранных календарей: название, место, участники, имена вложений.
     * Только сегодня и раньше, свежие сверху.
     */
    fun searchPastEvents(
        query: String,
        visibleCalendarIds: Set<Long>?,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): List<CalendarEvent> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        if (visibleCalendarIds != null && visibleCalendarIds.isEmpty()) return emptyList()

        val matchingIds = linkedSetOf<Long>()
        matchingIds += queryEventIdsByTitleOrLocation(needle, visibleCalendarIds)
        matchingIds += runCatching {
            queryEventIdsByTextColumns(
                CalendarContract.Attendees.CONTENT_URI,
                CalendarContract.Attendees.EVENT_ID,
                listOf(
                    CalendarContract.Attendees.ATTENDEE_NAME,
                    CalendarContract.Attendees.ATTENDEE_EMAIL,
                ),
                needle,
            )
        }.getOrDefault(emptySet())
        matchingIds += runCatching { queryEventIdsByAttachments(needle) }.getOrDefault(emptySet())
        matchingIds += runCatching { queryEventIdsByExtendedAttachmentFields(needle) }.getOrDefault(emptySet())
        matchingIds += runCatching { attachmentStore.searchEventIds(needle) }.getOrDefault(emptySet())
        val visibleIds = filterEventIdsByVisibleCalendars(matchingIds, visibleCalendarIds)
        if (visibleIds.isEmpty()) return emptyList()

        val rangeStart = today.minusYears(SEARCH_LOOKBACK_YEARS)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val results = LinkedHashMap<String, CalendarEvent>()
        val calendarIdCache = mutableMapOf<Long, Long?>()
        visibleIds.chunked(40).forEach { chunk ->
            mergeInstancesForSearch(
                eventIds = chunk,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                visibleCalendarIds = visibleCalendarIds,
                calendarIdCache = calendarIdCache,
                results = results,
            )
        }
        return attachAttendees(
            results.values
                .sortedWith(compareByDescending<CalendarEvent> { it.beginMillis }.thenBy { it.title })
                .take(SEARCH_RESULT_LIMIT),
        )
    }

    private fun queryEventIdsByTitleOrLocation(
        needle: String,
        visibleCalendarIds: Set<Long>?,
    ): Set<Long> {
        val selection = buildString {
            append("${CalendarContract.Events.DELETED} = ? AND ")
            append(visibleEventSql)
            if (visibleCalendarIds != null) {
                append(" AND ${CalendarContract.Events.CALENDAR_ID} IN (${visibleCalendarIds.joinToString(",")})")
            }
        }
        val ids = linkedSetOf<Long>()
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
            ),
            selection,
            arrayOf("0"),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            if (idIdx < 0) return@use
            while (cursor.moveToNext()) {
                val title = if (titleIdx >= 0) cursor.getStringOrNull(titleIdx).orEmpty() else ""
                val location = if (locIdx >= 0) cursor.getStringOrNull(locIdx).orEmpty() else ""
                if (title.contains(needle, ignoreCase = true) ||
                    location.contains(needle, ignoreCase = true)
                ) {
                    cursor.getLongOrNull(idIdx)?.let { ids.add(it) }
                }
            }
        }
        return ids
    }

    private fun queryEventIdsByTextColumns(
        uri: Uri,
        eventIdColumn: String,
        textColumns: List<String>,
        needle: String,
    ): Set<Long> {
        val projection = (listOf(eventIdColumn) + textColumns).toTypedArray()
        val ids = linkedSetOf<Long>()
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(eventIdColumn)
            if (idIdx < 0) return@use
            val textIdx = textColumns.map { cursor.getColumnIndex(it) }
            while (cursor.moveToNext()) {
                val matches = textIdx.any { idx ->
                    idx >= 0 && cursor.getStringOrNull(idx).orEmpty().contains(needle, ignoreCase = true)
                }
                if (matches) {
                    cursor.getLongOrNull(idIdx)?.let { ids.add(it) }
                }
            }
        }
        return ids
    }

    private fun queryEventIdsByAttachments(needle: String): Set<Long> {
        val ids = linkedSetOf<Long>()
        contentResolver.query(CALENDAR_ATTACHMENTS_URI, null, null, null, null)?.use { cursor ->
            val idIdx = cursor.columnIndexIgnoreCase("event_id", "eventId")
            if (idIdx < 0) return@use
            val textIdx = cursor.columnNames.mapIndexedNotNull { index, name ->
                when {
                    name.contains("file", ignoreCase = true) -> index
                    name.equals("title", ignoreCase = true) -> index
                    name.equals("name", ignoreCase = true) -> index
                    name.equals("uri", ignoreCase = true) -> index
                    else -> null
                }
            }
            while (cursor.moveToNext()) {
                val matches = textIdx.any { index ->
                    cursor.getStringOrNull(index).orEmpty().contains(needle, ignoreCase = true)
                }
                if (matches) {
                    cursor.getLongOrNull(idIdx)?.let { ids.add(it) }
                }
            }
        }
        return ids
    }

    private fun queryEventIdsByExtendedAttachmentFields(needle: String): Set<Long> {
        val ids = linkedSetOf<Long>()
        contentResolver.query(
            CalendarContract.ExtendedProperties.CONTENT_URI,
            arrayOf(
                CalendarContract.ExtendedProperties.EVENT_ID,
                CalendarContract.ExtendedProperties.NAME,
                CalendarContract.ExtendedProperties.VALUE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.ExtendedProperties.EVENT_ID)
            val nameIdx = cursor.getColumnIndex(CalendarContract.ExtendedProperties.NAME)
            val valueIdx = cursor.getColumnIndex(CalendarContract.ExtendedProperties.VALUE)
            if (idIdx < 0 || valueIdx < 0) return@use
            while (cursor.moveToNext()) {
                val value = cursor.getStringOrNull(valueIdx).orEmpty()
                if (!value.contains(needle, ignoreCase = true)) continue
                val name = if (nameIdx >= 0) cursor.getStringOrNull(nameIdx).orEmpty() else ""
                if (!looksLikeAttachmentProperty(name, value)) continue
                cursor.getLongOrNull(idIdx)?.let { ids.add(it) }
            }
        }
        return ids
    }

    private fun Cursor.columnIndexIgnoreCase(vararg names: String): Int {
        val wanted = names.map { it.lowercase() }.toSet()
        return columnNames.indexOfFirst { it.lowercase() in wanted }
    }

    private fun filterEventIdsByVisibleCalendars(
        eventIds: Set<Long>,
        visibleCalendarIds: Set<Long>?,
    ): Set<Long> {
        if (eventIds.isEmpty()) return emptySet()
        if (visibleCalendarIds == null) return eventIds
        val allowed = linkedSetOf<Long>()
        eventIds.chunked(40).forEach { chunk ->
            val selection = "${CalendarContract.Events._ID} IN (${chunk.joinToString(",") { "?" }})"
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID),
                selection,
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val calIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                if (idIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val id = cursor.getLongOrNull(idIdx) ?: continue
                    val calendarId = if (calIdx >= 0) cursor.getLongOrNull(calIdx) else null
                    if (matchesVisibleCalendar(calendarId, visibleCalendarIds)) {
                        allowed.add(id)
                    }
                }
            }
        }
        return allowed
    }

    private fun mergeInstancesForSearch(
        eventIds: List<Long>,
        rangeStart: Long,
        rangeEnd: Long,
        visibleCalendarIds: Set<Long>?,
        calendarIdCache: MutableMap<Long, Long?>,
        results: LinkedHashMap<String, CalendarEvent>,
    ) {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, rangeStart)
            ContentUris.appendId(this, rangeEnd)
        }.build()
        val selection = "${CalendarContract.Instances.EVENT_ID} IN (${eventIds.joinToString(",") { "?" }})"
        val projection = instanceEventProjection()
        contentResolver.query(
            uri,
            projection,
            selection,
            eventIds.map { it.toString() }.toTypedArray(),
            "${CalendarContract.Instances.BEGIN} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                var event = cursor.readEvent() ?: continue
                if (event.beginMillis >= rangeEnd) continue
                val resolvedCalId = event.calendarId
                    ?: resolveCalendarIdForEvent(contentResolver, event.eventId, calendarIdCache)
                if (resolvedCalId != null && event.calendarId == null) {
                    event = event.copy(calendarId = resolvedCalId)
                }
                if (!matchesVisibleCalendar(event.calendarId, visibleCalendarIds)) continue
                results["${event.eventId}-${event.beginMillis}"] = event
            }
        }
    }

    private fun looksLikeAttachmentProperty(name: String, value: String): Boolean {
        val property = name.lowercase()
        if (property.contains("attach") || property.contains("file")) return true
        val lower = value.lowercase()
        return ATTACHMENT_EXTENSIONS.any { lower.contains(it) }
    }

    companion object {
        private const val SEARCH_LOOKBACK_YEARS = 5L
        private const val SEARCH_RESULT_LIMIT = 150
        private val CALENDAR_ATTACHMENTS_URI = Uri.parse("content://com.android.calendar/attachments")
        private val ATTACHMENT_EXTENSIONS = listOf(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".png", ".jpg", ".jpeg", ".gif", ".heic", ".zip", ".txt",
        )
    }
}
