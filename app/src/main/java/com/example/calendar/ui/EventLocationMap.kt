package com.example.calendar.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.calendar.AppLocale
import com.example.calendar.R
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EventLocationMapPreviewState(
    val bitmap: Bitmap,
    val longitude: Double,
    val latitude: Double,
)

data class ResolvedEventPlace(
    val lines: List<String>,
    val longitude: Double,
    val latitude: Double,
)

data class YandexPlaceSuggestion(
    val title: String,
    val subtitle: String,
    val address: String,
    val longitude: Double? = null,
    val latitude: Double? = null,
)

object EventLocationMaps {

    private const val TAG = "EventLocationMaps"

    private val HouseWordPattern = Regex("""(?i)(?:дом|д)\.?\s*(\d+\p{L}?)""")
    private val TrailingHousePattern = Regex("""(?:^|\s)(\d+\p{L}?)$""")

    private val AddressStopWords = setOf(
        "дом", "улица", "город", "проспект", "переулок", "шоссе", "набережная",
        "бульвар", "площадь", "корпус", "строение", "россия", "область", "район",
        "квартира",
    )

    fun canShowEmbeddedMap(location: String): Boolean {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (EventConferenceLinks.isConferenceLocation(trimmed)) return false
        return true
    }

    suspend fun loadPreview(
        context: Context,
        location: String,
        locale: Locale = AppLocale.display,
    ): EventLocationMapPreviewState? = withContext(Dispatchers.IO) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return@withContext null
        val place = resolvePlace(context, trimmed, locale) ?: return@withContext null
        val bitmap = downloadBitmap(yandexStaticMapUrl(place.longitude, place.latitude))
            ?: return@withContext null
        EventLocationMapPreviewState(
            bitmap = bitmap,
            longitude = place.longitude,
            latitude = place.latitude,
        )
    }

    suspend fun resolvePlace(
        context: Context,
        location: String,
        locale: Locale = AppLocale.display,
    ): ResolvedEventPlace? = withContext(Dispatchers.IO) {
        val trimmed = location.trim()
        if (!canShowEmbeddedMap(trimmed)) return@withContext null
        val tokens = distinctiveTokens(trimmed)
        if (tokens.isEmpty()) return@withContext null
        for (query in addressQueryVariants(trimmed)) {
            val place = yandexSuggestPlace(query, tokens)
            if (place != null) return@withContext place
        }
        null
    }

    suspend fun suggestPlaces(query: String): List<YandexPlaceSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !canShowEmbeddedMap(trimmed)) return@withContext emptyList()
        fetchYandexSuggestions(trimmed)
    }

    suspend fun loadStaticMap(longitude: Double, latitude: Double): Bitmap? = withContext(Dispatchers.IO) {
        downloadBitmap(yandexStaticMapUrl(longitude, latitude))
    }

    fun openInYandexMaps(
        context: Context,
        location: String,
        longitude: Double? = null,
        latitude: Double? = null,
    ) {
        val uri = if (longitude != null && latitude != null) {
            val lon = String.format(Locale.US, "%.6f", longitude)
            val lat = String.format(Locale.US, "%.6f", latitude)
            Uri.parse("yandexmaps://maps.yandex.ru/?pt=$lon,$lat&z=16&l=map")
        } else {
            Uri.parse("yandexmaps://maps.yandex.ru/?text=${Uri.encode(location.trim())}")
        }
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("ru.yandex.yandexmaps")
        }
        val opened = runCatching { context.startActivity(mapsIntent) }.isSuccess
        if (!opened) {
            val fallback = if (longitude != null && latitude != null) {
                val lon = String.format(Locale.US, "%.6f", longitude)
                val lat = String.format(Locale.US, "%.6f", latitude)
                Uri.parse("https://yandex.ru/maps/?ll=$lon,$lat&z=16&l=map&pt=$lon,$lat,pm2rdm")
            } else {
                Uri.parse("https://yandex.ru/maps/?text=${Uri.encode(location.trim())}")
            }
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
            }
        }
    }

    private fun yandexStaticMapUrl(longitude: Double, latitude: Double): String {
        val lon = String.format(Locale.US, "%.6f", longitude)
        val lat = String.format(Locale.US, "%.6f", latitude)
        return "https://static-maps.yandex.ru/1.x/?ll=$lon,$lat&size=650,320&z=16&l=map&pt=$lon,$lat,pm2rdm&scale=2"
    }

    /**
     * «москва корабельная дом 1» → «Корабельная улица, 1, Москва».
     * Системный геокодер так находит дом, а свободную фразу часто путает с другим адресом.
     */
    private fun addressQueryVariants(raw: String): List<String> {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        val houseMatch = HouseWordPattern.find(cleaned)
        val house = houseMatch?.groupValues?.get(1)
            ?: TrailingHousePattern.find(cleaned)?.groupValues?.get(1)
        var rest = cleaned
        if (houseMatch != null) rest = rest.removeRange(houseMatch.range)
        rest = rest.replace(TrailingHousePattern, "").trim()
        val words = rest.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        val city = words.firstOrNull()
        val street = if (city != null && words.size > 1) {
            words.drop(1).joinToString(" ")
        } else {
            words.joinToString(" ")
        }
        val variants = mutableListOf(cleaned)
        if (street.isNotBlank() && city != null && words.size > 1 && !house.isNullOrBlank()) {
            val streetName = street.capitalizeRu()
            val cityName = city.capitalizeRu()
            variants += "$streetName улица, $house, $cityName"
            variants += "$cityName, $streetName улица, $house"
        }
        return variants.distinct()
    }

    private fun String.capitalizeRu(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("ru")) else it.toString() }

    private fun yandexSuggestPlace(query: String, tokens: List<String>): ResolvedEventPlace? {
        var best: ResolvedEventPlace? = null
        var bestScore = 0
        for (row in fetchYandexSuggestions(query)) {
            val score = "${row.title} ${row.subtitle} ${row.address}".matchScore(tokens)
            val lon = row.longitude
            val lat = row.latitude
            if (score <= bestScore || lon == null || lat == null) continue
            bestScore = score
            val lines = listOf(row.title, row.subtitle)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            best = ResolvedEventPlace(
                lines = lines.ifEmpty { listOf(row.address) },
                longitude = lon,
                latitude = lat,
            )
        }
        return best
    }

    private fun fetchYandexSuggestions(query: String): List<YandexPlaceSuggestion> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val urls = listOf(
            "https://suggest-maps.yandex.ru/suggest-geo?part=$encoded&lang=ru_RU&search_type=all&v=9&n=8",
            "https://yandex.ru/suggest-geo?part=$encoded&lang=ru_RU&search_type=all&v=9&n=8",
        )
        for (url in urls) {
            val body = downloadSuggestBody(url) ?: continue
            val parsed = parseYandexSuggestBody(body)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun downloadSuggestBody(url: String): String? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Accept", "application/json, text/javascript, */*;q=0.8")
                setRequestProperty("Accept-Language", "ru")
                setRequestProperty("Referer", "https://yandex.ru/maps/")
                setRequestProperty("Origin", "https://yandex.ru")
                connect()
            }
            try {
                val stream = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "suggest HTTP ${connection.responseCode} for $url")
                    return null
                }
                body.takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "suggest failed $url", it) }.getOrNull()
    }

    internal fun coordinatesFromUriForTests(uri: String): Pair<Double, Double>? =
        coordinatesFromYandexUri(uri)

    /** Координаты лежат в `ymapsbm1://geo?data=` двумя float: долгота, затем широта. */
    private fun coordinatesFromYandexUri(uri: String): Pair<Double, Double>? {
        if (uri.isBlank()) return null
        val data = Uri.parse(uri).getQueryParameter("data") ?: return null
        val raw = decodeYandexGeoData(data) ?: return null
        return findCoordinatePair(raw) ?: scanLonLat(raw)
    }

    /**
     * Яндекс кладёт в `data` URL-safe Base64, часто без padding и с запятой в конце.
     * [Base64.DEFAULT] такое не читает — из-за этого пропадала карта у адресов вроде
     * «Москва, Клары Цеткин 2».
     */
    private fun decodeYandexGeoData(data: String): ByteArray? {
        val cleaned = data.trim().trimEnd(',').replace(" ", "")
        if (cleaned.isEmpty()) return null
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return runCatching { Base64.decode(padded, Base64.URL_SAFE) }.getOrNull()
            ?: runCatching { Base64.decode(padded, Base64.DEFAULT) }.getOrNull()
            ?: runCatching {
                Base64.decode(
                    padded.replace('-', '+').replace('_', '/'),
                    Base64.DEFAULT,
                )
            }.getOrNull()
    }

    private fun scanLonLat(buf: ByteArray): Pair<Double, Double>? {
        var index = 0
        while (index + 10 <= buf.size) {
            if ((buf[index].toInt() and 0xff) == 0x0d &&
                (buf[index + 5].toInt() and 0xff) == 0x15
            ) {
                val longitude = ByteBuffer.wrap(buf, index + 1, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
                val latitude = ByteBuffer.wrap(buf, index + 6, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
                if (latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0 &&
                    (kotlin.math.abs(latitude) >= 1.0 || kotlin.math.abs(longitude) >= 1.0)
                ) {
                    return longitude to latitude
                }
            }
            index++
        }
        return null
    }

    private fun findCoordinatePair(buf: ByteArray): Pair<Double, Double>? {
        var index = 0
        while (index < buf.size) {
            val key = buf[index].toInt() and 0xff
            index++
            when (key and 7) {
                0 -> {
                    while (index < buf.size && buf[index].toInt() and 0x80 != 0) index++
                    index++
                }
                2 -> {
                    if (index >= buf.size) return null
                    var length = 0
                    var shift = 0
                    while (index < buf.size) {
                        val byte = buf[index].toInt() and 0xff
                        index++
                        length = length or ((byte and 0x7f) shl shift)
                        if (byte and 0x80 == 0) break
                        shift += 7
                    }
                    val end = (index + length).coerceAtMost(buf.size)
                    val chunk = buf.copyOfRange(index, end)
                    index = end
                    readLonLat(chunk)?.let { return it }
                    findCoordinatePair(chunk)?.let { return it }
                }
                5 -> index += 4
                1 -> index += 8
                else -> break
            }
        }
        return null
    }

    private fun readLonLat(chunk: ByteArray): Pair<Double, Double>? {
        if (chunk.size < 10) return null
        if ((chunk[0].toInt() and 0xff) != 0x0d || (chunk[5].toInt() and 0xff) != 0x15) return null
        val longitude = ByteBuffer.wrap(chunk, 1, 4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
        val latitude = ByteBuffer.wrap(chunk, 6, 4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        if (kotlin.math.abs(latitude) < 1.0 && kotlin.math.abs(longitude) < 1.0) return null
        return longitude to latitude
    }

    /** Слова запроса, по которым можно отличить нужную улицу от случайного адреса. */
    private fun distinctiveTokens(query: String): List<String> {
        return query.lowercase(Locale.forLanguageTag("ru"))
            .replace('ё', 'е')
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { token ->
                token.length >= 4 &&
                    token.any(Char::isLetter) &&
                    token !in AddressStopWords
            }
            .distinct()
    }

    /** Самое длинное слово запроса обязательно. Остальные добавляют очки, но не блокируют карту. */
    private fun String.matchScore(tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        val haystack = lowercase(Locale.forLanguageTag("ru")).replace('ё', 'е')
        val longest = tokens.maxBy { it.length }
        if (!haystack.contains(longest)) return 0
        return tokens.count { haystack.contains(it) }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                doInput = true
                connect()
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }.also {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

@Composable
fun EventLocationMapPreview(
    location: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var preview by remember(location) { mutableStateOf<EventLocationMapPreviewState?>(null) }
    val contentDescription = stringResource(R.string.event_location_map_cd)

    LaunchedEffect(location, AppLocale.display) {
        preview = EventLocationMaps.loadPreview(context, location, AppLocale.display)
    }

    val state = preview ?: return

    Image(
        bitmap = state.bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(152.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                EventLocationMaps.openInYandexMaps(
                    context = context,
                    location = location,
                    longitude = state.longitude,
                    latitude = state.latitude,
                )
            }
            .semantics { this.contentDescription = contentDescription },
    )
}

/**
 * Яндекс отдаёт JSONP `suggest.apply({...})`. [org.json.JSONObject] в JVM-тестах падает,
 * поэтому results[] разбираем скобками и строковыми полями.
 */
internal fun parseYandexSuggestBody(body: String): List<YandexPlaceSuggestion> {
    val jsonStart = body.indexOf('{')
    val jsonEnd = body.lastIndexOf('}')
    if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()
    val json = body.substring(jsonStart, jsonEnd + 1)
    val resultsKey = json.indexOf("\"results\"")
    if (resultsKey < 0) return emptyList()
    val arrayStart = json.indexOf('[', resultsKey)
    if (arrayStart < 0) return emptyList()
    val arrayEnd = matchingBracket(json, arrayStart, '[', ']') ?: return emptyList()
    val result = ArrayList<YandexPlaceSuggestion>()
    val seen = HashSet<String>()
    for (obj in splitJsonObjects(json.substring(arrayStart + 1, arrayEnd))) {
        val title = nestedTextField(obj, "title")
        val subtitle = nestedTextField(obj, "subtitle")
        val topLevel = stripObjectField(stripObjectField(obj, "title"), "subtitle")
        val full = jsonStringField(topLevel, "text").trim()
        val address = when {
            full.isNotEmpty() -> full
            title.isNotEmpty() && subtitle.isNotEmpty() -> "$title, $subtitle"
            title.isNotEmpty() -> title
            subtitle.isNotEmpty() -> subtitle
            else -> continue
        }
        if (!seen.add(address.lowercase())) continue
        val uri = jsonStringField(obj, "uri")
        val point = runCatching { EventLocationMaps.coordinatesFromUriForTests(uri) }.getOrNull()
        result += YandexPlaceSuggestion(
            title = title.ifEmpty { address },
            subtitle = subtitle,
            address = address,
            longitude = point?.first,
            latitude = point?.second,
        )
    }
    return result
}

private fun stripObjectField(obj: String, key: String): String {
    val token = "\"$key\""
    val keyAt = obj.indexOf(token)
    if (keyAt < 0) return obj
    val brace = obj.indexOf('{', keyAt + token.length)
    if (brace < 0) return obj
    val end = matchingBracket(obj, brace, '{', '}') ?: return obj
    return obj.removeRange(keyAt, end + 1)
}

private fun nestedTextField(obj: String, parentKey: String): String {
    val key = "\"$parentKey\""
    val keyAt = obj.indexOf(key)
    if (keyAt < 0) return ""
    val brace = obj.indexOf('{', keyAt + key.length)
    if (brace < 0) return ""
    val end = matchingBracket(obj, brace, '{', '}') ?: return ""
    return jsonStringField(obj.substring(brace, end + 1), "text")
}

private fun jsonStringField(obj: String, key: String): String {
    val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    return unescapeJsonString(regex.find(obj)?.groupValues?.get(1).orEmpty())
}

private fun unescapeJsonString(raw: String): String =
    raw.replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")

private fun splitJsonObjects(arrayInner: String): List<String> {
    val out = ArrayList<String>()
    var depth = 0
    var start = -1
    var i = 0
    while (i < arrayInner.length) {
        when (arrayInner[i]) {
            '"' -> {
                i++
                while (i < arrayInner.length) {
                    if (arrayInner[i] == '\\') {
                        i += 2
                        continue
                    }
                    if (arrayInner[i] == '"') break
                    i++
                }
            }
            '{' -> {
                if (depth == 0) start = i
                depth++
            }
            '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    out += arrayInner.substring(start, i + 1)
                    start = -1
                }
            }
        }
        i++
    }
    return out
}

private fun matchingBracket(text: String, openIndex: Int, open: Char, close: Char): Int? {
    var depth = 0
    var i = openIndex
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '"' -> {
                i++
                while (i < text.length) {
                    if (text[i] == '\\') {
                        i += 2
                        continue
                    }
                    if (text[i] == '"') break
                    i++
                }
            }
            ch == open -> depth++
            ch == close -> {
                depth--
                if (depth == 0) return i
            }
        }
        i++
    }
    return null
}
