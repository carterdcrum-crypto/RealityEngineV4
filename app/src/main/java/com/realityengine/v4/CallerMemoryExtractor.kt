package com.realityengine.v4

import android.content.Context
import java.util.Locale

/** Fast token-free capture of obvious explicit preferences during a live call; richer memory is learned post-call. */
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
            extractAfter(lower, clean, listOf("i work at ", "i work for ", "i live in ", "my job is "))?.let { add(p.importantFacts, it) }
            inferStyle(lower)?.let { p.preferredConversationStyle = it }
            starter(clean, lower)?.let { add(p.conversationStarters, it) }
        }
    }

    private fun inferStyle(text: String): String? = when {
        listOf("just tell me", "get to the point", "straight answer", "be direct").any(text::contains) -> "Direct and concise"
        listOf("explain", "why is", "how does", "tell me more").any(text::contains) -> "Detailed and explanatory"
        listOf("joking", "kidding", "lol", "that's funny").any(text::contains) -> "Casual and humorous"
        listOf("seriously", "important", "need to know", "be clear").any(text::contains) -> "Calm and matter-of-fact"
        else -> null
    }

    private fun starter(original: String, lower: String): String? {
        if (original.length !in 8..120) return null
        val positive = listOf("i like ", "i love ", "i enjoy ", "my favorite ").any(lower::contains)
        if (!positive) return null
        return "Ask about: ${original.take(100)}"
    }

    private fun extractAfter(lower: String, original: String, prefixes: List<String>): String? {
        for (prefix in prefixes) {
            val index = lower.indexOf(prefix)
            if (index >= 0) {
                val start = index + prefix.length
                val value = original.substring(start).substringBefore('.').substringBefore('?').substringBefore('!')
                    .trim { it == ' ' || it == ',' || it == ';' || it == ':' }
                if (value.length in 2..120) return value
            }
        }
        return null
    }

    private fun add(list: MutableList<String>, value: String) { if (list.none { it.equals(value, true) }) list.add(value) }
}
