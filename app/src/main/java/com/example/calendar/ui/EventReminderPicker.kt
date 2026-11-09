package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.EventReminderPreset
import com.example.calendar.displayLabel
import com.example.calendar.ui.theme.MacColors

@Composable
fun EventReminderPickerDialog(
    selectedPreset: EventReminderPreset,
    onDismissRequest: () -> Unit,
    onSelect: (EventReminderPreset) -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.78f)
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MacColors.contentBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                EventReminderPreset.selectable.forEach { preset ->
                    ReminderMenuRow(
                        text = preset.displayLabel(context),
                        selected = selectedPreset == preset,
                        onClick = {
                            onSelect(preset)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderMenuRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .padding(end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.primaryLabel,
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
        )
    }
}
