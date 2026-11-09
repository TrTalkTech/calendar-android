package com.example.calendar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.calendar.R
import com.example.calendar.data.EventIcs
import com.example.calendar.data.EventIcsContent
import java.io.File

object EventForward {
    fun share(
        context: Context,
        content: EventIcsContent,
        body: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val ics = EventIcs.build(content, nowEpochMillis)
        val subject = content.title.ifBlank { context.getString(R.string.event_no_title) }
        val file = File(context.cacheDir, "calendar2-${content.eventId}.ics")
        val attached = runCatching {
            file.writeText(ics)
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }.getOrNull()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (attached != null) "text/calendar" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (attached != null) {
                putExtra(Intent.EXTRA_STREAM, attached)
                clipData = ClipData.newUri(context.contentResolver, subject, attached)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val launched = runCatching {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.event_forward)),
            )
        }.isSuccess
        if (!launched) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(subject, body))
            Toast.makeText(
                context,
                context.getString(R.string.event_forward_copied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
