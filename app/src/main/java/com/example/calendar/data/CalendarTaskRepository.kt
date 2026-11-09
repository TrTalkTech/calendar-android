package com.example.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarTaskRepository(
    private val contentResolver: ContentResolver,
    private val eventWriter: CalendarEventWriter,
) {
    fun queryTasks(taskCalendars: List<WritableCalendar>): List<CalendarTask> {
        val calendars = taskCalendars.distinctBy { it.id }
        if (calendars.isEmpty()) return emptyList()
        val byId = calendars.associateBy { it.id }
        val ids = calendars.map { it.id }
        val placeholders = ids.joinToString(",") { "?" }
        val baseSelection = "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders) AND " +
            "${CalendarContract.Events.DELETED} = 0"
        val idArgs = ids.map { it.toString() }
        return readTasks(
            selection = "$baseSelection AND (${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ?)",
            args = idArgs + CALENDAR_EVENT_STATUS_CANCELLED.toString(),
            byId = byId,
        ) ?: readTasks(
            selection = baseSelection,
            args = idArgs,
            byId = byId,
        ).orEmpty()
    }

    private fun readTasks(
        selection: String,
        args: List<String>,
        byId: Map<Long, WritableCalendar>,
    ): List<CalendarTask>? {
        val zone = ZoneId.systemDefault()
        val result = mutableListOf<CalendarTask>()
        return try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.CALENDAR_ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.AVAILABILITY,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DISPLAY_COLOR,
                ),
                selection,
                args.toTypedArray(),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val calIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val availabilityIdx = cursor.getColumnIndex(CalendarContract.Events.AVAILABILITY)
                val notesIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Events.DISPLAY_COLOR)
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idIdx)
                    val calendarId = cursor.getLong(calIdx)
                    val calendar = byId[calendarId] ?: continue
                    val rawTitle = cursor.getStringOrNull(titleIdx).orEmpty()
                    val startMillis = cursor.getLongOrNull(startIdx) ?: continue
                    val allDay = cursor.getInt(allDayIdx) == 1
                    if (!calendar.isTaskCalendar) {
                        if (!allDay) continue
                        val availability = if (availabilityIdx >= 0) {
                            cursor.getIntOrNull(availabilityIdx) ?: 0
                        } else {
                            0
                        }
                        if (availability != CalendarContract.Events.AVAILABILITY_FREE) continue
                    }
                    val date = if (allDay) {
                        Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    } else {
                        Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
                    }
                    result.add(
                        CalendarTask(
                            eventId = eventId,
                            calendarId = calendarId,
                            title = stripDonePrefix(rawTitle).ifBlank { rawTitle.trim() },
                            date = date,
                            done = hasDonePrefix(rawTitle),
                            notes = if (notesIdx >= 0) cursor.getStringOrNull(notesIdx).orEmpty() else "",
                            color = if (colorIdx >= 0) cursor.getIntOrNull(colorIdx) else calendar?.color,
                            calendarName = calendar?.displayName,
                        ),
                    )
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "queryTasks failed", e)
            null
        }
    }

    fun insertTask(
        calendarId: Long,
        title: String,
        date: LocalDate,
        notes: String,
        reminderMinutesBefore: List<Int>,
    ): Long? {
        val start = date.atStartOfDay()
        val uri = eventWriter.insertEvent(
            calendarId = calendarId,
            title = title.trim().ifBlank { "Задача" },
            location = null,
            description = notes.trim().takeIf { it.isNotEmpty() },
            url = null,
            allDay = true,
            start = start,
            end = start,
            zone = ZoneId.systemDefault(),
            reminderMinutesBefore = reminderMinutesBefore,
            availability = CalendarContract.Events.AVAILABILITY_FREE,
        ) ?: return null
        return ContentUris.parseId(uri)
    }

    fun setTaskDone(eventId: Long, done: Boolean): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return try {
            val current = contentResolver.query(
                uri,
                arrayOf(CalendarContract.Events.TITLE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getStringOrNull(0)
            } ?: return false
            val visible = stripDonePrefix(current).ifBlank { current.trim().ifBlank { "Задача" } }
            val nextTitle = if (done) doneTitle(visible) else visible
            if (nextTitle == current) return true
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, nextTitle)
            }
            val updated = contentResolver.update(uri, values, null, null) >= 0
            if (updated) eventWriter.requestSyncForEventId(eventId)
            updated
        } catch (e: Exception) {
            Log.e(TAG, "setTaskDone failed eventId=$eventId", e)
            false
        }
    }

    fun updateTask(
        eventId: Long,
        calendarId: Long,
        title: String,
        date: LocalDate,
        notes: String,
        reminderMinutesBefore: List<Int>,
        done: Boolean,
    ): Boolean {
        val visible = title.trim().ifBlank { "Задача" }
        val storedTitle = if (done) doneTitle(visible) else visible
        val start = date.atStartOfDay()
        return eventWriter.updateEvent(
            eventId = eventId,
            calendarId = calendarId,
            title = storedTitle,
            location = null,
            description = notes.trim().takeIf { it.isNotEmpty() },
            url = null,
            allDay = true,
            start = start,
            end = start,
            reminderMinutesBefore = reminderMinutesBefore,
        ) >= 0
    }

    fun deleteTask(eventId: Long): Boolean = eventWriter.deleteEntireEvent(eventId)

    companion object {
        private const val TAG = "CalendarTaskRepository"
        private const val CALENDAR_EVENT_STATUS_CANCELLED = 2
        private const val DONE_PREFIX = "✓ "
        private val DONE_PREFIXES = listOf("✓ ", "✔ ", "☑ ", "✓", "✔", "☑")

        fun hasDonePrefix(title: String): Boolean {
            val trimmed = title.trimStart()
            return DONE_PREFIXES.any { trimmed.startsWith(it) }
        }

        fun stripDonePrefix(title: String): String {
            var text = title.trim()
            while (true) {
                val prefix = DONE_PREFIXES.firstOrNull { text.startsWith(it) } ?: break
                text = text.removePrefix(prefix).trimStart()
            }
            return text
        }

        fun doneTitle(visibleTitle: String): String = DONE_PREFIX + visibleTitle.trim()
    }
}
