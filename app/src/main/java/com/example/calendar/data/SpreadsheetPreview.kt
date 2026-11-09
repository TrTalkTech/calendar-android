package com.example.calendar.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

object SpreadsheetPreview {
    fun parse(file: File): List<List<String>> {
        val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
        if (bytes.isEmpty()) return emptyList()
        return when {
            isZip(bytes) -> xlsxRows(unzip(bytes))
            looksLikeXmlSpreadsheet(bytes) -> spreadsheetMlRows(bytes)
            else -> emptyList()
        }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun looksLikeXmlSpreadsheet(bytes: ByteArray): Boolean {
        val head = bytes.copyOfRange(0, minOf(bytes.size, 800))
            .toString(Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!head.startsWith("<")) return false
        return head.contains("Workbook", ignoreCase = true) ||
            head.contains("spreadsheet", ignoreCase = true) ||
            head.contains("Excel.Sheet", ignoreCase = true)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.size < 20_000_000) {
                    out[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun xlsxRows(files: Map<String, ByteArray>): List<List<String>> {
        val shared = sharedStrings(files["xl/sharedStrings.xml"])
        val sheet = files.entries.firstOrNull { (name, _) ->
            name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")
        }?.value ?: return emptyList()
        val parser = parser(sheet)
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        var cellType = ""
        var inValue = false
        var inInline = false
        val inline = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT && rows.size < 200) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "row" -> cells = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        inline.clear()
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInline = true
                }
                XmlPullParser.TEXT -> when {
                    inValue -> {
                        val raw = parser.text.orEmpty()
                        cells += if (cellType == "s") {
                            shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        } else {
                            raw
                        }
                        inValue = false
                    }
                    inInline -> inline.append(parser.text.orEmpty())
                }
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "t" -> inInline = false
                    "c" -> if (cellType == "inlineStr") cells += inline.toString()
                    "row" -> if (cells.isNotEmpty() && cells.size <= 30) rows += cells.toList()
                }
            }
            parser.next()
        }
        return rows
    }

    private fun sharedStrings(xml: ByteArray?): List<String> {
        if (xml == null || xml.isEmpty()) return emptyList()
        val parser = parser(xml)
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var inText = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "si" -> current.clear()
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) current.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "t" -> inText = false
                    "si" -> items += current.toString()
                }
            }
            parser.next()
        }
        return items
    }

    private fun spreadsheetMlRows(bytes: ByteArray): List<List<String>> {
        val parser = parser(bytes)
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        var inData = false
        val data = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT && rows.size < 200) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "Row" -> cells = mutableListOf()
                    "Data", "data" -> {
                        inData = true
                        data.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inData) data.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "Data", "data" -> {
                        cells += data.toString()
                        inData = false
                    }
                    "Row" -> if (cells.isNotEmpty() && cells.size <= 30) rows += cells.toList()
                }
            }
            parser.next()
        }
        return rows
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

    private fun localName(raw: String?): String = raw?.substringAfter(':') ?: ""
}
