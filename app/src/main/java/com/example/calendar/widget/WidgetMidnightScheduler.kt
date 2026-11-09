package com.example.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

internal object WidgetMidnightScheduler {

    const val ACTION_WIDGET_MIDNIGHT = "com.example.calendar.action.WIDGET_MIDNIGHT"

    private fun midnightAlarmIntent(appContext: Context): Intent =
        Intent(appContext, CalendarDateWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_MIDNIGHT
            data = Uri.parse("app://${appContext.packageName}/widget/midnight-alarm")
        }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val zone = ZoneId.systemDefault()
        val triggerAt = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .plusSeconds(3)
            .toInstant()
            .toEpochMilli()

        val intent = midnightAlarmIntent(appContext)
        val pending = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val intent = midnightAlarmIntent(appContext)
        val pending = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    /** Отменяет будильник только если на экране не осталось ни одного виджета приложения. */
    fun cancelIfNoWidgets(context: Context) {
        if (!CalendarWidgets.hasAnyWidgets(context)) {
            cancel(context)
        }
    }
}
