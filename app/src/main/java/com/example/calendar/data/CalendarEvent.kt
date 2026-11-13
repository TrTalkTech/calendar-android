package com.example.calendar.data

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarId: Long?,
    val calendarName: String?,
    val color: Int?,
    /** [CalendarContract.Events.EVENT_LOCATION] — геопозиция или видеовызов. */
    val location: String = "",
    val attendees: List<EventInvitee> = emptyList(),
    val notes: String = "",
    /** [android.provider.CalendarContract.Events.AVAILABILITY]: 0 занято, 1 свободно. */
    val availability: Int = 0,
    /** [android.provider.CalendarContract.Events.SELF_ATTENDEE_STATUS]. */
    val selfAttendeeStatus: Int = EventRsvp.STATUS_NONE,
)

/** Результат выборки дня: события и id, реально пришедшие из провайдера (без fallback). */
data class CalendarDayEventsQuery(
    val events: List<CalendarEvent>,
    val providerEventIds: Set<Long>,
    /**
     * Были recurring-мастера без строк в Instances — день мог быть неполным у OEM.
     * ViewModel делает warmup/sync и повторное чтение.
     */
    val needsInstancesRecovery: Boolean = false,
    /** Id recurring-событий, для которых occurrence синтезирован из RRULE. */
    val synthesizedRecurringEventIds: Set<Long> = emptySet(),
)
