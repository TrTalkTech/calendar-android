package com.example.calendar.data

/** Участник события: email и необязательное имя (из календаря или токена ввода). */
data class EventInvitee(
    val email: String,
    val name: String? = null,
)
