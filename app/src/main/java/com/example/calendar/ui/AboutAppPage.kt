package com.example.calendar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.appVersionName
import com.example.calendar.ui.theme.MacColors

private const val SiteUrl = "https://laktapp.ru/"
private const val PrivacyUrl = "https://laktapp.ru/calendar/privacy/"
private const val SupportEmail = "support@laktapp.ru"

@Composable
fun AboutDestination(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AboutAppPage(onBack = onBack, modifier = modifier)
}

@Composable
fun AboutAppPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.about_back),
                color = MacColors.accentBlue,
            )
        }
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.displayLarge,
            color = MacColors.primaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SettingsLink(stringResource(R.string.about_develop)) { openView(context, SiteUrl) }
        SettingsLink(stringResource(R.string.about_feedback)) { openMail(context, SupportEmail) }
        SettingsLink(stringResource(R.string.app_settings_privacy)) { openView(context, PrivacyUrl) }
    }
}

@Composable
private fun SettingsLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = MacColors.accentBlue,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun openView(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

private fun openMail(context: android.content.Context, email: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
