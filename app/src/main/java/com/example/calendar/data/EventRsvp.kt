package com.example.calendar.data

/** Ответ на входящее приглашение. Числа совпадают с CalendarContract.Attendees.ATTENDEE_STATUS_*. */
enum class EventRsvpAnswer {
    ACCEPTED,
    TENTATIVE,
    DECLINED,
}

object EventRsvp {
    const val STATUS_NONE = 0
    const val STATUS_ACCEPTED = 1
    const val STATUS_DECLINED = 2
    const val STATUS_INVITED = 3
    const val STATUS_TENTATIVE = 4

    fun hasAnswer(selfAttendeeStatus: Int): Boolean =
        fromSelfAttendeeStatus(selfAttendeeStatus) != null

    fun fromSelfAttendeeStatus(status: Int): EventRsvpAnswer? = when (status) {
        STATUS_ACCEPTED -> EventRsvpAnswer.ACCEPTED
        STATUS_DECLINED -> EventRsvpAnswer.DECLINED
        STATUS_TENTATIVE -> EventRsvpAnswer.TENTATIVE
        else -> null
    }

    fun toSelfAttendeeStatus(answer: EventRsvpAnswer): Int = when (answer) {
        EventRsvpAnswer.ACCEPTED -> STATUS_ACCEPTED
        EventRsvpAnswer.DECLINED -> STATUS_DECLINED
        EventRsvpAnswer.TENTATIVE -> STATUS_TENTATIVE
    }

    fun shouldHideFromAgenda(selfAttendeeStatus: Int?): Boolean =
        selfAttendeeStatus == STATUS_DECLINED

    /** Входящее приглашение без «принял»: без ответа или «под вопросом». */
    fun isNotAccepted(selfAttendeeStatus: Int?): Boolean =
        selfAttendeeStatus == STATUS_INVITED || selfAttendeeStatus == STATUS_TENTATIVE

    fun notDeclinedSql(column: String): String =
        "($column IS NULL OR $column != $STATUS_DECLINED)"

    fun canRespond(
        isOrganizer: Boolean,
        organizerEmail: String?,
        ownerAccount: String?,
        selfAttendeeStatus: Int,
        hasAttendeeData: Boolean,
    ): Boolean {
        if (isOrganizer) return false
        val owner = organizerEmail?.trim()?.lowercase().orEmpty()
        val account = ownerAccount?.trim()?.lowercase().orEmpty()
        if (account.isNotEmpty() && owner.isNotEmpty() && account == owner) return false
        if (selfAttendeeStatus == STATUS_INVITED ||
            selfAttendeeStatus == STATUS_ACCEPTED ||
            selfAttendeeStatus == STATUS_DECLINED ||
            selfAttendeeStatus == STATUS_TENTATIVE
        ) {
            return true
        }
        return hasAttendeeData && account.isNotEmpty() && owner.isNotEmpty() && account != owner
    }
}
