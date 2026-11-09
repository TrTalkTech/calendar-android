package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.R
import com.example.calendar.ui.theme.MacColors

@Composable
fun TaskNotesEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxHeight = rememberNotesFieldMaxHeight()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .keepVisibleWithIme(value.selection),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatChip(
                label = "Ж",
                selected = selectionHas(value, bold = true),
                onClick = { onValueChange(value.withStyle(SpanStyle(fontWeight = FontWeight.Bold))) },
            )
            FormatChip(
                label = "К",
                selected = selectionHas(value, italic = true),
                onClick = { onValueChange(value.withStyle(SpanStyle(fontStyle = FontStyle.Italic))) },
            )
            FormatChip(
                label = "Ч",
                selected = selectionHas(value, underline = true),
                onClick = {
                    onValueChange(value.withStyle(SpanStyle(textDecoration = TextDecoration.Underline)))
                },
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = maxHeight)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = Int.MAX_VALUE,
            textStyle = TextStyle(
                color = MacColors.primaryLabel,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
            placeholder = {
                Text(
                    text = stringResource(R.string.field_notes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.secondaryLabel,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun FormatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MacColors.segmentBackground else MacColors.contentBackground)
            .border(1.dp, MacColors.separator, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) MacColors.todayRed else MacColors.primaryLabel,
    )
}

private fun selectionHas(
    value: TextFieldValue,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
): Boolean {
    val start = value.selection.min
    val end = value.selection.max
    if (start >= end) return false
    return value.annotatedString.spanStyles.any { range ->
        range.start < end && range.end > start && when {
            bold -> range.item.fontWeight == FontWeight.Bold
            italic -> range.item.fontStyle == FontStyle.Italic
            underline -> range.item.textDecoration?.contains(TextDecoration.Underline) == true
            else -> false
        }
    }
}

private fun TextFieldValue.withStyle(style: SpanStyle): TextFieldValue {
    val start = selection.min
    val end = selection.max
    if (start >= end) return this
    val next = buildAnnotatedString {
        append(annotatedString)
        addStyle(style, start, end)
    }
    return copy(annotatedString = next, selection = selection)
}
