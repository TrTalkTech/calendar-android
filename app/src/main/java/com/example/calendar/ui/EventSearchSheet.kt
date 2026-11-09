package com.example.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.AppLocale
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import com.example.calendar.data.CalendarEvent
import com.example.calendar.ui.theme.MacColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

@Composable
fun EventSearchDialog(
    query: String,
    results: List<CalendarEvent>,
    loading: Boolean,
    today: LocalDate,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val zone = remember { ZoneId.systemDefault() }

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
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_events_placeholder),
                                color = MacColors.secondaryLabel,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MacColors.primaryLabel),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboard?.hide() },
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.dialog_cancel),
                                        tint = MacColors.secondaryLabel,
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MacColors.contentBackground,
                            unfocusedContainerColor = MacColors.contentBackground,
                        ),
                        shape = CircleShape,
                    )
                }

                val trimmed = query.trim()
                val groups = remember(results) {
                    results.groupBy { event ->
                        Instant.ofEpochMilli(event.beginMillis).atZone(zone).toLocalDate()
                    }
                }

                when {
                    trimmed.isEmpty() -> {
                        SearchMessage(stringResource(R.string.search_events_hint))
                    }

                    loading && results.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MacColors.todayRed)
                        }
                    }

                    results.isEmpty() -> {
                        SearchMessage(stringResource(R.string.search_events_no_results))
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            groups.forEach { (date, events) ->
                                item(key = "header-$date") {
                                    Text(
                                        text = searchDayHeaderText(date, today),
                                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MacColors.secondaryLabel,
                                    )
                                }
                                items(
                                    events,
                                    key = { "${it.eventId}-${it.beginMillis}" },
                                ) { event ->
                                    JournalEventCard(
                                        event = event,
                                        onOpen = { onEventClick(event) },
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun searchDayHeaderText(date: LocalDate, today: LocalDate): String {
    val dayMonth = RussianDateFormat.dayAndMonthGenitive(date)
    if (date == today) {
        return stringResource(R.string.journal_today_title, dayMonth)
    }
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, AppLocale.display)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(AppLocale.display) else it.toString() }
    return "$weekday, $dayMonth"
}
