package com.example.calendar.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.calendar.R
import com.example.calendar.widget.LauncherIconMasks.scaledTo
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Плитка 1×1 в оформлении виджета с laktapp.ru, обрезанная по выбранной форме. */
internal object DateWidgetRenderer {

    private const val ColorCard = 0xFFFFFFFF.toInt()
    private const val ColorLine = 0xFFD1D1D6.toInt()
    private const val ColorToday = 0xFFFF3B30.toInt()
    private const val ColorText = 0xFF1D1D1F.toInt()
    private const val ColorMuted = 0xFF6E6E73.toInt()

    fun render(
        context: Context,
        sizePx: Int,
        date: LocalDate,
        locale: Locale,
        mask: LauncherIconMask,
        todayColor: Int = ColorToday,
    ): Bitmap {
        val size = sizePx.coerceIn(128, 384)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pad = 0f
        val tile = RectF(pad, pad, size - pad, size - pad)
        val path = mask.path100.scaledTo(tile)
        val headerRatio = when (mask.shape) {
            LauncherIconShape.CIRCLE -> 0.24f
            LauncherIconShape.ROUNDED -> 0.21f
            LauncherIconShape.SQUARE -> 0.20f
        }
        val headerBottom = tile.top + tile.height() * headerRatio
        val sideInset = when (mask.shape) {
            LauncherIconShape.CIRCLE -> tile.width() * 0.18f
            LauncherIconShape.ROUNDED -> tile.width() * 0.08f
            LauncherIconShape.SQUARE -> tile.width() * 0.06f
        }

        val month = date.month
            .getDisplayName(TextStyle.SHORT, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            .uppercase(locale)
        val weekday = date.dayOfWeek
            .getDisplayName(TextStyle.SHORT, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val day = date.dayOfMonth.toString()

        val bold = font(context, R.font.geist_bold, Typeface.DEFAULT_BOLD)
        val regular = font(context, R.font.geist_regular, Typeface.DEFAULT)

        canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(ColorCard)
        canvas.drawRect(tile.left, tile.top, tile.right, headerBottom, paint(todayColor))

        val monthPaint = textPaint(ColorCard, bold, tile.width() * 0.115f).apply {
            letterSpacing = 0.08f
            isFakeBoldText = false
        }
        val monthCenterY = (tile.top + headerBottom) / 2f -
            (monthPaint.ascent() + monthPaint.descent()) / 2f
        canvas.drawText(month, tile.centerX(), monthCenterY, monthPaint)

        val weekdayPaint = textPaint(ColorMuted, regular, tile.width() * 0.12f)
        val weekdayBaseline = tile.bottom - tile.height() * 0.09f
        canvas.drawText(weekday, tile.centerX(), weekdayBaseline, weekdayPaint)

        val dayMaxWidth = tile.width() - sideInset * 2f
        val dayPaint = textPaint(ColorText, bold, tile.width() * 0.46f).apply {
            letterSpacing = -0.04f
        }
        while (dayPaint.textSize > tile.width() * 0.2f && dayPaint.measureText(day) > dayMaxWidth) {
            dayPaint.textSize *= 0.94f
        }
        val whiteTop = headerBottom
        val whiteBottom = weekdayBaseline + weekdayPaint.ascent()
        val dayCenterY = (whiteTop + whiteBottom) / 2f
        val dayBaseline = dayCenterY - (dayPaint.ascent() + dayPaint.descent()) / 2f
        canvas.drawText(day, tile.centerX(), dayBaseline, dayPaint)
        canvas.restore()

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ColorLine
            strokeWidth = (size / 160f).coerceAtLeast(1f)
        }
        canvas.drawPath(path, stroke)
        return bitmap
    }

    fun iconSizePx(context: Context, widthDp: Int = 0): Int {
        val density = context.resources.displayMetrics.density
        // Иконки занимают ~80% ширины ячейки; полная ширина виджета даёт плитку крупнее соседей.
        val fromCell = if (widthDp > 0) (widthDp * 0.8f * density).roundToInt() else 0
        val fallback = (58f * density).roundToInt()
        return maxOf(fromCell, fallback).coerceIn(128, 320)
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

    private fun paint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun textPaint(color: Int, typeface: Typeface, size: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.typeface = typeface
            textSize = size
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
}
