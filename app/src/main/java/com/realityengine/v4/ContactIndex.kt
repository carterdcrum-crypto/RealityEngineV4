package com.realityengine.v4

import android.content.Context

/**
 * Small contact-domain layer kept outside MainActivity so contact loading,
 * searching, and number resolution no longer grow the activity.
 */
class ContactIndex(private val context: Context) {
    fun resolveName(number: String): String? = ContactResolver.resolveName(context, number)

    fun all(): List<ContactResolver.Contact> = ContactResolver.allContacts(context)

    fun search(query: String): List<ContactResolver.Contact> {
        val needle = query.trim()
        if (needle.isEmpty()) return all()
        val digits = needle.filter(Char::isDigit)
        return all().filter { contact ->
            contact.name.contains(needle, ignoreCase = true) ||
                (digits.isNotEmpty() && contact.number.filter(Char::isDigit).contains(digits))
        }
    }
}
