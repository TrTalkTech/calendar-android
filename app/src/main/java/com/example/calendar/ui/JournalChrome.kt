package com.example.calendar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.AppLocale
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import com.example.calendar.data.CalendarEvent
import com.example.calendar.ui.theme.MacColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

enum class AppSection {
    Calendar,
    Tasks,
    Settings,
}

private val locationUrlPattern = Regex("""(?i)\b((?:https?://|www\.)\S+)""")

fun locationAsUrl(location: String): String? {
    val trimmed = location.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    val found = locationUrlPattern.find(trimmed)?.value ?: return null
    return if (found.startsWith("www.", ignoreCase = true)) "https://$found" else found
}

@Composable
fun JournalModeTabs(
    mode: CalendarMode,
    onSelect: (CalendarMode) -> Unit,
    currentYear: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(MacColors.segmentBackground)
            .padding(3.dp),
    ) {
        JournalModeTab(
            label = stringResource(R.string.mode_month),
            selected = mode == CalendarMode.MonthDay,
            onClick = { onSelect(CalendarMode.MonthDay) },
            modifier = Modifier.weight(1f),
        )
        JournalModeTab(
            label = currentYear.toString(),
            selected = mode == CalendarMode.Year,
            onClick = { onSelect(CalendarMode.Year) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun JournalModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MacColors.todayRed else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
            color = if (selected) Color.White else MacColors.secondaryLabel,
        )
    }
}

@Composable
fun JournalWeekdayHeader(modifier: Modifier = Modifier) {
    val locale = AppLocale.display
    val first = WeekFields.of(locale).firstDayOfWeek
    Row(modifier = modifier.fillMaxWidth()) {
        repeat(7) { index ->
            val label = first.plus(index.toLong())
                .getDisplayName(TextStyle.SHORT, locale)
                .replace(".", "")
                .lowercase(locale)
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MacColors.secondaryLabel,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun JournalBottomBar(
    section: AppSection,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MacColors.contentBackground)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JournalNavItem(
            icon = Icons.Outlined.CalendarMonth,
            label = stringResource(R.string.nav_calendar),
            selected = section == AppSection.Calendar,
            onClick = { onSelect(AppSection.Calendar) },
        )
        JournalNavItem(
            icon = Icons.Outlined.CheckCircle,
            label = stringResource(R.string.nav_tasks),
            selected = section == AppSection.Tasks,
            onClick = { onSelect(AppSection.Tasks) },
        )
        JournalNavItem(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.nav_settings),
            selected = section == AppSection.Settings,
            onClick = { onSelect(AppSection.Settings) },
        )
    }
}

@Composable
private fun JournalNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MacColors.todayRed else MacColors.secondaryLabel
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
fun JournalDayTitle(
    date: LocalDate,
    today: LocalDate,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onShiftDay: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = date,
            transitionSpec = { periodSlide() },
            modifier = Modifier
                .weight(1f)
                .horizontalPeriodSwipe(onShiftDay),
            label = "dayTitle",
        ) { day ->
            val title = if (day == today) {
                stringResource(R.string.journal_today_title, RussianDateFormat.dayAndMonthGenitive(day))
            } else {
                val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, AppLocale.display)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(AppLocale.display) else it.toString() }
                "$weekday, ${RussianDateFormat.dayAndMonthGenitive(day)}"
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MacColors.primaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = RussianDateFormat.weekAndDayCaption(day),
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MacColors.secondaryLabel,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.search_events),
                tint = MacColors.secondaryLabel,
            )
        }
        IconButton(onClick = onAddClick) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.new_event_title),
                tint = MacColors.secondaryLabel,
            )
        }
    }
}

@Composable
fun JournalExpandIcon(expanded: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
            contentDescription = stringResource(
                if (expanded) R.string.journal_collapse_month else R.string.journal_expand_month,
            ),
            tint = MacColors.secondaryLabel,
        )
    }
}

@Composable
fun JournalEventCard(
    event: CalendarEvent,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm", AppLocale.display)
    val timeText = if (event.allDay) {
        stringResource(R.string.all_day)
    } else {
        val start = Instant.ofEpochMilli(event.beginMillis).atZone(zone)
        val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
        "${start.format(timeFmt)}–${end.format(timeFmt)}"
    }
    val url = EventConferenceLinks.joinUrl(event.location, event.notes)
    val participantNames = event.attendees.mapNotNull { person ->
        person.name?.trim()?.takeIf { it.isNotEmpty() && !it.contains('@') }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MacColors.contentBackground)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MacColors.todayRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (url != null) {
                Text(
                    text = compactLinkLabel(url),
                    modifier = Modifier
                        .widthIn(max = 132.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }
                        }
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MacColors.todayRed,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(
            text = event.title,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MacColors.primaryLabel,
        )
        if (participantNames.isNotEmpty()) {
            Text(
                text = participantNames.joinToString(" · "),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MacColors.secondaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactLinkLabel(url: String): String {
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
}
