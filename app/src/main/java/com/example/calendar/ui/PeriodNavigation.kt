package com.example.calendar.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.calendar.CalendarPeriod
import com.example.calendar.R
import java.time.LocalDate

@Composable
fun Modifier.horizontalPeriodSwipe(onSwipe: (forward: Boolean) -> Unit): Modifier {
    val current = rememberUpdatedState(onSwipe)
    return pointerInput(Unit) {
        val thresholdPx = 56.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { _, dragAmount -> total += dragAmount },
            onDragCancel = { total = 0f },
            onDragEnd = {
                when {
                    total <= -thresholdPx -> current.value(true)
                    total >= thresholdPx -> current.value(false)
                }
                total = 0f
            },
        )
    }
}

fun <S : Comparable<S>> AnimatedContentTransitionScope<S>.periodSlide(): ContentTransform {
    val forward = targetState > initialState
    val direction = if (forward) 1 else -1
    return slideInHorizontally { width -> direction * width } togetherWith
        slideOutHorizontally { width -> -direction * width }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = CalendarPeriod.pickerMillis(initialDate),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onConfirm(CalendarPeriod.dateFromPickerMillis(millis))
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.calendars_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}
