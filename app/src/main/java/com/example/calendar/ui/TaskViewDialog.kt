package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import com.example.calendar.data.CalendarTask
import com.example.calendar.data.TaskNotesHtml
import com.example.calendar.ui.theme.MacColors

@Composable
fun TaskViewDialog(
    task: CalendarTask,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    val notes = remember(task.notes) { TaskNotesHtml.toAnnotated(task.notes) }
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
                        text = task.title.ifBlank { stringResource(R.string.event_no_title) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MacColors.primaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                    )
                    TextButton(onClick = onEdit) {
                        Text(
                            stringResource(R.string.edit_action),
                            color = MacColors.accentBlue,
                            style = MaterialTheme.typography.bodyLarge,
                        )
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MacColors.contentBackground)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = task.title.ifBlank { stringResource(R.string.event_no_title) },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MacColors.primaryLabel,
                        )
                        Text(
                            text = RussianDateFormat.dayAndMonthGenitive(task.date),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MacColors.secondaryLabel,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (notes.text.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MacColors.contentBackground)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.field_notes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MacColors.secondaryLabel,
                            )
                            SelectionContainer {
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MacColors.primaryLabel,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
