package com.example.calendar.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.calendar.AppLocale
import com.example.calendar.R
import com.example.calendar.data.ContactSuggestionsRepository
import com.example.calendar.data.EventAttachment
import com.example.calendar.data.EventInvitee
import com.example.calendar.data.EventRsvpAnswer
import com.example.calendar.ui.theme.MacColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EventViewActionHeight = 36.dp

@Composable
fun EventViewTopBar(
    monthLabel: String,
    centerTitle: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    showEdit: Boolean = true,
    onForward: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = MacColors.segmentBackground,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.titleMedium,
                    color = MacColors.primaryLabel,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.primaryLabel,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = centerTitle,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MacColors.primaryLabel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onForward != null || showEdit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onForward != null) {
                    Surface(
                        onClick = onForward,
                        shape = CircleShape,
                        color = MacColors.segmentBackground,
                        modifier = Modifier.size(EventViewActionHeight),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.event_forward),
                                tint = MacColors.primaryLabel,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (showEdit) {
                    Surface(
                        onClick = onEdit,
                        shape = CircleShape,
                        color = MacColors.segmentBackground,
                        modifier = Modifier.height(EventViewActionHeight),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(EventViewActionHeight)
                                .padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.edit_action),
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                                color = MacColors.primaryLabel,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
fun EventViewBody(
    title: String,
    location: String,
    notes: String,
    url: String,
    allDay: Boolean,
    start: LocalDateTime,
    end: LocalDateTime,
    calendarName: String,
    calendarColor: Int?,
    invitees: List<EventInvitee>,
    reminderPrimary: String,
    reminderSecondary: String,
    onNotificationClick: () -> Unit,
    onSecondNotificationClick: () -> Unit,
    repeatLabel: String?,
    repeatEndLabel: String?,
    repeatUntilLabel: String?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
    attachments: List<EventAttachment> = emptyList(),
    onAttachmentClick: (EventAttachment) -> Unit = {},
    showRsvp: Boolean = false,
    rsvpAnswer: EventRsvpAnswer? = null,
    onRsvp: (EventRsvpAnswer) -> Unit = {},
    onDuplicate: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = AppLocale.display
    val trimmedLocation = location.trim()
    val joinUrl = EventConferenceLinks.joinUrl(trimmedLocation, notes, url)
    val link = joinUrl ?: locationAsUrl(trimmedLocation)
    val canMap = EventLocationMaps.canShowEmbeddedMap(trimmedLocation)
    var place by remember(trimmedLocation) { mutableStateOf<ResolvedEventPlace?>(null) }
    LaunchedEffect(trimmedLocation, locale) {
        place = if (canMap) {
            EventLocationMaps.resolvePlace(context, trimmedLocation, locale)
        } else {
            null
        }
    }
    val addressLines = place?.lines?.takeIf { it.isNotEmpty() }
        ?: listOf(trimmedLocation).filter { it.isNotEmpty() }
    val barColor = calendarColor?.let { Color(it) } ?: MacColors.accentBlue
    val showTimeline = !allDay &&
        start.toLocalDate() == end.toLocalDate() &&
        end.isAfter(start)
    val showExtras = repeatLabel != null || url.isNotBlank() || notes.isNotBlank()

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(
                text = title.ifBlank { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.headlineMedium,
                color = MacColors.primaryLabel,
            )
            if (addressLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                addressLines.forEach { line ->
                    val lineLink = locationAsUrl(line) ?: link.takeIf { line == trimmedLocation }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MacColors.todayRed,
                        modifier = if (lineLink != null) {
                            Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lineLink)))
                            }
                        } else {
                            Modifier
                        },
                    )
                }
                if (link != null && addressLines.none { locationAsUrl(it) == link }) {
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MacColors.todayRed,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            },
                    )
                }
            } else if (link != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = trimmedLocation.ifBlank { link },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MacColors.todayRed,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = viewDateLine(start.toLocalDate(), end.toLocalDate(), locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MacColors.secondaryLabel,
            )
            Text(
                text = viewTimeLine(allDay, start, end),
                style = MaterialTheme.typography.bodyMedium,
                color = MacColors.secondaryLabel,
            )
        }

        if (showTimeline) {
            ViewCard {
                EventDayTimeline(
                    start = start,
                    end = end,
                    title = title.ifBlank { stringResource(R.string.event_no_title) },
                    location = trimmedLocation,
                    color = barColor,
                    showPeople = invitees.isNotEmpty(),
                )
            }
        }

        ViewCard {
            ViewValueRow(
                label = stringResource(R.string.field_calendar),
                value = calendarName.ifBlank { "—" },
                leadingDot = barColor,
            )
        }

        if (showRsvp) {
            EventRsvpChooser(
                selected = rsvpAnswer,
                onSelect = onRsvp,
            )
        }

        if (invitees.isNotEmpty()) {
            ViewCard {
                EventViewAttendees(invitees = invitees)
            }
        }

        if (attachments.isNotEmpty()) {
            ViewCard {
                attachments.forEachIndexed { index, attachment ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MacColors.separator,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAttachmentClick(attachment) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = attachment.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MacColors.accentBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MacColors.secondaryLabel,
                        )
                    }
                }
            }
        }

        ViewCard {
            ViewValueRow(
                label = stringResource(R.string.field_notification),
                value = reminderPrimary,
                onClick = onNotificationClick,
            )
            HorizontalDivider(
                color = MacColors.separator,
                modifier = Modifier.padding(start = 16.dp),
            )
            ViewValueRow(
                label = stringResource(R.string.field_second_notification),
                value = reminderSecondary,
                onClick = onSecondNotificationClick,
            )
        }

        if (showExtras) {
            ViewCard {
                var drewRow = false
                if (repeatLabel != null) {
                    ViewValueRow(
                        label = stringResource(R.string.field_repeat),
                        value = repeatLabel,
                    )
                    drewRow = true
                    if (repeatEndLabel != null) {
                        HorizontalDivider(
                            color = MacColors.separator,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                        ViewValueRow(
                            label = stringResource(R.string.field_repeat_end),
                            value = repeatEndLabel,
                        )
                    }
                    if (repeatUntilLabel != null) {
                        HorizontalDivider(
                            color = MacColors.separator,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                        ViewValueRow(
                            label = stringResource(R.string.field_repeat_until),
                            value = repeatUntilLabel,
                        )
                    }
                }
                if (url.isNotBlank()) {
                    if (drewRow || repeatLabel != null) {
                        HorizontalDivider(
                            color = MacColors.separator,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    Text(
                        text = url.trim(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = locationAsUrl(url) ?: url.trim()
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MacColors.accentBlue,
                    )
                    drewRow = true
                }
                if (notes.isNotBlank()) {
                    if (drewRow || repeatLabel != null || url.isNotBlank()) {
                        HorizontalDivider(
                            color = MacColors.separator,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    LinkedNoteText(
                        text = notes.trim(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        place?.let { resolved ->
            EventViewMapCard(
                location = trimmedLocation,
                place = resolved,
                caption = addressLines.firstOrNull().orEmpty(),
            )
        }

        if (onDuplicate != null) {
            ViewCard {
                EventActionRow(
                    label = stringResource(R.string.event_duplicate),
                    onClick = onDuplicate,
                )
            }
        }

        if (showDelete) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onDelete,
                    shape = CircleShape,
                    color = MacColors.contentBackground,
                ) {
                    Text(
                        text = stringResource(R.string.delete_event_button),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MacColors.todayRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventDayTimeline(
    start: LocalDateTime,
    end: LocalDateTime,
    title: String,
    location: String,
    color: Color,
    showPeople: Boolean,
) {
    val rowHeight = 48.dp
    val labelWidth = 52.dp
    val firstHour = (start.hour - 1).coerceAtLeast(0)
    val endSlot = if (end.minute == 0 && end.hour > start.hour) end.hour else end.hour + 1
    val lastLabel = (endSlot + 1).coerceAtLeast(firstHour + 1).coerceAtMost(24)
    val hourSpan = lastLabel - firstHour
    val totalHeight = rowHeight * hourSpan
    val startMinutes = (start.hour - firstHour) * 60 + start.minute
    val endMinutes = ((end.hour - firstHour) * 60 + end.minute).coerceAtLeast(startMinutes + 15)
    val barTop = rowHeight * (startMinutes / 60f)
    val barHeight = (rowHeight * ((endMinutes - startMinutes) / 60f) - 4.dp).coerceAtLeast(32.dp)
    val ink = onCalendarColor(color)
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (hour in firstHour until lastLabel) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = hourLabel(hour, timeFmt),
                        modifier = Modifier
                            .width(labelWidth)
                            .padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MacColors.secondaryLabel,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                        color = MacColors.separator,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(start = labelWidth, end = 8.dp)
                .offset(y = barTop)
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (location.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = null,
                            tint = ink,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = location,
                            color = ink,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showPeople) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.field_invitees),
                    tint = ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EventViewAttendees(invitees: List<EventInvitee>) {
    val context = LocalContext.current
    val repository = remember(context) { ContactSuggestionsRepository(context.contentResolver) }
    val hasContacts = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    var people by remember(invitees) {
        mutableStateOf(invitees.map { AttendeeRow(inviteeLabel(it, null), it.email) })
    }
    LaunchedEffect(invitees, hasContacts) {
        if (!hasContacts) {
            people = invitees.map { AttendeeRow(inviteeLabel(it, null), it.email) }
            return@LaunchedEffect
        }
        people = withContext(Dispatchers.IO) {
            invitees.map { invitee ->
                val contactName = repository.lookupByEmail(invitee.email)
                    ?.displayName
                    ?.takeIf { it.isNotBlank() }
                AttendeeRow(inviteeLabel(invitee, contactName), invitee.email)
            }
        }
    }
    var showAll by remember(invitees) { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.email_copied)
    val copyEmail: (String) -> Unit = { email ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("email", email))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    AttendeePlaque(
        count = invitees.size,
        people = people.take(PreviewAttendeeRows),
        onOpenAll = { showAll = true },
        onPersonClick = null,
    )

    if (showAll) {
        Dialog(
            onDismissRequest = { showAll = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                color = MacColors.windowBackground,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                ) {
                    Surface(
                        onClick = { showAll = false },
                        shape = CircleShape,
                        color = MacColors.segmentBackground,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = "‹",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MacColors.primaryLabel,
                        )
                    }
                    ViewCard {
                        AttendeePlaque(
                            count = invitees.size,
                            people = people,
                            onOpenAll = null,
                            onPersonClick = copyEmail,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendeePlaque(
    count: Int,
    people: List<AttendeeRow>,
    onOpenAll: (() -> Unit)?,
    onPersonClick: ((String) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.field_invitees),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onOpenAll != null) {
                        Modifier.clickable(onClick = onOpenAll)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.secondaryLabel,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.secondaryLabel,
            )
        }
    }
    people.forEach { person ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onPersonClick != null && person.email.isNotBlank()) {
                        Modifier.clickable { onPersonClick(person.email) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.5.dp, MacColors.secondaryLabel, CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = person.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.primaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EventViewMapCard(
    location: String,
    place: ResolvedEventPlace,
    caption: String,
) {
    val context = LocalContext.current
    var bitmap by remember(place.longitude, place.latitude) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(place.longitude, place.latitude) {
        bitmap = EventLocationMaps.loadStaticMap(place.longitude, place.latitude)
    }
    val image = bitmap ?: return
    val description = stringResource(R.string.event_location_map_cd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                EventLocationMaps.openInYandexMaps(
                    context = context,
                    location = location,
                    longitude = place.longitude,
                    latitude = place.latitude,
                )
            },
    ) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (caption.isNotEmpty()) {
            Text(
                text = caption,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class RsvpChoice(
    val answer: EventRsvpAnswer,
    val label: String,
    val statusLabel: String,
    val icon: ImageVector,
    val iconBackground: Color,
)

@Composable
private fun rememberRsvpChoices(): List<RsvpChoice> = listOf(
    RsvpChoice(
        answer = EventRsvpAnswer.ACCEPTED,
        label = stringResource(R.string.event_rsvp_accept),
        statusLabel = stringResource(R.string.event_rsvp_status_accepted),
        icon = Icons.Filled.Check,
        iconBackground = Color(0xFF2E8B3A),
    ),
    RsvpChoice(
        answer = EventRsvpAnswer.TENTATIVE,
        label = stringResource(R.string.event_rsvp_tentative),
        statusLabel = stringResource(R.string.event_rsvp_status_tentative),
        icon = Icons.Filled.QuestionMark,
        iconBackground = Color(0xFF5F6368),
    ),
    RsvpChoice(
        answer = EventRsvpAnswer.DECLINED,
        label = stringResource(R.string.event_rsvp_decline),
        statusLabel = stringResource(R.string.event_rsvp_status_declined),
        icon = Icons.Filled.Close,
        iconBackground = Color(0xFFD23B2F),
    ),
)

@Composable
private fun EventRsvpChooser(
    selected: EventRsvpAnswer?,
    onSelect: (EventRsvpAnswer) -> Unit,
) {
    val items = rememberRsvpChoices()
    var expanded by remember(selected) { mutableStateOf(selected == null) }
    val groupLabel = stringResource(R.string.event_rsvp_label)
    val current = items.firstOrNull { it.answer == selected }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MacColors.segmentBackground)
            .border(1.dp, MacColors.separator, RoundedCornerShape(12.dp))
            .semantics { contentDescription = groupLabel }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = if (current != null && !expanded) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.SpaceEvenly
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current != null && !expanded) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MacColors.contentBackground)
                    .border(1.dp, MacColors.separator, RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RsvpChoiceContent(item = current, compact = false)
            }
            Text(
                text = stringResource(R.string.event_rsvp_change),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MacColors.accentBlue,
                ),
            )
        } else {
            items.forEach { item ->
                val isSelected = selected == item.answer
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) MacColors.selectionFill else MacColors.contentBackground,
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MacColors.accentBlue else MacColors.separator,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable {
                            onSelect(item.answer)
                            if (selected != null) expanded = false
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RsvpChoiceContent(item = item, compact = true)
                }
            }
        }
    }
}

@Composable
private fun RsvpChoiceContent(
    item: RsvpChoice,
    compact: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(item.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (item.answer == EventRsvpAnswer.TENTATIVE) {
                Text(
                    text = "?",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp,
                )
            } else {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (compact) item.label else item.statusLabel,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MacColors.accentBlue,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun EventActionRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.accentBlue,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.secondaryLabel,
        )
    }
}

@Composable
private fun ViewCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MacColors.contentBackground),
        content = content,
    )
}

@Composable
private fun ViewValueRow(
    label: String,
    value: String,
    leadingDot: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (leadingDot != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(leadingDot),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.secondaryLabel,
        )
    }
}

private const val PreviewAttendeeRows = 3

private data class AttendeeRow(
    val label: String,
    val email: String,
)

private val noteUrlPattern = Regex("""(?i)\b((?:https?://|www\.)\S+)""")

@Composable
private fun LinkedNoteText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val linkColor = MacColors.accentBlue
    val annotated = buildAnnotatedString {
        var cursor = 0
        noteUrlPattern.findAll(text).forEach { match ->
            val raw = match.value.trimEnd('.', ',', ';', ')', ']')
            if (raw.isEmpty()) return@forEach
            append(text.substring(cursor, match.range.first))
            val href = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            withLink(LinkAnnotation.Url(href)) {
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(raw)
                }
            }
            cursor = match.range.first + raw.length
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    SelectionContainer(modifier = modifier) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
    }
}

private fun inviteeLabel(invitee: EventInvitee, contactName: String?): String {
    val name = contactName ?: invitee.name?.takeIf { it.isNotBlank() }
    val clean = name?.takeUnless { it.equals(invitee.email, ignoreCase = true) }
    return clean?.takeIf { it.isNotBlank() } ?: invitee.email
}

private fun viewDateLine(start: LocalDate, end: LocalDate, locale: java.util.Locale): String {
    val startText = formatViewDate(start, locale)
    if (start == end) return startText
    return "$startText — ${formatViewDate(end, locale)}"
}

private fun formatViewDate(date: LocalDate, locale: java.util.Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
    val month = date.month.getDisplayName(TextStyle.SHORT, locale)
    return "$weekday, ${date.dayOfMonth} $month ${date.year} г."
}

@Composable
private fun viewTimeLine(allDay: Boolean, start: LocalDateTime, end: LocalDateTime): String {
    if (allDay) return stringResource(R.string.all_day)
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    return "${start.format(fmt)} — ${end.format(fmt)}"
}

private fun hourLabel(hour: Int, formatter: DateTimeFormatter): String {
    val normalized = if (hour >= 24) 0 else hour
    return java.time.LocalTime.of(normalized, 0).format(formatter)
}

private fun onCalendarColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.65f) Color.Black else Color.White
}
