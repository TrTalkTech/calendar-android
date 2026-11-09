package com.example.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.calendar.data.CalendarEvent

internal object WidgetAgendaTickScheduler {

    const val ACTION_WIDGET_AGENDA_TICK = "com.example.calendar.action.WIDGET_AGENDA_TICK"

    private const val MIN_LEAD_MS = 15_000L

    private fun tickIntent(appContext: Context): Intent =
        Intent(appContext, CalendarAgendaWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_AGENDA_TICK
            data = Uri.parse("app://${appContext.packageName}/widget/agenda-tick")
        }

    fun schedule(context: Context, triggerAtMillis: Long?) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            appContext,
            1,
            tickIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        if (CalendarAgendaWidgetProvider.appWidgetIds(appContext).isEmpty()) return
        val at = triggerAtMillis ?: return
        if (at <= System.currentTimeMillis() + MIN_LEAD_MS) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancelIfNoAgendaWidgets(context: Context) {
        if (CalendarAgendaWidgetProvider.appWidgetIds(context).isEmpty()) {
            val appContext = context.applicationContext
            val pending = PendingIntent.getBroadcast(
                appContext,
                1,
                tickIntent(appContext),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        }
    }
}

/** Все дневные и те, что ещё не закончились. */
internal fun remainingEventsForNow(
    events: List<CalendarEvent>,
    nowMillis: Long,
): List<CalendarEvent> = events.filter { event ->
    event.allDay || event.endMillis > nowMillis
}

/** Ближайшее начало или конец события — чтобы список на виджете сменился сам. */
internal fun nextAgendaTickMillis(
    events: List<CalendarEvent>,
    nowMillis: Long,
): Long? = events.asSequence()
    .filterNot { it.allDay }
    .flatMap { sequenceOf(it.beginMillis, it.endMillis) }
    .filter { it > nowMillis + 15_000L }
    .minOrNull()
