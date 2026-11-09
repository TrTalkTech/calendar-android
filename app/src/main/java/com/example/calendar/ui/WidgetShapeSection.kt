package com.example.calendar.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.ui.theme.MacColors
import com.example.calendar.widget.DateWidgetRenderer
import com.example.calendar.widget.LauncherIconMasks
import com.example.calendar.widget.LauncherIconShape
import java.time.LocalDate
import java.util.Locale

@Composable
fun WidgetShapeSection(
    selected: LauncherIconShape,
    onShapeChange: (LauncherIconShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_shape_title),
            style = MaterialTheme.typography.bodySmall,
            color = MacColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.widget_shape_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MacColors.secondaryLabel,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MacColors.contentBackground)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            WidgetShapeChoice(
                shape = LauncherIconShape.SQUARE,
                label = stringResource(R.string.widget_shape_square),
                selected = selected == LauncherIconShape.SQUARE,
                onClick = { onShapeChange(LauncherIconShape.SQUARE) },
            )
            WidgetShapeChoice(
                shape = LauncherIconShape.ROUNDED,
                label = stringResource(R.string.widget_shape_rounded),
                selected = selected == LauncherIconShape.ROUNDED,
                onClick = { onShapeChange(LauncherIconShape.ROUNDED) },
            )
            WidgetShapeChoice(
                shape = LauncherIconShape.CIRCLE,
                label = stringResource(R.string.widget_shape_circle),
                selected = selected == LauncherIconShape.CIRCLE,
                onClick = { onShapeChange(LauncherIconShape.CIRCLE) },
            )
        }
    }
}

@Composable
private fun WidgetShapeChoice(
    shape: LauncherIconShape,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val todayColor = MacColors.todayRed.toArgb()
    val preview = remember(shape, todayColor) {
        DateWidgetRenderer.render(
            context = context,
            sizePx = 192,
            date = LocalDate.now(),
            locale = Locale.forLanguageTag("ru-RU"),
            mask = LauncherIconMasks.forShape(shape),
            todayColor = todayColor,
        )
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MacColors.primaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        val fill = if (selected) MacColors.accentBlue else Color.Transparent
        val stroke = if (selected) MacColors.accentBlue else MacColors.separator
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
}
