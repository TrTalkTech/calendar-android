package com.example.calendar.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.data.AttachmentOpenMode
import com.example.calendar.ui.theme.MacColors

@Composable
fun AttachmentOpenSection(
    selected: AttachmentOpenMode,
    onModeChange: (AttachmentOpenMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_settings_attachments),
            style = MaterialTheme.typography.bodySmall,
            color = MacColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.app_settings_attachments_hint),
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
            AttachmentOpenChoice(
                label = stringResource(R.string.app_settings_attachments_in_app),
                selected = selected == AttachmentOpenMode.IN_APP,
                onClick = { onModeChange(AttachmentOpenMode.IN_APP) },
            )
            AttachmentOpenChoice(
                label = stringResource(R.string.app_settings_attachments_external),
                selected = selected == AttachmentOpenMode.EXTERNAL,
                onClick = { onModeChange(AttachmentOpenMode.EXTERNAL) },
            )
        }
    }
}

@Composable
private fun AttachmentOpenChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
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
