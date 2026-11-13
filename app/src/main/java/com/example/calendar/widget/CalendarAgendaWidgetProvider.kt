package com.example.calendar.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.calendar.AppLocale
import com.example.calendar.MainActivity
import com.example.calendar.R
import com.example.calendar.data.AccountSlots
import com.example.calendar.data.AccountSlotsPreferences
import com.example.calendar.data.AppTaskMarker
import com.example.calendar.data.CalendarEvent
import com.example.calendar.data.CalendarEventWriter
import com.example.calendar.data.CalendarRepository
import com.example.calendar.data.VisibleCalendarsPreferences
import com.example.calendar.data.filterIdsForEventLists
import com.example.calendar.data.isHiddenExchangeTask
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun updateAgendaWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {
    val views = RemoteViews(context.packageName, R.layout.widget_agenda)
    val today = LocalDate.now()
    val nowMillis = System.currentTimeMillis()
    val todaysEvents = loadTodayEvents(context, today)
    val bitmap = drawAgendaWidgetBitmap(
        context = context,
        options = appWidgetManager.getAppWidgetOptions(appWidgetId),
        today = today,
        events = remainingEventsForNow(todaysEvents, nowMillis),
    )
    views.setImageViewBitmap(R.id.widget_agenda_root, bitmap)
    WidgetAgendaTickScheduler.schedule(context, nextAgendaTickMillis(todaysEvents, nowMillis))

    val openApp = PendingIntent.getActivity(
        context,
        appWidgetId + 20_000,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_agenda_root, openApp)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Виджет 4×2 в формате HyperOS: слева день недели, крупная дата
 * и список событий сегодня, справа мини-сетка месяца.
 */
class CalendarAgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateAgendaWidget(context, appWidgetManager, id)
        }
        WidgetMidnightScheduler.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAgendaWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        WidgetMidnightScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetMidnightScheduler.cancelIfNoWidgets(context)
        WidgetAgendaTickScheduler.cancelIfNoAgendaWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetAgendaTickScheduler.ACTION_WIDGET_AGENDA_TICK -> {
                val mgr = AppWidgetManager.getInstance(context)
                for (id in appWidgetIds(context)) {
                    updateAgendaWidget(context, mgr, id)
                }
                return
            }
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
                ComponentName(context, CalendarAgendaWidgetProvider::class.java),
            )
    }
}

private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", AppLocale.display)
private val timeFormatter = DateTimeFormatter.ofPattern("H:mm", AppLocale.display)

private fun loadTodayEvents(context: Context, today: LocalDate): List<CalendarEvent> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    return try {
        val calendars = CalendarEventWriter(context.contentResolver).queryVisibleCalendars()
        val filter = filterIdsForEventLists(
            VisibleCalendarsPreferences(context).readFilter(),
            calendars,
        )
        val slots = AccountSlotsPreferences(context).readOrSeed(calendars)
        val hideTasks = AccountSlots.exchangeTaskWriteCalendarIds(slots, calendars)
        val dayEvents = CalendarRepository(context.contentResolver, context).queryEventsForDay(
            date = today,
            visibleCalendarIds = filter,
        ).events
        val appTaskIds = AppTaskMarker.markedEventIds(
            context.contentResolver,
            dayEvents.map { it.eventId },
        )
        dayEvents.filterNot { it.isHiddenExchangeTask(hideTasks, appTaskIds) }
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun drawAgendaWidgetBitmap(
    context: Context,
    options: Bundle,
    today: LocalDate,
    events: List<CalendarEvent>,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val (width, height) = widgetBitmapSizePx(
        context = context,
        options = options,
        density = density,
        fallbackWidthDp = 320,
        fallbackHeightDp = 146,
    )

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.density = context.resources.displayMetrics.densityDpi
    val canvas = Canvas(bitmap)
    drawWidgetGlass(canvas, width, height, density)

    val geistBold = context.resources.getFont(R.font.geist_bold)
    val geistMedium = context.resources.getFont(R.font.geist_medium)
    val geistRegular = context.resources.getFont(R.font.geist_regular)

    val padX = width * 0.055f
    val padY = height * 0.10f
    val yearMonth = YearMonth.from(today)
    val weekRows = monthWeekRows(yearMonth)
    val cell = widgetMonthCellPx(height, padY, weekRows)
    val gridWidth = cell * 7f
    val gridLeft = width - padX - gridWidth
    val dateRight = gridLeft - width * 0.04f

    val weekday = weekdayFormatter.format(today).replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(AppLocale.display) else ch.toString()
    }
    val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        textSize = height * 0.078f
        typeface = geistMedium
    }
    val weekdayY = padY - weekdayPaint.fontMetrics.ascent
    canvas.drawText(weekday, padX, weekdayY, weekdayPaint)

    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = widgetAccentArgb(context)
        textSize = height * 0.17f
        typeface = geistBold
    }
    val dateY = weekdayY + weekdayPaint.textSize * 0.08f - datePaint.fontMetrics.ascent
    canvas.drawText(today.dayOfMonth.toString(), padX, dateY, datePaint)

    val eventsTop = dateY + datePaint.fontMetrics.descent + height * 0.02f
    drawTodayEvents(
        canvas = canvas,
        context = context,
        events = events,
        left = padX,
        top = eventsTop,
        right = dateRight,
        bottom = height - padY,
        regular = geistRegular,
        medium = geistMedium,
        density = density,
        textSizePx = (height * 0.10f).coerceIn(28f, 40f),
    )

    drawMiniMonth(
        canvas = canvas,
        today = today,
        yearMonth = yearMonth,
        weekRows = weekRows,
        left = gridLeft,
        top = padY,
        cell = cell,
        bold = geistBold,
        regular = geistRegular,
        todayColor = widgetAccentArgb(context),
    )
    return bitmap
}

private fun drawTodayEvents(
    canvas: Canvas,
    context: Context,
    events: List<CalendarEvent>,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    regular: android.graphics.Typeface,
    medium: android.graphics.Typeface,
    density: Float,
    textSizePx: Float,
) {
    val areaHeight = bottom - top
    if (areaHeight <= 0f || right <= left) return

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = textSizePx
        typeface = medium
    }
    val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        textSize = titlePaint.textSize * 0.92f
        typeface = regular
    }
    val lineHeight = titlePaint.textSize * 1.45f
    val maxRows = (areaHeight / lineHeight).toInt().coerceAtLeast(1)

    if (events.isEmpty()) {
        canvas.drawText(
            ellipsize(titlePaint, context.getString(R.string.widget_agenda_empty), right - left),
            left,
            top - titlePaint.fontMetrics.ascent,
            titlePaint.apply { color = 0xCCFFFFFF.toInt() },
        )
        return
    }

    val showMore = events.size > maxRows
    val visibleCount = if (showMore) (maxRows - 1).coerceAtLeast(1) else events.size
    val zone = ZoneId.systemDefault()
    val visible = events.take(visibleCount)
    val timeLabels = visible.map { event ->
        if (event.allDay) {
            context.getString(R.string.all_day)
        } else {
            Instant.ofEpochMilli(event.beginMillis).atZone(zone).toLocalTime().format(timeFormatter)
        }
    }
    val timeWidth = (timeLabels.maxOf { timePaint.measureText(it) } + density * 8f)
    val barWidth = density * 2.4f
    val barGap = density * 6f

    visible.forEachIndexed { index, event ->
        val y = top + lineHeight * index - titlePaint.fontMetrics.ascent
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = event.color ?: 0xFF4F8EF5.toInt()
        }
        val barTop = y + titlePaint.fontMetrics.ascent + density
        val barBottom = y + titlePaint.fontMetrics.descent - density
        canvas.drawRoundRect(
            RectF(left, barTop, left + barWidth, barBottom),
            barWidth,
            barWidth,
            barPaint,
        )
        val textLeft = left + barWidth + barGap
        canvas.drawText(timeLabels[index], textLeft, y, timePaint)
        val titleLeft = textLeft + timeWidth
        val title = event.title.ifBlank { context.getString(R.string.event_no_title) }
        canvas.drawText(
            ellipsize(titlePaint, title, right - titleLeft),
            titleLeft,
            y,
            titlePaint,
        )
    }

    if (showMore && visibleCount < maxRows) {
        val morePaint = Paint(timePaint)
        val y = top + lineHeight * visibleCount - morePaint.fontMetrics.ascent
        if (y + morePaint.fontMetrics.descent <= bottom + 2f) {
            canvas.drawText(
                context.getString(R.string.widget_agenda_more, events.size - visibleCount),
                left,
                y,
                morePaint,
            )
        }
    }
}

private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
    if (maxWidth <= 0f) return ""
    if (paint.measureText(text) <= maxWidth) return text
    var trimmed = text
    while (trimmed.isNotEmpty() && paint.measureText("$trimmed…") > maxWidth) {
        trimmed = trimmed.dropLast(1)
    }
    return if (trimmed.isEmpty()) "…" else "$trimmed…"
}

