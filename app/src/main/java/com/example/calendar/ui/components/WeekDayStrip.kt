package com.example.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.AppLocale
import com.example.calendar.data.ProductionDay
import com.example.calendar.ui.theme.MacColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private const val MaxDotsInStrip = 3
private val StripDotSize = 3.dp

fun weekStartContaining(date: LocalDate, locale: Locale = AppLocale.display): LocalDate {
    val weekFields = WeekFields.of(locale)
    return date.with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
}

@Composable
fun WeekDayStrip(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    focusMonth: YearMonth,
    dayDotColorsByDate: Map<LocalDate, List<Int>>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    journalStyle: Boolean = false,
    productionDays: Map<LocalDate, ProductionDay> = emptyMap(),
) {
    val locale = AppLocale.display

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (journalStyle) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MacColors.contentBackground)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            repeat(7) { i ->
                val day = weekStart.plusDays(i.toLong())
                WeekStripDayCell(
                    day = day,
                    selectedDate = selectedDate,
                    today = today,
                    focusMonth = focusMonth,
                    dayDotColors = dayDotColorsByDate[day]?.take(MaxDotsInStrip) ?: emptyList(),
                    onClick = { onDayClick(day) },
                    locale = locale,
                    journalStyle = journalStyle,
                    productionDay = productionDays[day],
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekStripDayCell(
    day: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    focusMonth: YearMonth,
    dayDotColors: List<Int>,
    onClick: () -> Unit,
    locale: Locale,
    journalStyle: Boolean = false,
    productionDay: ProductionDay? = null,
    modifier: Modifier = Modifier,
) {
    val inFocusMonth = YearMonth.from(day) == focusMonth
    val isToday = day == today
    val isSelected = if (journalStyle) day == selectedDate else day == selectedDate && !isToday
    val todayOutline = journalStyle && isToday && !isSelected
    val cellShape = RoundedCornerShape(10.dp)

    val holidayNumber = inFocusMonth && productionDay?.isNonWorking == true
    val numColor = when {
        journalStyle && isSelected -> Color.White
        journalStyle && isToday -> MacColors.todayRed
        journalStyle && !inFocusMonth -> MacColors.gridOutsideMonth.copy(alpha = 0.65f)
        journalStyle && holidayNumber -> MacColors.todayRed
        journalStyle -> MacColors.primaryLabel
        isToday -> Color.White
        !inFocusMonth -> MacColors.gridOutsideMonth.copy(alpha = 0.65f)
        holidayNumber -> MacColors.todayRed
        else -> MacColors.primaryLabel
    }
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)

    val todayBg = MacColors.todayRed
    val selectionBg = MacColors.selectionFill

    Column(
        modifier = modifier
            .then(
                if (todayOutline) Modifier.border(1.5.dp, todayBg, cellShape) else Modifier,
            )
            .clip(cellShape)
            .background(
                when {
                    journalStyle && isSelected -> todayBg
                    journalStyle -> Color.Transparent
                    isToday -> todayBg
                    isSelected -> selectionBg
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!journalStyle) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) Color.White.copy(alpha = 0.9f) else MacColors.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = "${day.dayOfMonth}",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                lineHeight = 21.sp,
            ),
            color = numColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (dayDotColors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dayDotColors.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(StripDotSize)
                            .clip(CircleShape)
                            .background(Color(argb)),
                    )
                }
            }
        }
    }
}
