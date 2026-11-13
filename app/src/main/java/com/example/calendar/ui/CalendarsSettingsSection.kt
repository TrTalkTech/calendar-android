package com.example.calendar.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.R
import com.example.calendar.data.AccountSlots
import com.example.calendar.data.AccountSlotsState
import com.example.calendar.data.WritableCalendar
import com.example.calendar.data.eventCalendars
import com.example.calendar.data.taskCalendars
import com.example.calendar.ui.theme.MacColors

@Composable
fun CalendarsSettingsSection(
    hasReadPermission: Boolean,
    onRequestReadPermission: () -> Unit,
    calendars: List<WritableCalendar>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetryLoad: () -> Unit,
    slots: AccountSlotsState,
    onSlotsChange: (AccountSlotsState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    fun openSyncSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.calendars_accounts_title),
            style = MaterialTheme.typography.bodySmall,
            color = MacColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        when {
            !hasReadPermission -> {
                Text(
                    text = stringResource(R.string.calendars_permission_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MacColors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Button(
                    onClick = onRequestReadPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.calendars_grant_read))
                }
            }

            isLoading && calendars.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MacColors.accentBlue)
                }
            }

            loadFailed && calendars.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.calendars_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MacColors.secondaryLabel,
                    )
                    Button(
                        onClick = onRetryLoad,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.calendars_retry_load))
                    }
                }
            }

            calendars.isEmpty() -> {
                Text(
                    text = stringResource(R.string.calendars_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MacColors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            else -> {
                // Показываем только настроенные слоты + один пустой для добавления.
                val visibleSlots = remember(slots) { slotsWithIndex(slots) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MacColors.contentBackground)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visibleSlots.forEach { (index, _) ->
                        AccountSlotCard(
                            calendars = calendars,
                            state = slots,
                            index = index,
                            onOpen = { editingSlot = index },
                            onActivate = {
                                onSlotsChange(AccountSlots.setActive(slots, index))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(onClick = ::openSyncSettings, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.calendars_open_sync_settings),
                    color = MacColors.accentBlue,
                )
            }
        }
    }

    val slotIndex = editingSlot
    if (slotIndex != null) {
        AccountSlotEditorDialog(
            calendars = calendars,
            state = slots,
            slotIndex = slotIndex,
            onStateChange = onSlotsChange,
            onDismiss = { editingSlot = null },
        )
    }
}

@Composable
private fun AccountSlotCard(
    calendars: List<WritableCalendar>,
    state: AccountSlotsState,
    index: Int,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slot = state.slot(index)
    val byId = calendars.associateBy { it.id }
    val defaultCal = slot.defaultEventCalendarId?.let { byId[it] }
        ?: slot.eventCalendarIds.firstOrNull()?.let { byId[it] }
    val name = defaultCal?.displayName?.ifBlank { null }
        ?: stringResource(R.string.calendars_slot_empty)
    val number = stringResource(R.string.calendars_slot_number, index + 1)
    val selected = state.activeIndex == index && slot.isConfigured

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MacColors.windowBackground else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val openLabel = stringResource(R.string.calendars_slot_open, name)
        val activateLabel = stringResource(
            if (selected) R.string.calendars_slot_active else R.string.calendars_slot_activate,
            name,
        )
        // Радио-кнопка активного слота
        val fill = if (selected) MacColors.accentBlue else Color.Transparent
        val stroke = if (selected) MacColors.accentBlue else MacColors.separator
        Box(
            modifier = Modifier
                .size(32.dp)
                .semantics { contentDescription = activateLabel }
                .clickable {
                    if (slot.isConfigured) onActivate() else onOpen()
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.5.dp, stroke, CircleShape)
                    .background(fill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .semantics { contentDescription = openLabel }
                .clickable(onClick = onOpen),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.primaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = number,
                style = MaterialTheme.typography.bodySmall,
                color = MacColors.secondaryLabel,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AccountSlotEditorDialog(
    calendars: List<WritableCalendar>,
    state: AccountSlotsState,
    slotIndex: Int,
    onStateChange: (AccountSlotsState) -> Unit,
    onDismiss: () -> Unit,
) {
    val slot = state.slot(slotIndex)
    val usedElsewhere = AccountSlots.usedIds(state, exceptSlot = slotIndex)
    val eventChoices = calendars.eventCalendars().filter { calendar ->
        calendar.id in slot.eventCalendarIds || calendar.id !in usedElsewhere
    }
    val taskChoices = calendars.taskCalendars().filter { calendar ->
        calendar.id == slot.taskCalendarId || calendar.id !in usedElsewhere
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MacColors.contentBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
            ) {
                Text(
                    text = stringResource(R.string.calendars_slot_editor_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MacColors.primaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.calendars_events_section),
                        style = MaterialTheme.typography.bodySmall,
                        color = MacColors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    eventChoices.forEach { calendar ->
                        val selected = calendar.id in slot.eventCalendarIds
                        val isDefault = selected && calendar.id == slot.defaultEventCalendarId
                        CalendarPickRow(
                            calendar = calendar,
                            selected = selected,
                            defaultLabel = if (isDefault) {
                                stringResource(R.string.calendars_default_label)
                            } else {
                                null
                            },
                            showDefaultToggle = selected,
                            isDefault = isDefault,
                            onToggle = {
                                onStateChange(AccountSlots.toggleEventCalendar(state, slotIndex, calendar.id))
                            },
                            onMakeDefault = {
                                onStateChange(
                                    AccountSlots.setDefaultEventCalendar(state, slotIndex, calendar.id),
                                )
                            },
                        )
                    }
                    if (eventChoices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.calendars_none_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MacColors.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    HorizontalDivider(
                        color = MacColors.separator,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.calendars_tasks_section),
                        style = MaterialTheme.typography.bodySmall,
                        color = MacColors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    if (taskChoices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.calendars_no_tasks),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MacColors.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    } else {
                        taskChoices.forEach { calendar ->
                            val selected = calendar.id == slot.taskCalendarId
                            CalendarPickRow(
                                calendar = calendar,
                                selected = selected,
                                defaultLabel = if (selected) {
                                    stringResource(R.string.tasks_title)
                                } else {
                                    null
                                },
                                showDefaultToggle = false,
                                isDefault = false,
                                onToggle = {
                                    onStateChange(
                                        AccountSlots.setTaskCalendar(
                                            state,
                                            slotIndex,
                                            calendar.id,
                                        ),
                                    )
                                },
                                onMakeDefault = {},
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 8.dp, bottom = 4.dp),
                ) {
                    Text(stringResource(R.string.dialog_done), color = MacColors.accentBlue)
                }
            }
        }
    }
}

@Composable
private fun CalendarPickRow(
    calendar: WritableCalendar,
    selected: Boolean,
    defaultLabel: String?,
    showDefaultToggle: Boolean,
    isDefault: Boolean,
    onToggle: () -> Unit,
    onMakeDefault: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(
                    1.5.dp,
                    if (selected) MacColors.accentBlue else MacColors.separator,
                    CircleShape,
                )
                .background(if (selected) MacColors.accentBlue else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(calendar.color?.let { Color(it) } ?: MacColors.accentBlue),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = calendar.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.primaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val extra = listOfNotNull(
                defaultLabel,
                calendar.accountName?.takeIf { it.isNotBlank() && !it.equals(calendar.displayName, true) },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(
                    text = extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = MacColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showDefaultToggle) {
            Icon(
                imageVector = if (isDefault) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = stringResource(R.string.calendars_make_default),
                tint = if (isDefault) Color(0xFFFFCC00) else MacColors.secondaryLabel,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onMakeDefault)
                    .padding(4.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
