package com.example.calendar.data

import android.net.Uri
import java.io.File

data class EventAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val localFile: File? = null,
    val contentUri: Uri? = null,
    val source: Source = Source.LOCAL,
) {
    enum class Source { LOCAL, PROVIDER }
}
