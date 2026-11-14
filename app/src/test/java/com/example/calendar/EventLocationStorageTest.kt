package com.example.calendar

import com.example.calendar.ui.EventLocationMaps
import com.example.calendar.ui.YandexPlaceSuggestion
import com.example.calendar.ui.parseYandexSuggestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLocationStorageTest {

    @Test
    fun displayLabel_prefersTitleAndSubtitleOverRawText() {
        val place = YandexPlaceSuggestion(
            title = "метро Медведково",
            subtitle = "улица Широкая",
            address = "деревня Медведково, Москва",
            longitude = 37.661234,
            latitude = 55.887654,
        )
        assertEquals("метро Медведково, улица Широкая", EventLocationMaps.displayLabel(place))
    }

    @Test
    fun storageLocation_appendsAnchoredCoordinates() {
        val place = YandexPlaceSuggestion(
            title = "МитХук",
            subtitle = "ресторан, Москва",
            address = "какой-то другой текст",
            longitude = 37.123456,
            latitude = 55.654321,
        )
        val stored = EventLocationMaps.storageLocation(place)
        assertTrue(stored.startsWith("МитХук, ресторан, Москва"))
        assertTrue(stored.contains("@37.123456,55.654321"))
        assertEquals(
            "МитХук, ресторан, Москва",
            EventLocationMaps.humanLocationLabel(stored),
        )
        assertEquals(37.123456 to 55.654321, EventLocationMaps.anchoredCoordinates(stored))
    }

    @Test
    fun storageLocation_withoutCoords_isDisplayLabelOnly() {
        val place = YandexPlaceSuggestion(
            title = "Кафе",
            subtitle = "Москва",
            address = "Кафе, Москва",
        )
        assertEquals("Кафе, Москва", EventLocationMaps.storageLocation(place))
        assertNull(EventLocationMaps.anchoredCoordinates(EventLocationMaps.storageLocation(place)))
    }

    @Test
    fun displayLines_keepsStoredLabelWhenResolveWouldDisagree() {
        val stored = EventLocationMaps.appendAnchoredCoordinates(
            "метро Медведково, улица Широкая",
            37.66,
            55.88,
        )
        assertEquals(
            listOf("метро Медведково, улица Широкая"),
            EventLocationMaps.displayLines(stored),
        )
        assertFalse(EventLocationMaps.humanLocationLabel(stored).contains("деревня", ignoreCase = true))
    }

    @Test
    fun canShowEmbeddedMap_trueWhenAnchoredEvenIfQueryLooksSparse() {
        val stored = "метро Медведково @37.661000,55.887000"
        assertTrue(EventLocationMaps.canShowEmbeddedMap(stored))
        assertNotNull(EventLocationMaps.anchoredCoordinates(stored))
    }

    @Test
    fun parseSuggest_storageUsesTitleSubtitle() {
        val body = """
            suggest.apply({"results":[
              {"type":"biz","title":{"text":"метро Медведково"},"subtitle":{"text":"улица Широкая"},"text":"деревня Медведково","uri":""}
            ]})
        """.trimIndent()
        val place = parseYandexSuggestBody(body).single()
        assertEquals("метро Медведково, улица Широкая", EventLocationMaps.displayLabel(place))
        assertEquals("деревня Медведково", place.address)
        assertEquals(
            "метро Медведково, улица Широкая",
            EventLocationMaps.storageLocation(place),
        )
    }

    @Test
    fun anchoredCoordinates_readsYandexMapsPtUrl() {
        val raw = "МитХук https://yandex.ru/maps/?pt=37.500000,55.700000&z=16"
        assertEquals(37.5 to 55.7, EventLocationMaps.anchoredCoordinates(raw))
        assertEquals("МитХук", EventLocationMaps.humanLocationLabel(raw))
    }
}
