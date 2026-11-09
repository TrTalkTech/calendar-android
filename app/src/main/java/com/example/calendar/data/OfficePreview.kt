package com.example.calendar.data

import android.content.Context
import app.opendocument.core.Html
import app.opendocument.core.HtmlConfig
import app.opendocument.core.HtmlPage
import app.opendocument.core.HtmlViewportMode
import app.opendocument.core.Odr
import java.io.File

data class OfficePage(val title: String, val path: String)

sealed interface RenderedFile {
    data class Office(val pages: List<OfficePage>) : RenderedFile
}

object OfficePreview {
    fun isOffice(fileName: String): Boolean = DocumentKinds.isOffice(fileName)

    fun render(context: Context, source: File): RenderedFile.Office {
        val directory = File(
            context.cacheDir,
            "office-preview/" + source.absolutePath.hashCode().toUInt().toString(16) + "-" + source.length(),
        )
        val cached = htmlPages(directory)
        if (cached.isNotEmpty()) return RenderedFile.Office(cached)
        directory.delete()
        directory.mkdirs()
        val decoded = Odr.open(source.absolutePath)
        try {
            val config = HtmlConfig()
            config.editable = false
            config.embedImages = true
            config.embedShippedResources = true
            config.textDocumentMargin = true
            config.viewportMode = HtmlViewportMode.FIT_WIDTH
            config.spreadsheetViewportMode = HtmlViewportMode.FIT_WIDTH
            config.keyboardNavigation = false
            config.keyboardShortcuts = false
            val service = Html.translate(decoded, config)
            try {
                val written = service.bringOffline(directory.absolutePath)
                val pages = written.pages().mapNotNull { page ->
                    val file = resolve(directory, page)
                    if (!file.isFile) null else OfficePage(page.name.ifBlank { file.nameWithoutExtension }, file.absolutePath)
                }
                if (pages.isEmpty()) error("В документе не нашлось страниц")
                return RenderedFile.Office(pages)
            } finally {
                service.close()
            }
        } finally {
            decoded.close()
        }
    }

    private fun htmlPages(directory: File): List<OfficePage> {
        if (!directory.isDirectory) return emptyList()
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("html", true) }
            .sortedBy { it.name }
            .map { OfficePage(it.nameWithoutExtension, it.absolutePath) }
            .toList()
    }

    private fun resolve(directory: File, page: HtmlPage): File {
        val raw = File(page.path)
        val located = if (raw.isAbsolute) raw else File(directory, page.path)
        if (located.isFile) return located
        return File(directory, page.name)
    }
}
