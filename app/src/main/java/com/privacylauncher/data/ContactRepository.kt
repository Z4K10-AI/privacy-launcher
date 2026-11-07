package com.privacylauncher.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One contact matching a search. Nothing but a name and a way back to it. */
data class ContactMatch(val lookupKey: String, val contactId: Long, val name: String) {
    val id: String get() = "c:$lookupKey"
}

/**
 * Searching the phone's own contacts, if and only if the user has asked for
 * it and granted the permission.
 *
 * This is the only permission the launcher declares, and it is worth being
 * precise about the shape of it:
 *
 *  - Nothing requests it at startup, on first run, or when the drawer opens.
 *    The only thing that triggers the request is the user turning contact
 *    search on, either from the row in the drawer or the switch in Settings.
 *  - The query goes through [ContactsContract.Contacts.CONTENT_FILTER_URI],
 *    so the matching happens inside the contacts provider. This app never
 *    reads the contact list, only the handful of rows that match what was
 *    typed.
 *  - The projection is a name and a lookup key. No numbers, no addresses, no
 *    email, no photos, no groups.
 *  - Nothing is cached, counted, indexed or written anywhere. Results live in
 *    memory for as long as the search text does.
 *  - Tapping a result hands the lookup URI to whichever contacts app is
 *    installed. This app does not display contact details itself.
 *
 * The launcher is fully usable with this switched off, which is the default.
 */
class ContactRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = MAX_RESULTS): List<ContactMatch> {
        if (query.isBlank() || !hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_FILTER_URI,
                Uri.encode(query.trim()),
            )
            val results = mutableListOf<ContactMatch>()
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ),
                    null,
                    null,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    val keyColumn =
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameColumn = cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    )
                    while (cursor.moveToNext() && results.size < limit) {
                        val name = cursor.getString(nameColumn) ?: continue
                        val key = cursor.getString(keyColumn) ?: continue
                        results += ContactMatch(key, cursor.getLong(idColumn), name)
                    }
                }
            }
            // A provider that is missing, locked or throwing is not worth
            // crashing the home screen over. No results is the right answer.
            results
        }
    }

    /** Hands the contact to whichever contacts app is installed. */
    fun open(match: ContactMatch): Boolean {
        val uri = ContactsContract.Contacts.getLookupUri(match.contactId, match.lookupKey)
            ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    companion object {
        const val PERMISSION = Manifest.permission.READ_CONTACTS

        /** Contacts are a sideshow in an app drawer, not the main list. */
        const val MAX_RESULTS = 4
    }
}
