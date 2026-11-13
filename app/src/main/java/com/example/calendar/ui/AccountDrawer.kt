package com.example.calendar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calendar.R
import com.example.calendar.data.ACCOUNT_SLOT_COUNT
import com.example.calendar.data.AccountSlots
import com.example.calendar.data.AccountSlotsState
import com.example.calendar.data.WritableCalendar
import com.example.calendar.ui.theme.MacColors
import kotlin.math.abs

/**
 * Открывает боковую панель свайпом справа-налево в любую сторону от любой точки экрана.
 * Ловим события в Initial-фазе (родитель видит их раньше детей) и поглощаем
 * только драг вправо — пейджер месяцев/недель (свайп влево) продолжает работать.
 */
@Composable
fun Modifier.leftEdgeSwipeDrawerGesture(
    enabled: Boolean = true,
    onOpen: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalX = 0f
            var totalY = 0f
            var horizontal: Boolean? = null
            var consumed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - change.previousPosition.x
                val totalDx = change.position.x - down.position.x
                val totalDy = change.position.y - down.position.y
                totalX = totalDx
                totalY = totalDy
                if (horizontal == null &&
                    (abs(totalDx) > viewConfiguration.touchSlop || abs(totalDy) > viewConfiguration.touchSlop)
                ) {
                    // вправо открывает панель, влево и вертикаль оставляем детям
                    horizontal = totalDx > 0 && abs(totalDx) > abs(totalDy)
                }
                if (horizontal == true) {
                    if (dx != 0f) change.consume()
                    consumed = true
                    if (totalX >= thresholdPx) {
                        onOpen()
                        return@awaitEachGesture
                    }
                }
                if (consumed && totalX < 0) {
                    // палец уведён назад влево — жест отменён, отдаём события детям
                    return@awaitEachGesture
                }
            }
        }
    }
}

/** Настроенные/зарезервированные слоты + один первый свободный (для добавления), без 10 заглушек. */
internal fun slotsWithIndex(state: AccountSlotsState): List<Pair<Int, com.example.calendar.data.AccountSlot>> =
    state.slots.withIndex().let { all ->
        val kept = all
            .filter { it.value.isConfigured || it.value.isReserved }
            .map { it.index to it.value }
        val firstFree = all
            .firstOrNull { !it.value.isConfigured && !it.value.isReserved }
            ?.let { it.index to it.value }
        if (firstFree != null && kept.none { it.first == firstFree.first }) {
            kept + firstFree
        } else {
            kept
        }
    }

/** Содержимое боковой панели: список карточек-аккаунтов, тап переключает активный. */
@Composable
fun AccountDrawerOverlay(
    state: AccountSlotsState,
    calendars: List<WritableCalendar>,
    onActivate: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 220))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * progress.value))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onDismiss() },
    ) {
        Surface(
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
            color = MacColors.windowBackground,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.82f)
                .graphicsLayer { translationX = -(1f - progress.value) * size.width }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.calendars_accounts_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MacColors.primaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                val byId = calendars.associateBy { it.id }
                val visibleSlots = slotsWithIndex(state)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    visibleSlots.forEach { (index, slot) ->
                        val defaultCal = slot.defaultEventCalendarId?.let { byId[it] }
                            ?: slot.eventCalendarIds.firstOrNull()?.let { byId[it] }
                        AccountDrawerRow(
                            index = index,
                            title = defaultCal?.displayName?.ifBlank { null }
                                ?: stringResource(R.string.calendars_slot_empty),
                            subtitle = defaultCal?.accountName?.takeIf { it.isNotBlank() },
                            selected = state.activeIndex == index && slot.isConfigured,
                            enabled = slot.isConfigured,
                            color = defaultCal?.color?.let { Color(it) },
                            onClick = {
                                if (slot.isConfigured) {
                                    onActivate(index)
                                    onDismiss()
                                } else {
                                    onOpenSettings()
                                    onDismiss()
                                }
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onOpenSettings()
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MacColors.secondaryLabel,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.calendars_slot_editor_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MacColors.secondaryLabel,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDrawerRow(
    index: Int,
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    color: Color?,
    onClick: () -> Unit,
) {
    val number = stringResource(R.string.calendars_slot_number, index + 1)
    val selectLabel = stringResource(R.string.calendars_slot_activate, title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MacColors.contentBackground else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = selectLabel }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        color ?: MacColors.accentBlue,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.trim().take(1).uppercase().ifBlank { number.trimStart('№') },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MacColors.accentBlue)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MacColors.primaryLabel else MacColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null && subtitle != title) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MacColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
