package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import com.example.calendar.data.CalendarTask
import com.example.calendar.ui.theme.MacColors
import java.time.LocalDate

private data class TaskSection(
    val titleRes: Int,
    val tasks: List<CalendarTask>,
    val showDate: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    tasks: List<CalendarTask>,
    today: LocalDate,
    hasTaskCalendar: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onAddClick: () -> Unit,
    onToggleDone: (CalendarTask, Boolean) -> Unit,
    onOpenTask: (CalendarTask) -> Unit,
    onDelete: (CalendarTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember(tasks, today) { groupTasks(tasks, today) }
    var pendingDelete by remember { mutableStateOf<CalendarTask?>(null) }
    val refreshState = rememberPullToRefreshState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tasks_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MacColors.primaryLabel,
                modifier = Modifier.weight(1f),
            )
            if (hasTaskCalendar) {
                IconButton(onClick = onAddClick) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.tasks_new_title),
                        tint = MacColors.secondaryLabel,
                    )
                }
            }
        }
        when {
            !hasTaskCalendar && !loading -> {
                Text(
                    text = stringResource(R.string.tasks_missing_calendar),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                )
            }

            loading && tasks.isEmpty() && !refreshing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MacColors.todayRed)
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    state = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = refreshState,
                            isRefreshing = refreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            color = MacColors.todayRed,
                            containerColor = MacColors.contentBackground,
                        )
                    },
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (sections.all { it.tasks.isEmpty() }) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.tasks_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MacColors.secondaryLabel,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            sections.filter { it.tasks.isNotEmpty() }.forEach { section ->
                                item(key = "header-${section.titleRes}") {
                                    Text(
                                        text = stringResource(section.titleRes),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MacColors.secondaryLabel,
                                        modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 2.dp),
                                    )
                                }
                                items(section.tasks, key = { it.eventId }) { task ->
                                    TaskRow(
                                        task = task,
                                        today = today,
                                        showDate = section.showDate,
                                        onToggle = { onToggleDone(task, !task.done) },
                                        onOpen = { onOpenTask(task) },
                                        onRequestDelete = { pendingDelete = task },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.tasks_delete_title)) },
            text = { Text(stringResource(R.string.tasks_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(task)
                        pendingDelete = null
                    },
                ) {
                    Text(
                        stringResource(R.string.tasks_delete_button),
                        color = MacColors.todayRed,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(
    task: CalendarTask,
    today: LocalDate,
    showDate: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MacColors.todayRed)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = stringResource(R.string.tasks_delete_button),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MacColors.contentBackground)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onRequestDelete,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle() },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title.ifBlank { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (task.done) MacColors.secondaryLabel else MacColors.primaryLabel,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDate) {
                Text(
                    text = RussianDateFormat.dayAndMonthGenitive(task.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!task.done && task.date.isBefore(today)) {
                        MacColors.todayRed
                    } else {
                        MacColors.secondaryLabel
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    }
}

private fun groupTasks(tasks: List<CalendarTask>, today: LocalDate): List<TaskSection> {
    val open = tasks.filterNot { it.done }
    val done = tasks.filter { it.done }
        .sortedByDescending { it.date }
        .take(50)
    return listOf(
        TaskSection(
            titleRes = R.string.tasks_section_overdue,
            tasks = open.filter { it.date.isBefore(today) }.sortedBy { it.date },
            showDate = true,
        ),
        TaskSection(
            titleRes = R.string.tasks_section_today,
            tasks = open.filter { it.date == today }.sortedBy { it.title },
            showDate = false,
        ),
        TaskSection(
            titleRes = R.string.tasks_section_later,
            tasks = open.filter { it.date.isAfter(today) }.sortedBy { it.date },
            showDate = true,
        ),
        TaskSection(
            titleRes = R.string.tasks_section_done,
            tasks = done,
            showDate = true,
        ),
    )
}
