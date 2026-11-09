@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.calendar.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.appVersionName
import com.example.calendar.data.AccountSlotsState
import com.example.calendar.data.AppAccentColor
import com.example.calendar.data.AppThemeMode
import com.example.calendar.data.AttachmentOpenPreferences
import com.example.calendar.data.WritableCalendar
import com.example.calendar.ui.theme.MacColors
import com.example.calendar.widget.CalendarWidgets
import com.example.calendar.widget.DateWidgetShapePreferences

@Composable
fun AppSettingsSheet(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    accentColor: AppAccentColor,
    onAccentColorChange: (AppAccentColor) -> Unit,
    hasReadPermission: Boolean,
    onRequestReadPermission: () -> Unit,
    calendars: List<WritableCalendar>,
    calendarsLoading: Boolean,
    calendarsLoadFailed: Boolean,
    onRetryCalendarsLoad: () -> Unit,
    accountSlots: AccountSlotsState,
    onAccountSlotsChange: (AccountSlotsState) -> Unit,
    productionCalendarEnabled: Boolean,
    onProductionCalendarEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val maxSheetHeight = with(density) { (windowHeightPx * 0.92f).toDp() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MacColors.contentBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        AppSettingsPage(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            accentColor = accentColor,
            onAccentColorChange = onAccentColorChange,
            hasReadPermission = hasReadPermission,
            onRequestReadPermission = onRequestReadPermission,
            calendars = calendars,
            calendarsLoading = calendarsLoading,
            calendarsLoadFailed = calendarsLoadFailed,
            onRetryCalendarsLoad = onRetryCalendarsLoad,
            accountSlots = accountSlots,
            onAccountSlotsChange = onAccountSlotsChange,
            productionCalendarEnabled = productionCalendarEnabled,
            onProductionCalendarEnabledChange = onProductionCalendarEnabledChange,
            onDismiss = onDismiss,
            showClose = true,
            modifier = Modifier.heightIn(max = maxSheetHeight),
        )
    }
}

@Composable
fun AppSettingsPage(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    accentColor: AppAccentColor,
    onAccentColorChange: (AppAccentColor) -> Unit,
    hasReadPermission: Boolean,
    onRequestReadPermission: () -> Unit,
    calendars: List<WritableCalendar>,
    calendarsLoading: Boolean,
    calendarsLoadFailed: Boolean,
    onRetryCalendarsLoad: () -> Unit,
    accountSlots: AccountSlotsState,
    onAccountSlotsChange: (AccountSlotsState) -> Unit,
    productionCalendarEnabled: Boolean,
    onProductionCalendarEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit = {},
    showClose: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    val widgetShapePreferences = remember { DateWidgetShapePreferences(context) }
    var widgetShape by remember { mutableStateOf(widgetShapePreferences.read()) }
    val attachmentOpenPreferences = remember { AttachmentOpenPreferences(context) }
    var attachmentOpenMode by remember { mutableStateOf(attachmentOpenPreferences.read()) }
    var aboutDestination by rememberSaveable { mutableStateOf("root") }
    if (aboutDestination != "root") {
        AboutDestination(
            onBack = { aboutDestination = "root" },
            modifier = modifier,
        )
        return
    }

    fun openSystemAppSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
            Text(
                text = stringResource(R.string.app_settings_title),
                style = MaterialTheme.typography.displayLarge,
                color = MacColors.primaryLabel,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            ThemeAppearanceSection(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                accentColor = accentColor,
                onAccentColorChange = onAccentColorChange,
            )

            WidgetShapeSection(
                selected = widgetShape,
                onShapeChange = { shape ->
                    widgetShapePreferences.write(shape)
                    widgetShape = shape
                    CalendarWidgets.refreshAll(context)
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )

            AttachmentOpenSection(
                selected = attachmentOpenMode,
                onModeChange = { mode ->
                    attachmentOpenPreferences.write(mode)
                    attachmentOpenMode = mode
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )

            ProductionCalendarSection(
                enabled = productionCalendarEnabled,
                onEnabledChange = onProductionCalendarEnabledChange,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            CalendarsSettingsSection(
                hasReadPermission = hasReadPermission,
                onRequestReadPermission = onRequestReadPermission,
                calendars = calendars,
                isLoading = calendarsLoading,
                loadFailed = calendarsLoadFailed,
                onRetryLoad = onRetryCalendarsLoad,
                slots = accountSlots,
                onSlotsChange = onAccountSlotsChange,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            TextButton(
                onClick = { aboutDestination = "about" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_open),
                    color = MacColors.accentBlue,
                )
            }

            Text(
                text = stringResource(R.string.about_footer, versionName),
                color = MacColors.secondaryLabel,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { aboutDestination = "about" }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )

            TextButton(
                onClick = { openSystemAppSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_settings_system),
                    color = MacColors.accentBlue,
                )
            }

            if (showClose) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.calendars_close),
                            fontWeight = FontWeight.SemiBold,
                            color = MacColors.accentBlue,
                        )
                    }
                }
            }
    }
}
