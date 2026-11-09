package com.example.calendar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.calendar.EventInviteeEmails
import com.example.calendar.R
import com.example.calendar.data.ContactInviteSuggestion
import com.example.calendar.data.ContactSuggestionsRepository
import com.example.calendar.data.EventInvitee
import com.example.calendar.ui.theme.MacColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class InviteeViewItem(
    val email: String,
    val displayName: String?,
)

@Composable
fun InviteesViewModeRow(
    invitees: List<EventInvitee>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) {
        ContactSuggestionsRepository(context.contentResolver)
    }
    fun checkContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    var hasContactsPermission by remember { mutableStateOf(checkContactsPermission()) }
    var contactsPermissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasContactsPermission = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasContactsPermission = checkContactsPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var items by remember(invitees) {
        mutableStateOf(
            invitees.map { inv ->
                val storedName = inv.name?.takeIf { it.isNotBlank() }
                    ?.takeUnless { it.equals(inv.email, ignoreCase = true) }
                InviteeViewItem(email = inv.email, displayName = storedName)
            },
        )
    }

    LaunchedEffect(invitees, hasContactsPermission) {
        if (invitees.isEmpty()) {
            items = emptyList()
            return@LaunchedEffect
        }
        if (!hasContactsPermission) {
            if (!contactsPermissionRequested) {
                contactsPermissionRequested = true
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            items = invitees.map { inv ->
                val storedName = inv.name?.takeIf { it.isNotBlank() }
                    ?.takeUnless { it.equals(inv.email, ignoreCase = true) }
                InviteeViewItem(email = inv.email, displayName = storedName)
            }
            return@LaunchedEffect
        }
        items = withContext(Dispatchers.IO) {
            invitees.map { inv ->
                val contactName = repository.lookupByEmail(inv.email)
                    ?.displayName
                    ?.takeIf { it.isNotBlank() }
                val resolvedName = (contactName ?: inv.name?.takeIf { it.isNotBlank() })
                    ?.takeUnless { it.equals(inv.email, ignoreCase = true) }
                InviteeViewItem(
                    email = inv.email,
                    displayName = resolvedName,
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(R.string.field_invitees),
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            if (invitees.isEmpty()) {
                Text(
                    text = stringResource(R.string.value_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.secondaryLabel,
                )
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (item.displayName != null) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MacColors.primaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MacColors.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = item.email,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MacColors.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InviteesContactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) {
        ContactSuggestionsRepository(context.contentResolver)
    }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasContactsPermission = granted
    }

    var suggestions by remember { mutableStateOf<List<ContactInviteSuggestion>>(emptyList()) }
    val searchQuery = remember(value) { EventInviteeEmails.trailingQuery(value) }
    val corporateFallback = stringResource(R.string.invitees_corporate_directory)

    LaunchedEffect(searchQuery, hasContactsPermission, corporateFallback) {
        if (!hasContactsPermission || searchQuery.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        val local = withContext(Dispatchers.IO) {
            repository.search(searchQuery)
        }
        suggestions = local
        delay(150)
        val corporate = withContext(Dispatchers.IO) {
            repository.searchCorporateDirectory(searchQuery, corporateFallback)
        }
        suggestions = ContactSuggestionsRepository.mergeInviteSuggestions(local, corporate)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (!hasContactsPermission &&
                    EventInviteeEmails.trailingQuery(newValue).length >= 2
                ) {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
                onValueChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            placeholder = {
                Text(placeholder, color = MacColors.secondaryLabel)
            },
            singleLine = false,
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MacColors.primaryLabel),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(0.dp),
        )

        if (hasContactsPermission && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MacColors.contentBackground,
                shadowElevation = 2.dp,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    items(
                        suggestions,
                        key = { "${it.contactId}-${it.email}-${it.directoryName.orEmpty()}" },
                    ) { suggestion ->
                        ContactSuggestionRow(
                            suggestion = suggestion,
                            onClick = {
                                onValueChange(
                                    EventInviteeEmails.appendInvitee(
                                        value = value,
                                        displayName = suggestion.displayName,
                                        email = suggestion.email,
                                    ),
                                )
                                suggestions = emptyList()
                            },
                        )
                        HorizontalDivider(color = MacColors.separator)
                    }
                }
            }
        } else if (!hasContactsPermission && searchQuery.length >= 2) {
            Text(
                text = stringResource(R.string.invitees_contacts_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MacColors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ContactSuggestionRow(
    suggestion: ContactInviteSuggestion,
    onClick: () -> Unit,
) {
    val corporate = !suggestion.directoryName.isNullOrBlank()
    if (corporate) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorporateContactPhoto(suggestion)
            Column(modifier = Modifier.weight(1f)) {
                val hasName = !suggestion.displayName.equals(suggestion.email, ignoreCase = true)
                if (hasName) {
                    Text(
                        text = suggestion.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MacColors.primaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = suggestion.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MacColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = suggestion.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MacColors.primaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = when {
            !suggestion.phone.isNullOrBlank() -> "${suggestion.email} · ${suggestion.phone}"
            else -> suggestion.email
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MacColors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CorporateContactPhoto(suggestion: ContactInviteSuggestion) {
    val context = LocalContext.current
    val repository = remember(context) { ContactSuggestionsRepository(context.contentResolver) }
    var photo by remember(suggestion.photoUri, suggestion.lookupKey, suggestion.contactId) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(suggestion.photoUri, suggestion.lookupKey, suggestion.contactId) {
        if (suggestion.photoUri.isNullOrBlank() &&
            suggestion.lookupKey.isNullOrBlank() &&
            suggestion.contactId <= 0L
        ) {
            photo = null
            return@LaunchedEffect
        }
        photo = withContext(Dispatchers.IO) {
            repository.loadSuggestionPhoto(suggestion)?.asImageBitmap()
        }
    }
    val image = photo ?: return
    Image(
        bitmap = image,
        contentDescription = suggestion.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(end = 12.dp)
            .size(36.dp)
            .clip(CircleShape),
    )
}
