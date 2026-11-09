package com.example.calendar.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class EventAttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val filesRoot = File(appContext.filesDir, DIR_EVENT)
    private val pendingRoot = File(appContext.cacheDir, DIR_PENDING)
    private val openCache = File(appContext.cacheDir, DIR_OPEN)

    fun list(eventId: Long): List<EventAttachment> {
        val local = readIndex(eventId).mapNotNull { record ->
            val file = File(eventDir(eventId), record.fileName)
            if (!file.isFile) return@mapNotNull null
            EventAttachment(
                id = record.id,
                displayName = record.displayName,
                mimeType = record.mimeType,
                localFile = file,
                source = EventAttachment.Source.LOCAL,
            )
        }
        val seen = local.map { it.displayName.lowercase() }.toMutableSet()
        val provider = queryProviderAttachments(eventId).filter { attachment ->
            seen.add(attachment.displayName.lowercase())
        }
        return local + provider
    }

    fun importFromPicker(uri: Uri): ImportResult {
        val displayName = queryDisplayName(uri) ?: "Файл"
        val mime = DocumentKinds.mimeOf(displayName, resolver.getType(uri))
        pendingRoot.mkdirs()
        val id = UUID.randomUUID().toString()
        val dest = File(pendingRoot, id + "_" + sanitize(displayName))
        return try {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        written += read
                        if (written > MAX_BYTES) {
                            dest.delete()
                            return ImportResult.TooLarge
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return ImportResult.Failed
            if (!dest.isFile || dest.length() <= 0L) {
                dest.delete()
                return ImportResult.Failed
            }
            ImportResult.Ok(
                EventAttachment(
                    id = id,
                    displayName = displayName,
                    mimeType = mime,
                    localFile = dest,
                    source = EventAttachment.Source.LOCAL,
                ),
            )
        } catch (e: Exception) {
            dest.delete()
            Log.e(TAG, "importFromPicker failed", e)
            ImportResult.Failed
        }
    }

    fun attachToEvent(eventId: Long, attachment: EventAttachment): EventAttachment? {
        val source = attachment.localFile?.takeIf { it.isFile } ?: return null
        val dir = eventDir(eventId)
        dir.mkdirs()
        val dest = File(dir, attachment.id + "_" + sanitize(attachment.displayName))
        val copied = runCatching {
            if (source.canonicalPath == dest.canonicalPath) {
                true
            } else {
                source.copyTo(dest, overwrite = true)
                if (source.parentFile == pendingRoot) source.delete()
                dest.isFile
            }
        }.getOrDefault(false)
        if (!copied) return null
        val stored = attachment.copy(
            localFile = dest,
            source = EventAttachment.Source.LOCAL,
        )
        val records = readIndex(eventId).filterNot { it.id == stored.id }.toMutableList()
        records += IndexRecord(stored.id, stored.displayName, stored.mimeType, dest.name)
        writeIndex(eventId, records)
        writeExtendedProperty(eventId, stored.displayName)
        return stored
    }

    fun remove(eventId: Long, attachment: EventAttachment) {
        if (attachment.source != EventAttachment.Source.LOCAL) return
        val records = readIndex(eventId)
        val record = records.firstOrNull { it.id == attachment.id }
        if (record != null) {
            File(eventDir(eventId), record.fileName).delete()
            writeIndex(eventId, records.filterNot { it.id == attachment.id })
        }
        attachment.localFile?.takeIf { it.parentFile == pendingRoot }?.delete()
        deleteExtendedProperty(eventId, attachment.displayName)
    }

    fun deleteAll(eventId: Long) {
        val names = readIndex(eventId).map { it.displayName }
        eventDir(eventId).deleteRecursively()
        writeIndex(eventId, emptyList())
        names.forEach { deleteExtendedProperty(eventId, it) }
    }

    fun searchEventIds(needle: String): Set<Long> {
        if (needle.isBlank()) return emptySet()
        val ids = linkedSetOf<Long>()
        prefs.all.forEach { (key, value) ->
            val eventId = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@forEach
            val raw = value as? String ?: return@forEach
            if (raw.contains(needle, ignoreCase = true)) ids.add(eventId)
        }
        return ids
    }

    fun ensureLocalFile(attachment: EventAttachment): File? {
        attachment.localFile?.takeIf { it.isFile }?.let { return it }
        val uri = attachment.contentUri ?: return null
        openCache.mkdirs()
        val dest = File(openCache, attachment.id + "_" + sanitize(attachment.displayName))
        if (dest.isFile && dest.length() > 0L) return dest
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
    }

    fun openExternally(context: Context, attachment: EventAttachment): Boolean {
        val file = ensureLocalFile(attachment) ?: return false
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, attachment.displayName))
        }.isSuccess
    }

    private fun eventDir(eventId: Long): File = File(filesRoot, eventId.toString())

    private fun queryDisplayName(uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getStringOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun queryProviderAttachments(eventId: Long): List<EventAttachment> {
        return runCatching {
            resolver.query(CALENDAR_ATTACHMENTS_URI, null, null, null, null)?.use { cursor ->
                val eventIdx = cursor.columnIndexIgnoreCase("event_id", "eventId")
                if (eventIdx < 0) return@use emptyList()
                val nameIdx = cursor.columnIndexIgnoreCase("filename", "fileName", "title", "name", "displayName")
                val uriIdx = cursor.columnIndexIgnoreCase("uri", "url", "filenameUrl", "data")
                val mimeIdx = cursor.columnIndexIgnoreCase("mimetype", "mimeType", "mime")
                val idIdx = cursor.columnIndexIgnoreCase("_id", "id")
                val result = mutableListOf<EventAttachment>()
                while (cursor.moveToNext()) {
                    if (cursor.getLongOrNull(eventIdx) != eventId) continue
                    val name = (if (nameIdx >= 0) cursor.getStringOrNull(nameIdx) else null)
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "Файл" }
                    val rawUri = if (uriIdx >= 0) cursor.getStringOrNull(uriIdx).orEmpty() else ""
                    val contentUri = rawUri.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    val mime = DocumentKinds.mimeOf(
                        name,
                        if (mimeIdx >= 0) cursor.getStringOrNull(mimeIdx) else null,
                    )
                    val rowId = if (idIdx >= 0) cursor.getLongOrNull(idIdx)?.toString() else null
                    result += EventAttachment(
                        id = "provider-" + (rowId ?: name),
                        displayName = name,
                        mimeType = mime,
                        contentUri = contentUri,
                        source = EventAttachment.Source.PROVIDER,
                    )
                }
                result
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun readIndex(eventId: Long): List<IndexRecord> {
        val raw = prefs.getString(KEY_PREFIX + eventId, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val fileName = obj.optString("file")
                    if (id.isBlank() || fileName.isBlank()) continue
                    add(
                        IndexRecord(
                            id = id,
                            displayName = obj.optString("name").ifBlank { "Файл" },
                            mimeType = obj.optString("mime").ifBlank { "application/octet-stream" },
                            fileName = fileName,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(eventId: Long, records: List<IndexRecord>) {
        val editor = prefs.edit()
        if (records.isEmpty()) {
            editor.remove(KEY_PREFIX + eventId)
        } else {
            val array = JSONArray()
            records.forEach { record ->
                array.put(
                    JSONObject()
                        .put("id", record.id)
                        .put("name", record.displayName)
                        .put("mime", record.mimeType)
                        .put("file", record.fileName),
                )
            }
            editor.putString(KEY_PREFIX + eventId, array.toString())
        }
        editor.commit()
    }

    private fun writeExtendedProperty(eventId: Long, displayName: String) {
        runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.ExtendedProperties.EVENT_ID, eventId)
                put(CalendarContract.ExtendedProperties.NAME, PROPERTY_NAME)
                put(CalendarContract.ExtendedProperties.VALUE, displayName)
            }
            resolver.insert(CalendarContract.ExtendedProperties.CONTENT_URI, values)
        }
    }

    private fun deleteExtendedProperty(eventId: Long, displayName: String) {
        runCatching {
            resolver.delete(
                CalendarContract.ExtendedProperties.CONTENT_URI,
                "${CalendarContract.ExtendedProperties.EVENT_ID}=? AND " +
                    "${CalendarContract.ExtendedProperties.NAME}=? AND " +
                    "${CalendarContract.ExtendedProperties.VALUE}=?",
                arrayOf(eventId.toString(), PROPERTY_NAME, displayName),
            )
        }
    }

    private fun Cursor.columnIndexIgnoreCase(vararg names: String): Int {
        val wanted = names.map { it.lowercase() }.toSet()
        return columnNames.indexOfFirst { it.lowercase() in wanted }
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').ifBlank { "file" }
        return base.replace(UNSAFE_NAME, "_").take(80).ifBlank { "file" }
    }

    sealed interface ImportResult {
        data class Ok(val attachment: EventAttachment) : ImportResult
        data object TooLarge : ImportResult
        data object Failed : ImportResult
    }

    private data class IndexRecord(
        val id: String,
        val displayName: String,
        val mimeType: String,
        val fileName: String,
    )

    companion object {
        private const val TAG = "EventAttachments"
        private const val PREFS_NAME = "calendar_event_attachments"
        private const val KEY_PREFIX = "event_"
        private const val DIR_EVENT = "event-attachments"
        private const val DIR_PENDING = "pending-attachments"
        private const val DIR_OPEN = "open-attachments"
        private const val PROPERTY_NAME = "ATTACHMENT"
        private const val DEFAULT_BUFFER = 16 * 1024
        const val MAX_BYTES = 50L * 1024 * 1024
        private val CALENDAR_ATTACHMENTS_URI = Uri.parse("content://com.android.calendar/attachments")
        private val UNSAFE_NAME = Regex("[^\\p{L}\\p{N}._\\- ]")
    }
}
