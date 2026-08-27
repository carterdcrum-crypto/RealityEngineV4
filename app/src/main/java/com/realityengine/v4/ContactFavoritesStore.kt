package com.realityengine.v4

import android.content.Context

/** Lightweight local favorites for the contact Index. Phone numbers are normalized before storage. */
class ContactFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFavorite(number: String): Boolean = key(number) in favorites()

    fun toggle(number: String): Boolean {
        val normalized = key(number)
        if (normalized.isBlank()) return false
        val current = favorites().toMutableSet()
        val favoriteNow = if (normalized in current) {
            current.remove(normalized)
            false
        } else {
            current.add(normalized)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return favoriteNow
    }

    fun filter(contacts: List<ContactResolver.Contact>): List<ContactResolver.Contact> {
        val current = favorites()
        return contacts.filter { key(it.number) in current }
    }

    private fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    private fun key(number: String): String {
        val digits = number.filter(Char::isDigit)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    companion object {
        private const val PREFS = "contact_index_ui"
        private const val KEY_FAVORITES = "favorite_numbers"
    }
}
