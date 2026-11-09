package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.RecurrenceFreq
import com.example.calendar.RepeatEndPreset
import com.example.calendar.RepeatMenuPreset
import com.example.calendar.ui.theme.MacColors

@Composable
fun RepeatPresetPickerDialog(
    selectedPreset: RepeatMenuPreset,
    onDismissRequest: () -> Unit,
    onNever: () -> Unit,
    onDaily: () -> Unit,
    onWeekly: () -> Unit,
    onBiweekly: () -> Unit,
    onMonthly: () -> Unit,
    onYearly: () -> Unit,
    onOpenCustom: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.field_repeat)) },
        text = {
            Column {
                PresetDialogRow(
                    text = stringResource(R.string.value_never),
                    selected = selectedPreset == RepeatMenuPreset.NEVER,
                    onClick = onNever,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_every_day),
                    selected = selectedPreset == RepeatMenuPreset.DAILY,
                    onClick = onDaily,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_every_week),
                    selected = selectedPreset == RepeatMenuPreset.WEEKLY,
                    onClick = onWeekly,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_every_n_weeks, 2),
                    selected = selectedPreset == RepeatMenuPreset.BIWEEKLY,
                    onClick = onBiweekly,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_every_month),
                    selected = selectedPreset == RepeatMenuPreset.MONTHLY,
                    onClick = onMonthly,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_every_year),
                    selected = selectedPreset == RepeatMenuPreset.YEARLY,
                    onClick = onYearly,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MacColors.separator,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_menu_customization),
                    selected = selectedPreset == RepeatMenuPreset.CUSTOM,
                    onClick = onOpenCustom,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_done))
            }
        },
    )
}

@Composable
fun RepeatEndPickerDialog(
    selectedPreset: RepeatEndPreset,
    onDismissRequest: () -> Unit,
    onNever: () -> Unit,
    onOnDate: () -> Unit,
    onDateEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.field_repeat_end)) },
        text = {
            Column {
                PresetDialogRow(
                    text = stringResource(R.string.value_never),
                    selected = selectedPreset == RepeatEndPreset.NEVER,
                    onClick = onNever,
                )
                PresetDialogRow(
                    text = stringResource(R.string.repeat_end_on_date),
                    selected = selectedPreset == RepeatEndPreset.ON_DATE,
                    onClick = onOnDate,
                    enabled = onDateEnabled,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_done))
            }
        },
    )
}

@Composable
private fun PresetDialogRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .padding(end = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MacColors.accentBlue else MacColors.secondaryLabel,
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MacColors.primaryLabel else MacColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun RepeatCustomSettingsOverlay(
    freq: RecurrenceFreq,
    onFreqChange: (RecurrenceFreq) -> Unit,
    interval: Int,
    onIntervalChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MacColors.windowBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.repeat_cd_back),
                        tint = MacColors.accentBlue,
                    )
                }
                Text(
                    text = stringResource(R.string.repeat_menu_customization),
                    style = MaterialTheme.typography.titleMedium,
                    color = MacColors.primaryLabel,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.repeat_configure_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MacColors.secondaryLabel,
                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
                )
                FreqRadioRow(
                    label = stringResource(R.string.repeat_mode_daily),
                    selected = freq == RecurrenceFreq.DAILY,
                    onSelect = { onFreqChange(RecurrenceFreq.DAILY) },
                )
                FreqRadioRow(
                    label = stringResource(R.string.repeat_mode_weekly),
                    selected = freq == RecurrenceFreq.WEEKLY,
                    onSelect = { onFreqChange(RecurrenceFreq.WEEKLY) },
                )
                FreqRadioRow(
                    label = stringResource(R.string.repeat_mode_monthly),
                    selected = freq == RecurrenceFreq.MONTHLY,
                    onSelect = { onFreqChange(RecurrenceFreq.MONTHLY) },
                )
                FreqRadioRow(
                    label = stringResource(R.string.repeat_mode_yearly),
                    selected = freq == RecurrenceFreq.YEARLY,
                    onSelect = { onFreqChange(RecurrenceFreq.YEARLY) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MacColors.separator,
                )

                Text(
                    text = stringResource(R.string.repeat_each_header),
                    style = MaterialTheme.typography.bodySmall,
                    color = MacColors.secondaryLabel,
                )
                Text(
                    text = when (freq) {
                        RecurrenceFreq.DAILY -> stringResource(R.string.repeat_unit_nominative_day)
                        RecurrenceFreq.WEEKLY -> stringResource(R.string.repeat_unit_nominative_week)
                        RecurrenceFreq.MONTHLY -> stringResource(R.string.repeat_unit_nominative_month)
                        RecurrenceFreq.YEARLY -> stringResource(R.string.repeat_unit_nominative_year)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.primaryLabel,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                val listState = rememberLazyListState()
                LaunchedEffect(freq) {
                    listState.scrollToItem((interval - 1).coerceIn(0, 999))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(
                        count = 1000,
                        key = { index -> index + 1 },
                    ) { index ->
                        val n = index + 1
                        val label = when (freq) {
                            RecurrenceFreq.DAILY -> RussianQuantity.days(n)
                            RecurrenceFreq.WEEKLY -> RussianQuantity.weeks(n)
                            RecurrenceFreq.MONTHLY -> RussianQuantity.months(n)
                            RecurrenceFreq.YEARLY -> RussianQuantity.years(n)
                        }
                        val selected = n == interval
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onIntervalChange(n) }
                                .background(
                                    if (selected) MacColors.segmentBackground else MacColors.windowBackground,
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MacColors.accentBlue else MacColors.primaryLabel,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FreqRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MacColors.accentBlue,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
    }
}
