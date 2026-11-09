@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.calendar.ui

import android.app.DatePickerDialog
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.EventReminderPreset
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import com.example.calendar.data.CalendarEventWriter
import com.example.calendar.data.CalendarTask
import com.example.calendar.data.CalendarTaskRepository
import com.example.calendar.data.TaskNotesHtml
import com.example.calendar.data.WritableCalendar
import com.example.calendar.data.preferredTaskCalendar
import com.example.calendar.data.writeLabel
import com.example.calendar.displayLabel
import com.example.calendar.minutesBeforeForSave
import com.example.calendar.ui.theme.MacColors
import java.time.LocalDate

@Composable
fun NewTaskDialog(
    calendars: List<WritableCalendar>,
    preferredAccountNames: Set<String> = emptySet(),
    preferredTaskCalendarId: Long? = null,
    initialDate: LocalDate,
    existingTask: CalendarTask? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember {
        val writer = CalendarEventWriter(context.contentResolver)
        CalendarTaskRepository(context.contentResolver, writer)
    }
    var title by remember { mutableStateOf(existingTask?.title.orEmpty()) }
    var date by remember { mutableStateOf(existingTask?.date ?: initialDate) }
    var notes by remember {
        mutableStateOf(TextFieldValue(TaskNotesHtml.toAnnotated(existingTask?.notes.orEmpty())))
    }
    var reminder by remember { mutableStateOf(EventReminderPreset.NONE) }
    var reminderMenuOpen by remember { mutableStateOf(false) }
    var userPickedCalendar by remember { mutableStateOf(false) }
    var selectedCalendarIndex by remember {
        mutableIntStateOf(
            initialTaskCalendarIndex(
                calendars,
                existingTask,
                preferredAccountNames,
                preferredTaskCalendarId,
            ),
        )
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canSave = title.isNotBlank() && calendars.isNotEmpty() && !saving

    LaunchedEffect(existingTask?.eventId) {
        val eventId = existingTask?.eventId ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            CalendarEventWriter(context.contentResolver).loadEventForEdit(eventId)
        } ?: return@LaunchedEffect
        title = CalendarTaskRepository.stripDonePrefix(loaded.title).ifBlank { loaded.title }
        date = loaded.start.toLocalDate()
        notes = TextFieldValue(TaskNotesHtml.toAnnotated(loaded.notes))
        reminder = loaded.reminderMinutesBefore.firstOrNull()
            ?.let(EventReminderPreset::fromStoredMinutes)
            ?: EventReminderPreset.NONE
        val idx = calendars.indexOfFirst { it.id == loaded.calendarId }
        if (idx >= 0) selectedCalendarIndex = idx
    }
    LaunchedEffect(
        calendars.map { it.id },
        existingTask?.eventId,
        preferredAccountNames,
        preferredTaskCalendarId,
    ) {
        if (existingTask != null || userPickedCalendar) return@LaunchedEffect
        selectedCalendarIndex = initialTaskCalendarIndex(
            calendars,
            null,
            preferredAccountNames,
            preferredTaskCalendarId,
        )
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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MacColors.segmentBackground,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("✕", style = MaterialTheme.typography.bodyLarge, color = MacColors.primaryLabel)
                        }
                    }
                    Text(
                        text = stringResource(
                            if (existingTask == null) R.string.tasks_new_title else R.string.edit_screen_title,
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MacColors.primaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                    )
                    Surface(
                        onClick = {
                            val calendar = calendars.getOrNull(selectedCalendarIndex) ?: return@Surface
                            if (!canSave) return@Surface
                            saving = true
                            scope.launch {
                                val notesHtml = TaskNotesHtml.toHtml(notes.annotatedString)
                                val ok = withContext(Dispatchers.IO) {
                                    if (existingTask == null) {
                                        repository.insertTask(
                                            calendarId = calendar.id,
                                            title = title,
                                            date = date,
                                            notes = notesHtml,
                                            reminderMinutesBefore = listOf(reminder).minutesBeforeForSave(),
                                        ) != null
                                    } else {
                                        repository.updateTask(
                                            eventId = existingTask.eventId,
                                            calendarId = calendar.id,
                                            title = title,
                                            date = date,
                                            notes = notesHtml,
                                            reminderMinutesBefore = listOf(reminder).minutesBeforeForSave(),
                                            done = existingTask.done,
                                        )
                                    }
                                }
                                saving = false
                                if (!ok) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.tasks_save_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    onSaved()
                                }
                            }
                        },
                        enabled = canSave,
                        shape = CircleShape,
                        color = if (canSave) MacColors.todayRed else MacColors.segmentBackground,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (canSave) Color.White else MacColors.secondaryLabel,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TaskFormCard {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            placeholder = {
                                Text(stringResource(R.string.field_title), color = MacColors.secondaryLabel)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MacColors.primaryLabel),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    }
                    TaskFormCard {
                        TaskValueRow(
                            label = stringResource(R.string.field_due_date),
                            value = RussianDateFormat.dayAndMonthGenitive(date),
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        date = LocalDate.of(year, month + 1, day)
                                    },
                                    date.year,
                                    date.monthValue - 1,
                                    date.dayOfMonth,
                                ).show()
                            },
                        )
                        if (calendars.isNotEmpty()) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MacColors.separator,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            val selected = calendars.getOrNull(selectedCalendarIndex)
                            TaskValueRow(
                                label = stringResource(R.string.field_calendar),
                                value = selected?.writeLabel().orEmpty(),
                                onClick = {
                                    if (calendars.size > 1) {
                                        userPickedCalendar = true
                                        selectedCalendarIndex = (selectedCalendarIndex + 1) % calendars.size
                                    }
                                },
                            )
                        }
                        androidx.compose.material3.HorizontalDivider(
                            color = MacColors.separator,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                        TaskValueRow(
                            label = stringResource(R.string.field_notification),
                            value = reminder.displayLabel(context),
                            onClick = { reminderMenuOpen = true },
                        )
                    }
                    TaskFormCard {
                        TaskNotesEditor(
                            value = notes,
                            onValueChange = { notes = it },
                        )
                    }
                }
            }
        }
        if (reminderMenuOpen) {
            EventReminderPickerDialog(
                selectedPreset = reminder,
                onDismissRequest = { reminderMenuOpen = false },
                onSelect = {
                    reminder = it
                    reminderMenuOpen = false
                },
            )
        }
    }
}

private fun initialTaskCalendarIndex(
    calendars: List<WritableCalendar>,
    existingTask: CalendarTask?,
    preferredAccountNames: Set<String>,
    preferredTaskCalendarId: Long? = null,
): Int {
    if (existingTask != null) {
        val existing = calendars.indexOfFirst { it.id == existingTask.calendarId }
        if (existing >= 0) return existing
    }
    if (preferredTaskCalendarId != null) {
        val slot = calendars.indexOfFirst { it.id == preferredTaskCalendarId }
        if (slot >= 0) return slot
    }
    val preferredId = calendars.preferredTaskCalendar(preferredAccountNames)?.id
    val preferred = calendars.indexOfFirst { it.id == preferredId }
    return preferred.takeIf { it >= 0 } ?: 0
}

@Composable
private fun TaskFormCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MacColors.contentBackground),
        content = content,
    )
}

@Composable
private fun TaskValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "▾",
                style = MaterialTheme.typography.bodySmall,
                color = MacColors.secondaryLabel,
            )
        }
    }
}
