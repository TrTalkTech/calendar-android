package com.example.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.AppLocale
import com.example.calendar.data.ProductionDay
import com.example.calendar.ui.theme.IphoneYearColors
import com.example.calendar.ui.theme.MacColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

private const val MaxEventDotsPerDayCell = 4
private val EventDotSize = 4.dp
private val EventDotSpacing = 3.dp

@Composable
fun MonthCalendarGrid(
    yearMonth: YearMonth,
    compact: Boolean,
    selectedDate: LocalDate,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    showWeekdayRow: Boolean = true,
    /** Цвета точек под числом: дата → цвета календарей (вкладка «Месяц»). */
    dayDotColorsByDate: Map<LocalDate, List<Int>> = emptyMap(),
    /** Равномерная сетка по высоте родителя — мини-месяцы на вкладке «Год». */
    denseYearLayout: Boolean = false,
    /** Тёмные цвета и типографика как в приложении «Календарь» на iPhone (год). */
    iphoneYearStyle: Boolean = false,
    /** Выбранный день — красный квадрат, как в электронном журнале. */
    journalHighlight: Boolean = false,
    productionDays: Map<LocalDate, ProductionDay> = emptyMap(),
) {
    val locale = AppLocale.display
    val weekFields = WeekFields.of(locale)
    val firstDayOfWeek = weekFields.firstDayOfWeek

    val weekdayLabels = remember(firstDayOfWeek, locale) {
        List(7) { i ->
            firstDayOfWeek.plus(i.toLong()).getDisplayName(DateTextStyle.NARROW, locale)
        }
    }

    val firstGridDay = remember(yearMonth, firstDayOfWeek) {
        yearMonth.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }

    val cellMinHeight: Dp = if (compact) 18.dp else 40.dp
    val dayTextStyle = if (denseYearLayout && iphoneYearStyle) {
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 10.sp,
            letterSpacing = (-0.35).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    } else if (denseYearLayout) {
        MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp)
    } else if (compact) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.32).sp)
    }
    val headerStyle = if (denseYearLayout) {
        MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp, lineHeight = 6.sp)
    } else if (compact) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }

    val cellShape = if (denseYearLayout) {
        RoundedCornerShape(if (iphoneYearStyle) 10.dp else 4.dp)
    } else {
        RoundedCornerShape(20.dp)
    }
    val cellPad = if (denseYearLayout) 0.dp else 1.dp

    // На годовом обзоре в стиле iPhone выделяем только сегодняшний день:
    // выбранная дата не должна оставлять второй кружок рядом с today.
    val showSelection = (!compact || denseYearLayout) && !iphoneYearStyle

    if (denseYearLayout) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            if (showWeekdayRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    weekdayLabels.forEach { label ->
                        Text(
                            text = label,
                            style = headerStyle,
                            color = MacColors.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!showWeekdayRow) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val gap = 1.dp
                        val gapsTotal = gap * 5
                        val cellSide = minOf(maxWidth / 7, (maxHeight - gapsTotal) / 6)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            repeat(6) { weekIndex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(cellSide),
                                ) {
                                    repeat(7) { dayIndex ->
                                        YearGridDayCell(
                                            weekIndex = weekIndex,
                                            dayIndex = dayIndex,
                                            firstGridDay = firstGridDay,
                                            yearMonth = yearMonth,
                                            today = today,
                                            selectedDate = selectedDate,
                                            showSelection = showSelection,
                                            onDayClick = onDayClick,
                                            dayTextStyle = dayTextStyle,
                                            cellShape = cellShape,
                                            cellPad = cellPad,
                                            hideAdjacentMonthNumbers = true,
                                            iphoneYearStyle = iphoneYearStyle,
                                            dayDotColorsByDate = dayDotColorsByDate,
                                            showDayEventDots = false,
                                            productionDays = productionDays,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    repeat(6) { weekIndex ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            repeat(7) { dayIndex ->
                                YearGridDayCell(
                                    weekIndex = weekIndex,
                                    dayIndex = dayIndex,
                                    firstGridDay = firstGridDay,
                                    yearMonth = yearMonth,
                                    today = today,
                                    selectedDate = selectedDate,
                                    showSelection = showSelection,
                                    onDayClick = onDayClick,
                                    dayTextStyle = dayTextStyle,
                                    cellShape = cellShape,
                                    cellPad = cellPad,
                                    hideAdjacentMonthNumbers = true,
                                    iphoneYearStyle = iphoneYearStyle,
                                    dayDotColorsByDate = dayDotColorsByDate,
                                    showDayEventDots = false,
                                    productionDays = productionDays,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showWeekdayRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = headerStyle,
                        color = MacColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!compact) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    color = MacColors.separator,
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        val monthWeekIndices = remember(yearMonth, firstGridDay) {
            (0 until 6).filter { weekIndex ->
                (0 until 7).any { dayIndex ->
                    val cellDate = firstGridDay.plusDays((weekIndex * 7 + dayIndex).toLong())
                    YearMonth.from(cellDate) == yearMonth
                }
            }
        }

        for (weekIndex in monthWeekIndices) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(7) { dayIndex ->
                    YearGridDayCell(
                        weekIndex = weekIndex,
                        dayIndex = dayIndex,
                        firstGridDay = firstGridDay,
                        yearMonth = yearMonth,
                        today = today,
                        selectedDate = selectedDate,
                        showSelection = showSelection,
                        onDayClick = onDayClick,
                        dayTextStyle = dayTextStyle,
                        cellShape = cellShape,
                        cellPad = cellPad,
                        hideAdjacentMonthNumbers = false,
                        iphoneYearStyle = false,
                        dayDotColorsByDate = dayDotColorsByDate,
                        showDayEventDots = !compact,
                        journalHighlight = journalHighlight,
                        productionDays = productionDays,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = cellMinHeight)
                            .aspectRatio(1f, matchHeightConstraintsFirst = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun YearGridDayCell(
    weekIndex: Int,
    dayIndex: Int,
    firstGridDay: LocalDate,
    yearMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    showSelection: Boolean,
    onDayClick: (LocalDate) -> Unit,
    dayTextStyle: TextStyle,
    cellShape: RoundedCornerShape,
    cellPad: Dp,
    /** Числа только у дней текущего месяца в мини-календаре года. */
    hideAdjacentMonthNumbers: Boolean = false,
    iphoneYearStyle: Boolean = false,
    dayDotColorsByDate: Map<LocalDate, List<Int>> = emptyMap(),
    showDayEventDots: Boolean = false,
    journalHighlight: Boolean = false,
    productionDays: Map<LocalDate, ProductionDay> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val cellDate = firstGridDay.plusDays((weekIndex * 7 + dayIndex).toLong())
    val eventDotColors = if (showDayEventDots) {
        dayDotColorsByDate[cellDate]?.take(MaxEventDotsPerDayCell) ?: emptyList()
    } else {
        emptyList()
    }
    val inMonth = cellDate.month == yearMonth.month && cellDate.year == yearMonth.year
    // Сегодняшний день есть и в хвосте сетки предыдущего месяца. Кружок только в его месяце,
    // иначе одна и та же дата подсвечивается дважды.
    val isToday = inMonth && cellDate == today
    val selectionInMonth = YearMonth.from(selectedDate) == yearMonth
    val isSelected =
        showSelection && !iphoneYearStyle && inMonth && selectionInMonth &&
            cellDate == selectedDate && (journalHighlight || !isToday)
    val journalSelected = journalHighlight && isSelected
    val todayOutline = journalHighlight && isToday && !isSelected
    val highlightShape = if (journalHighlight) RoundedCornerShape(8.dp) else cellShape

    val showDayNumber = when {
        hideAdjacentMonthNumbers && !inMonth -> false
        else -> true
    }

    val holidayNumber = inMonth && productionDays[cellDate]?.isNonWorking == true
    val textColor = when {
        !showDayNumber -> Color.Transparent
        journalSelected -> Color.White
        journalHighlight && isToday -> MacColors.todayRed
        iphoneYearStyle && isToday -> Color.White
        iphoneYearStyle && holidayNumber -> MacColors.todayRed
        iphoneYearStyle && inMonth -> MacColors.primaryLabel
        iphoneYearStyle -> Color.Transparent
        !inMonth -> MacColors.gridOutsideMonth.copy(alpha = 0.55f)
        isToday -> MacColors.contentBackground
        holidayNumber -> MacColors.todayRed
        else -> MacColors.primaryLabel
    }

    val todayBg = if (iphoneYearStyle) IphoneYearColors.accent else MacColors.todayRed
    val selectionBg = MacColors.selectionFill

    Box(
        modifier = modifier
            .padding(if (iphoneYearStyle) 0.dp else cellPad)
            .then(
                if (todayOutline) Modifier.border(1.5.dp, todayBg, highlightShape) else Modifier,
            )
            .then(
                if (iphoneYearStyle) {
                    Modifier
                } else {
                    Modifier
                        .clip(highlightShape)
                        .background(
                            when {
                                journalSelected -> todayBg
                                journalHighlight -> Color.Transparent
                                isToday -> todayBg
                                isSelected -> selectionBg
                                else -> Color.Transparent
                            },
                        )
                },
            )
            .clickable { onDayClick(cellDate) },
        contentAlignment = Alignment.Center,
    ) {
        if (iphoneYearStyle) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.78f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .background(todayBg, CircleShape),
                )
            }
            if (showDayNumber) {
                Text(
                    text = "${cellDate.dayOfMonth}",
                    style = dayTextStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (showDayNumber) "${cellDate.dayOfMonth}" else "",
                    style = dayTextStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (eventDotColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(EventDotSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        eventDotColors.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(EventDotSize)
                                    .clip(CircleShape)
                                    .background(Color(argb)),
                            )
                        }
                    }
                }
            }
        }
    }
}
