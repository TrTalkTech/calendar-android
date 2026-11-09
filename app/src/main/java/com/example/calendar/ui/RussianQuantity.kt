package com.example.calendar.ui

/**
 * Краткие подписи для списка интервалов (1 … n …).
 */
internal object RussianQuantity {

    fun days(n: Int): String = "$n ${pluralDays(n)}"

    fun weeks(n: Int): String = "$n ${pluralWeeks(n)}"

    fun months(n: Int): String = "$n ${pluralMonths(n)}"

    fun years(n: Int): String = "$n ${pluralYears(n)}"

    private fun pluralDays(n: Int): String = when {
        n % 100 in 11..14 -> "дней"
        n % 10 == 1 -> "день"
        n % 10 in 2..4 -> "дня"
        else -> "дней"
    }

    private fun pluralWeeks(n: Int): String = when {
        n % 100 in 11..14 -> "недель"
        n % 10 == 1 -> "неделя"
        n % 10 in 2..4 -> "недели"
        else -> "недель"
    }

    private fun pluralMonths(n: Int): String = when {
        n % 100 in 11..14 -> "месяцев"
        n % 10 == 1 -> "месяц"
        n % 10 in 2..4 -> "месяца"
        else -> "месяцев"
    }

    private fun pluralYears(n: Int): String = when {
        n % 100 in 11..14 -> "лет"
        n % 10 == 1 -> "год"
        n % 10 in 2..4 -> "года"
        else -> "лет"
    }
}
