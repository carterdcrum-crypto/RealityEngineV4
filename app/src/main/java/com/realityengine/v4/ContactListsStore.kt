package com.realityengine.v4

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reality Engine-local contact lists/labels.
 *
 * Android contact-group APIs are account-provider specific and can behave differently across
 * Google/Samsung/local accounts. These lists intentionally stay local to Reality Engine so they
 * work consistently while still referencing the user's real system contacts by normalized number.
 */
class ContactListsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun names(): List<String> = read().keys().asSequence().toList().sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun create(name: String): Boolean {
        val clean = cleanName(name)
        if (clean.isBlank()) return false
        val root = read()
        if (root.has(clean)) return false
        root.put(clean, JSONArray())
        write(root)
        return true
    }

    fun delete(name: String): Boolean {
        val root = read()
        if (!root.has(name)) return false
        root.remove(name)
        write(root)
        return true
    }

    fun contains(name: String, number: String): Boolean {
        val key = normalize(number)
        if (key.isBlank()) return false
        val members = read().optJSONArray(name) ?: return false
        for (i in 0 until members.length()) {
            if (members.optString(i) == key) return true
        }
        return false
    }

    fun listsFor(number: String): List<String> {
        val root = read()
        val key = normalize(number)
        if (key.isBlank()) return emptyList()
        return root.keys().asSequence()
            .filter { name ->
                val members = root.optJSONArray(name) ?: return@filter false
                (0 until members.length()).any { members.optString(it) == key }
            }
            .toList()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun setMember(name: String, number: String, member: Boolean): Boolean {
        val cleanName = cleanName(name)
        val key = normalize(number)
        if (cleanName.isBlank() || key.isBlank()) return false
        val root = read()
        val current = root.optJSONArray(cleanName) ?: JSONArray()
        val values = LinkedHashSet<String>()
        for (i in 0 until current.length()) current.optString(i).takeIf { it.isNotBlank() }?.let(values::add)
        if (member) values += key else values -= key
        root.put(cleanName, JSONArray(values.toList()))
        write(root)
        return true
    }

    fun filter(name: String, contacts: List<ContactResolver.Contact>): List<ContactResolver.Contact> =
        contacts.filter { contains(name, it.number) }

    private fun read(): JSONObject = runCatching {
        JSONObject(prefs.getString(KEY_JSON, "{}") ?: "{}")
    }.getOrElse { JSONObject() }

    private fun write(root: JSONObject) {
        prefs.edit().putString(KEY_JSON, root.toString()).apply()
    }

    private fun cleanName(value: String): String = value.trim().replace(Regex("\\s+"), " ").take(40)

    private fun normalize(number: String): String {
        val digits = number.filter(Char::isDigit)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    companion object {
        private const val PREFS = "contact_index_ui"
        private const val KEY_JSON = "contact_lists_json_v1"
    }
}
