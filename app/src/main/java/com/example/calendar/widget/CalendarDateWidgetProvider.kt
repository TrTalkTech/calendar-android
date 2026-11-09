package com.example.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.calendar.MainActivity
import com.example.calendar.R
import java.time.LocalDate
import java.util.Locale

private val widgetLocale: Locale = Locale.forLanguageTag("ru-RU")

internal fun updateDateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {
    val views = RemoteViews(context.packageName, R.layout.widget_calendar_ios)
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val iconPx = DateWidgetRenderer.iconSizePx(context, widthDp)
    val shape = DateWidgetShapePreferences(context).read()
    val face = DateWidgetRenderer.render(
        context = context,
        sizePx = iconPx,
        date = LocalDate.now(),
        locale = widgetLocale,
        mask = LauncherIconMasks.forShape(shape),
        todayColor = widgetAccentArgb(context),
    )
    views.setImageViewBitmap(R.id.widget_face, face)
    views.setTextViewText(R.id.widget_app_label, context.getString(R.string.app_name))
    views.setContentDescription(
        R.id.widget_face,
        context.getString(R.string.app_name),
    )

    val openApp = PendingIntent.getActivity(
        context,
        appWidgetId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_root, openApp)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Виджет 1×1 в оформлении плитки с laktapp.ru.
 * Форму (квадрат / скругление / круг) выбирает пользователь.
 */
class CalendarDateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateDateAppWidget(context, appWidgetManager, id)
        }
        WidgetMidnightScheduler.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        updateDateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        WidgetMidnightScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetMidnightScheduler.cancelIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            WidgetMidnightScheduler.ACTION_WIDGET_MIDNIGHT,
            -> {
                CalendarWidgets.refreshAll(context)
                WidgetMidnightScheduler.schedule(context)
                return
            }
        }
        super.onReceive(context, intent)
    }
}
