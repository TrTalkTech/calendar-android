package com.example.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import com.example.calendar.AppLocale
import com.example.calendar.data.AppThemePreferences
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.max
import kotlin.math.min

internal const val WIDGET_DATE_RED = 0xFFFF3B30.toInt()

internal fun widgetAccentArgb(context: Context): Int =
    AppThemePreferences(context).readAccent().argb()
internal const val WIDGET_GLASS = 0x8A141418.toInt()
internal const val WIDGET_GLASS_STROKE = 0x33FFFFFF

internal val widgetWeekdayLabels = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

internal fun monthWeekRows(yearMonth: YearMonth): Int {
    val first = yearMonth.atDay(1).with(
        TemporalAdjusters.previousOrSame(WeekFields.of(AppLocale.display).firstDayOfWeek),
    )
    val last = yearMonth.atEndOfMonth()
    return ((last.toEpochDay() - first.toEpochDay()).toInt() / 7) + 1
}

/** Размер клетки сетки — как на правой половине широкого виджета. */
internal fun widgetMonthCellPx(height: Int, padY: Float, weekRows: Int): Float {
    val gridAvailH = height - padY * 2f
    return (gridAvailH / (weekRows + 0.9f)).coerceAtMost(height * 0.145f)
}

internal fun drawWidgetGlass(canvas: Canvas, width: Int, height: Int, density: Float) {
    val radius = min(width, height) * 0.14f
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(
        bounds,
        radius,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WIDGET_GLASS },
    )
    canvas.drawRoundRect(
        bounds.insetCopy(density * 0.5f),
        radius,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = WIDGET_GLASS_STROKE
        },
    )
}

internal fun drawMiniMonth(
    canvas: Canvas,
    today: LocalDate,
    yearMonth: YearMonth,
    weekRows: Int,
    left: Float,
    top: Float,
    cell: Float,
    bold: Typeface,
    regular: Typeface,
    todayColor: Int = WIDGET_DATE_RED,
) {
    if (cell <= 0f) return

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = cell * 0.42f
        textAlign = Paint.Align.CENTER
        typeface = bold
    }
    val headerBaseline = top - headerPaint.fontMetrics.ascent
    widgetWeekdayLabels.forEachIndexed { index, label ->
        canvas.drawText(
            label,
            left + cell * index + cell / 2f,
            headerBaseline,
            headerPaint,
        )
    }

    val gridTop = headerBaseline + headerPaint.textSize * 0.35f
    val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = cell * 0.52f
        textAlign = Paint.Align.CENTER
        typeface = regular
    }
    val todayFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = todayColor }
    val todayText = Paint(dayPaint).apply { color = Color.WHITE }

    val firstGridDay = yearMonth.atDay(1).with(
        TemporalAdjusters.previousOrSame(WeekFields.of(AppLocale.display).firstDayOfWeek),
    )
    for (index in 0 until weekRows * 7) {
        val date = firstGridDay.plusDays(index.toLong())
        if (date.month != yearMonth.month || date.year != yearMonth.year) continue
        val cx = left + cell * (index % 7) + cell / 2f
        val cy = gridTop + cell * (index / 7) + cell / 2f
        val paint = if (date == today) {
            canvas.drawCircle(cx, cy, cell * 0.38f, todayFill)
            todayText
        } else {
            dayPaint
        }
        val metrics = paint.fontMetrics
        canvas.drawText(
            date.dayOfMonth.toString(),
            cx,
            cy - (metrics.ascent + metrics.descent) / 2f,
            paint,
        )
    }
}

/** Высота блока сетки от [top] до низа последней строки — как рисует [drawMiniMonth]. */
internal fun miniMonthContentHeight(cell: Float, weekRows: Int, bold: Typeface): Float {
    if (cell <= 0f) return 0f
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cell * 0.42f
        typeface = bold
    }
    val headerFromTop = -headerPaint.fontMetrics.ascent
    val gridTopFromTop = headerFromTop + headerPaint.textSize * 0.35f
    return gridTopFromTop + weekRows * cell
}

/**
 * В портрете лаунчер отдаёт текущую ячейку как minWidth × maxHeight.
 * max(min, max) берёт ландшафтную ширину — картинка потом сплющивается.
 */
internal fun widgetBitmapSizePx(
    context: Context,
    options: Bundle,
    density: Float,
    fallbackWidthDp: Int,
    fallbackHeightDp: Int,
): Pair<Int, Int> {
    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
    val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val widthDp = (if (portrait) minW else maxW).takeIf { it >= 80 }
        ?: max(minW, maxW).takeIf { it >= 80 }
        ?: fallbackWidthDp
    val heightDp = (if (portrait) maxH else minH).takeIf { it >= 60 }
        ?: max(minH, maxH).takeIf { it >= 60 }
        ?: fallbackHeightDp
    return (widthDp * density).toInt().coerceIn(1, 1600) to
        (heightDp * density).toInt().coerceIn(1, 900)
}

internal fun RectF.insetCopy(inset: Float): RectF =
    RectF(left + inset, top + inset, right - inset, bottom - inset)
