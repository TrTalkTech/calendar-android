package com.example.calendar.data

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.CalendarContract
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

/**
 * Метка задач, созданных Calendar 2.0.
 * Нужна, чтобы не прятать обычные all-day FREE встречи Exchange как «задачи».
 */
object AppTaskMarker {
    const val PROPERTY_NAME = "ru.laktapp.calendar.TASK"
    const val PROPERTY_VALUE = "1"
    private const val TAG = "AppTaskMarker"

    fun mark(contentResolver: ContentResolver, eventId: Long) {
        if (eventId <= 0L) return
        try {
            contentResolver.delete(
                CalendarContract.ExtendedProperties.CONTENT_URI,
                "${CalendarContract.ExtendedProperties.EVENT_ID}=? AND " +
                    "${CalendarContract.ExtendedProperties.NAME}=?",
                arrayOf(eventId.toString(), PROPERTY_NAME),
            )
            val values = ContentValues().apply {
                put(CalendarContract.ExtendedProperties.EVENT_ID, eventId)
                put(CalendarContract.ExtendedProperties.NAME, PROPERTY_NAME)
                put(CalendarContract.ExtendedProperties.VALUE, PROPERTY_VALUE)
            }
            contentResolver.insert(CalendarContract.ExtendedProperties.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.w(TAG, "mark failed eventId=$eventId", e)
        }
    }

    fun markedEventIds(contentResolver: ContentResolver, eventIds: Collection<Long>): Set<Long> {
        val ids = eventIds.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return emptySet()
        val marked = mutableSetOf<Long>()
        ids.chunked(40).forEach { chunk ->
            val selection =
                "${CalendarContract.ExtendedProperties.EVENT_ID} IN (${chunk.joinToString(",") { "?" }}) AND " +
                    "${CalendarContract.ExtendedProperties.NAME}=?"
            val args = chunk.map { it.toString() } + PROPERTY_NAME
            try {
                contentResolver.query(
                    CalendarContract.ExtendedProperties.CONTENT_URI,
                    arrayOf(CalendarContract.ExtendedProperties.EVENT_ID),
                    selection,
                    args.toTypedArray(),
                    null,
                )?.use { cursor ->
                    val idx = cursor.getColumnIndex(CalendarContract.ExtendedProperties.EVENT_ID)
                    if (idx < 0) return@use
                    while (cursor.moveToNext()) {
                        cursor.getLongOrNull(idx)?.let { marked += it }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "query marked failed", e)
            }
        }
        return marked
    }

    fun isAppTaskEvent(
        event: CalendarEvent,
        markedIds: Set<Long>,
    ): Boolean {
        if (event.eventId in markedIds) return true
        return CalendarTaskRepository.hasDonePrefix(event.title)
    }
}
