package com.realityengine.v4

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent, compact caller memory keyed by normalized phone number. */
class CallerProfileStore(context: Context) {
    data class EvidenceEvent(
        val timestampMs: Long = System.currentTimeMillis(),
        val acoustic: Float = 0f,
        val linguistic: Float = 0f,
        val factual: Float = 0f,
        val combined: Float = 0f,
        val context: String = ""
    )

    data class CallerProfile(
        val phoneNumber: String,
        var displayName: String = "",
        val likes: MutableList<String> = mutableListOf(),
        val dislikes: MutableList<String> = mutableListOf(),
        var preferredConversationStyle: String = "",
        var coachPersonaId: String = "AUTO",
        val topics: MutableList<String> = mutableListOf(),
        val conversationStarters: MutableList<String> = mutableListOf(),
        val importantFacts: MutableList<String> = mutableListOf(),
        val unresolvedTopics: MutableList<String> = mutableListOf(),
        val evidenceEvents: MutableList<EvidenceEvent> = mutableListOf(),
        var lastCallSummary: String = "",
        var updatedAtMs: Long = System.currentTimeMillis()
    ) {
        fun compactContext(): List<String> = buildList {
            if (displayName.isNotBlank()) add("Caller: $displayName")
            if (likes.isNotEmpty()) add("Likes: ${likes.takeLast(5).joinToString(", ")}")
            if (dislikes.isNotEmpty()) add("Dislikes: ${dislikes.takeLast(5).joinToString(", ")}")
            if (preferredConversationStyle.isNotBlank()) add("Preferred style: $preferredConversationStyle")
            if (coachPersonaId != "AUTO") add("Caller coach persona override: ${CoachPersonaCatalog.byId(coachPersonaId).label}")
            if (topics.isNotEmpty()) add("Recent topics: ${topics.takeLast(5).joinToString(", ")}")
            if (conversationStarters.isNotEmpty()) add("Good starters: ${conversationStarters.takeLast(3).joinToString(" | ")}")
            importantFacts.takeLast(5).forEach { add(it) }
            unresolvedTopics.takeLast(3).forEach { add("Open topic: $it") }
            evidenceEvents.takeLast(3).forEach { add("Signal event: acoustic=${pct(it.acoustic)}, linguistic=${pct(it.linguistic)}, factual=${pct(it.factual)}, combined=${pct(it.combined)}${if (it.context.isBlank()) "" else "; ${it.context.take(100)}"}") }
            if (lastCallSummary.isNotBlank()) add("Last call: $lastCallSummary")
        }

        private fun pct(value: Float) = "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
    }

    private val prefs = context.getSharedPreferences("caller_profiles", Context.MODE_PRIVATE)
    private val tombstones = context.getSharedPreferences("caller_profile_tombstones", Context.MODE_PRIVATE)

    @Synchronized
    fun load(phoneNumber: String): CallerProfile {
        val key = normalize(phoneNumber)
        val raw = prefs.getString(key, null) ?: return CallerProfile(key)
        return try { fromJson(JSONObject(raw), key) } catch (_: Throwable) { CallerProfile(key) }
    }

    @Synchronized
    fun save(profile: CallerProfile) {
        write(profile, touch = true)
        tombstones.edit().remove(normalize(profile.phoneNumber)).apply()
    }

    @Synchronized
    fun update(phoneNumber: String, block: (CallerProfile) -> Unit): CallerProfile {
        val profile = load(phoneNumber)
        block(profile)
        trim(profile)
        save(profile)
        return profile
    }

    fun recordEvidence(phoneNumber: String, event: EvidenceEvent): CallerProfile = update(phoneNumber) {
        it.evidenceEvents.add(event.copy(context = event.context.trim().replace(Regex("\\s+"), " ").take(220)))
    }

    fun setCoachPersona(phoneNumber: String, personaId: String): CallerProfile = update(phoneNumber) {
        it.coachPersonaId = normalizeContactPersona(personaId)
    }

    fun clearRecentTopics(phoneNumber: String): CallerProfile = update(phoneNumber) {
        it.topics.clear()
    }

    fun clearLastCall(phoneNumber: String): CallerProfile = update(phoneNumber) {
        it.lastCallSummary = ""
    }

    fun clearCallEvidence(phoneNumber: String): CallerProfile = update(phoneNumber) {
        it.evidenceEvents.clear()
    }

    @Synchronized
    fun deleteProfile(phoneNumber: String): Boolean {
        val key = normalize(phoneNumber)
        tombstones.edit().putLong(key, System.currentTimeMillis()).apply()
        return prefs.edit().remove(key).commit()
    }

    /** Raw profile snapshot for encrypted/RLS-protected cloud sync. */
    @Synchronized
    fun exportJson(phoneNumber: String): String = toJson(load(phoneNumber)).toString()

    /** Import a newer cloud profile without rewriting its cloud timestamp as a local edit. */
    @Synchronized
    fun importCloudJson(phoneNumber: String, rawJson: String, remoteUpdatedAtMs: Long): CallerProfile? {
        val key = normalize(phoneNumber)
        if (tombstoneAt(phoneNumber) > 0L) return null
        return runCatching {
            val profile = fromJson(JSONObject(rawJson), key)
            profile.updatedAtMs = maxOf(profile.updatedAtMs, remoteUpdatedAtMs)
            trim(profile)
            write(profile, touch = false)
            profile
        }.getOrNull()
    }

    fun tombstoneAt(phoneNumber: String): Long = tombstones.getLong(normalize(phoneNumber), 0L)

    fun clearTombstone(phoneNumber: String) {
        tombstones.edit().remove(normalize(phoneNumber)).apply()
    }

    fun injectInto(context: ConversationContext, phoneNumber: String) {
        load(phoneNumber).compactContext().take(12).forEach(context::rememberFact)
    }

    private fun write(profile: CallerProfile, touch: Boolean) {
        val key = normalize(profile.phoneNumber)
        if (touch) profile.updatedAtMs = System.currentTimeMillis()
        profile.coachPersonaId = normalizeContactPersona(profile.coachPersonaId)
        prefs.edit().putString(key, toJson(profile).toString()).apply()
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim()
        val plus = trimmed.startsWith("+")
        val digits = trimmed.filter(Char::isDigit)
        return if (plus) "+$digits" else digits.ifBlank { trimmed }
    }

    private fun normalizeContactPersona(value: String?): String {
        val clean = value.orEmpty().trim().uppercase()
        if (clean == "AUTO" || clean.isBlank()) return "AUTO"
        return CoachPersonaCatalog.normalize(clean)
    }

    private fun trim(p: CallerProfile) {
        trimList(p.likes, 12); trimList(p.dislikes, 12); trimList(p.topics, 16)
        trimList(p.conversationStarters, 8); trimList(p.importantFacts, 20); trimList(p.unresolvedTopics, 8)
        while (p.evidenceEvents.size > 40) p.evidenceEvents.removeAt(0)
        p.preferredConversationStyle = p.preferredConversationStyle.take(160)
        p.coachPersonaId = normalizeContactPersona(p.coachPersonaId)
        p.lastCallSummary = p.lastCallSummary.take(700)
    }

    private fun trimList(list: MutableList<String>, max: Int) {
        val cleaned = list.map { it.trim().replace(Regex("\\s+"), " ").take(180) }.filter { it.isNotBlank() }.distinct()
        list.clear(); list.addAll(cleaned.takeLast(max))
    }

    private fun toJson(p: CallerProfile) = JSONObject().apply {
        put("phone", p.phoneNumber); put("name", p.displayName); put("likes", JSONArray(p.likes)); put("dislikes", JSONArray(p.dislikes))
        put("style", p.preferredConversationStyle); put("coachPersona", p.coachPersonaId); put("topics", JSONArray(p.topics)); put("starters", JSONArray(p.conversationStarters))
        put("facts", JSONArray(p.importantFacts)); put("open", JSONArray(p.unresolvedTopics)); put("events", JSONArray().apply { p.evidenceEvents.forEach { e -> put(JSONObject().apply { put("ts", e.timestampMs); put("a", e.acoustic.toDouble()); put("l", e.linguistic.toDouble()); put("f", e.factual.toDouble()); put("c", e.combined.toDouble()); put("ctx", e.context) }) } }); put("summary", p.lastCallSummary); put("updated", p.updatedAtMs)
    }

    private fun fromJson(o: JSONObject, fallback: String) = CallerProfile(
        phoneNumber = o.optString("phone", fallback), displayName = o.optString("name"), likes = strings(o.optJSONArray("likes")),
        dislikes = strings(o.optJSONArray("dislikes")), preferredConversationStyle = o.optString("style"), coachPersonaId = normalizeContactPersona(o.optString("coachPersona", "AUTO")), topics = strings(o.optJSONArray("topics")),
        conversationStarters = strings(o.optJSONArray("starters")), importantFacts = strings(o.optJSONArray("facts")),
        unresolvedTopics = strings(o.optJSONArray("open")), evidenceEvents = events(o.optJSONArray("events")), lastCallSummary = o.optString("summary"), updatedAtMs = o.optLong("updated", System.currentTimeMillis())
    )

    private fun strings(a: JSONArray?): MutableList<String> = mutableListOf<String>().apply {
        if (a != null) for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun events(a: JSONArray?): MutableList<EvidenceEvent> = mutableListOf<EvidenceEvent>().apply {
        if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { o -> add(EvidenceEvent(o.optLong("ts"), o.optDouble("a").toFloat(), o.optDouble("l").toFloat(), o.optDouble("f").toFloat(), o.optDouble("c").toFloat(), o.optString("ctx"))) }
    }
}
