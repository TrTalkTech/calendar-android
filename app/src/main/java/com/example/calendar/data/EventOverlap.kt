package com.example.calendar.data

data class OverlapCandidate(
    val eventId: Long?,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

data class BusyEvent(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val availability: Int = 0,
    val calendarName: String? = null,
)

object EventOverlap {
    /** CalendarContract.Events.AVAILABILITY_FREE */
    const val AVAILABILITY_FREE = 1

    fun intervalsOverlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean {
        if (aEnd <= aStart || bEnd <= bStart) return false
        return aStart < bEnd && bStart < aEnd
    }

    fun conflicts(candidate: OverlapCandidate, occupied: List<BusyEvent>): List<BusyEvent> {
        if (candidate.allDay) return emptyList()
        if (candidate.endMillis <= candidate.beginMillis) return emptyList()
        return occupied.filter { slot ->
            if (candidate.eventId != null && slot.eventId == candidate.eventId) return@filter false
            if (slot.allDay) return@filter false
            if (slot.availability == AVAILABILITY_FREE) return@filter false
            if (slot.calendarName?.isTaskCalendarName() == true) return@filter false
            intervalsOverlap(
                candidate.beginMillis,
                candidate.endMillis,
                slot.beginMillis,
                slot.endMillis,
            )
        }
    }
}

fun CalendarEvent.toBusyEvent(): BusyEvent = BusyEvent(
    eventId = eventId,
    title = title,
    beginMillis = beginMillis,
    endMillis = endMillis,
    allDay = allDay,
    availability = availability,
    calendarName = calendarName,
)
