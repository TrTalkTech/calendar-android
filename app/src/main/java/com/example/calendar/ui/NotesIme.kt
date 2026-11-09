package com.example.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun rememberNotesFieldMaxHeight(): Dp {
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenHeightDp.dp
    val keyboard = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val chrome = 220.dp
    return (screen - keyboard - chrome).coerceIn(140.dp, 280.dp)
}

@Composable
fun Modifier.keepVisibleWithIme(contentKey: Any): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, contentKey, imeBottom) {
        if (!focused) return@LaunchedEffect
        delay(80)
        requester.bringIntoView()
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }
}
