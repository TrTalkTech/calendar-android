package com.example.calendar.data

object DocumentKinds {
    private val officeExtensions = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
    private val spreadsheetExtensions = setOf("xls", "xlsx")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "heic", "heif", "webp", "bmp", "tif", "tiff")
    private val textExtensions = setOf("txt", "md")

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun isPdf(fileName: String): Boolean = extensionOf(fileName) == "pdf"

    fun isOffice(fileName: String): Boolean = extensionOf(fileName) in officeExtensions

    fun isSpreadsheet(fileName: String): Boolean = extensionOf(fileName) in spreadsheetExtensions

    fun isImage(fileName: String): Boolean = extensionOf(fileName) in imageExtensions

    fun isText(fileName: String): Boolean = extensionOf(fileName) in textExtensions

    fun canPreviewInApp(fileName: String): Boolean =
        isPdf(fileName) || isOffice(fileName) || isImage(fileName) || isText(fileName)

    fun followsViewerSetting(fileName: String): Boolean = isPdf(fileName) || isOffice(fileName)

    fun mimeOf(fileName: String, fallback: String?): String {
        val guessed = when (extensionOf(fileName)) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> null
        }
        return guessed ?: fallback?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: "application/octet-stream"
    }
}
