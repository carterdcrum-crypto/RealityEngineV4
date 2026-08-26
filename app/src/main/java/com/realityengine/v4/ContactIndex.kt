package com.realityengine.v4

import android.content.Context

/**
 * Contact-domain layer kept outside MainActivity so loading, filtering and
 * presentation rules do not grow the activity.
 */
class ContactIndex(private val context: Context) {
    data class Section(val label: String, val contacts: List<ContactResolver.Contact>)

    fun resolveName(number: String): String? = ContactResolver.resolveName(context, number)

    fun all(): List<ContactResolver.Contact> = ContactResolver.allContacts(context)
        .distinctBy { it.contactId to normalizeNumber(it.number) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.number } })

    fun search(query: String): List<ContactResolver.Contact> {
        val needle = query.trim()
        if (needle.isEmpty()) return all()
        val digits = normalizeNumber(needle)
        return all().filter { contact ->
            contact.name.contains(needle, ignoreCase = true) ||
                (digits.isNotEmpty() && normalizeNumber(contact.number).contains(digits))
        }
    }

    /** Alphabetical sections for the modern Index UI. Numbers/blank names live under #. */
    fun sections(query: String = ""): List<Section> = search(query)
        .groupBy { sectionLabel(it) }
        .toSortedMap(sectionComparator)
        .map { (label, contacts) -> Section(label, contacts) }

    private fun sectionLabel(contact: ContactResolver.Contact): String {
        val first = contact.name.trim().firstOrNull()?.uppercaseChar()
        return if (first != null && first.isLetter()) first.toString() else "#"
    }

    private fun normalizeNumber(value: String): String = value.filter(Char::isDigit)

    private val sectionComparator = Comparator<String> { left, right ->
        when {
            left == right -> 0
            left == "#" -> 1
            right == "#" -> -1
            else -> left.compareTo(right)
        }
    }
}
