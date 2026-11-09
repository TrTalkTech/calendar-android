package com.example.calendar.data

private val TASK_CALENDAR_NAMES = setOf(
    "tasks",
    "task",
    "задачи",
    "задача",
)

val WritableCalendar.isTaskCalendar: Boolean
    get() = displayName.isTaskCalendarName()

fun String.isTaskCalendarName(): Boolean =
    trim().lowercase() in TASK_CALENDAR_NAMES

fun List<WritableCalendar>.taskCalendars(): List<WritableCalendar> =
    filter { it.isTaskCalendar }

fun WritableCalendar.writeLabel(): String {
    val account = accountName?.trim().orEmpty()
    return if (account.isEmpty() || account.equals(displayName, ignoreCase = true)) {
        displayName
    } else {
        "$displayName · $account"
    }
}

fun List<WritableCalendar>.eventCalendars(): List<WritableCalendar> =
    filterNot { it.isTaskCalendar }

fun List<WritableCalendar>.preferredAccountNames(visibleFilter: Set<Long>?): Set<String> {
    val source = if (visibleFilter == null) this else filter { it.id in visibleFilter }
    return source.eventCalendars()
        .mapNotNull { it.accountName?.trim()?.takeIf { name -> name.isNotEmpty() } }
        .map { it.lowercase() }
        .toSet()
}

fun List<WritableCalendar>.taskCalendarsForWrite(
    preferredAccountNames: Set<String> = emptySet(),
): List<WritableCalendar> {
    val preferred = preferredAccountNames.map { it.lowercase() }.toSet()
    return taskCalendars().sortedWith(
        compareByDescending<WritableCalendar> { it.accountName.orEmpty().lowercase() in preferred }
            .thenByDescending { it.canModifyEvents }
            .thenByDescending { it.syncEvents }
            .thenByDescending { it.accountType.isExchangeLike() }
            .thenByDescending { it.isPrimary }
            .thenBy { it.accountName.orEmpty().lowercase() },
    )
}

fun List<WritableCalendar>.preferredTaskCalendar(
    preferredAccountNames: Set<String> = emptySet(),
): WritableCalendar? {
    val tasks = taskCalendarsForWrite(preferredAccountNames)
    if (tasks.isEmpty()) return null
    val preferred = preferredAccountNames.map { it.lowercase() }.toSet()
    fun List<WritableCalendar>.pick(): WritableCalendar? =
        firstOrNull { it.canModifyEvents && it.accountType.isExchangeLike() }
            ?: firstOrNull { it.canModifyEvents }
            ?: firstOrNull { it.accountType.isExchangeLike() }
            ?: firstOrNull()

    if (preferred.isNotEmpty()) {
        tasks.filter { it.accountName.orEmpty().lowercase() in preferred }.pick()?.let { return it }
    }
    return tasks.pick()
}

/**
 * Первый экран и поиск никогда не показывают календарь задач,
 * даже если он отмечен в фильтре или фильтр не задан (все календари).
 */
fun CalendarEvent.isHiddenExchangeTask(writeCalendarIds: Set<Long>): Boolean =
    allDay &&
        availability == EventOverlap.AVAILABILITY_FREE &&
        calendarId != null &&
        calendarId in writeCalendarIds

fun filterIdsForEventLists(
    rawFilter: Set<Long>?,
    calendars: List<WritableCalendar>,
): Set<Long>? {
    val eventIds = calendars.eventCalendars().map { it.id }.toSet()
    val hasTaskCalendar = calendars.any { it.isTaskCalendar }
    if (!hasTaskCalendar) return rawFilter
    return when (rawFilter) {
        null -> eventIds
        else -> rawFilter.intersect(eventIds)
    }
}

private fun String?.isExchangeLike(): Boolean {
    val type = orEmpty().lowercase()
    return type.contains("exchange") ||
        type.contains("eas") ||
        type.contains("outlook") ||
        type.contains("office") ||
        type.contains("microsoft")
}
