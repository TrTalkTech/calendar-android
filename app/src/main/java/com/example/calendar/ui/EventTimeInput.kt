package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.R
import com.example.calendar.ui.theme.MacColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeDisplayFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Начало текущего часа на выбранной дате. */
fun defaultNewEventStart(date: LocalDate): LocalDateTime {
    val hourStart = LocalTime.now().withMinute(0).withSecond(0).withNano(0)
    return LocalDateTime.of(date, hourStart)
}

fun defaultNewEventEnd(start: LocalDateTime): LocalDateTime = start.plusHours(1)

fun formatEventTime(time: LocalTime): String = time.format(timeDisplayFormatter)

fun sanitizeEventTimeInput(raw: String): String {
    if (raw.isBlank()) return ""
    if (raw.contains(':')) {
        val parts = raw.split(':', limit = 2)
        val hours = parts[0].filter(Char::isDigit).take(2)
        val minutes = parts.getOrElse(1) { "" }.filter(Char::isDigit).take(2)
        return if (minutes.isEmpty()) hours else "$hours:$minutes"
    }
    val digits = raw.filter(Char::isDigit).take(4)
    return when (digits.length) {
        in 0..2 -> digits
        else -> "${digits.take(2)}:${digits.drop(2)}"
    }
}

fun parseEventTimeText(text: String): LocalTime? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.contains(':')) {
        val parts = trimmed.split(':', limit = 2)
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
    val digits = trimmed.filter(Char::isDigit)
    return when (digits.length) {
        1, 2 -> {
            val hour = digits.toInt()
            if (hour !in 0..23) null else LocalTime.of(hour, 0)
        }
        3, 4 -> {
            val hour = digits.take(2).toIntOrNull() ?: return null
            val minute = digits.drop(2).toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) null else LocalTime.of(hour, minute)
        }
        else -> null
    }
}

private fun formatTwoDigits(value: Int): String = value.toString().padStart(2, '0')

private fun sanitizeTwoDigitInput(raw: String, maxValue: Int): String {
    val digits = raw.filter(Char::isDigit).take(2)
    if (digits.isEmpty()) return ""
    val asInt = digits.toIntOrNull() ?: return digits
    return if (asInt > maxValue) digits.take(1) else digits
}

private fun parseTwoDigitUnit(text: String, maxValue: Int): Int? {
    val digits = text.filter(Char::isDigit)
    if (digits.isEmpty()) return null
    val value = digits.toIntOrNull() ?: return null
    return value.takeIf { it in 0..maxValue }
}

/** Компактная капсула «11:00» для строки начала и конца. */
@Composable
fun EventTimeCompactField(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(formatEventTime(value)) }
    LaunchedEffect(value) {
        if (parseEventTimeText(text) != value) {
            text = formatEventTime(value)
        }
    }
    val color = if (enabled) MacColors.primaryLabel else MacColors.secondaryLabel
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val sanitized = sanitizeEventTimeInput(raw)
            text = sanitized
            parseEventTimeText(sanitized)?.let(onValueChange)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = color,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .width(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MacColors.segmentBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/**
 * Два крупных поля: часы и минуты, с подписями под каждым.
 */
@Composable
fun EventTimeNumericField(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeUnitField(
            label = stringResource(R.string.time_hours_label),
            value = value.hour,
            maxValue = 23,
            enabled = enabled,
            onValueChange = { hour ->
                onValueChange(value.withHour(hour).withSecond(0).withNano(0))
            },
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
            ),
            color = MacColors.primaryLabel,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        TimeUnitField(
            label = stringResource(R.string.time_minutes_label),
            value = value.minute,
            maxValue = 59,
            enabled = enabled,
            onValueChange = { minute ->
                onValueChange(value.withMinute(minute).withSecond(0).withNano(0))
            },
        )
    }
}

@Composable
private fun TimeUnitField(
    label: String,
    value: Int,
    maxValue: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(formatTwoDigits(value)) }

    LaunchedEffect(value) {
        if (parseTwoDigitUnit(text, maxValue) != value) {
            text = formatTwoDigits(value)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(
            value = text,
            onValueChange = { raw ->
                val sanitized = sanitizeTwoDigitInput(raw, maxValue)
                text = sanitized
                parseTwoDigitUnit(sanitized, maxValue)?.let(onValueChange)
            },
            enabled = enabled,
            modifier = Modifier
                .width(84.dp)
                .defaultMinSize(minHeight = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MacColors.segmentBackground),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = MacColors.primaryLabel,
            ),
            placeholder = {
                Text(
                    text = "00",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp,
                    ),
                    color = MacColors.secondaryLabel,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MacColors.segmentBackground,
                unfocusedContainerColor = MacColors.segmentBackground,
                disabledContainerColor = MacColors.segmentBackground,
                focusedIndicatorColor = MacColors.accentBlue,
                unfocusedIndicatorColor = MacColors.separator,
                disabledIndicatorColor = MacColors.separator,
                cursorColor = MacColors.accentBlue,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MacColors.secondaryLabel,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
