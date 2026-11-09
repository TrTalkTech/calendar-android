package com.example.calendar.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CalendarContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.CalendarPeriod
import com.example.calendar.data.AccountSlots
import com.example.calendar.data.AccountSlotsPreferences
import com.example.calendar.data.AccountSlotsState
import com.example.calendar.data.CalendarBootstrapPreferences
import com.example.calendar.data.CalendarDayEventsQuery
import com.example.calendar.data.CalendarEvent
import com.example.calendar.data.CalendarEventWriter
import com.example.calendar.data.CalendarProviderWarmup
import com.example.calendar.data.CalendarRepository
import com.example.calendar.data.CalendarTask
import com.example.calendar.data.CalendarTaskRepository
import com.example.calendar.data.ContactBirthdaysRepository
import com.example.calendar.data.ProductionCalendarPreferences
import com.example.calendar.data.ProductionCalendarRepository
import com.example.calendar.data.ProductionDay
import com.example.calendar.data.VisibleCalendarsPreferences
import com.example.calendar.data.WritableCalendar
import com.example.calendar.data.filterIdsForEventLists
import com.example.calendar.data.isHiddenExchangeTask
import com.example.calendar.data.mergeContactBirthdayDots
import com.example.calendar.data.withMergedContactBirthdays
import com.example.calendar.widget.CalendarWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class CalendarMode {
    Year,
    MonthDay,
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarRepository(
        application.contentResolver,
        application.applicationContext,
    )

    private val visibleCalendarsPreferences = VisibleCalendarsPreferences(application)
    private val accountSlotsPreferences = AccountSlotsPreferences(application)
    private val productionCalendarRepository = ProductionCalendarRepository(application)
    private val productionCalendarPreferences = ProductionCalendarPreferences(application)
    private val bootstrapPreferences = CalendarBootstrapPreferences(application)
    private val eventWriter = CalendarEventWriter(application.contentResolver)
    private val taskRepository = CalendarTaskRepository(application.contentResolver, eventWriter)
    private val contactBirthdays = ContactBirthdaysRepository(
        application.contentResolver,
        application.applicationContext,
    )

    private val _mode = MutableStateFlow(CalendarMode.MonthDay)
    val mode: StateFlow<CalendarMode> = _mode.asStateFlow()

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Число месяца, которое листание месяцев старается сохранить. 31 января → 28 февраля → 31 марта. */
    private var dayOfMonthAnchor: Int = _selectedDate.value.dayOfMonth

    private val _visibleMonth = MutableStateFlow(YearMonth.from(LocalDate.now()))
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _visibleYear = MutableStateFlow(LocalDate.now().year)
    val visibleYear: StateFlow<Int> = _visibleYear.asStateFlow()

    private val _dayEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val dayEvents: StateFlow<List<CalendarEvent>> = _dayEvents.asStateFlow()

    private val _isLoadingEvents = MutableStateFlow(false)
    val isLoadingEvents: StateFlow<Boolean> = _isLoadingEvents.asStateFlow()

    private val _monthDotColorsByDay = MutableStateFlow<Map<LocalDate, List<Int>>>(emptyMap())
    val monthDotColorsByDay: StateFlow<Map<LocalDate, List<Int>>> = _monthDotColorsByDay.asStateFlow()

    private val _visibleCalendarsFilter = MutableStateFlow(visibleCalendarsPreferences.readFilter())
    val visibleCalendarsFilter: StateFlow<Set<Long>?> = _visibleCalendarsFilter.asStateFlow()

    private val _accountSlots = MutableStateFlow(accountSlotsPreferences.read())
    val accountSlots: StateFlow<AccountSlotsState> = _accountSlots.asStateFlow()

    private val _isRefreshingCalendars = MutableStateFlow(false)
    val isRefreshingCalendars: StateFlow<Boolean> = _isRefreshingCalendars.asStateFlow()

    private val _eventSearchQuery = MutableStateFlow("")
    val eventSearchQuery: StateFlow<String> = _eventSearchQuery.asStateFlow()

    private val _eventSearchResults = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val eventSearchResults: StateFlow<List<CalendarEvent>> = _eventSearchResults.asStateFlow()

    private val _eventSearchLoading = MutableStateFlow(false)
    val eventSearchLoading: StateFlow<Boolean> = _eventSearchLoading.asStateFlow()

    private var eventSearchJob: Job? = null

    private val _deviceCalendars = MutableStateFlow<List<WritableCalendar>>(emptyList())
    val deviceCalendars: StateFlow<List<WritableCalendar>> = _deviceCalendars.asStateFlow()

    private val _deviceCalendarsLoading = MutableStateFlow(false)
    val deviceCalendarsLoading: StateFlow<Boolean> = _deviceCalendarsLoading.asStateFlow()

    private val _deviceCalendarsLoadFailed = MutableStateFlow(false)
    val deviceCalendarsLoadFailed: StateFlow<Boolean> = _deviceCalendarsLoadFailed.asStateFlow()

    private val _tasks = MutableStateFlow<List<CalendarTask>>(emptyList())
    val tasks: StateFlow<List<CalendarTask>> = _tasks.asStateFlow()

    private val _tasksLoading = MutableStateFlow(false)
    val tasksLoading: StateFlow<Boolean> = _tasksLoading.asStateFlow()

    private val _isRefreshingTasks = MutableStateFlow(false)
    val isRefreshingTasks: StateFlow<Boolean> = _isRefreshingTasks.asStateFlow()

    private var eventsLoadGeneration = 0
    private var pinnedEventsForDay: List<CalendarEvent> = emptyList()
    private var pendingSavedEvents: List<CalendarEvent> = emptyList()
    private val dayZone: ZoneId = ZoneId.systemDefault()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var calendarObserver: ContentObserver? = null
    private var dateChangeReceiver: BroadcastReceiver? = null
    private var calendarReloadScheduled = false
    private var observerReloadDeferredUntil = 0L
    private val deviceCalendarsQueryMutex = Mutex()

    private val _productionCalendarEnabled = MutableStateFlow(productionCalendarPreferences.readEnabled())
    val productionCalendarEnabled: StateFlow<Boolean> = _productionCalendarEnabled.asStateFlow()

    private val _productionDays = MutableStateFlow<Map<LocalDate, ProductionDay>>(emptyMap())
    val productionDays: StateFlow<Map<LocalDate, ProductionDay>> = _productionDays.asStateFlow()

    init {
        reloadProductionCalendar()
    }

    fun setProductionCalendarEnabled(enabled: Boolean) {
        productionCalendarPreferences.writeEnabled(enabled)
        _productionCalendarEnabled.value = enabled
        reloadProductionCalendar()
    }

    private fun reloadProductionCalendar() {
        viewModelScope.launch {
            if (!_productionCalendarEnabled.value) {
                _productionDays.value = emptyMap()
                return@launch
            }
            val nowYear = LocalDate.now().year
            val years = setOf(
                nowYear - 1,
                nowYear,
                nowYear + 1,
                _visibleYear.value,
                _visibleMonth.value.year,
            )
            _productionDays.value = withContext(Dispatchers.IO) {
                productionCalendarRepository.daysForYears(years)
            }
        }
    }

    private fun ensureProductionYearsLoaded() {
        if (!_productionCalendarEnabled.value) return
        val needed = setOf(_visibleYear.value, _visibleMonth.value.year, LocalDate.now().year)
        val have = _productionDays.value.keys.mapTo(HashSet()) { it.year }
        if (!needed.all { it in have }) {
            reloadProductionCalendar()
        }
    }

    private fun eventOverlapsDay(event: CalendarEvent, date: LocalDate): Boolean {
        val dayStart = date.atStartOfDay(dayZone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(dayZone).toInstant().toEpochMilli()
        return event.beginMillis < dayEnd && event.endMillis > dayStart
    }

    private fun dayFallbackEvents(date: LocalDate): List<CalendarEvent> {
        val merged = (pinnedEventsForDay + pendingSavedEvents)
            .filter { eventOverlapsDay(it, date) }
        return merged.distinctBy { "${it.eventId}-${it.beginMillis}" }
    }

    private fun rememberPendingSavedEvent(anchor: CalendarEvent) {
        pendingSavedEvents = (pendingSavedEvents + anchor).distinctBy { it.eventId }
        observerReloadDeferredUntil = SystemClock.elapsedRealtime() + 12_000L
    }

    private fun reconcilePendingSavedEvents(query: CalendarDayEventsQuery, date: LocalDate) {
        if (pendingSavedEvents.isEmpty()) return
        pendingSavedEvents = pendingSavedEvents.filter { pending ->
            val confirmedOnDay = query.events.any { event ->
                event.eventId == pending.eventId &&
                    event.eventId in query.providerEventIds &&
                    eventOverlapsDay(event, date)
            }
            !confirmedOnDay
        }
    }

    private fun pendingEventsForDay(date: LocalDate): List<CalendarEvent> {
        return pendingSavedEvents.filter { eventOverlapsDay(it, date) }
    }

    private fun loadPassesAcceptance(query: CalendarDayEventsQuery, date: LocalDate): Boolean {
        val pending = pendingEventsForDay(date)
        if (pending.isEmpty()) return true
        return pending.all { pendingEvent ->
            query.events.any { event ->
                event.eventId == pendingEvent.eventId && eventOverlapsDay(event, date)
            }
        }
    }

    private suspend fun loadDeviceCalendarsFromProvider(): List<WritableCalendar> =
        deviceCalendarsQueryMutex.withLock {
            withContext(Dispatchers.IO) {
                withTimeout(DEVICE_CALENDARS_QUERY_TIMEOUT_MS) {
                    eventWriter.queryVisibleCalendars()
                }
            }
        }

    private fun dualWriteVisibleFilter(slots: AccountSlotsState, calendars: List<WritableCalendar>) {
        val allIds = calendars.map { it.id }.toSet()
        val selected = AccountSlots.eventFilterIds(slots) ?: allIds
        visibleCalendarsPreferences.writeFilter(selected, allIds)
        _visibleCalendarsFilter.value = visibleCalendarsPreferences.readFilter()
    }

    private fun applyLoadedCalendars(calendars: List<WritableCalendar>) {
        if (calendars.isNotEmpty()) {
            _deviceCalendars.value = calendars
        }
        val source = calendars.ifEmpty { _deviceCalendars.value }
        if (source.isEmpty()) return
        val slots = accountSlotsPreferences.readOrSeed(source)
        _accountSlots.value = slots
        dualWriteVisibleFilter(slots, source)
    }

    private fun activeTaskCalendars(calendars: List<WritableCalendar>): List<WritableCalendar> =
        AccountSlots.taskCalendarsForActive(_accountSlots.value, calendars)

    private suspend fun resolveVisibleFilter(): Set<Long>? = withContext(Dispatchers.IO) {
        val calendars = runCatching { loadDeviceCalendarsFromProvider() }.getOrElse { emptyList() }
        applyLoadedCalendars(calendars)
        _visibleCalendarsFilter.value
    }

    fun refreshDeviceCalendars() {
        viewModelScope.launch {
            if (_deviceCalendarsLoading.value) return@launch
            _deviceCalendarsLoading.value = true
            _deviceCalendarsLoadFailed.value = false
            try {
                val calendars = loadDeviceCalendarsFromProvider()
                applyLoadedCalendars(calendars)
            } catch (e: Exception) {
                Log.e(TAG, "refreshDeviceCalendars failed", e)
                _deviceCalendarsLoadFailed.value = true
            } finally {
                _deviceCalendarsLoading.value = false
            }
        }
    }

    private fun eventListFilter(raw: Set<Long>?): Set<Long>? =
        filterIdsForEventLists(raw, _deviceCalendars.value)

    private suspend fun loadDayAndMonthDots(
        date: LocalDate,
        month: YearMonth,
        filter: Set<Long>?,
    ): Pair<CalendarDayEventsQuery, Map<LocalDate, List<Int>>> =
        withContext(Dispatchers.IO) {
            val eventFilter = eventListFilter(filter)
            var query = repository.queryEventsForDay(
                date,
                visibleCalendarIds = eventFilter,
                fallbackEvents = dayFallbackEvents(date),
            )
            if (query.events.isEmpty() && eventFilter?.isEmpty() != true) {
                CalendarProviderWarmup.warmInstancesCache(
                    getApplication<Application>().contentResolver,
                    date,
                    dayZone,
                )
                query = repository.queryEventsForDay(
                    date,
                    visibleCalendarIds = eventFilter,
                    fallbackEvents = dayFallbackEvents(date),
                )
            }
            val hideTaskCalendars = AccountSlots.exchangeTaskWriteCalendarIds(
                _accountSlots.value,
                _deviceCalendars.value,
            )
            val monthDots = repository.queryCalendarDotColorsForMonthGrid(
                month,
                visibleCalendarIds = eventFilter,
                skipAllDayFreeOnCalendarIds = hideTaskCalendars,
            )
            val contactDay = contactBirthdays.queryBirthdaysForDay(date, dayZone)
            val contactDotDates = contactBirthdays.queryBirthdayDatesForMonthGrid(month)
            val visibleEvents = query.events.filterNot { it.isHiddenExchangeTask(hideTaskCalendars) }
            val mergedQuery = query.copy(events = visibleEvents).withMergedContactBirthdays(contactDay)
            val mergedDots = mergeContactBirthdayDots(monthDots, contactDotDates)
            mergedQuery to mergedDots
        }

    private fun applyLoadedDay(
        query: CalendarDayEventsQuery,
        dots: Map<LocalDate, List<Int>>,
        date: LocalDate,
    ) {
        reconcilePendingSavedEvents(query, date)
        _dayEvents.value = query.events
        _monthDotColorsByDay.value = dots
    }

    fun setMode(newMode: CalendarMode) {
        _mode.value = newMode
        if (newMode == CalendarMode.MonthDay) {
            _visibleMonth.value = YearMonth.from(_selectedDate.value)
            loadEventsForSelectedDay()
        } else if (newMode == CalendarMode.Year) {
            _visibleYear.value = _selectedDate.value.year
            ensureProductionYearsLoaded()
        }
    }

    fun selectDate(date: LocalDate) {
        if (_selectedDate.value != date) {
            pinnedEventsForDay = emptyList()
        }
        dayOfMonthAnchor = date.dayOfMonth
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
        _visibleYear.value = date.year
        loadEventsForSelectedDay()
        ensureProductionYearsLoaded()
    }

    fun shiftSelectedByDays(dayDelta: Long) {
        if (dayDelta == 0L) return
        selectDate(CalendarPeriod.shiftDays(_selectedDate.value, dayDelta))
    }

    fun updateEventSearch(query: String) {
        _eventSearchQuery.value = query
        eventSearchJob?.cancel()
        val needle = query.trim()
        if (needle.isEmpty()) {
            _eventSearchResults.value = emptyList()
            _eventSearchLoading.value = false
            return
        }
        _eventSearchLoading.value = true
        eventSearchJob = viewModelScope.launch {
            try {
                delay(EVENT_SEARCH_DEBOUNCE_MS)
                val filter = eventListFilter(_visibleCalendarsFilter.value)
                val today = LocalDate.now()
                val found = withContext(Dispatchers.IO) {
                    val hideTaskCalendars = AccountSlots.exchangeTaskWriteCalendarIds(
                        _accountSlots.value,
                        _deviceCalendars.value,
                    )
                    val events = repository.searchPastEvents(
                        query = needle,
                        visibleCalendarIds = filter,
                        zone = dayZone,
                        today = today,
                    ).filterNot { it.isHiddenExchangeTask(hideTaskCalendars) }
                    val birthdays = contactBirthdays.searchPastBirthdays(needle, today, dayZone)
                    (events + birthdays)
                        .distinctBy { "${it.eventId}-${it.beginMillis}" }
                        .sortedWith(compareByDescending<CalendarEvent> { it.beginMillis }.thenBy { it.title })
                        .take(EVENT_SEARCH_RESULT_LIMIT)
                }
                if (isActive) {
                    _eventSearchResults.value = found
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "event search failed", e)
                if (isActive) {
                    _eventSearchResults.value = emptyList()
                }
            } finally {
                if (isActive) {
                    _eventSearchLoading.value = false
                }
            }
        }
    }

    fun clearEventSearch() {
        eventSearchJob?.cancel()
        _eventSearchQuery.value = ""
        _eventSearchResults.value = emptyList()
        _eventSearchLoading.value = false
    }

    fun currentToday(): LocalDate = LocalDate.now(dayZone)

    fun goToToday() {
        val now = currentToday()
        _today.value = now
        openMonthAt(now)
    }

    /** Обновляет «сегодня». Если курсор стоял на старом сегодня — переносит его на новую дату. */
    fun refreshToday() {
        val now = currentToday()
        val previous = _today.value
        if (now == previous) return
        val selectedWasToday = _selectedDate.value == previous
        _today.value = now
        if (selectedWasToday) {
            openMonthAt(now)
        }
        loadTasks()
    }

    fun openMonthAt(date: LocalDate) {
        if (_selectedDate.value != date) {
            pinnedEventsForDay = emptyList()
        }
        dayOfMonthAnchor = date.dayOfMonth
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
        _visibleYear.value = date.year
        _mode.value = CalendarMode.MonthDay
        loadEventsForSelectedDay()
        ensureProductionYearsLoaded()
    }

    fun shiftSelectedByWeeks(weekDelta: Long) {
        val newDate = CalendarPeriod.shiftWeeks(_selectedDate.value, weekDelta)
        pinnedEventsForDay = emptyList()
        dayOfMonthAnchor = newDate.dayOfMonth
        _selectedDate.value = newDate
        _visibleMonth.value = YearMonth.from(newDate)
        _visibleYear.value = newDate.year
        loadEventsForSelectedDay()
    }

    fun goToPreviousMonth() {
        val prev = _visibleMonth.value.minusMonths(1)
        _visibleMonth.value = prev
        clampSelectedDateToMonth(prev)
        loadEventsForSelectedDay()
    }

    fun goToNextMonth() {
        val next = _visibleMonth.value.plusMonths(1)
        _visibleMonth.value = next
        clampSelectedDateToMonth(next)
        loadEventsForSelectedDay()
    }

    fun goToPreviousYear() {
        shiftVisibleYear(-1)
    }

    fun goToNextYear() {
        shiftVisibleYear(1)
    }

    private fun shiftVisibleYear(delta: Int) {
        val y = _visibleYear.value + delta
        _visibleYear.value = y
        val d = _selectedDate.value
        val month = YearMonth.of(y, d.monthValue)
        val moved = CalendarPeriod.dateInMonth(month, dayOfMonthAnchor)
        pinnedEventsForDay = emptyList()
        _selectedDate.value = moved
        _visibleMonth.value = YearMonth.from(moved)
        loadEventsForSelectedDay()
        reloadProductionCalendar()
    }

    private fun clampSelectedDateToMonth(month: YearMonth) {
        val nextDate = CalendarPeriod.dateInMonth(month, dayOfMonthAnchor)
        if (_selectedDate.value != nextDate) {
            pinnedEventsForDay = emptyList()
        }
        _selectedDate.value = nextDate
        _visibleYear.value = _selectedDate.value.year
    }

    fun loadEventsForSelectedDay() {
        val generation = ++eventsLoadGeneration
        viewModelScope.launch {
            reloadSelectedDay(generation, showLoading = true)
        }
    }

    /** Потянуть список вниз: requestSync системных учёток выбранных календарей и повторное чтение. */
    fun refreshSelectedCalendars() {
        if (_isRefreshingCalendars.value) return
        val generation = ++eventsLoadGeneration
        viewModelScope.launch {
            _isRefreshingCalendars.value = true
            try {
                val calendarIds = _visibleCalendarsFilter.value
                val accounts = withContext(Dispatchers.IO) {
                    runCatching {
                        CalendarProviderWarmup.requestSyncForCalendarIds(
                            getApplication(),
                            calendarIds,
                        )
                    }.getOrDefault(emptyList())
                }
                CalendarProviderWarmup.awaitCalendarSync(accounts)
                if (generation != eventsLoadGeneration) return@launch
                reloadSelectedDay(generation, showLoading = false)
            } catch (_: Exception) {
                if (generation == eventsLoadGeneration) {
                    runCatching {
                        reloadSelectedDay(generation, showLoading = false)
                    }
                }
            } finally {
                if (generation == eventsLoadGeneration) {
                    _isRefreshingCalendars.value = false
                }
            }
        }
    }

    private suspend fun reloadSelectedDay(
        generation: Int,
        showLoading: Boolean,
    ) {
        if (showLoading) _isLoadingEvents.value = true
        try {
            val date = _selectedDate.value
            val month = _visibleMonth.value
            var filter = resolveVisibleFilter()
            val waitsMs = longArrayOf(0L, 800L, 2_000L, 5_000L)
            var query: CalendarDayEventsQuery? = null
            var dots = _monthDotColorsByDay.value
            for (waitMs in waitsMs) {
                if (waitMs > 0) delay(waitMs)
                if (generation != eventsLoadGeneration) return
                val loaded = loadDayAndMonthDots(
                    date = date,
                    month = month,
                    filter = filter,
                )
                query = loaded.first
                dots = loaded.second
                filter = resolveVisibleFilter()
                if (loadPassesAcceptance(query, date)) break
            }
            if (generation != eventsLoadGeneration) return
            query?.let { applyLoadedDay(it, dots, date) }
        } finally {
            if (showLoading && generation == eventsLoadGeneration) {
                _isLoadingEvents.value = false
            }
        }
    }

    /**
     * Первый запуск: прогрев [CalendarContract.Instances], запрос синхронизации и повторные чтения.
     */
    fun bootstrapCalendarIfNeeded() {
        viewModelScope.launch {
            val needsInitialWarm = withContext(Dispatchers.IO) {
                val calendars = runCatching { loadDeviceCalendarsFromProvider() }.getOrElse { emptyList() }
                if (calendars.isNotEmpty()) {
                    applyLoadedCalendars(calendars)
                }
                if (!bootstrapPreferences.isInitialLoadComplete()) {
                    val app = getApplication<Application>()
                    CalendarProviderWarmup.requestCalendarSync(app)
                    CalendarProviderWarmup.warmInstancesCache(
                        app.contentResolver,
                        _selectedDate.value,
                        dayZone,
                    )
                    bootstrapPreferences.markInitialLoadComplete()
                    true
                } else {
                    false
                }
            }
            startWatchingCalendarChanges()
            startWatchingDateChanges()
            if (needsInitialWarm) {
                reloadEventsWithRetries(pinned = null, initialBootstrap = true)
            } else {
                loadEventsForSelectedDay()
            }
        }
    }

    fun reloadEventsAfterCalendarWrite(pinned: CalendarEvent? = null) {
        pinned?.let {
            rememberPendingSavedEvent(it)
            pinnedEventsForDay = listOf(it)
        }
        reloadEventsWithRetries(pinned = pinned, initialBootstrap = false)
        CalendarWidgets.refreshAll(getApplication())
    }

    private fun reloadEventsWithRetries(pinned: CalendarEvent?, initialBootstrap: Boolean) {
        if (pinned != null) {
            pinnedEventsForDay = listOf(pinned)
        }
        val generation = ++eventsLoadGeneration
        viewModelScope.launch {
            _isLoadingEvents.value = true
            try {
                val waitsMs = when {
                    initialBootstrap -> longArrayOf(0L, 600L, 1_200L, 2_500L, 5_000L, 8_000L)
                    pinned != null -> longArrayOf(0L, 400L, 800L, 1_500L, 3_000L, 5_000L, 8_000L, 12_000L)
                    else -> longArrayOf(0L, 400L, 800L, 1_500L)
                }
                for (waitMs in waitsMs) {
                    if (waitMs > 0) delay(waitMs)
                    if (generation != eventsLoadGeneration) return@launch
                    val date = _selectedDate.value
                    val month = _visibleMonth.value
                    val filter = resolveVisibleFilter()
                    val (query, dots) = loadDayAndMonthDots(
                        date = date,
                        month = month,
                        filter = filter,
                    )
                    if (generation != eventsLoadGeneration) return@launch
                    applyLoadedDay(query, dots, date)
                    if (loadPassesAcceptance(query, date)) break
                }
            } finally {
                if (generation == eventsLoadGeneration) {
                    _isLoadingEvents.value = false
                }
            }
        }
    }

    fun applyVisibleCalendarsFilter(selectedIds: Set<Long>, allIdsOnDevice: Set<Long>) {
        visibleCalendarsPreferences.writeFilter(selectedIds, allIdsOnDevice)
        _visibleCalendarsFilter.value = visibleCalendarsPreferences.readFilter()
        loadEventsForSelectedDay()
        CalendarWidgets.refreshAll(getApplication())
    }

    fun applyAccountSlots(state: AccountSlotsState) {
        val calendars = _deviceCalendars.value
        val normalized = AccountSlots.normalize(state, calendars)
        accountSlotsPreferences.write(normalized)
        _accountSlots.value = normalized
        dualWriteVisibleFilter(normalized, calendars)
        loadEventsForSelectedDay()
        viewModelScope.launch { reloadTasks(showLoading = false) }
        CalendarWidgets.refreshAll(getApplication())
    }

    fun loadTasks() {
        viewModelScope.launch {
            reloadTasks(showLoading = true)
        }
    }

    fun reloadTasksAfterCalendarWrite() {
        viewModelScope.launch {
            val taskIds = activeTaskCalendars(_deviceCalendars.value).map { it.id }.toSet()
            val accounts = withContext(Dispatchers.IO) {
                runCatching {
                    CalendarProviderWarmup.requestSyncForCalendarIds(
                        getApplication(),
                        taskIds.ifEmpty { null },
                    )
                }.getOrDefault(emptyList())
            }
            CalendarProviderWarmup.awaitCalendarSync(accounts)
            reloadTasks(showLoading = false)
        }
    }

    fun refreshTasks() {
        if (_isRefreshingTasks.value) return
        viewModelScope.launch {
            _isRefreshingTasks.value = true
            try {
                val taskIds = activeTaskCalendars(_deviceCalendars.value).map { it.id }.toSet()
                val accounts = withContext(Dispatchers.IO) {
                    runCatching {
                        CalendarProviderWarmup.requestSyncForCalendarIds(
                            getApplication(),
                            taskIds.ifEmpty { null },
                        )
                    }.getOrDefault(emptyList())
                }
                CalendarProviderWarmup.awaitCalendarSync(accounts)
                reloadTasks(showLoading = false)
            } catch (_: Exception) {
                runCatching { reloadTasks(showLoading = false) }
            } finally {
                _isRefreshingTasks.value = false
            }
        }
    }

    private suspend fun reloadTasks(showLoading: Boolean) {
        if (showLoading) _tasksLoading.value = true
        try {
            val calendars = runCatching { loadDeviceCalendarsFromProvider() }.getOrElse { _deviceCalendars.value }
            applyLoadedCalendars(calendars)
            val source = calendars.ifEmpty { _deviceCalendars.value }
            val loaded = withContext(Dispatchers.IO) {
                taskRepository.queryTasks(activeTaskCalendars(source))
            }
            _tasks.value = loaded
        } catch (e: Exception) {
            Log.e(TAG, "reloadTasks failed", e)
        } finally {
            if (showLoading) _tasksLoading.value = false
        }
    }

    fun setTaskDone(task: CalendarTask, done: Boolean) {
        viewModelScope.launch {
            val updated = _tasks.value.map { current ->
                if (current.eventId == task.eventId) current.copy(done = done) else current
            }
            _tasks.value = updated
            val ok = withContext(Dispatchers.IO) { taskRepository.setTaskDone(task.eventId, done) }
            if (!ok) {
                _tasks.value = _tasks.value.map { current ->
                    if (current.eventId == task.eventId) current.copy(done = task.done) else current
                }
            } else {
                reloadTasks(showLoading = false)
            }
        }
    }

    fun deleteTask(task: CalendarTask) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { taskRepository.deleteTask(task.eventId) }
            if (ok) {
                _tasks.value = _tasks.value.filterNot { it.eventId == task.eventId }
                reloadTasks(showLoading = false)
            }
        }
    }

    fun readVisibleCalendarsFilter(): Set<Long>? = _visibleCalendarsFilter.value

    fun startWatchingDateChanges() {
        if (dateChangeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshToday()
            }
        }
        dateChangeReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        getApplication<Application>().registerReceiver(
            receiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
        refreshToday()
    }

    fun startWatchingCalendarChanges() {
        if (calendarObserver != null) return
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                scheduleReloadFromProvider()
            }
        }
        calendarObserver = observer
        val resolver = getApplication<Application>().contentResolver
        resolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        resolver.registerContentObserver(CalendarContract.Instances.CONTENT_URI, true, observer)
    }

    private fun scheduleReloadFromProvider() {
        val now = SystemClock.elapsedRealtime()
        if (now < observerReloadDeferredUntil) {
            if (calendarReloadScheduled) return
            calendarReloadScheduled = true
            mainHandler.postDelayed({
                calendarReloadScheduled = false
                scheduleReloadFromProvider()
            }, observerReloadDeferredUntil - now)
            return
        }
        if (calendarReloadScheduled) return
        calendarReloadScheduled = true
        mainHandler.postDelayed({
            calendarReloadScheduled = false
            loadEventsForSelectedDay()
            loadTasks()
        }, 450)
    }

    override fun onCleared() {
        calendarObserver?.let { observer ->
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        }
        calendarObserver = null
        dateChangeReceiver?.let { receiver ->
            runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        }
        dateChangeReceiver = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "CalendarViewModel"
        private const val DEVICE_CALENDARS_QUERY_TIMEOUT_MS = 10_000L
        private const val EVENT_SEARCH_DEBOUNCE_MS = 250L
        private const val EVENT_SEARCH_RESULT_LIMIT = 150
    }
}
