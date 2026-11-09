package com.example.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.RemoteViews
import com.example.calendar.MainActivity
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import java.time.LocalDate
import java.time.YearMonth

internal fun updateMonthGridWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {
    val views = RemoteViews(context.packageName, R.layout.widget_month_grid)
    val bitmap = drawMonthWidgetBitmap(
        context,
        appWidgetManager.getAppWidgetOptions(appWidgetId),
        LocalDate.now(),
    )
    views.setImageViewBitmap(R.id.widget_month_root, bitmap)

    val openApp = PendingIntent.getActivity(
        context,
        appWidgetId + 10_000,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_month_root, openApp)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Виджет 2×2: заголовок «месяц год» и та же сетка, что справа на широком виджете.
 */
class CalendarMonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateMonthGridWidget(context, appWidgetManager, id)
        }
        WidgetMidnightScheduler.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateMonthGridWidget(context, appWidgetManager, appWidgetId)
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

    companion object {
        fun appWidgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, CalendarMonthWidgetProvider::class.java),
            )
    }
}

/**
 * 2×2 — заголовок «Октябрь 2026» и сетка как справа на широком виджете.
 */
private fun drawMonthWidgetBitmap(context: Context, options: Bundle, today: LocalDate): Bitmap {
    val density = context.resources.displayMetrics.density
    val (width, height) = widgetBitmapSizePx(
        context = context,
        options = options,
        density = density,
        fallbackWidthDp = 160,
        fallbackHeightDp = 146,
    )

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.density = context.resources.displayMetrics.densityDpi
    val canvas = Canvas(bitmap)
    drawWidgetGlass(canvas, width, height, density)

    val padX = width * 0.07f
    val geistBold = context.resources.getFont(R.font.geist_bold)
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = height * 0.09f
        typeface = geistBold
    }
    val titleMetrics = titlePaint.fontMetrics
    val titleHeight = -titleMetrics.ascent + titleMetrics.descent
    val titleGridGap = height * 0.02f
    val yearMonth = YearMonth.from(today)
    val weekRows = monthWeekRows(yearMonth)

    var cell = (width - padX * 2f) / 7f
    var gridHeight = miniMonthContentHeight(cell, weekRows, geistBold)
    val minPad = height * 0.06f
    val block = titleHeight + titleGridGap + gridHeight
    var padY = (height - block) / 2f
    if (padY < minPad) {
        val targetGrid = (height - titleHeight - titleGridGap - minPad * 2f).coerceAtLeast(1f)
        cell *= targetGrid / gridHeight
        gridHeight = miniMonthContentHeight(cell, weekRows, geistBold)
        padY = (height - titleHeight - titleGridGap - gridHeight) / 2f
    }

    val titleBaseline = padY - titleMetrics.ascent
    canvas.drawText(
        RussianDateFormat.monthYearHeader(yearMonth),
        padX,
        titleBaseline,
        titlePaint,
    )
    val gridTop = titleBaseline + titleMetrics.descent + titleGridGap
    val gridLeft = (width - cell * 7f) / 2f

    drawMiniMonth(
        canvas = canvas,
        today = today,
        yearMonth = yearMonth,
        weekRows = weekRows,
        left = gridLeft,
        top = gridTop,
        cell = cell,
        bold = geistBold,
        regular = context.resources.getFont(R.font.geist_regular),
        todayColor = widgetAccentArgb(context),
    )
    return bitmap
}
