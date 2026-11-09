package com.example.calendar.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

class CalendarBootstrapPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isInitialLoadComplete(): Boolean = prefs.getBoolean(KEY_INITIAL_LOAD_DONE, false)

    fun markInitialLoadComplete() {
        prefs.edit().putBoolean(KEY_INITIAL_LOAD_DONE, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "calendar_bootstrap"
        private const val KEY_INITIAL_LOAD_DONE = "initial_load_done"
    }
}

object CalendarProviderWarmup {

    fun warmInstancesCache(
        contentResolver: ContentResolver,
        centerDate: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        monthsRadius: Long = 18,
    ) {
        val start = centerDate.minusMonths(monthsRadius).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = centerDate.plusMonths(monthsRadius).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, start)
            ContentUris.appendId(this, end)
        }.build()
        contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.EVENT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                // Прогрев кэша [CalendarContract.Instances] у провайдера.
            }
        }
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.DELETED} = ?",
            arrayOf("0"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                // Прогрев [CalendarContract.Events].
            }
        }
    }

    fun requestCalendarSync(context: Context) {
        val contentResolver = context.contentResolver
        val seen = LinkedHashSet<String>()
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            if (nameIdx < 0 || typeIdx < 0) return
            while (cursor.moveToNext()) {
                val accountName = cursor.getString(nameIdx) ?: continue
                val accountType = cursor.getString(typeIdx) ?: continue
                val key = "$accountType:$accountName"
                if (!seen.add(key)) continue
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

    /**
     * Синхронизация учёток, которым принадлежат [calendarIds].
     * null — все календари на устройстве. Пустой набор — обновлять нечего.
     * Android обновляет календарь целиком по учётке, отдельный id календаря в requestSync не передаётся.
     */
    fun requestSyncForCalendarIds(context: Context, calendarIds: Set<Long>?): List<Account> {
        if (calendarIds != null && calendarIds.isEmpty()) return emptyList()
        val contentResolver = context.contentResolver
        val selection = buildString {
            append("${CalendarContract.Calendars.DELETED} = ?")
            if (calendarIds != null) {
                append(" AND ${CalendarContract.Calendars._ID} IN (")
                append(calendarIds.joinToString(","))
                append(")")
            }
        }
        val accounts = LinkedHashMap<String, Account>()
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
            ),
            selection,
            arrayOf("0"),
            null,
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            if (nameIdx < 0 || typeIdx < 0) return emptyList()
            while (cursor.moveToNext()) {
                val accountName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                val accountType = cursor.getString(typeIdx)?.takeIf { it.isNotBlank() } ?: continue
                accounts.putIfAbsent("$accountType:$accountName", Account(accountName, accountType))
            }
        }
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        accounts.values.forEach { account ->
            runCatching {
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
            }
        }
        return accounts.values.toList()
    }

    suspend fun awaitCalendarSync(accounts: List<Account>, timeoutMs: Long = 8_000L) {
        if (accounts.isEmpty()) return
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        delay(400)
        while (SystemClock.elapsedRealtime() < deadline) {
            val busy = runCatching {
                accounts.any { account ->
                    ContentResolver.isSyncActive(account, CalendarContract.AUTHORITY) ||
                        ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY)
                }
            }.getOrElse {
                // READ_SYNC_STATS обычным приложениям недоступен: статус не читаем, ждём коротко и выходим.
                delay(1_600)
                return
            }
            if (!busy) return
            delay(300)
        }
    }
}
