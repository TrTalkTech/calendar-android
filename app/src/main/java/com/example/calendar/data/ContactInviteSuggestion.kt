package com.example.calendar.data

data class ContactInviteSuggestion(
    val contactId: Long,
    val displayName: String,
    val email: String,
    val phone: String? = null,
    /** Подпись корпоративного каталога. Пусто у людей из телефонной книги. */
    val directoryName: String? = null,
    /** content:// фото из корпоративной книги, если каталог его отдал. */
    val photoUri: String? = null,
    val lookupKey: String? = null,
)
