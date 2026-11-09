package com.example.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Обновление всех виджетов приложения (1×1, 2×2 и 4×2). */
internal object CalendarWidgets {

    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val dateIds = mgr.getAppWidgetIds(
            ComponentName(context, CalendarDateWidgetProvider::class.java),
        )
        for (id in dateIds) {
            updateDateAppWidget(context, mgr, id)
        }
        val monthIds = CalendarMonthWidgetProvider.appWidgetIds(context)
        for (id in monthIds) {
            updateMonthGridWidget(context, mgr, id)
        }
        val agendaIds = CalendarAgendaWidgetProvider.appWidgetIds(context)
        for (id in agendaIds) {
            updateAgendaWidget(context, mgr, id)
        }
    }

    fun hasAnyWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        val dateIds = mgr.getAppWidgetIds(
            ComponentName(context, CalendarDateWidgetProvider::class.java),
        )
        if (dateIds.isNotEmpty()) return true
        if (CalendarMonthWidgetProvider.appWidgetIds(context).isNotEmpty()) return true
        return CalendarAgendaWidgetProvider.appWidgetIds(context).isNotEmpty()
    }
}
