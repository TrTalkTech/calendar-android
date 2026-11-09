package com.example.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.ui.theme.MacColors

/** Отступ снизу контента, чтобы не перекрывался нижней панелью (кнопки + FAB). */
val HomeBottomBarContentInset = 72.dp

private val HomeBottomBarPaddingBottom = 12.dp
private val HomeBottomBarMiddleHeight = 56.dp

@Composable
fun HomeBottomNavBar(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    settingsContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCapsuleButton(
            text = primaryLabel,
            onClick = onPrimaryClick,
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = settingsContentDescription,
                tint = MacColors.primaryLabel,
            )
        }
    }
}

@Composable
fun NavCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 112.dp, minHeight = 44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MacColors.segmentBackground,
            contentColor = MacColors.primaryLabel,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HomeScreenBottomBar(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    settingsContentDescription: String,
    onAddEventClick: () -> Unit,
    modifier: Modifier = Modifier,
    middleModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = HomeBottomBarPaddingBottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HomeBottomNavBar(
            primaryLabel = primaryLabel,
            onPrimaryClick = onPrimaryClick,
            onSettingsClick = onSettingsClick,
            settingsContentDescription = settingsContentDescription,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(HomeBottomBarMiddleHeight)
                .padding(horizontal = 8.dp)
                .then(middleModifier),
        )
        HomeAddEventFab(onClick = onAddEventClick)
    }
}

@Composable
fun HomeAddEventFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MacColors.accentBlue,
        contentColor = Color.White,
        shape = CircleShape,
    ) {
        Text(
            text = "+",
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
        )
    }
}
