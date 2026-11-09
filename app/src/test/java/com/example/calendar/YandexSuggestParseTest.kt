package com.example.calendar

import com.example.calendar.ui.parseYandexSuggestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexSuggestParseTest {

    @Test
    fun parseYandexSuggestBody_readsJsonpResults() {
        val body = """
            suggest.apply({"part":"moskva","results":[
              {"type":"toponym","title":{"text":"Москва","hl":[]},"subtitle":{"text":"Россия"},"text":"Москва ","uri":"ymapsbm1://geo?data=abc"},
              {"type":"biz","title":{"text":"Москва-Сити"},"subtitle":{"text":"Пресненская набережная"},"text":"Москва-Сити, Пресненская набережная","uri":""}
            ]})
        """.trimIndent()

        val rows = parseYandexSuggestBody(body)
        assertEquals(2, rows.size)
        assertEquals("Москва", rows[0].title)
        assertEquals("Россия", rows[0].subtitle)
        assertEquals("Москва", rows[0].address.trim())
        assertEquals("Москва-Сити", rows[1].title)
        assertTrue(rows[1].address.contains("Пресненская"))
    }

    @Test
    fun parseYandexSuggestBody_emptyResults() {
        val body = """suggest.apply({"part":"x","results":[]})"""
        assertTrue(parseYandexSuggestBody(body).isEmpty())
    }

    @Test
    fun parseYandexSuggestBody_dedupesSameAddress() {
        val body = """
            suggest.apply({"results":[
              {"title":{"text":"Москва"},"subtitle":{"text":"Россия"},"text":"Москва"},
              {"title":{"text":"Москва"},"subtitle":{"text":"Россия"},"text":"Москва"}
            ]})
        """.trimIndent()
        assertEquals(1, parseYandexSuggestBody(body).size)
        assertEquals("Москва", parseYandexSuggestBody(body).single().address)
    }
}
