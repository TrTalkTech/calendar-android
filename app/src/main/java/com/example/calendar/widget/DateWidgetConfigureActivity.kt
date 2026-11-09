package com.example.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.data.AppThemePreferences
import com.example.calendar.ui.WidgetShapeSection
import com.example.calendar.ui.theme.CalendarTheme
import com.example.calendar.ui.theme.MacColors

class DateWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        val preferences = DateWidgetShapePreferences(this)
        val themePreferences = AppThemePreferences(this)

        enableEdgeToEdge()
        setContent {
            CalendarTheme(
                themeMode = themePreferences.readMode(),
                accentColor = themePreferences.readAccent(),
            ) {
                var selected by remember { mutableStateOf(preferences.read()) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MacColors.windowBackground)
                        .systemBarsPadding()
                        .padding(top = 16.dp, bottom = 24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.widget_shape_configure_title),
                        style = MaterialTheme.typography.displayLarge,
                        color = MacColors.primaryLabel,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    WidgetShapeSection(
                        selected = selected,
                        onShapeChange = { selected = it },
                    )
                    TextButton(
                        onClick = { applyShape(appWidgetId, selected) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.calendars_done),
                            fontWeight = FontWeight.SemiBold,
                            color = MacColors.accentBlue,
                        )
                    }
                }
            }
        }
    }

    private fun applyShape(appWidgetId: Int, shape: LauncherIconShape) {
        DateWidgetShapePreferences(this).write(shape)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            updateDateAppWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
        } else {
            CalendarWidgets.refreshAll(this)
        }
        setResult(RESULT_OK, resultIntent(appWidgetId))
        finish()
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
