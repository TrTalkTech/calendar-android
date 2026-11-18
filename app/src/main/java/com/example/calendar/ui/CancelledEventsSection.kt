package com.example.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.ui.theme.MacColors

@Composable
fun CancelledEventsSection(
    showCancelled: Boolean,
    onShowCancelledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MacColors.segmentBackground)
                .clickable { onShowCancelledChange(!showCancelled) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.app_settings_cancelled_events_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.primaryLabel,
                )
                Text(
                    text = stringResource(R.string.app_settings_cancelled_events_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MacColors.secondaryLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = showCancelled,
                onCheckedChange = onShowCancelledChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF34C759),
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = MacColors.segmentBackground,
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}
