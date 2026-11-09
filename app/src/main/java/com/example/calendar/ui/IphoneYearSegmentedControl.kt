package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.ui.theme.MacColors

/** Сегмент «Год / Месяц» на белом фоне (как iOS, светлая дорожка). */
@Composable
fun IphoneYearSegmentedControl(
    labels: List<Pair<CalendarMode, String>>,
    selected: CalendarMode,
    onSelect: (CalendarMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MacColors.segmentBackground)
            .padding(2.dp),
    ) {
        labels.forEach { (mode, label) ->
            val isSelected = mode == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onSelect(mode) },
                shape = RoundedCornerShape(7.dp),
                color = if (isSelected) MacColors.contentBackground else Color.Transparent,
                shadowElevation = if (isSelected) 1.dp else 0.dp,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MacColors.primaryLabel,
                    )
                }
            }
        }
    }
}
