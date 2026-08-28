package com.realityengine.v4

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract

/** Safe duplicate detection plus Android contact aggregation for confirmed duplicate groups. */
class ContactDuplicateManager(private val context: Context) {
    data class Result(val success: Boolean, val message: String = "")

    fun findDuplicateGroups(contacts: List<ContactResolver.Contact>): List<List<ContactResolver.Contact>> {
        val byNumber = LinkedHashMap<String, MutableList<ContactResolver.Contact>>()
        contacts.forEach { contact ->
            val key = normalize(contact.number)
            if (key.isNotBlank()) byNumber.getOrPut(key) { mutableListOf() } += contact
        }
        return byNumber.values
            .map { group -> group.distinctBy { it.contactId } }
            .filter { it.size > 1 }
            .sortedByDescending { it.size }
    }

    fun merge(group: List<ContactResolver.Contact>): Result {
        val contactIds = group.map { it.contactId }.filter { it >= 0L }.distinct()
        if (contactIds.size < 2) return Result(false, "Need at least two real contacts to merge")
        return try {
            val placeholders = contactIds.joinToString(",") { "?" }
            val rawIds = mutableListOf<Long>()
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} IN ($placeholders)",
                contactIds.map(Long::toString).toTypedArray(),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
                while (cursor.moveToNext()) rawIds += cursor.getLong(idIndex)
            }
            if (rawIds.size < 2) return Result(false, "Could not resolve raw contacts for merge")

            val ops = arrayListOf<ContentProviderOperation>()
            for (i in 0 until rawIds.lastIndex) {
                for (j in i + 1 until rawIds.size) {
                    ops += ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                        .withValue(
                            ContactsContract.AggregationExceptions.TYPE,
                            ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER,
                        )
                        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawIds[i])
                        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, rawIds[j])
                        .build()
                }
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Result(true)
        } catch (t: Throwable) {
            Result(false, t.message ?: "Could not merge duplicate contacts")
        }
    }

    private fun normalize(number: String): String {
        val digits = number.filter(Char::isDigit)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}
