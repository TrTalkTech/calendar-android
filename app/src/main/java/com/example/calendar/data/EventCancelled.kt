package com.example.calendar.data

/** Отменённые организатором встречи (не путать с declined RSVP). */
object EventCancelled {
    const val STATUS_CANCELLED = 2

    fun isCancelledStatus(status: Int?): Boolean = status == STATUS_CANCELLED

    fun isCancelledTitle(title: String?): Boolean {
        val t = title?.trim().orEmpty()
        if (t.isEmpty()) return false
        val lower = t.lowercase()
        return lower.startsWith("canceled:") ||
            lower.startsWith("cancelled:") ||
            lower.startsWith("отменено:") ||
            lower.startsWith("отмена:")
    }

    /** Скрывать из повестки, если галка «показывать отменённые» выключена. */
    fun shouldHideFromAgenda(
        status: Int?,
        title: String?,
        showCancelled: Boolean,
    ): Boolean {
        if (showCancelled) return false
        return isCancelledStatus(status) || isCancelledTitle(title)
    }
}
