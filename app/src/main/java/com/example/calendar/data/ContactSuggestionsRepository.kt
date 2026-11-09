package com.example.calendar.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class ContactSuggestionsRepository(
    private val contentResolver: ContentResolver,
) {
    private var corporateDirectoriesCache: List<CorporateDirectory>? = null

    fun lookupByEmail(email: String): ContactInviteSuggestion? {
        val normalized = email.trim()
        if (normalized.isEmpty()) return null
        lookupByEmailFilterUri(normalized)?.let { return it }
        return lookupByEmailExactDataQuery(normalized)
    }

    private fun lookupByEmailFilterUri(email: String): ContactInviteSuggestion? {
        val filterUri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
            Uri.encode(email),
        )
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.LOOKUP_KEY,
        )
        return contentResolver.query(filterUri, projection, null, null, null)?.use { cursor ->
            val contactIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LOOKUP_KEY)
            if (contactIdx < 0 || emailIdx < 0) return@use null
            while (cursor.moveToNext()) {
                val address = cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                if (!address.equals(email, ignoreCase = true)) continue
                val contactId = cursor.getLong(contactIdx)
                val lookupKey = if (lookupIdx >= 0) cursor.getStringOrNull(lookupIdx) else null
                val displayName = resolveDisplayName(contactId, lookupKey)
                if (displayName.isEmpty() || displayName.equals(address, ignoreCase = true)) continue
                return@use ContactInviteSuggestion(
                    contactId = contactId,
                    displayName = displayName,
                    email = address,
                )
            }
            null
        }
    }

    private fun lookupByEmailExactDataQuery(email: String): ContactInviteSuggestion? {
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND " +
            "(${ContactsContract.CommonDataKinds.Email.ADDRESS} = ? OR " +
            "${ContactsContract.CommonDataKinds.Email.ADDRESS} = ?)"
        val args = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            email,
            email.lowercase(Locale.ROOT),
        )
        return contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.Data.LOOKUP_KEY,
            ),
            selection,
            args,
            null,
        )?.use { cursor ->
            val contactIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
            if (contactIdx < 0 || emailIdx < 0 || !cursor.moveToFirst()) return@use null
            val contactId = cursor.getLong(contactIdx)
            val resolvedEmail = cursor.getStringOrNull(emailIdx)?.trim().orEmpty().ifEmpty { email }
            val lookupKey = if (lookupIdx >= 0) cursor.getStringOrNull(lookupIdx) else null
            val displayName = resolveDisplayName(contactId, lookupKey)
            if (displayName.isEmpty() || displayName.equals(resolvedEmail, ignoreCase = true)) {
                return@use null
            }
            ContactInviteSuggestion(
                contactId = contactId,
                displayName = displayName,
                email = resolvedEmail,
            )
        }
    }

    fun search(query: String, limit: Int = 8): List<ContactInviteSuggestion> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<ContactInviteSuggestion>()
        searchByContactName(normalized, limit, seen, results)
        if (results.size < limit) {
            searchByEmail(normalized, limit, seen, results)
        }
        if (results.size < limit) {
            searchByPhone(normalized, limit, seen, results)
        }
        return results
    }

    private fun searchByContactName(
        query: String,
        limit: Int,
        seen: MutableSet<String>,
        results: MutableList<ContactInviteSuggestion>,
    ) {
        val uri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_FILTER_URI,
            Uri.encode(query),
        )
        contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            if (idIdx < 0) return
            while (cursor.moveToNext() && results.size < limit) {
                val contactId = cursor.getLong(idIdx)
                val lookupKey = if (lookupIdx >= 0) cursor.getStringOrNull(lookupIdx) else null
                addEmailsForContact(contactId, lookupKey, limit, seen, results)
            }
        }
    }

    private fun searchByEmail(
        query: String,
        limit: Int,
        seen: MutableSet<String>,
        results: MutableList<ContactInviteSuggestion>,
    ) {
        val selection = "${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?"
        val args = arrayOf("%$query%")
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.LOOKUP_KEY,
            ),
            selection,
            args,
            null,
        )?.use { cursor ->
            val contactIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LOOKUP_KEY)
            if (contactIdx < 0 || emailIdx < 0) return
            while (cursor.moveToNext() && results.size < limit) {
                val contactId = cursor.getLong(contactIdx)
                val email = cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                if (email.isEmpty()) continue
                val lookupKey = if (lookupIdx >= 0) cursor.getStringOrNull(lookupIdx) else null
                addSuggestion(
                    contactId = contactId,
                    lookupKey = lookupKey,
                    email = email,
                    limit = limit,
                    seen = seen,
                    results = results,
                )
            }
        }
    }

    private fun searchByPhone(
        query: String,
        limit: Int,
        seen: MutableSet<String>,
        results: MutableList<ContactInviteSuggestion>,
    ) {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(query),
        )
        contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.PhoneLookup.NUMBER,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
            val numberIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
            if (idIdx < 0) return
            while (cursor.moveToNext() && results.size < limit) {
                val contactId = cursor.getLong(idIdx)
                val phone = if (numberIdx >= 0) cursor.getStringOrNull(numberIdx)?.trim() else null
                val lookupKey = if (lookupIdx >= 0) cursor.getStringOrNull(lookupIdx) else null
                addEmailsForContact(contactId, lookupKey, limit, seen, results, phone)
            }
        }
    }

    private fun addEmailsForContact(
        contactId: Long,
        lookupKey: String?,
        limit: Int,
        seen: MutableSet<String>,
        results: MutableList<ContactInviteSuggestion>,
        phone: String? = null,
    ) {
        val selection = "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?"
        val args = arrayOf(contactId.toString())
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC",
        )?.use { cursor ->
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            if (emailIdx < 0) return
            while (cursor.moveToNext() && results.size < limit) {
                val email = cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                if (email.isEmpty()) continue
                addSuggestion(contactId, lookupKey, email, phone, limit, seen, results)
            }
        }
    }

    private fun addSuggestion(
        contactId: Long,
        lookupKey: String?,
        email: String,
        phone: String? = null,
        limit: Int,
        seen: MutableSet<String>,
        results: MutableList<ContactInviteSuggestion>,
    ) {
        if (results.size >= limit) return
        val key = "$contactId:$email"
        if (!seen.add(key)) return
        val displayName = resolveDisplayName(contactId, lookupKey)
        results += ContactInviteSuggestion(
            contactId = contactId,
            displayName = displayName,
            email = email,
            phone = phone,
        )
    }

    private fun resolveDisplayName(contactId: Long, lookupKey: String?): String {
        readStructuredName(contactId)?.let { return it }
        val uri = ContactsContract.Contacts.CONTENT_URI
        val selection = "${ContactsContract.Contacts._ID} = ?"
        val args = arrayOf(contactId.toString())
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                if (idx >= 0) {
                    return cursor.getStringOrNull(idx)?.trim().orEmpty()
                }
            }
        }
        if (!lookupKey.isNullOrBlank()) {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                lookupKey,
            )
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    if (idx >= 0) {
                        return cursor.getStringOrNull(idx)?.trim().orEmpty()
                    }
                }
            }
        }
        return ""
    }

    private fun readStructuredName(contactId: Long): String? {
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND " +
            "${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(
            contactId.toString(),
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        )
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ),
            selection,
            args,
            null,
        )?.use { cursor ->
            val givenIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
            val familyIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
            if (givenIdx < 0 && familyIdx < 0) return null
            if (cursor.moveToFirst()) {
                val given = if (givenIdx >= 0) cursor.getStringOrNull(givenIdx)?.trim().orEmpty() else ""
                val family = if (familyIdx >= 0) cursor.getStringOrNull(familyIdx)?.trim().orEmpty() else ""
                val formatted = listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
                if (formatted.isNotEmpty()) return formatted
            }
        }
        return null
    }

    /**
     * Люди из каталогов учёток на телефоне (корпоративная книга Exchange, Workspace и т.п.).
     * Локальная телефонная книга сюда не входит: её уже отдал [search].
     */
    suspend fun searchCorporateDirectory(
        query: String,
        fallbackLabel: String,
        limit: Int = 8,
    ): List<ContactInviteSuggestion> {
        val normalized = query.trim()
        if (normalized.length < 2 || limit <= 0) return emptyList()
        val results = mutableListOf<ContactInviteSuggestion>()
        val seenEmails = LinkedHashSet<String>()
        withTimeoutOrNull(CORPORATE_SEARCH_TIMEOUT_MS) {
            val directories = corporateDirectories()
            for (directory in directories) {
                if (results.size >= limit) break
                ignoringCancellation {
                    queryEmailFilter(
                        query = normalized,
                        uri = directoryFilterUri(normalized, directory.id),
                        directoryLabel = directory.label(fallbackLabel),
                        limit = limit,
                        seenEmails = seenEmails,
                        results = results,
                    )
                }
            }
            if (results.size < limit && directories.isEmpty()) {
                val localEmails = ignoringCancellation { emailsInDefaultDirectory(normalized) }.orEmpty()
                seenEmails.addAll(localEmails)
                val label = fallbackLabel.trim().ifBlank { fallbackLabel }
                ignoringCancellation {
                    queryEmailFilter(
                        query = normalized,
                        uri = Uri.withAppendedPath(
                            ContactsContract.CommonDataKinds.Email.ENTERPRISE_CONTENT_FILTER_URI,
                            Uri.encode(normalized),
                        ),
                        directoryLabel = label,
                        limit = limit,
                        seenEmails = seenEmails,
                        results = results,
                    )
                }
            }
        }
        return results
    }

    private fun corporateDirectories(): List<CorporateDirectory> {
        corporateDirectoriesCache?.let { return it }
        val loaded = ignoringCancellation { readCorporateDirectories() } ?: return emptyList()
        corporateDirectoriesCache = loaded
        return loaded
    }

    private fun readCorporateDirectories(): List<CorporateDirectory> {
        val projection = arrayOf(
            ContactsContract.Directory._ID,
            ContactsContract.Directory.DISPLAY_NAME,
            ContactsContract.Directory.ACCOUNT_NAME,
        )
        val found = mutableListOf<CorporateDirectory>()
        contentResolver.query(
            ContactsContract.Directory.ENTERPRISE_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Directory._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Directory.DISPLAY_NAME)
            val accountIdx = cursor.getColumnIndex(ContactsContract.Directory.ACCOUNT_NAME)
            if (idIdx < 0) return found
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (id == ContactsContract.Directory.DEFAULT ||
                    id == ContactsContract.Directory.LOCAL_INVISIBLE
                ) {
                    continue
                }
                found += CorporateDirectory(
                    id = id,
                    displayName = if (nameIdx >= 0) cursor.getStringOrNull(nameIdx)?.trim().orEmpty() else "",
                    accountName = if (accountIdx >= 0) cursor.getStringOrNull(accountIdx)?.trim().orEmpty() else "",
                )
            }
        }
        return found
    }

    private fun directoryFilterUri(query: String, directoryId: Long): Uri {
        return Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
            Uri.encode(query),
        ).buildUpon()
            .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, directoryId.toString())
            .build()
    }

    private fun emailsInDefaultDirectory(query: String): Set<String> {
        val emails = LinkedHashSet<String>()
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
            Uri.encode(query),
        )
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            null,
            null,
            null,
        )?.use { cursor ->
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            if (emailIdx < 0) return emails
            while (cursor.moveToNext()) {
                val email = cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                if (email.isNotEmpty()) emails += email.lowercase(Locale.ROOT)
            }
        }
        return emails
    }

    private fun queryEmailFilter(
        query: String,
        uri: Uri,
        directoryLabel: String,
        limit: Int,
        seenEmails: MutableSet<String>,
        results: MutableList<ContactInviteSuggestion>,
    ) {
        if (query.isBlank() || results.size >= limit) return
        // null-проекция: каталог сам решает, какие колонки отдать (полное имя и фото).
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val primaryIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val altIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE)
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val thumbIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            if (emailIdx < 0) return
            while (cursor.moveToNext() && results.size < limit) {
                val email = cursor.getStringOrNull(emailIdx)?.trim().orEmpty()
                if (email.isEmpty()) continue
                if (!seenEmails.add(email.lowercase(Locale.ROOT))) continue
                val displayName = fullestPersonName(
                    names = listOf(primaryIdx, nameIdx, altIdx).map { idx ->
                        if (idx < 0) null else cursor.getStringOrNull(idx)
                    },
                    email = email,
                ) ?: email
                val contactId = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else 0L
                val photoUri = sequenceOf(thumbIdx, photoIdx)
                    .mapNotNull { idx ->
                        if (idx < 0) null else cursor.getStringOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    .firstOrNull()
                val lookupKey = if (lookupIdx >= 0) {
                    cursor.getStringOrNull(lookupIdx)?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                results += ContactInviteSuggestion(
                    contactId = contactId,
                    displayName = displayName,
                    email = email,
                    directoryName = directoryLabel,
                    photoUri = photoUri,
                    lookupKey = lookupKey,
                )
            }
        }
    }

    fun loadSuggestionPhoto(suggestion: ContactInviteSuggestion): Bitmap? {
        if (suggestion.directoryName.isNullOrBlank()) return null
        suggestion.photoUri?.let { raw ->
            decodePhotoUri(Uri.parse(raw))?.let { return it }
        }
        val lookupKey = suggestion.lookupKey
        if (!lookupKey.isNullOrBlank()) {
            val fromLookup = ignoringCancellation {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                    lookupKey,
                )
                val contactUri = ContactsContract.Contacts.lookupContact(contentResolver, lookupUri)
                    ?: return@ignoringCancellation null
                ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, contactUri, true)
                    ?.use { BitmapFactory.decodeStream(it) }
            }
            if (fromLookup != null) return fromLookup
        }
        if (suggestion.contactId > 0L) {
            val fromId = ignoringCancellation {
                val contactUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_URI,
                    suggestion.contactId.toString(),
                )
                ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, contactUri, true)
                    ?.use { BitmapFactory.decodeStream(it) }
            }
            if (fromId != null) return fromId
        }
        return null
    }

    private fun decodePhotoUri(uri: Uri): Bitmap? {
        return ignoringCancellation {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }

    private fun fullestPersonName(names: List<String?>, email: String): String? {
        val candidates = names.mapNotNull { raw ->
            raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals(email, ignoreCase = true) }
        }
        if (candidates.isEmpty()) return null
        return candidates.maxWith(
            compareBy<String> { name -> name.split(Regex("\\s+")).count { it.isNotEmpty() } }
                .thenBy { it.length },
        )
    }

    private data class CorporateDirectory(
        val id: Long,
        val displayName: String,
        val accountName: String,
    ) {
        fun label(fallback: String): String =
            displayName.ifBlank { accountName }.ifBlank { fallback }
    }

    companion object {
        private const val CORPORATE_SEARCH_TIMEOUT_MS = 4_000L

        fun mergeInviteSuggestions(
            local: List<ContactInviteSuggestion>,
            corporate: List<ContactInviteSuggestion>,
        ): List<ContactInviteSuggestion> {
            if (corporate.isEmpty()) return local
            val seen = local.mapTo(HashSet()) { it.email.lowercase(Locale.ROOT) }
            return local + corporate.filter { seen.add(it.email.lowercase(Locale.ROOT)) }
        }

        private inline fun <T> ignoringCancellation(block: () -> T): T? {
            return try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
}
