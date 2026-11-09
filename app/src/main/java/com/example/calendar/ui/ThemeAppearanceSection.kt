package com.example.calendar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.data.AppAccentColor
import com.example.calendar.data.AppThemeMode
import com.example.calendar.ui.theme.MacColors

private val ThemePreviewAspect = 469f / 952f
private val ThemePreviewCorner = RoundedCornerShape(20.dp)

@Composable
fun ThemeAppearanceSection(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    accentColor: AppAccentColor,
    onAccentColorChange: (AppAccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemDark = isSystemInDarkTheme()
    val previewIsDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_settings_appearance),
            style = MaterialTheme.typography.bodySmall,
            color = MacColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MacColors.contentBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                ThemePreviewChoice(
                    imageRes = R.drawable.theme_preview_light,
                    label = stringResource(R.string.app_settings_theme_light),
                    selected = !previewIsDark,
                    bezel = Color(0xFFD1D1D6),
                    onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                )
                ThemePreviewChoice(
                    imageRes = R.drawable.theme_preview_dark,
                    label = stringResource(R.string.app_settings_theme_dark),
                    selected = previewIsDark,
                    bezel = Color(0xFF3A3A3C),
                    onClick = { onThemeModeChange(AppThemeMode.DARK) },
                )
            }
            HorizontalDivider(
                color = MacColors.separator,
                modifier = Modifier.padding(start = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (themeMode == AppThemeMode.SYSTEM) {
                            onThemeModeChange(if (systemDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                        } else {
                            onThemeModeChange(AppThemeMode.SYSTEM)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_settings_theme_system),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.primaryLabel,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = themeMode == AppThemeMode.SYSTEM,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onThemeModeChange(AppThemeMode.SYSTEM)
                        } else {
                            onThemeModeChange(if (systemDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF34C759),
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = MacColors.segmentBackground,
                        uncheckedThumbColor = Color.White,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            HorizontalDivider(
                color = MacColors.separator,
                modifier = Modifier.padding(start = 16.dp),
            )
            Text(
                text = stringResource(R.string.app_settings_accent),
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.primaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppAccentColor.entries.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == accentColor,
                        onClick = { onAccentColorChange(accent) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: AppAccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = accent.composeColor(dark = false)
    val label = stringResource(accent.labelRes())
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MacColors.primaryLabel else MacColors.separator.copy(alpha = 0.4f),
                shape = CircleShape,
            )
            .semantics { this.contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ThemePreviewChoice(
    @DrawableRes imageRes: Int,
    label: String,
    selected: Boolean,
    bezel: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = label,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(ThemePreviewAspect)
                .clip(ThemePreviewCorner)
                .border(1.5.dp, bezel, ThemePreviewCorner),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MacColors.primaryLabel,
        )
        Spacer(modifier = Modifier.height(6.dp))
        ThemeSelectionMark(selected = selected)
    }
}

@Composable
private fun ThemeSelectionMark(selected: Boolean) {
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
