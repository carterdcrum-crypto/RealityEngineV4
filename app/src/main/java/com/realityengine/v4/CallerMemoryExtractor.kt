package com.realityengine.v4

import android.content.Context
import java.util.Locale

/** Token-free extraction of explicit caller preferences/facts into the persistent profile. */
class CallerMemoryExtractor(context: Context) {
    private val profiles = CallerProfileStore(context.applicationContext)

    fun observe(phoneNumber: String, transcript: String) {
        if (phoneNumber.isBlank() || transcript.isBlank()) return
        val clean = transcript.trim().replace(Regex("\\s+"), " ")
        val lower = clean.lowercase(Locale.US)
        profiles.update(phoneNumber) { p ->
            extractAfter(lower, clean, listOf("i like ", "i love ", "i enjoy "))?.let { add(p.likes, it) }
            extractAfter(lower, clean, listOf("i don't like ", "i dislike ", "i hate "))?.let { add(p.dislikes, it) }
            extractAfter(lower, clean, listOf("i prefer ", "i'd rather "))?.let { add(p.importantFacts, "Preference: $it") }
            extractAfter(lower, clean, listOf("my favorite "))?.let { add(p.importantFacts, "Favorite: $it") }
            extractAfter(lower, clean, listOf("i work at ", "i work for ", "i live in ", "i live at ", "my job is "))?.let { add(p.importantFacts, it) }
            if (clean.length in 12..140) add(p.topics, topic(clean))
        }
    }

    private fun extractAfter(lower: String, original: String, prefixes: List<String>): String? {
        for (prefix in prefixes) {
            val index = lower.indexOf(prefix)
            if (index >= 0) {
                val start = index + prefix.length
                val value = original.substring(start)
                    .substringBefore('.')
                    .substringBefore('?')
                    .substringBefore('!')
                    .trim { it == ' ' || it == ',' || it == ';' || it == ':' }
                if (value.length in 2..120) return value
            }
        }
        return null
    }

    private fun topic(value: String): String = value.take(120)
    private fun add(list: MutableList<String>, value: String) { if (list.none { it.equals(value, true) }) list.add(value) }
}
