package com.example.calendar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calendar.AppLocale
import com.example.calendar.R
import com.example.calendar.RussianDateFormat
import com.example.calendar.data.AccountSlots
import com.example.calendar.data.AppAccentColor
import com.example.calendar.data.AppThemeMode
import com.example.calendar.data.CalendarEvent
import com.example.calendar.data.CalendarEventWriter
import com.example.calendar.data.CalendarTask
import com.example.calendar.data.EventFormDraft
import com.example.calendar.data.ProductionDay
import com.example.calendar.ui.components.MonthCalendarGrid
import com.example.calendar.ui.components.WeekDayStrip
import com.example.calendar.ui.components.weekStartContaining
import com.example.calendar.ui.theme.IphoneYearColors
import com.example.calendar.ui.theme.MacColors
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHomeScreen(
    viewModel: CalendarViewModel = viewModel(),
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    accentColor: AppAccentColor = AppAccentColor.RED,
    onAccentColorChange: (AppAccentColor) -> Unit = {},
) {
    val context = LocalContext.current
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasWriteCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCalendarPermission = result[Manifest.permission.READ_CALENDAR] == true
        hasWriteCalendarPermission = result[Manifest.permission.WRITE_CALENDAR] == true
        if (hasCalendarPermission) {
            viewModel.bootstrapCalendarIfNeeded()
        }
        if (result[Manifest.permission.READ_CONTACTS] == true) {
            viewModel.loadEventsForSelectedDay()
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasWriteCalendarPermission = granted
    }

    LaunchedEffect(hasCalendarPermission) {
        if (hasCalendarPermission) {
            viewModel.bootstrapCalendarIfNeeded()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startWatchingDateChanges()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        hasWriteCalendarPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
    }

    var showEventSheet by remember { mutableStateOf(false) }
    var sheetExistingEventId by remember { mutableStateOf<Long?>(null) }
    var sheetInstanceBeginMillis by remember { mutableStateOf<Long?>(null) }
    var sheetInstanceEndMillis by remember { mutableStateOf<Long?>(null) }
    var sheetDraft by remember { mutableStateOf<EventFormDraft?>(null) }
    var newEventSession by remember { mutableStateOf(0) }
    val eventWriter = remember(context) { CalendarEventWriter(context.contentResolver) }

    val mode by viewModel.mode.collectAsStateWithLifecycle()

    val calendarPermissions = remember {
        arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
        )
    }

    var showAppSettingsSheet by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(AppSection.Calendar) }
    var monthEventsWeekStrip by rememberSaveable { mutableStateOf(true) }
    /** Режим недели/месяца для текущего месяца; чужой месяц с года его не затирает. */
    var currentMonthWeekStrip by rememberSaveable { mutableStateOf(true) }
    var showEventSearch by rememberSaveable { mutableStateOf(false) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var viewingTask by remember { mutableStateOf<CalendarTask?>(null) }
    var editingTask by remember { mutableStateOf<CalendarTask?>(null) }

    LaunchedEffect(mode) {
        if (mode != CalendarMode.MonthDay) {
            showEventSheet = false
            sheetExistingEventId = null
            sheetInstanceBeginMillis = null
            sheetInstanceEndMillis = null
            sheetDraft = null
        }
    }

    LaunchedEffect(section, hasCalendarPermission) {
        if (section == AppSection.Settings && hasCalendarPermission) {
            viewModel.refreshDeviceCalendars()
        }
        if (section == AppSection.Tasks && hasCalendarPermission) {
            viewModel.loadTasks()
        }
    }

    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val dayEvents by viewModel.dayEvents.collectAsStateWithLifecycle()
    val loadingEvents by viewModel.isLoadingEvents.collectAsStateWithLifecycle()
    val refreshingCalendars by viewModel.isRefreshingCalendars.collectAsStateWithLifecycle()
    val monthDotColorsByDay by viewModel.monthDotColorsByDay.collectAsStateWithLifecycle()
    val visibleYear by viewModel.visibleYear.collectAsStateWithLifecycle()
    val deviceCalendars by viewModel.deviceCalendars.collectAsStateWithLifecycle()
    val deviceCalendarsLoading by viewModel.deviceCalendarsLoading.collectAsStateWithLifecycle()
    val deviceCalendarsLoadFailed by viewModel.deviceCalendarsLoadFailed.collectAsStateWithLifecycle()
    val eventSearchQuery by viewModel.eventSearchQuery.collectAsStateWithLifecycle()
    val eventSearchResults by viewModel.eventSearchResults.collectAsStateWithLifecycle()
    val eventSearchLoading by viewModel.eventSearchLoading.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val tasksLoading by viewModel.tasksLoading.collectAsStateWithLifecycle()
    val refreshingTasks by viewModel.isRefreshingTasks.collectAsStateWithLifecycle()
    val accountSlots by viewModel.accountSlots.collectAsStateWithLifecycle()
    val taskCalendars = remember(deviceCalendars, accountSlots) {
        AccountSlots.taskFormCalendars(accountSlots, deviceCalendars)
    }
    val today by viewModel.today.collectAsStateWithLifecycle()
    val productionCalendarEnabled by viewModel.productionCalendarEnabled.collectAsStateWithLifecycle()
    val productionDays by viewModel.productionDays.collectAsStateWithLifecycle()
    val openNewEventSheet: () -> Unit = {
        newEventSession++
        sheetExistingEventId = null
        sheetInstanceBeginMillis = null
        sheetInstanceEndMillis = null
        sheetDraft = null
        showEventSheet = true
    }

    val yearChrome = mode == CalendarMode.Year

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (yearChrome && section == AppSection.Calendar) {
            IphoneYearColors.yearScreenBackground
        } else {
            MacColors.windowBackground
        },
        bottomBar = {
            JournalBottomBar(
                section = section,
                onSelect = { section = it },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (section == AppSection.Calendar) {
                JournalModeTabs(
                    mode = mode,
                    currentYear = today.year,
                    onSelect = { viewModel.setMode(it) },
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            }
            when (section) {
                AppSection.Tasks -> {
                    if (!hasCalendarPermission) {
                        PermissionPlaceholder(
                            onGrantClick = { permissionLauncher.launch(calendarPermissions) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        TasksScreen(
                            tasks = tasks,
                            today = today,
                            hasTaskCalendar = taskCalendars.isNotEmpty(),
                            loading = tasksLoading,
                            refreshing = refreshingTasks,
                            onRefresh = viewModel::refreshTasks,
                            onAddClick = { showTaskSheet = true },
                            onToggleDone = viewModel::setTaskDone,
                            onOpenTask = { viewingTask = it },
                            onDelete = viewModel::deleteTask,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                AppSection.Settings -> {
                    AppSettingsPage(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        accentColor = accentColor,
                        onAccentColorChange = onAccentColorChange,
                        hasReadPermission = hasCalendarPermission,
                        onRequestReadPermission = { permissionLauncher.launch(calendarPermissions) },
                        calendars = deviceCalendars,
                        calendarsLoading = deviceCalendarsLoading,
                        calendarsLoadFailed = deviceCalendarsLoadFailed,
                        onRetryCalendarsLoad = viewModel::refreshDeviceCalendars,
                        accountSlots = accountSlots,
                        onAccountSlotsChange = viewModel::applyAccountSlots,
                        productionCalendarEnabled = productionCalendarEnabled,
                        onProductionCalendarEnabledChange = viewModel::setProductionCalendarEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }

                AppSection.Calendar -> {
                    if (!hasCalendarPermission) {
                        PermissionPlaceholder(
                            onGrantClick = {
                                permissionLauncher.launch(calendarPermissions)
                            },
                            useIphoneYearChrome = yearChrome,
                            modifier = Modifier.weight(1f),
                        )
                    } else when (mode) {
                        CalendarMode.Year -> {
                            var showYearDatePicker by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                YearNavigationBar(
                                    year = visibleYear,
                                    onPreviousYear = { viewModel.goToPreviousYear() },
                                    onNextYear = { viewModel.goToNextYear() },
                                    onPickDate = { showYearDatePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedContent(
                                    targetState = visibleYear,
                                    transitionSpec = { periodSlide() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .horizontalPeriodSwipe { forward ->
                                            if (forward) {
                                                viewModel.goToNextYear()
                                            } else {
                                                viewModel.goToPreviousYear()
                                            }
                                        },
                                    label = "yearPager",
                                ) { year ->
                                    DeferredYearOverview(
                                        year = year,
                                        today = today,
                                        selectedDate = selectedDate,
                                        productionDays = productionDays,
                                        onMonthClick = { month ->
                                            val currentMonth = YearMonth.from(today)
                                            if (month == currentMonth) {
                                                monthEventsWeekStrip = currentMonthWeekStrip
                                                val date = if (YearMonth.from(selectedDate) == currentMonth) {
                                                    selectedDate
                                                } else {
                                                    today
                                                }
                                                viewModel.openMonthAt(date)
                                            } else {
                                                monthEventsWeekStrip = false
                                                viewModel.openMonthAt(month.atDay(1))
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            if (showYearDatePicker) {
                                CalendarDatePickerDialog(
                                    initialDate = selectedDate,
                                    onDismiss = { showYearDatePicker = false },
                                    onConfirm = { date ->
                                        showYearDatePicker = false
                                        viewModel.selectDate(date)
                                    },
                                )
                            }
                        }

                        CalendarMode.MonthDay -> {
                            MonthAndDayPanel(
                                visibleMonth = visibleMonth,
                                selectedDate = selectedDate,
                                today = today,
                                events = dayEvents,
                                loading = loadingEvents,
                                refreshing = refreshingCalendars,
                                onRefresh = viewModel::refreshSelectedCalendars,
                                dayDotColorsByDate = monthDotColorsByDay,
                                onShiftDays = viewModel::shiftSelectedByDays,
                                onShiftWeeks = viewModel::shiftSelectedByWeeks,
                                onShiftMonths = { delta ->
                                    if (delta > 0) viewModel.goToNextMonth() else viewModel.goToPreviousMonth()
                                },
                                onPickDate = viewModel::selectDate,
                                productionDays = productionDays,
                                weekStripMode = monthEventsWeekStrip,
                                onWeekStripModeChange = {
                                    monthEventsWeekStrip = it
                                    if (visibleMonth == YearMonth.from(today)) {
                                        currentMonthWeekStrip = it
                                    }
                                },
                                onGoToToday = {
                                    monthEventsWeekStrip = currentMonthWeekStrip
                                    viewModel.goToToday()
                                },
                                onAddEvent = openNewEventSheet,
                                onSearchEvents = {
                                    viewModel.clearEventSearch()
                                    showEventSearch = true
                                },
                                onDayClick = viewModel::selectDate,
                                onEventClick = { ev ->
                                    if (ev.eventId >= 0) {
                                        newEventSession++
                                        sheetExistingEventId = ev.eventId
                                        sheetInstanceBeginMillis = ev.beginMillis
                                        sheetInstanceEndMillis = ev.endMillis
                                        sheetDraft = null
                                        showEventSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                            )
                        }
                    }
                }
            }
            }
            if (showEventSheet && hasCalendarPermission) {
                key(newEventSession) {
                    NewEventDialog(
                        sessionId = newEventSession,
                        existingEventId = sheetExistingEventId,
                        existingInstanceBeginMillis = sheetInstanceBeginMillis,
                        existingInstanceEndMillis = sheetInstanceEndMillis,
                        initialDate = sheetDraft?.start?.toLocalDate() ?: selectedDate,
                        eventWriter = eventWriter,
                        hasWritePermission = hasWriteCalendarPermission,
                        onRequestWritePermission = {
                            writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
                        },
                        onDismiss = {
                            showEventSheet = false
                            sheetExistingEventId = null
                            sheetInstanceBeginMillis = null
                            sheetInstanceEndMillis = null
                            sheetDraft = null
                        },
                        onSaved = { anchor -> viewModel.reloadEventsAfterCalendarWrite(anchor) },
                        allowedCalendarIds = accountSlots.active().eventCalendarIds.toSet()
                            .ifEmpty { null },
                        defaultCalendarId = accountSlots.active().defaultEventCalendarId,
                        draft = sheetDraft,
                        onDuplicate = { copy ->
                            sheetExistingEventId = null
                            sheetInstanceBeginMillis = null
                            sheetInstanceEndMillis = null
                            sheetDraft = copy
                            newEventSession++
                            showEventSheet = true
                        },
                    )
                }
            }
            viewingTask?.let { task ->
                TaskViewDialog(
                    task = task,
                    onDismiss = { viewingTask = null },
                    onEdit = { editingTask = task },
                )
            }
            if ((showTaskSheet || editingTask != null) && hasCalendarPermission) {
                NewTaskDialog(
                    calendars = taskCalendars,
                    preferredTaskCalendarId = AccountSlots.preferredWriteTaskCalendarId(
                        accountSlots,
                        deviceCalendars,
                    ),
                    initialDate = selectedDate,
                    existingTask = editingTask,
                    onDismiss = {
                        showTaskSheet = false
                        editingTask = null
                    },
                    onSaved = {
                        showTaskSheet = false
                        editingTask = null
                        viewingTask = null
                        viewModel.reloadTasksAfterCalendarWrite()
                    },
                )
            }
            if (showEventSearch && hasCalendarPermission) {
                EventSearchDialog(
                    query = eventSearchQuery,
                    results = eventSearchResults,
                    loading = eventSearchLoading,
                    today = today,
                    onQueryChange = viewModel::updateEventSearch,
                    onDismiss = {
                        showEventSearch = false
                        viewModel.clearEventSearch()
                    },
                    onEventClick = { event ->
                        val eventDay = Instant.ofEpochMilli(event.beginMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        showEventSearch = false
                        viewModel.clearEventSearch()
                        viewModel.openMonthAt(eventDay)
                        if (event.eventId >= 0) {
                            newEventSession++
                            sheetExistingEventId = event.eventId
                            sheetInstanceBeginMillis = event.beginMillis
                            sheetInstanceEndMillis = event.endMillis
                            sheetDraft = null
                            showEventSheet = true
                        }
                    },
                )
            }
            if (showAppSettingsSheet) {
                AppSettingsSheet(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    accentColor = accentColor,
                    onAccentColorChange = onAccentColorChange,
                    hasReadPermission = hasCalendarPermission,
                    onRequestReadPermission = { permissionLauncher.launch(calendarPermissions) },
                    calendars = deviceCalendars,
                    calendarsLoading = deviceCalendarsLoading,
                    calendarsLoadFailed = deviceCalendarsLoadFailed,
                    onRetryCalendarsLoad = viewModel::refreshDeviceCalendars,
                    accountSlots = accountSlots,
                    onAccountSlotsChange = viewModel::applyAccountSlots,
                    productionCalendarEnabled = productionCalendarEnabled,
                    onProductionCalendarEnabledChange = viewModel::setProductionCalendarEnabled,
                    onDismiss = { showAppSettingsSheet = false },
                )
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(
    onGrantClick: () -> Unit,
    useIphoneYearChrome: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.permission_calendar_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = if (useIphoneYearChrome) {
                MacColors.primaryLabel.copy(alpha = 0.85f)
            } else {
                MacColors.secondaryLabel
            },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onGrantClick,
            colors = if (useIphoneYearChrome) {
                ButtonDefaults.buttonColors(
                    containerColor = IphoneYearColors.accent,
                    contentColor = Color.White,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Text(stringResource(R.string.grant_access))
        }
    }
}

@Composable
private fun YearNavigationBar(
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPreviousYear) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.period_previous_year),
                tint = MacColors.accentBlue,
            )
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.headlineLarge,
            color = MacColors.primaryLabel,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onPickDate,
                onLongClickLabel = stringResource(R.string.pick_date),
            ),
        )
        IconButton(onClick = onNextYear) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.period_next_year),
                tint = MacColors.accentBlue,
            )
        }
    }
}

@Composable
private fun DeferredYearOverview(
    year: Int,
    today: LocalDate,
    selectedDate: LocalDate,
    productionDays: Map<LocalDate, ProductionDay>,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var gridsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        gridsReady = true
    }
    if (gridsReady) {
        YearOverview(
            year = year,
            today = today,
            selectedDate = selectedDate,
            productionDays = productionDays,
            onMonthClick = onMonthClick,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MacColors.windowBackground),
        )
    }
}

@Composable
private fun YearOverview(
    year: Int,
    today: LocalDate,
    selectedDate: LocalDate,
    productionDays: Map<LocalDate, ProductionDay>,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = remember(year) {
        (1..12).map { YearMonth.of(year, it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MacColors.windowBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { col ->
                    val idx = row * 3 + col
                    MiniMonthCard(
                        yearMonth = months[idx],
                        today = today,
                        selectedDate = selectedDate,
                        productionDays = productionDays,
                        onMonthClick = onMonthClick,
                        plainYearTile = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMonthCard(
    yearMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    productionDays: Map<LocalDate, ProductionDay>,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    /** Один общий фон года — без отдельных «плиток» и промежутков под фон окна. */
    plainYearTile: Boolean = false,
) {
    val locale = AppLocale.display
    val monthTitle = remember(yearMonth, locale, plainYearTile) {
        val style = if (plainYearTile) TextStyle.FULL else TextStyle.SHORT
        yearMonth.month.getDisplayName(style, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    val tileModifier = if (plainYearTile) {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MacColors.contentBackground)
    } else {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MacColors.contentBackground)
    }

    val isCurrentMonthOnYear =
        plainYearTile && yearMonth.year == today.year && yearMonth.month == today.month

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(tileModifier)
            .clickable { onMonthClick(yearMonth) }
            .padding(
                horizontal = if (plainYearTile) 6.dp else 3.dp,
                vertical = if (plainYearTile) 4.dp else 2.dp,
            ),
    ) {
        Text(
            text = monthTitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (plainYearTile) 13.sp else 9.sp,
                lineHeight = if (plainYearTile) 16.sp else 10.sp,
                fontWeight = if (plainYearTile) FontWeight.Bold else FontWeight.Normal,
            ),
            color = when {
                plainYearTile && isCurrentMonthOnYear -> IphoneYearColors.accent
                plainYearTile -> MacColors.primaryLabel
                else -> MacColors.secondaryLabel
            },
            maxLines = if (plainYearTile) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    bottom = if (plainYearTile) 3.dp else 1.dp,
                    start = if (plainYearTile) 0.dp else 1.dp,
                ),
        )
        MonthCalendarGrid(
            yearMonth = yearMonth,
            compact = true,
            denseYearLayout = true,
            iphoneYearStyle = plainYearTile,
            selectedDate = selectedDate,
            today = today,
            onDayClick = { onMonthClick(yearMonth) },
            showWeekdayRow = !plainYearTile,
            productionDays = productionDays,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun MonthAndDayPanel(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    dayDotColorsByDate: Map<LocalDate, List<Int>>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onGoToToday: () -> Unit,
    onAddEvent: () -> Unit,
    onSearchEvents: () -> Unit,
    onShiftDays: (Long) -> Unit,
    onShiftWeeks: (Long) -> Unit,
    onShiftMonths: (Long) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    weekStripMode: Boolean,
    onWeekStripModeChange: (Boolean) -> Unit,
    productionDays: Map<LocalDate, ProductionDay> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val weekStart = remember(selectedDate) { weekStartContaining(selectedDate) }
    val headerText = if (weekStripMode) {
        RussianDateFormat.weekSpanHeader(weekStart)
    } else {
        RussianDateFormat.monthYearHeader(visibleMonth)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val monthExpanded = !weekStripMode
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapToken by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) snapToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var snappedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedDate, today, snapToken, events) {
        if (events.isEmpty()) return@LaunchedEffect
        val key = "$selectedDate-$today-$snapToken"
        if (snappedKey == key) return@LaunchedEffect
        snappedKey = key
        val index = if (selectedDate == today) {
            indexOfEventNearestNow(events, System.currentTimeMillis())
        } else {
            0
        }
        withFrameNanos { }
        listState.scrollToItem(index.coerceIn(0, events.lastIndex))
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MacColors.contentBackground)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.go_to_today),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onGoToToday()
                            snapToken++
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MacColors.secondaryLabel,
                )
                Text(
                    text = headerText,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showDatePicker = true },
                            onLongClickLabel = stringResource(R.string.pick_date),
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    ),
                    color = MacColors.primaryLabel,
                )
                JournalExpandIcon(
                    expanded = monthExpanded,
                    onClick = { onWeekStripModeChange(monthExpanded) },
                )
            }
            if (monthExpanded) {
                AnimatedContent(
                    targetState = visibleMonth,
                    transitionSpec = { periodSlide() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalPeriodSwipe { forward ->
                            onShiftMonths(if (forward) 1 else -1)
                        },
                    label = "monthPager",
                ) { month ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        JournalWeekdayHeader(modifier = Modifier.padding(bottom = 4.dp))
                        MonthCalendarGrid(
                            yearMonth = month,
                            compact = false,
                            selectedDate = selectedDate,
                            today = today,
                            onDayClick = onDayClick,
                            dayDotColorsByDate = dayDotColorsByDate,
                            showWeekdayRow = false,
                            journalHighlight = true,
                            productionDays = productionDays,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = weekStart,
                    transitionSpec = { periodSlide() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalPeriodSwipe { forward ->
                            onShiftWeeks(if (forward) 1 else -1)
                        },
                    label = "weekPager",
                ) { start ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        JournalWeekdayHeader(modifier = Modifier.padding(bottom = 4.dp))
                        WeekDayStrip(
                            weekStart = start,
                            selectedDate = selectedDate,
                            today = today,
                            focusMonth = visibleMonth,
                            dayDotColorsByDate = dayDotColorsByDate,
                            onDayClick = onDayClick,
                            journalStyle = true,
                            productionDays = productionDays,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (showDatePicker) {
            CalendarDatePickerDialog(
                initialDate = selectedDate,
                onDismiss = { showDatePicker = false },
                onConfirm = { date ->
                    showDatePicker = false
                    onPickDate(date)
                },
            )
        }

        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = refreshState,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = MacColors.todayRed,
                    containerColor = MacColors.contentBackground,
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            JournalDayTitle(
                date = selectedDate,
                today = today,
                onSearchClick = onSearchEvents,
                onAddClick = onAddEvent,
                onShiftDay = { forward -> onShiftDays(if (forward) 1 else -1) },
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            productionDays[selectedDate]?.let { day ->
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (day.isNonWorking) MacColors.todayRed else MacColors.secondaryLabel,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
            ) {
                when {
                    loading && events.isEmpty() && !refreshing -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = MacColors.todayRed)
                            }
                        }
                    }

                    events.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_events),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MacColors.secondaryLabel,
                                )
                            }
                        }
                    }

                    else -> {
                        items(events, key = { "${it.eventId}-${it.beginMillis}" }) { event ->
                            JournalEventCard(
                                event = event,
                                onOpen = { onEventClick(event) },
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

/** Индекс события, ближайшего к [nowMillis]: идущее сейчас, иначе следующее, иначе последнее прошедшее. */
internal fun indexOfEventNearestNow(events: List<CalendarEvent>, nowMillis: Long): Int {
    if (events.isEmpty()) return 0
    val timed = events.withIndex().filter { !it.value.allDay }
    if (timed.isEmpty()) return 0
    val ongoing = timed.filter { (_, event) ->
        event.beginMillis <= nowMillis && nowMillis < event.endMillis
    }
    if (ongoing.isNotEmpty()) return ongoing.minBy { it.value.beginMillis }.index
    val upcoming = timed.filter { (_, event) -> event.beginMillis >= nowMillis }
    if (upcoming.isNotEmpty()) return upcoming.minBy { it.value.beginMillis }.index
    return timed.maxBy { it.value.endMillis }.index
}
