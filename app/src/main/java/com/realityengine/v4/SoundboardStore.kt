package com.realityengine.v4

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persistent soundboard metadata. Audio bytes stay in the user's selected document provider. */
class SoundboardStore(context: Context) {
    data class Entry(
        val id: String,
        val name: String,
        val uri: Uri,
        val addedAtMs: Long,
    )

    private val prefs = context.applicationContext.getSharedPreferences("call_soundboard", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<Entry> = read().sortedBy { it.name.lowercase() }

    @Synchronized
    fun add(uri: Uri, displayName: String): Entry {
        val current = read().toMutableList()
        val name = cleanName(displayName).ifBlank { "Sound ${current.size + 1}" }
        val entry = Entry(UUID.randomUUID().toString(), name, uri, System.currentTimeMillis())
        current.removeAll { it.uri == uri }
        current += entry
        write(current)
        return entry
    }

    @Synchronized
    fun rename(id: String, newName: String): Boolean {
        val clean = cleanName(newName)
        if (clean.isBlank()) return false
        val current = read().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return false
        current[index] = current[index].copy(name = clean)
        write(current)
        return true
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val current = read().toMutableList()
        val changed = current.removeAll { it.id == id }
        if (changed) write(current)
        return changed
    }

    fun count(): Int = all().size

    private fun read(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val name = o.optString("name")
                    val uri = o.optString("uri")
                    if (id.isBlank() || name.isBlank() || uri.isBlank()) continue
                    add(Entry(id, name, Uri.parse(uri), o.optLong("added", 0L)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("uri", entry.uri.toString())
                put("added", entry.addedAtMs)
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun cleanName(value: String): String = value.trim().replace(Regex("\\s+"), " ").take(60)

    companion object {
        private const val KEY = "entries"
    }
}
