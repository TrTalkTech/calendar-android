@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.calendar.ui

import android.app.DatePickerDialog
import android.content.ContentUris
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.AppLocale
import com.example.calendar.R
import com.example.calendar.RecurrenceFreq
import com.example.calendar.EventInviteeEmails
import com.example.calendar.EventReminderPreset
import com.example.calendar.RRuleBuilder
import com.example.calendar.RRuleDisplayFormatter
import com.example.calendar.RussianDateFormat
import com.example.calendar.RepeatEndPreset
import com.example.calendar.data.AttachmentOpenMode
import com.example.calendar.data.AttachmentOpenPreferences
import com.example.calendar.data.BusyEvent
import com.example.calendar.data.CalendarEvent
import com.example.calendar.data.CalendarEventWriter
import com.example.calendar.data.CalendarRepository
import com.example.calendar.data.DocumentKinds
import com.example.calendar.data.EventAttachment
import com.example.calendar.data.EventAttachmentStore
import com.example.calendar.data.EventFormDraft
import com.example.calendar.data.EventIcs
import com.example.calendar.data.EventIcsContent
import com.example.calendar.data.EventInvitee
import com.example.calendar.data.EventOverlap
import com.example.calendar.data.EventRsvp
import com.example.calendar.data.EventRsvpAnswer
import com.example.calendar.data.OverlapCandidate
import com.example.calendar.data.WritableCalendar
import com.example.calendar.data.eventCalendars
import com.example.calendar.data.toBusyEvent
import com.example.calendar.displayLabel
import com.example.calendar.minutesBeforeForSave
import com.example.calendar.ui.theme.MacColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun NewEventDialog(
    sessionId: Int,
    existingEventId: Long?,
    /** [CalendarContract.Instances.BEGIN/END] выбранного экземпляра — для удаления одного повтора. */
    existingInstanceBeginMillis: Long? = null,
    existingInstanceEndMillis: Long? = null,
    initialDate: LocalDate,
    eventWriter: CalendarEventWriter,
    hasWritePermission: Boolean,
    onRequestWritePermission: () -> Unit,
    onDismiss: () -> Unit,
    onSaved: (CalendarEvent?) -> Unit,
    allowedCalendarIds: Set<Long>? = null,
    defaultCalendarId: Long? = null,
    draft: EventFormDraft? = null,
    onDuplicate: (EventFormDraft) -> Unit = {},
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    /** Для правки существующего события — [CalendarContract.Events.EVENT_TIMEZONE], иначе сохранение сдвигает моменты. */
    var writeTimeZone by remember(sessionId, existingEventId) { mutableStateOf(zone) }
    val locale = AppLocale.display

    val isExisting = existingEventId != null
    var isViewMode by remember(sessionId, existingEventId) { mutableStateOf(isExisting) }
    var eventLoaded by remember(sessionId, existingEventId) { mutableStateOf(!isExisting) }
    var eventLoadFailed by remember { mutableStateOf(false) }

    var title by remember(sessionId) { mutableStateOf(draft?.title.orEmpty()) }
    var location by remember(sessionId) { mutableStateOf(draft?.location.orEmpty()) }
    var notes by remember(sessionId) { mutableStateOf(draft?.notes.orEmpty()) }
    var url by remember(sessionId) { mutableStateOf(draft?.url.orEmpty()) }
    var inviteesText by remember(sessionId) {
        mutableStateOf(
            draft?.attendees.orEmpty().joinToString(", ") { attendee ->
                EventInviteeEmails.formatToken(attendee.name.orEmpty(), attendee.email)
            },
        )
    }
    var allDay by remember(sessionId) { mutableStateOf(draft?.allDay == true) }
    val defaultStart = remember(sessionId, initialDate) { defaultNewEventStart(initialDate) }
    var start by remember(sessionId, initialDate) {
        mutableStateOf(draft?.start ?: defaultStart)
    }
    var end by remember(sessionId, initialDate) {
        mutableStateOf(draft?.end ?: defaultNewEventEnd(defaultStart))
    }

    var calendars by remember(sessionId) { mutableStateOf<List<WritableCalendar>>(emptyList()) }
    var selectedCalendarIndex by remember(sessionId) { mutableStateOf(0) }
    /** Календарь записи: из загруженного события, не «основной», если событие вне списка. */
    var saveTargetCalendarId by remember(sessionId, existingEventId) { mutableStateOf<Long?>(null) }
    var saveRequestedWhileDenied by remember(sessionId) { mutableStateOf(false) }
    var recurrenceRRule by remember(sessionId) { mutableStateOf<String?>(null) }
    /** Только при true в update пишем RRULE; иначе провайдер не должен трогать повтор (риск «удалить» серию). */
    var repeatRuleUserTouched by remember(sessionId) { mutableStateOf(false) }
    var repeatPresetMenuOpen by remember(sessionId) { mutableStateOf(false) }
    var repeatEndMenuOpen by remember(sessionId) { mutableStateOf(false) }
    var repeatCustomOpen by remember(sessionId) { mutableStateOf(false) }
    var repeatEndPreset by remember(sessionId) { mutableStateOf(RepeatEndPreset.NEVER) }
    var repeatUntilDate by remember(sessionId) { mutableStateOf(initialDate) }
    var repeatEndUserTouched by remember(sessionId) { mutableStateOf(false) }
    var loadedRecurrenceRuleSnapshot by remember(sessionId, existingEventId) { mutableStateOf<String?>(null) }
    var customRepeatFreq by remember(sessionId) { mutableStateOf(RecurrenceFreq.DAILY) }
    var customRepeatInterval by remember(sessionId) { mutableStateOf(1) }
    val draftReminders = remember(sessionId) {
        draft?.reminderMinutesBefore.orEmpty().map(EventReminderPreset::fromStoredMinutes)
    }
    var primaryReminder by remember(sessionId) {
        mutableStateOf(draftReminders.getOrElse(0) { EventReminderPreset.NONE })
    }
    var secondaryReminder by remember(sessionId) {
        mutableStateOf(draftReminders.getOrElse(1) { EventReminderPreset.NONE })
    }
    var reminderMenuOpen by remember(sessionId) { mutableStateOf(false) }
    var secondReminderMenuOpen by remember(sessionId) { mutableStateOf(false) }
    var attachments by remember(sessionId) { mutableStateOf<List<EventAttachment>>(emptyList()) }
    var pendingAdds by remember(sessionId) { mutableStateOf<List<EventAttachment>>(emptyList()) }
    var pendingDeletes by remember(sessionId) { mutableStateOf<List<EventAttachment>>(emptyList()) }
    var viewingAttachment by remember(sessionId) { mutableStateOf<EventAttachment?>(null) }

    var showDeleteConfirmSimple by remember(sessionId) { mutableStateOf(false) }
    var showDeleteRecurringChoice by remember(sessionId) { mutableStateOf(false) }
    var showRsvp by remember(sessionId) { mutableStateOf(false) }
    var rsvpAnswer by remember(sessionId) { mutableStateOf<EventRsvpAnswer?>(null) }
    var pendingRsvp by remember(sessionId) { mutableStateOf<EventRsvpAnswer?>(null) }
    var overlapConflicts by remember(sessionId) { mutableStateOf<List<BusyEvent>>(emptyList()) }
    var showOverlapDialog by remember(sessionId) { mutableStateOf(false) }
    val calendarRepository = remember(context) { CalendarRepository(context.contentResolver, context) }

    val formInteractive = !isViewMode
    val repeatDisplay = RRuleDisplayFormatter.format(context, recurrenceRRule)
    val attachmentStore = remember(context) { EventAttachmentStore(context) }
    val attachmentOpenPreferences = remember(context) { AttachmentOpenPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val pickAttachment = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { attachmentStore.importFromPicker(uri) }
            when (result) {
                is EventAttachmentStore.ImportResult.Ok -> {
                    attachments = attachments + result.attachment
                    pendingAdds = pendingAdds + result.attachment
                }
                EventAttachmentStore.ImportResult.TooLarge -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.attachment_too_large),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                EventAttachmentStore.ImportResult.Failed -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.attachment_add_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun persistAttachments(eventId: Long) {
        pendingDeletes.forEach { attachmentStore.remove(eventId, it) }
        pendingAdds.forEach { attachmentStore.attachToEvent(eventId, it) }
        pendingAdds = emptyList()
        pendingDeletes = emptyList()
        attachments = attachmentStore.list(eventId)
    }

    fun openAttachment(attachment: EventAttachment) {
        coroutineScope.launch {
            val file = withContext(Dispatchers.IO) { attachmentStore.ensureLocalFile(attachment) }
            if (file == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.attachment_open_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val resolved = attachment.copy(localFile = file)
            val preferInApp = attachmentOpenPreferences.read() == AttachmentOpenMode.IN_APP
            val useViewer = DocumentKinds.canPreviewInApp(resolved.displayName) &&
                (!DocumentKinds.followsViewerSetting(resolved.displayName) || preferInApp)
            if (useViewer) {
                viewingAttachment = resolved
            } else if (!attachmentStore.openExternally(context, resolved)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.attachment_open_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun removeAttachment(attachment: EventAttachment) {
        attachments = attachments.filterNot { it.id == attachment.id }
        if (pendingAdds.any { it.id == attachment.id }) {
            pendingAdds = pendingAdds.filterNot { it.id == attachment.id }
            attachment.localFile?.delete()
        } else {
            pendingDeletes = pendingDeletes + attachment
        }
    }

    fun resolveCalendarIdForSave(): Long? {
        saveTargetCalendarId?.let { return it }
        return calendars.getOrNull(selectedCalendarIndex)?.id
    }

    fun buildListAnchor(eventId: Long): CalendarEvent {
        val begin = existingInstanceBeginMillis ?: run {
            if (allDay) {
                start.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                start.atZone(writeTimeZone).toInstant().toEpochMilli()
            }
        }
        val endRaw = existingInstanceEndMillis ?: run {
            if (allDay) {
                end.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                end.atZone(writeTimeZone).toInstant().toEpochMilli()
            }
        }
        val endMillis = if (endRaw > begin) {
            endRaw
        } else {
            begin + if (allDay) 86400000L else 3600000L
        }
        val displayTitle = title.trim().ifBlank { context.getString(R.string.event_no_title) }
        return CalendarEvent(
            eventId = eventId,
            title = displayTitle,
            beginMillis = begin,
            endMillis = endMillis,
            allDay = allDay,
            calendarId = resolveCalendarIdForSave(),
            calendarName = calendars.getOrNull(selectedCalendarIndex)?.displayName,
            color = calendars.getOrNull(selectedCalendarIndex)?.color,
            location = location.trim(),
        )
    }

    fun parsedInvitees(): List<EventInvitee> = EventInviteeEmails.parseInvitees(inviteesText)

    fun validateInvitees(): Boolean {
        val invalid = EventInviteeEmails.invalidEmails(parsedInvitees().map { it.email })
        if (invalid.isEmpty()) return true
        Toast.makeText(
            context,
            context.getString(R.string.event_invitees_invalid, invalid.joinToString(", ")),
            Toast.LENGTH_LONG,
        ).show()
        return false
    }

    fun resolvedReminderMinutes(): List<Int> =
        listOf(primaryReminder, secondaryReminder).minutesBeforeForSave()

    fun repeatEndDisplayValue(): String = when (repeatEndPreset) {
        RepeatEndPreset.NEVER -> context.getString(R.string.value_never)
        RepeatEndPreset.ON_DATE -> context.getString(R.string.repeat_end_on_date)
    }

    fun resolvedRecurrenceRule(): String? {
        val base = recurrenceRRule?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (repeatEndPreset) {
            RepeatEndPreset.NEVER -> RRuleBuilder.withoutRepeatEnd(base)
            RepeatEndPreset.ON_DATE -> RRuleBuilder.withUntilDate(
                rrule = base,
                untilDate = repeatUntilDate,
                allDay = allDay,
                eventStart = start,
                eventEnd = end,
                zone = if (allDay) ZoneId.of("UTC") else writeTimeZone,
            )
        }
    }

    fun shouldUpdateRecurrence(): Boolean {
        if (existingEventId == null) return false
        if (repeatRuleUserTouched || repeatEndUserTouched) return true
        val resolved = resolvedRecurrenceRule().orEmpty()
        val loaded = loadedRecurrenceRuleSnapshot.orEmpty()
        return RRuleBuilder.normalizeForComparison(resolved) !=
            RRuleBuilder.normalizeForComparison(loaded)
    }

    fun validateRepeatUntil(): Boolean {
        if (repeatEndPreset != RepeatEndPreset.ON_DATE) return true
        if (repeatUntilDate.isBefore(start.toLocalDate())) {
            Toast.makeText(
                context,
                context.getString(R.string.repeat_until_before_start),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        return true
    }

    LaunchedEffect(sessionId, existingEventId, allowedCalendarIds, defaultCalendarId) {
        val loadedCalendars = eventWriter.queryVisibleCalendars()
        calendars = if (existingEventId == null) {
            val events = loadedCalendars.eventCalendars()
            val allowed = allowedCalendarIds
            if (allowed.isNullOrEmpty()) events
            else events.filter { it.id in allowed }.ifEmpty { events }
        } else {
            loadedCalendars
        }
        selectedCalendarIndex = when {
            defaultCalendarId != null ->
                calendars.indexOfFirst { it.id == defaultCalendarId }.takeIf { it >= 0 } ?: 0
            else -> calendars.indexOfFirst { it.isPrimary }.takeIf { it >= 0 } ?: 0
        }
        if (existingEventId == null) {
            writeTimeZone = zone
            showRsvp = false
            rsvpAnswer = null
            if (draft != null) {
                title = draft.title
                location = draft.location
                notes = draft.notes
                url = draft.url
                allDay = draft.allDay
                start = draft.start
                end = draft.end
                inviteesText = draft.attendees.joinToString(", ") { attendee ->
                    EventInviteeEmails.formatToken(attendee.name.orEmpty(), attendee.email)
                }
                saveTargetCalendarId = draft.calendarId
                val draftCalIdx = calendars.indexOfFirst { it.id == draft.calendarId }
                if (draftCalIdx >= 0) selectedCalendarIndex = draftCalIdx
                val reminders = draft.reminderMinutesBefore.map(EventReminderPreset::fromStoredMinutes)
                primaryReminder = reminders.getOrElse(0) { EventReminderPreset.NONE }
                secondaryReminder = reminders.getOrElse(1) { EventReminderPreset.NONE }
            } else {
                saveTargetCalendarId = null
                inviteesText = ""
                primaryReminder = EventReminderPreset.NONE
                secondaryReminder = EventReminderPreset.NONE
            }
            repeatEndPreset = RepeatEndPreset.NEVER
            repeatUntilDate = (draft?.start?.toLocalDate() ?: initialDate)
            repeatEndUserTouched = false
            loadedRecurrenceRuleSnapshot = null
            eventLoaded = true
            eventLoadFailed = false
            recurrenceRRule = null
            repeatRuleUserTouched = false
            attachments = emptyList()
            pendingAdds = emptyList()
            pendingDeletes = emptyList()
            return@LaunchedEffect
        }
        eventLoaded = false
        val p = eventWriter.loadEventForEdit(existingEventId, zone)
        if (p == null) {
            eventLoadFailed = true
            eventLoaded = true
            recurrenceRRule = null
            repeatRuleUserTouched = false
            primaryReminder = EventReminderPreset.NONE
            secondaryReminder = EventReminderPreset.NONE
            attachments = emptyList()
            return@LaunchedEffect
        }
        title = p.title
        location = p.location
        notes = p.notes
        url = p.url
        inviteesText = p.attendees.joinToString(", ") { attendee ->
            EventInviteeEmails.formatToken(attendee.name.orEmpty(), attendee.email)
        }
        allDay = p.allDay
        start = p.start
        end = p.end
        recurrenceRRule = p.recurrenceRule?.let { RRuleBuilder.withoutRepeatEnd(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val loadedUntil = RRuleBuilder.parseUntilDate(p.recurrenceRule, p.allDay, p.eventTimeZone)
        repeatUntilDate = loadedUntil ?: p.start.toLocalDate()
        repeatEndPreset = if (loadedUntil != null) RepeatEndPreset.ON_DATE else RepeatEndPreset.NEVER
        loadedRecurrenceRuleSnapshot = p.recurrenceRule?.trim()?.takeIf { it.isNotEmpty() }
        writeTimeZone = p.eventTimeZone
        saveTargetCalendarId = p.calendarId
        val idx = calendars.indexOfFirst { it.id == p.calendarId }
        if (idx >= 0) selectedCalendarIndex = idx
        repeatRuleUserTouched = false
        repeatEndUserTouched = false
        val loadedReminders = p.reminderMinutesBefore.map(EventReminderPreset::fromStoredMinutes)
        primaryReminder = loadedReminders.getOrElse(0) { EventReminderPreset.NONE }
        secondaryReminder = loadedReminders.getOrElse(1) { EventReminderPreset.NONE }
        attachments = attachmentStore.list(existingEventId)
        pendingAdds = emptyList()
        pendingDeletes = emptyList()
        showRsvp = p.canRespondToInvite
        rsvpAnswer = EventRsvp.fromSelfAttendeeStatus(p.selfAttendeeStatus)
        eventLoaded = true
        eventLoadFailed = false
    }

    LaunchedEffect(recurrenceRRule) {
        if (recurrenceRRule.isNullOrBlank()) {
            repeatEndPreset = RepeatEndPreset.NEVER
        }
    }

    LaunchedEffect(start) {
        if (repeatEndPreset == RepeatEndPreset.ON_DATE &&
            repeatUntilDate.isBefore(start.toLocalDate())
        ) {
            repeatUntilDate = start.toLocalDate()
        }
    }

    LaunchedEffect(primaryReminder) {
        if (primaryReminder == EventReminderPreset.NONE) {
            secondaryReminder = EventReminderPreset.NONE
        }
    }

    fun currentDraft(): EventFormDraft = EventFormDraft(
        title = title,
        location = location,
        notes = notes,
        url = url,
        allDay = allDay,
        start = start,
        end = end,
        calendarId = resolveCalendarIdForSave() ?: calendars.getOrNull(selectedCalendarIndex)?.id,
        attendees = parsedInvitees(),
        reminderMinutesBefore = resolvedReminderMinutes(),
    )

    fun collectOverlaps(): List<BusyEvent> {
        if (allDay) return emptyList()
        val ids = calendars.eventCalendars().map { it.id }.toSet()
        if (ids.isEmpty()) return emptyList()
        val begin = start.atZone(writeTimeZone).toInstant().toEpochMilli()
        val endMs = end.atZone(writeTimeZone).toInstant().toEpochMilli()
        if (endMs <= begin) return emptyList()
        val lastDay = if (end.toLocalTime() == LocalTime.MIDNIGHT) {
            end.toLocalDate().minusDays(1)
        } else {
            end.toLocalDate()
        }
        val firstDay = start.toLocalDate()
        val dayCount = ChronoUnit.DAYS.between(firstDay, lastDay).coerceAtLeast(0) + 1
        val occupied = (0 until dayCount).flatMap { offset ->
            calendarRepository.queryEventsForDay(
                date = firstDay.plusDays(offset),
                zone = writeTimeZone,
                visibleCalendarIds = ids,
            ).events
        }.distinctBy { it.eventId to it.beginMillis }
            .map { it.toBusyEvent() }
        return EventOverlap.conflicts(
            candidate = OverlapCandidate(
                eventId = existingEventId,
                beginMillis = begin,
                endMillis = endMs,
                allDay = false,
            ),
            occupied = occupied,
        )
    }

    fun confirmNoOverlapOrPrompt(): Boolean {
        val conflicts = collectOverlaps()
        if (conflicts.isEmpty()) return true
        overlapConflicts = conflicts
        showOverlapDialog = true
        return false
    }

    fun icsContent(): EventIcsContent {
        val begin = if (allDay) {
            start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            start.atZone(writeTimeZone).toInstant().toEpochMilli()
        }
        val endMs = if (allDay) {
            end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            end.atZone(writeTimeZone).toInstant().toEpochMilli()
        }
        val join = EventConferenceLinks.joinUrl(location, notes, url)
        val description = buildString {
            if (!join.isNullOrBlank()) {
                append(join)
                append("\n\n")
            }
            if (notes.isNotBlank()) append(notes.trim())
        }.trim()
        return EventIcsContent(
            eventId = existingEventId ?: 0L,
            title = title.trim(),
            location = location.trim(),
            description = description,
            allDay = allDay,
            startEpochMillis = begin,
            endEpochMillis = endMs,
        )
    }

    fun forwardEvent() {
        val content = icsContent()
        val join = EventConferenceLinks.joinUrl(location, notes, url)
        EventForward.share(
            context = context,
            content = content,
            body = EventIcs.emailBody(content, writeTimeZone, join, locale),
        )
    }

    fun requestDuplicate() {
        val current = start.toLocalDate()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onDuplicate(currentDraft().shiftedTo(LocalDate.of(year, month + 1, day)))
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).apply {
            setTitle(context.getString(R.string.event_duplicate_date_title))
            show()
        }
    }

    fun applyRsvp(answer: EventRsvpAnswer) {
        val id = existingEventId ?: return
        if (!hasWritePermission) {
            pendingRsvp = answer
            onRequestWritePermission()
            return
        }
        val ok = eventWriter.updateSelfAttendeeStatus(id, answer)
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.event_rsvp_failed), Toast.LENGTH_SHORT).show()
            return
        }
        rsvpAnswer = answer
        if (answer == EventRsvpAnswer.DECLINED) {
            onSaved(null)
            onDismiss()
        } else {
            onSaved(buildListAnchor(id))
        }
    }

    val dateFmt = remember(locale) {
        DateTimeFormatter.ofPattern("d MMM yyyy 'г.'", locale)
    }

    fun commitEvent(): Boolean {
        if (calendars.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.no_calendars_to_save),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        if (!hasWritePermission) {
            saveRequestedWhileDenied = true
            onRequestWritePermission()
            return false
        }
        val rangeInvalid = if (allDay) {
            end.toLocalDate().isBefore(start.toLocalDate())
        } else {
            !end.isAfter(start)
        }
        if (rangeInvalid) {
            Toast.makeText(
                context,
                context.getString(R.string.event_invalid_time_range),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        if (!validateInvitees()) return false
        if (!validateRepeatUntil()) return false
        val attendees = parsedInvitees()
        val recurrenceToSave = resolvedRecurrenceRule()
        val reminderMinutesBefore = resolvedReminderMinutes()
        val cal = calendars.getOrNull(selectedCalendarIndex) ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.no_calendars_to_save),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        val resolvedCalendarId = resolveCalendarIdForSave() ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.no_calendars_to_save),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        val savedEventId = if (existingEventId != null) {
            val ok = eventWriter.updateEvent(
                eventId = existingEventId,
                calendarId = resolvedCalendarId,
                title = title,
                location = location.takeIf { it.isNotBlank() },
                description = notes.takeIf { it.isNotBlank() },
                url = url.takeIf { it.isNotBlank() },
                allDay = allDay,
                start = start,
                end = end,
                recurrenceRule = recurrenceToSave,
                updateRecurrence = shouldUpdateRecurrence(),
                zone = writeTimeZone,
                attendees = attendees,
                reminderMinutesBefore = reminderMinutesBefore,
            ) >= 0
            if (ok) existingEventId else null
        } else {
            eventWriter.insertEvent(
                calendarId = cal.id,
                title = title,
                location = location.takeIf { it.isNotBlank() },
                description = notes.takeIf { it.isNotBlank() },
                url = url.takeIf { it.isNotBlank() },
                allDay = allDay,
                start = start,
                end = end,
                zone = zone,
                recurrenceRule = recurrenceToSave,
                attendees = attendees,
                reminderMinutesBefore = reminderMinutesBefore,
            )?.let { ContentUris.parseId(it) }
        }
        if (savedEventId == null) {
            Toast.makeText(
                context,
                context.getString(R.string.event_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        persistAttachments(savedEventId)
        onSaved(buildListAnchor(savedEventId))
        onDismiss()
        return true
    }

    fun performSave(ignoreOverlap: Boolean = false) {
        if (!ignoreOverlap && !confirmNoOverlapOrPrompt()) return
        if (!commitEvent()) return
    }

    LaunchedEffect(hasWritePermission, saveRequestedWhileDenied, calendars, existingEventId, isViewMode) {
        if (!hasWritePermission || !saveRequestedWhileDenied || calendars.isEmpty() || isViewMode) return@LaunchedEffect
        saveRequestedWhileDenied = false
        performSave()
    }

    LaunchedEffect(hasWritePermission, pendingRsvp) {
        val answer = pendingRsvp ?: return@LaunchedEffect
        if (!hasWritePermission) return@LaunchedEffect
        pendingRsvp = null
        applyRsvp(answer)
    }

    val canSave = eventLoaded && !eventLoadFailed

    fun instanceBoundsForDelete(): Pair<Long, Long> {
        val begin = existingInstanceBeginMillis
            ?: runCatching { start.atZone(writeTimeZone).toInstant().toEpochMilli() }.getOrElse { 0L }
        val endRaw = existingInstanceEndMillis
            ?: runCatching { end.atZone(writeTimeZone).toInstant().toEpochMilli() }.getOrElse { 0L }
        val end = if (endRaw > begin) {
            endRaw
        } else {
            begin + if (allDay) 86400000L else 3600000L
        }
        return begin to end
    }

    fun finishDeleteSuccess() {
        showDeleteConfirmSimple = false
        showDeleteRecurringChoice = false
        onSaved(null)
        onDismiss()
    }

    fun tryDeleteEntire(): Boolean {
        val id = existingEventId ?: return false
        if (!hasWritePermission) {
            Toast.makeText(
                context,
                context.getString(R.string.event_delete_permission_needed),
                Toast.LENGTH_SHORT,
            ).show()
            onRequestWritePermission()
            return false
        }
        return if (eventWriter.deleteEntireEvent(id)) {
            attachmentStore.deleteAll(id)
            finishDeleteSuccess()
            true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.event_delete_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }

    fun tryDeleteRecurringInstance(): Boolean {
        val id = existingEventId ?: return false
        val cal = calendars.getOrNull(selectedCalendarIndex) ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.no_calendars_to_save),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        if (!hasWritePermission) {
            Toast.makeText(
                context,
                context.getString(R.string.event_delete_permission_needed),
                Toast.LENGTH_SHORT,
            ).show()
            onRequestWritePermission()
            return false
        }
        val (b, e) = instanceBoundsForDelete()
        if (b <= 0L) {
            Toast.makeText(
                context,
                context.getString(R.string.event_delete_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        return if (eventWriter.deleteRecurringInstance(id, cal.id, b, e, allDay, writeTimeZone, title)) {
            finishDeleteSuccess()
            true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.event_delete_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }

    fun requestDelete() {
        if (!hasWritePermission) {
            Toast.makeText(
                context,
                context.getString(R.string.event_delete_permission_needed),
                Toast.LENGTH_SHORT,
            ).show()
            onRequestWritePermission()
            return
        }
        if (recurrenceRRule.isNullOrBlank()) {
            showDeleteConfirmSimple = true
        } else {
            showDeleteRecurringChoice = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = MacColors.windowBackground,
        ) {
            val anchorDate = start.toLocalDate()
            val showDeleteInEditMode = existingEventId != null && !isViewMode && eventLoaded && !eventLoadFailed
            val viewScroll = rememberScrollState()
            val editScroll = rememberScrollState()
            val showEventView = isViewMode && isExisting && eventLoaded && !eventLoadFailed
            val viewStart = occurrenceDateTime(
                millis = existingInstanceBeginMillis,
                allDay = allDay,
                zone = writeTimeZone,
                fallback = start,
            )
            val viewEnd = occurrenceDateTime(
                millis = existingInstanceEndMillis,
                allDay = allDay,
                zone = writeTimeZone,
                fallback = end,
                exclusiveAllDayEnd = true,
            )
            val displayTitle = title.trim().ifBlank { stringResource(R.string.event_no_title) }
            val titleCollapsePx = with(LocalDensity.current) { 96.dp.toPx() }
            Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showEventView) {
                    EventViewTopBar(
                        monthLabel = RussianDateFormat.monthNominative(viewStart.toLocalDate()),
                        centerTitle = if (viewScroll.value > titleCollapsePx) displayTitle else "",
                        onClose = onDismiss,
                        onEdit = { isViewMode = false },
                        onForward = { forwardEvent() },
                    )
                } else {
                    EventFormHeader(
                        isViewMode = isViewMode,
                        isExisting = isExisting,
                        centerTitle = when {
                            isViewMode -> displayTitle
                            isExisting -> stringResource(R.string.edit_screen_title)
                            else -> stringResource(R.string.new_event_title)
                        },
                        canSave = canSave,
                        onClose = onDismiss,
                        onSave = { performSave() },
                        onEdit = { isViewMode = false },
                    )
                }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(if (showEventView) viewScroll else editScroll)
                    .navigationBarsPadding(),
            ) {
                when {
                    isExisting && !eventLoaded && !eventLoadFailed -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MacColors.accentBlue)
                        }
                    }
                    eventLoadFailed -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.event_load_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MacColors.secondaryLabel,
                            )
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.calendars_close))
                            }
                        }
                    }
                    showEventView -> {
                        val selectedCalendar = calendars.getOrNull(selectedCalendarIndex)
                        EventViewBody(
                            title = displayTitle,
                            location = location,
                            notes = notes,
                            url = url,
                            allDay = allDay,
                            start = viewStart,
                            end = viewEnd,
                            calendarName = selectedCalendar?.displayName.orEmpty(),
                            calendarColor = selectedCalendar?.color,
                            invitees = parsedInvitees(),
                            reminderPrimary = primaryReminder.displayLabel(context),
                            reminderSecondary = secondaryReminder.displayLabel(context),
                            onNotificationClick = { reminderMenuOpen = true },
                            onSecondNotificationClick = { secondReminderMenuOpen = true },
                            repeatLabel = if (recurrenceRRule.isNullOrBlank()) null else repeatDisplay,
                            repeatEndLabel = if (recurrenceRRule.isNullOrBlank()) {
                                null
                            } else {
                                repeatEndDisplayValue()
                            },
                            repeatUntilLabel = if (
                                !recurrenceRRule.isNullOrBlank() &&
                                repeatEndPreset == RepeatEndPreset.ON_DATE
                            ) {
                                repeatUntilDate.format(dateFmt)
                            } else {
                                null
                            },
                            onDelete = { requestDelete() },
                            attachments = attachments,
                            onAttachmentClick = ::openAttachment,
                            showRsvp = showRsvp,
                            rsvpAnswer = rsvpAnswer,
                            onRsvp = { applyRsvp(it) },
                            onDuplicate = { requestDuplicate() },
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                        IosFormCard {
                            IosPlainField(
                                readOnly = !formInteractive,
                                value = title,
                                onValueChange = { title = it },
                                placeholder = stringResource(R.string.field_title),
                                singleLine = true,
                            )
                            HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            LocationFieldWithSuggestion(
                                value = location,
                                onValueChange = { location = it },
                                readOnly = !formInteractive,
                                placeholder = stringResource(R.string.field_location),
                            )
                            if (
                                isViewMode &&
                                isExisting &&
                                EventLocationMaps.canShowEmbeddedMap(location)
                            ) {
                                EventLocationMapPreview(location = location.trim())
                            }
                        }

                        IosFormCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = formInteractive) { allDay = !allDay }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.field_all_day),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MacColors.primaryLabel,
                                )
                                Switch(
                                    checked = allDay,
                                    onCheckedChange = { if (formInteractive) allDay = it },
                                    enabled = formInteractive,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MacColors.accentBlue,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            DateTimeRow(
                                interactive = formInteractive,
                                label = stringResource(R.string.field_start),
                                dateText = start.toLocalDate().format(dateFmt),
                                time = if (allDay) null else start.toLocalTime(),
                                onTimeChange = if (!allDay && formInteractive) {
                                    { time ->
                                        start = start.withHour(time.hour).withMinute(time.minute)
                                            .withSecond(0).withNano(0)
                                    }
                                } else {
                                    null
                                },
                                onDateClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m0, d ->
                                            start = start.withYear(y).withMonth(m0 + 1).withDayOfMonth(d)
                                        },
                                        start.year,
                                        start.monthValue - 1,
                                        start.dayOfMonth,
                                    ).show()
                                },
                            )
                            HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            DateTimeRow(
                                interactive = formInteractive,
                                label = stringResource(R.string.field_end),
                                dateText = end.toLocalDate().format(dateFmt),
                                time = if (allDay) null else end.toLocalTime(),
                                onTimeChange = if (!allDay && formInteractive) {
                                    { time ->
                                        end = end.withHour(time.hour).withMinute(time.minute)
                                            .withSecond(0).withNano(0)
                                    }
                                } else {
                                    null
                                },
                                onDateClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m0, d ->
                                            end = end.withYear(y).withMonth(m0 + 1).withDayOfMonth(d)
                                        },
                                        end.year,
                                        end.monthValue - 1,
                                        end.dayOfMonth,
                                    ).show()
                                },
                            )
                        }

                        IosFormCard {
                            if (formInteractive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { repeatPresetMenuOpen = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.field_repeat),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MacColors.primaryLabel,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = repeatDisplay,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MacColors.secondaryLabel,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "▾",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MacColors.secondaryLabel,
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                    }
                                }
                            } else {
                                StaticChevronRow(
                                    label = stringResource(R.string.field_repeat),
                                    value = repeatDisplay,
                                )
                            }
                            HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            if (formInteractive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { repeatEndMenuOpen = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.field_repeat_end),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MacColors.primaryLabel,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = repeatEndDisplayValue(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MacColors.secondaryLabel,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "▾",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MacColors.secondaryLabel,
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                    }
                                }
                            } else {
                                StaticChevronRow(
                                    label = stringResource(R.string.field_repeat_end),
                                    value = repeatEndDisplayValue(),
                                )
                            }
                            if (repeatEndPreset == RepeatEndPreset.ON_DATE) {
                                HorizontalDivider(
                                    color = MacColors.separator,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                                DateTimeRow(
                                    interactive = formInteractive,
                                    label = stringResource(R.string.field_repeat_until),
                                    dateText = repeatUntilDate.format(dateFmt),
                                    time = null,
                                    onTimeChange = null,
                                    onDateClick = {
                                        DatePickerDialog(
                                            context,
                                            { _, y, m0, d ->
                                                repeatUntilDate = LocalDate.of(y, m0 + 1, d)
                                                repeatEndUserTouched = true
                                            },
                                            repeatUntilDate.year,
                                            repeatUntilDate.monthValue - 1,
                                            repeatUntilDate.dayOfMonth,
                                        ).show()
                                    },
                                )
                            }
                        }

                        IosFormCard {
                            CalendarPickerRow(
                                interactive = formInteractive,
                                calendars = calendars,
                                selectedIndex = selectedCalendarIndex,
                                onSelectIndex = { index ->
                                    selectedCalendarIndex = index
                                    calendars.getOrNull(index)?.id?.let { saveTargetCalendarId = it }
                                },
                            )
                            HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            if (formInteractive) {
                                var inviteesEditorOpen by remember { mutableStateOf(false) }
                                val inviteeCount = remember(inviteesText) {
                                    EventInviteeEmails.parseInvitees(inviteesText).size
                                }
                                StaticChevronRow(
                                    label = stringResource(R.string.field_invitees),
                                    value = if (inviteeCount == 0) {
                                        stringResource(R.string.value_none)
                                    } else {
                                        inviteeCount.toString()
                                    },
                                    showChevronEnd = true,
                                    onClick = { inviteesEditorOpen = !inviteesEditorOpen },
                                )
                                if (inviteesEditorOpen) {
                                    InviteesContactField(
                                        value = inviteesText,
                                        onValueChange = { inviteesText = it },
                                        placeholder = stringResource(R.string.field_invitees_placeholder),
                                    )
                                }
                            } else {
                                InviteesViewModeRow(
                                    invitees = remember(inviteesText) {
                                        EventInviteeEmails.parseInvitees(inviteesText)
                                    },
                                )
                            }
                        }

                        IosFormCard {
                            if (formInteractive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { reminderMenuOpen = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.field_notification),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MacColors.primaryLabel,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = primaryReminder.displayLabel(context),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MacColors.secondaryLabel,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "▾",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MacColors.secondaryLabel,
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                    }
                                }
                            } else {
                                StaticChevronRow(
                                    label = stringResource(R.string.field_notification),
                                    value = primaryReminder.displayLabel(context),
                                )
                            }
                            if (primaryReminder != EventReminderPreset.NONE) {
                                HorizontalDivider(
                                    color = MacColors.separator,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                                if (formInteractive) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { secondReminderMenuOpen = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.field_second_notification),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MacColors.primaryLabel,
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = secondaryReminder.displayLabel(context),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MacColors.secondaryLabel,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "▾",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MacColors.secondaryLabel,
                                                modifier = Modifier.padding(top = 1.dp),
                                            )
                                        }
                                    }
                                } else {
                                    StaticChevronRow(
                                        label = stringResource(R.string.field_second_notification),
                                        value = secondaryReminder.displayLabel(context),
                                    )
                                }
                            }
                        }

                        IosFormCard {
                            IosPlainField(
                                readOnly = !formInteractive,
                                value = url,
                                onValueChange = { url = it },
                                placeholder = stringResource(R.string.field_url),
                                singleLine = true,
                            )
                            HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            IosPlainField(
                                readOnly = !formInteractive,
                                value = notes,
                                onValueChange = { notes = it },
                                placeholder = stringResource(R.string.field_notes),
                                singleLine = false,
                                minLines = 4,
                                maxHeight = rememberNotesFieldMaxHeight(),
                                bringIntoViewKey = notes.length,
                            )
                        }

                        IosFormCard {
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
                                        .clickable { openAttachment(attachment) }
                                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
                                    if (formInteractive && attachment.source == EventAttachment.Source.LOCAL) {
                                        Text(
                                            text = "✕",
                                            modifier = Modifier
                                                .clickable { removeAttachment(attachment) }
                                                .padding(8.dp),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MacColors.todayRed,
                                        )
                                    }
                                }
                            }
                            if (formInteractive) {
                                if (attachments.isNotEmpty()) {
                                    HorizontalDivider(
                                        color = MacColors.separator,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.field_add_attachment),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { pickAttachment.launch(arrayOf("*/*")) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MacColors.accentBlue,
                                )
                            } else if (attachments.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.field_attachments),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MacColors.secondaryLabel,
                                )
                            }
                        }

                        if (showDeleteInEditMode) {
                            IosFormCard {
                                TextButton(
                                    onClick = { requestDelete() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.delete_event_button),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MacColors.todayRed,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
            }

            if (repeatCustomOpen) {
                RepeatCustomSettingsOverlay(
                    freq = customRepeatFreq,
                    onFreqChange = { customRepeatFreq = it },
                    interval = customRepeatInterval,
                    onIntervalChange = { customRepeatInterval = it },
                    onBack = {
                        recurrenceRRule = RRuleBuilder.buildCustom(
                            customRepeatFreq,
                            customRepeatInterval,
                            anchorDate,
                        )
                        repeatRuleUserTouched = true
                        repeatCustomOpen = false
                    },
                )
            }

            if (repeatPresetMenuOpen) {
                RepeatPresetPickerDialog(
                    selectedPreset = RRuleBuilder.classifyRepeatMenuPreset(recurrenceRRule, anchorDate),
                    onDismissRequest = { repeatPresetMenuOpen = false },
                    onNever = {
                        recurrenceRRule = null
                        repeatEndPreset = RepeatEndPreset.NEVER
                        repeatRuleUserTouched = true
                        repeatPresetMenuOpen = false
                    },
                    onDaily = {
                        recurrenceRRule = RRuleBuilder.buildCustom(
                            RecurrenceFreq.DAILY,
                            1,
                            anchorDate,
                        )
                        repeatRuleUserTouched = true
                        repeatPresetMenuOpen = false
                    },
                    onWeekly = {
                        recurrenceRRule = RRuleBuilder.weeklyWithByDay(1, anchorDate)
                        repeatRuleUserTouched = true
                        repeatPresetMenuOpen = false
                    },
                    onBiweekly = {
                        recurrenceRRule = RRuleBuilder.weeklyWithByDay(2, anchorDate)
                        repeatRuleUserTouched = true
                        repeatPresetMenuOpen = false
                    },
                    onMonthly = {
                        recurrenceRRule = RRuleBuilder.buildCustom(
                            RecurrenceFreq.MONTHLY,
                            1,
                            anchorDate,
                        )
                        repeatRuleUserTouched = true
                        repeatPresetMenuOpen = false
                    },
                    onYearly = {
                        recurrenceRRule = RRuleBuilder.buildCustom(
                            RecurrenceFreq.YEARLY,
                            1,
                            anchorDate,
                        )
                        repeatRuleUserTouched = true
                        repeatPresetMenuOpen = false
                    },
                    onOpenCustom = {
                        val parsed = RRuleBuilder.parseForCustom(recurrenceRRule)
                        customRepeatFreq = parsed?.freq ?: RecurrenceFreq.DAILY
                        customRepeatInterval = parsed?.interval ?: 1
                        repeatPresetMenuOpen = false
                        repeatCustomOpen = true
                    },
                )
            }

            if (repeatEndMenuOpen) {
                RepeatEndPickerDialog(
                    selectedPreset = repeatEndPreset,
                    onDismissRequest = { repeatEndMenuOpen = false },
                    onNever = {
                        repeatEndPreset = RepeatEndPreset.NEVER
                        repeatEndUserTouched = true
                        repeatEndMenuOpen = false
                    },
                    onOnDate = {
                        if (recurrenceRRule.isNullOrBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.repeat_end_requires_repeat),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            repeatEndPreset = RepeatEndPreset.ON_DATE
                            if (repeatUntilDate.isBefore(start.toLocalDate())) {
                                repeatUntilDate = start.toLocalDate()
                            }
                            repeatEndUserTouched = true
                            repeatEndMenuOpen = false
                        }
                    },
                    onDateEnabled = !recurrenceRRule.isNullOrBlank(),
                )
            }

            if (reminderMenuOpen) {
                EventReminderPickerDialog(
                    selectedPreset = primaryReminder,
                    onDismissRequest = { reminderMenuOpen = false },
                    onSelect = { primaryReminder = it },
                )
            }

            if (secondReminderMenuOpen) {
                EventReminderPickerDialog(
                    selectedPreset = secondaryReminder,
                    onDismissRequest = { secondReminderMenuOpen = false },
                    onSelect = { secondaryReminder = it },
                )
            }

            if (showDeleteConfirmSimple) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmSimple = false },
                    title = { Text(stringResource(R.string.delete_event_confirm_title)) },
                    text = { Text(stringResource(R.string.delete_event_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = { tryDeleteEntire() },
                        ) {
                            Text(
                                stringResource(R.string.delete_event_button),
                                color = MacColors.todayRed,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmSimple = false }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    },
                )
            }

            viewingAttachment?.localFile?.takeIf { it.isFile }?.let { file ->
                val attachment = viewingAttachment ?: return@let
                FileViewerDialog(
                    file = file,
                    fileName = attachment.displayName,
                    onBack = { viewingAttachment = null },
                    onOpenExternal = {
                        if (!attachmentStore.openExternally(context, attachment)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.attachment_open_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

            if (showDeleteRecurringChoice) {
                AlertDialog(
                    onDismissRequest = { showDeleteRecurringChoice = false },
                    title = { Text(stringResource(R.string.delete_recurring_title)) },
                    text = {
                        Text(
                            text = stringResource(R.string.delete_recurring_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MacColors.secondaryLabel,
                        )
                    },
                    confirmButton = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    showDeleteRecurringChoice = false
                                    tryDeleteRecurringInstance()
                                },
                            ) {
                                Text(stringResource(R.string.delete_recurring_instance))
                            }
                            TextButton(
                                onClick = {
                                    showDeleteRecurringChoice = false
                                    tryDeleteEntire()
                                },
                            ) {
                                Text(
                                    stringResource(R.string.delete_recurring_series),
                                    color = MacColors.todayRed,
                                )
                            }
                            TextButton(onClick = { showDeleteRecurringChoice = false }) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                        }
                    },
                )
            }
            if (showOverlapDialog) {
                AlertDialog(
                    onDismissRequest = { showOverlapDialog = false },
                    title = { Text(stringResource(R.string.event_overlap_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.event_overlap_message))
                            overlapConflicts.take(3).forEach { conflict ->
                                Text(
                                    text = stringResource(
                                        R.string.event_overlap_item,
                                        conflict.title,
                                        overlapTimeLabel(conflict, writeTimeZone),
                                    ),
                                    color = MacColors.secondaryLabel,
                                )
                            }
                            val extra = overlapConflicts.size - 3
                            if (extra > 0) {
                                Text(stringResource(R.string.event_overlap_more, extra))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showOverlapDialog = false
                                performSave(ignoreOverlap = true)
                            },
                        ) {
                            Text(stringResource(R.string.event_overlap_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOverlapDialog = false }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    },
                )
            }
        }
    }
    }
}

private fun overlapTimeLabel(conflict: BusyEvent, zone: ZoneId): String {
    val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val start = Instant.ofEpochMilli(conflict.beginMillis)
    val end = Instant.ofEpochMilli(conflict.endMillis)
    return "${fmt.format(start)} — ${fmt.format(end)}"
}

private fun occurrenceDateTime(
    millis: Long?,
    allDay: Boolean,
    zone: ZoneId,
    fallback: LocalDateTime,
    exclusiveAllDayEnd: Boolean = false,
): LocalDateTime {
    if (millis == null) return fallback
    return if (allDay) {
        val day = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
        if (exclusiveAllDayEnd) {
            day.minusDays(1).atTime(23, 59)
        } else {
            day.atStartOfDay()
        }
    } else {
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    }
}

@Composable
private fun EventFormHeader(
    isViewMode: Boolean,
    isExisting: Boolean,
    centerTitle: String,
    canSave: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit,
) {
    val saveActionLabel = stringResource(R.string.save_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = MacColors.segmentBackground,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("✕", style = MaterialTheme.typography.bodyLarge, color = MacColors.primaryLabel)
            }
        }
        Text(
            text = centerTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MacColors.primaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        )
        Box(
            modifier = Modifier.width(if (isViewMode && isExisting) 88.dp else 36.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (isViewMode && isExisting) {
                TextButton(onClick = onEdit) {
                    Text(
                        stringResource(R.string.edit_action),
                        color = MacColors.accentBlue,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MacColors.segmentBackground)
                        .semantics { contentDescription = saveActionLabel }
                        .clickable(
                            enabled = canSave,
                            onClick = onSave,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (canSave) MacColors.primaryLabel else MacColors.secondaryLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun IosFormCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MacColors.contentBackground),
        content = content,
    )
}

@Composable
private fun IosPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1,
    readOnly: Boolean = false,
    maxHeight: Dp? = null,
    bringIntoViewKey: Any? = null,
) {
    val heightModifier = if (maxHeight != null) {
        Modifier.heightIn(min = 112.dp, max = maxHeight)
    } else {
        Modifier
    }
    val imeModifier = if (bringIntoViewKey != null) {
        Modifier.keepVisibleWithIme(bringIntoViewKey)
    } else {
        Modifier
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        modifier = Modifier
            .fillMaxWidth()
            .then(heightModifier)
            .then(imeModifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        placeholder = {
            Text(placeholder, color = MacColors.secondaryLabel)
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MacColors.primaryLabel),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(0.dp),
    )
}

@Composable
private fun LocationFieldWithSuggestion(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    placeholder: String,
) {
    var suggestions by remember { mutableStateOf<List<YandexPlaceSuggestion>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(value, readOnly) {
        val query = value.trim()
        if (
            readOnly ||
            query.length < 2 ||
            query == selectedAddress ||
            !EventLocationMaps.canShowEmbeddedMap(query)
        ) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = EventLocationMaps.suggestPlaces(query)
            .filterNot { it.address.equals(query, ignoreCase = true) }
            .take(8)
    }
    IosPlainField(
        readOnly = readOnly,
        value = value,
        onValueChange = {
            selectedAddress = null
            onValueChange(it)
        },
        placeholder = placeholder,
        singleLine = true,
    )
    if (!readOnly && suggestions.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .keepVisibleWithIme(suggestions.size),
        ) {
            suggestions.forEachIndexed { index, place ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MacColors.separator,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedAddress = place.address
                            suggestions = emptyList()
                            onValueChange(place.address)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = place.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MacColors.primaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (place.subtitle.isNotBlank()) {
                        Text(
                            text = place.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MacColors.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    dateText: String,
    time: LocalTime?,
    onTimeChange: ((LocalTime) -> Unit)?,
    onDateClick: () -> Unit,
    interactive: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MacColors.segmentBackground)
                    .then(
                        if (interactive) {
                            Modifier.clickable(onClick = onDateClick)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MacColors.primaryLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (time != null) {
                if (interactive && onTimeChange != null) {
                    EventTimeCompactField(
                        value = time,
                        onValueChange = onTimeChange,
                        enabled = true,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MacColors.segmentBackground),
                    ) {
                        Text(
                            text = formatEventTime(time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MacColors.primaryLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticChevronRow(
    label: String,
    value: String,
    showChevronEnd: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.secondaryLabel,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (showChevronEnd) "›" else "▾",
                style = MaterialTheme.typography.bodySmall,
                color = MacColors.secondaryLabel,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun CalendarPickerRow(
    calendars: List<WritableCalendar>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    interactive: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cal = calendars.getOrNull(selectedIndex)
    val canPick = interactive && calendars.size > 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canPick) { menuOpen = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.field_calendar),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor = cal?.color?.let { Color(it) } ?: MacColors.accentBlue
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = cal?.displayName ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.secondaryLabel,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (canPick) "▾" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MacColors.secondaryLabel,
            )
        }
    }

    if (menuOpen && calendars.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text(stringResource(R.string.field_calendar)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    calendars.forEachIndexed { index, c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectIndex(index)
                                    menuOpen = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(c.color?.let { Color(it) } ?: MacColors.accentBlue),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(c.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuOpen = false }) {
                    Text(stringResource(R.string.dialog_done))
                }
            },
        )
    }
}
