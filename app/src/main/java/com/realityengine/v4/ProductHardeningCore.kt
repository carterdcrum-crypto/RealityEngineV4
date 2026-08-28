package com.realityengine.v4

import android.content.Context
import android.os.Build
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Runtime health for the active call. Stores telemetry only; never transcript contents. */
object CallSessionHealthState {
    enum class Level { WAITING, GOOD, DEGRADED, ERROR }

    data class Event(val atMs: Long, val kind: String, val detail: String)
    data class Snapshot(
        val sessionStartedAtMs: Long = 0L,
        val audio: Level = Level.WAITING,
        val stt: Level = Level.WAITING,
        val coach: Level = Level.WAITING,
        val audioBytes: Long = 0L,
        val lastAudioAtMs: Long = 0L,
        val lastTranscriptAtMs: Long = 0L,
        val lastTurnDeliveredAtMs: Long = 0L,
        val lastCoachAtMs: Long = 0L,
        val coachLatencyMs: Long = 0L,
        val coachProvider: String = "",
        val lastError: String = "",
        val events: List<Event> = emptyList(),
    ) {
        fun compact(nowMs: Long = System.currentTimeMillis()): String {
            fun dot(level: Level) = when (level) {
                Level.GOOD -> "●"
                Level.DEGRADED -> "◐"
                Level.ERROR -> "×"
                Level.WAITING -> "○"
            }
            val latency = coachLatencyMs.takeIf { it > 0L }?.let { " · ${it}ms" }.orEmpty()
            return "AUDIO ${dot(audio)}  STT ${dot(stt)}  COACH ${dot(coach)}$latency"
        }
    }

    @Volatile private var current = Snapshot()
    private val listeners = LinkedHashSet<(Snapshot) -> Unit>()
    private var sttLevel = Level.WAITING
    private var coachLevel = Level.WAITING

    @Synchronized
    fun beginSession() {
        sttLevel = Level.WAITING
        coachLevel = Level.WAITING
        current = Snapshot(sessionStartedAtMs = System.currentTimeMillis())
        addEventLocked("SESSION", "started")
        notifyLocked()
    }

    @Synchronized
    fun markAudioFrame(byteCount: Int) {
        if (byteCount <= 0) return
        val now = System.currentTimeMillis()
        current = current.copy(
            audioBytes = current.audioBytes + byteCount,
            lastAudioAtMs = now,
            audio = Level.GOOD,
        )
        notifyLocked()
    }

    @Synchronized
    fun markSttConnecting() {
        sttLevel = Level.WAITING
        current = current.copy(stt = sttLevel)
        addEventLocked("STT", "connecting")
        notifyLocked()
    }

    @Synchronized
    fun markSttReady() {
        sttLevel = Level.GOOD
        current = current.copy(stt = sttLevel, lastError = "")
        addEventLocked("STT", "connected")
        notifyLocked()
    }

    @Synchronized
    fun markTranscript(isFinal: Boolean) {
        val now = System.currentTimeMillis()
        sttLevel = Level.GOOD
        current = current.copy(stt = sttLevel, lastTranscriptAtMs = now)
        if (isFinal) addEventLocked("STT", "final transcript received")
        notifyLocked()
    }

    @Synchronized
    fun markTurnDelivered(isCaller: Boolean) {
        current = current.copy(lastTurnDeliveredAtMs = System.currentTimeMillis())
        addEventLocked("TURN", if (isCaller) "caller turn delivered" else "user turn delivered")
        notifyLocked()
    }

    @Synchronized
    fun markSttClosed(reason: String?) {
        val detail = reason.orEmpty().trim().take(120)
        sttLevel = if (detail.isBlank()) Level.WAITING else Level.ERROR
        current = current.copy(stt = sttLevel, lastError = detail.ifBlank { current.lastError })
        addEventLocked("STT", if (detail.isBlank()) "closed" else "closed: $detail")
        notifyLocked()
    }

    @Synchronized
    fun markCoachAnalyzing() {
        coachLevel = Level.DEGRADED
        current = current.copy(coach = coachLevel)
        addEventLocked("COACH", "request started")
        notifyLocked()
    }

    @Synchronized
    fun markCoachReady(provider: String, latencyMs: Long) {
        coachLevel = Level.GOOD
        current = current.copy(
            coach = coachLevel,
            lastCoachAtMs = System.currentTimeMillis(),
            coachLatencyMs = latencyMs.coerceAtLeast(0L),
            coachProvider = provider.take(32),
            lastError = "",
        )
        addEventLocked("COACH", "${provider.ifBlank { "provider" }} ready in ${latencyMs.coerceAtLeast(0L)}ms")
        notifyLocked()
    }

    @Synchronized
    fun markCoachError(message: String) {
        coachLevel = Level.ERROR
        val clean = message.trim().replace(Regex("\\s+"), " ").take(140)
        current = current.copy(coach = coachLevel, lastError = clean)
        addEventLocked("COACH", clean.ifBlank { "request failed" })
        notifyLocked()
    }

    @Synchronized
    fun finishSession() {
        addEventLocked("SESSION", "finished")
        notifyLocked()
    }

    @Synchronized
    fun snapshot(nowMs: Long = System.currentTimeMillis()): Snapshot {
        val audioLevel = when {
            current.lastAudioAtMs == 0L -> Level.WAITING
            nowMs - current.lastAudioAtMs <= 2_500L -> Level.GOOD
            nowMs - current.lastAudioAtMs <= 6_000L -> Level.DEGRADED
            else -> Level.ERROR
        }
        val sttFresh = when {
            sttLevel == Level.ERROR -> Level.ERROR
            current.lastTranscriptAtMs == 0L -> sttLevel
            nowMs - current.lastTranscriptAtMs <= 8_000L -> Level.GOOD
            current.lastAudioAtMs > 0L && nowMs - current.lastAudioAtMs <= 2_500L -> Level.DEGRADED
            else -> sttLevel
        }
        if (audioLevel != current.audio || sttFresh != current.stt || coachLevel != current.coach) {
            current = current.copy(audio = audioLevel, stt = sttFresh, coach = coachLevel)
        }
        return current
    }

    @Synchronized
    fun addListener(listener: (Snapshot) -> Unit) {
        listeners += listener
        listener(snapshot())
    }

    @Synchronized fun removeListener(listener: (Snapshot) -> Unit) { listeners -= listener }

    @Synchronized
    fun diagnosticText(): String = buildString {
        val s = snapshot()
        append(s.compact()).append('\n')
        append("PCM ").append(s.audioBytes).append(" bytes")
        if (s.coachProvider.isNotBlank()) append(" · ").append(s.coachProvider)
        if (s.lastError.isNotBlank()) append("\nLAST ERROR · ").append(s.lastError)
        s.events.takeLast(12).forEach { event ->
            append("\n").append(event.kind).append(" · ").append(event.detail)
        }
    }

    private fun addEventLocked(kind: String, detail: String) {
        current = current.copy(events = (current.events + Event(System.currentTimeMillis(), kind, detail.take(160))).takeLast(40))
    }

    private fun notifyLocked() {
        val snapshot = current
        listeners.toList().forEach { it(snapshot) }
    }
}

/** Long-lived usage counters; no audio or transcript content is stored. */
class RuntimeUsageStore(context: Context) {
    data class Summary(
        val calls: Long,
        val callMs: Long,
        val deepgramMs: Long,
        val coachRequests: Long,
        val inputTokens: Long,
        val outputTokens: Long,
    )

    private val prefs = context.applicationContext.getSharedPreferences("runtime_usage_v1", Context.MODE_PRIVATE)

    @Synchronized fun recordCall(durationMs: Long) {
        prefs.edit()
            .putLong("calls", prefs.getLong("calls", 0L) + 1L)
            .putLong("call_ms", prefs.getLong("call_ms", 0L) + durationMs.coerceAtLeast(0L))
            .apply()
    }

    @Synchronized fun recordDeepgram(durationMs: Long) {
        prefs.edit().putLong("deepgram_ms", prefs.getLong("deepgram_ms", 0L) + durationMs.coerceAtLeast(0L)).apply()
    }

    @Synchronized fun recordCoach(inputTokens: Int, outputTokens: Int) {
        prefs.edit()
            .putLong("coach_requests", prefs.getLong("coach_requests", 0L) + 1L)
            .putLong("input_tokens", prefs.getLong("input_tokens", 0L) + inputTokens.coerceAtLeast(0))
            .putLong("output_tokens", prefs.getLong("output_tokens", 0L) + outputTokens.coerceAtLeast(0))
            .apply()
    }

    fun summary() = Summary(
        calls = prefs.getLong("calls", 0L),
        callMs = prefs.getLong("call_ms", 0L),
        deepgramMs = prefs.getLong("deepgram_ms", 0L),
        coachRequests = prefs.getLong("coach_requests", 0L),
        inputTokens = prefs.getLong("input_tokens", 0L),
        outputTokens = prefs.getLong("output_tokens", 0L),
    )
}

/** Lightweight thermal guard used to reduce optional AI work when the phone is hot. */
class ThermalGuard(context: Context) {
    data class Snapshot(val status: Int, val label: String, val throttle: Boolean)
    private val power = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun snapshot(): Snapshot {
        if (Build.VERSION.SDK_INT < 29) return Snapshot(0, "UNAVAILABLE", false)
        val status = power.currentThermalStatus
        val label = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
            PowerManager.THERMAL_STATUS_LIGHT -> "WARM"
            PowerManager.THERMAL_STATUS_MODERATE -> "HOT"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN"
        }
        return Snapshot(status, label, status >= PowerManager.THERMAL_STATUS_MODERATE)
    }
}

class CallBookmarkStore(context: Context) {
    data class Bookmark(val timestampMs: Long, val isCaller: Boolean?, val text: String)
    private val prefs = context.applicationContext.getSharedPreferences("call_bookmarks_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun add(phoneNumber: String, entry: LiveTranscriptState.Entry) {
        val key = PhoneNumberKey.normalize(phoneNumber).orEmpty().ifBlank { "unknown" }
        val current = list(phoneNumber).toMutableList()
        if (current.none { it.text.equals(entry.text, true) && it.isCaller == entry.isCaller }) {
            current += Bookmark(entry.updatedAtMs, entry.isCaller, entry.text.trim().take(500))
        }
        val array = JSONArray()
        current.takeLast(60).forEach { b ->
            array.put(JSONObject().apply {
                put("ts", b.timestampMs)
                if (b.isCaller != null) put("caller", b.isCaller)
                put("text", b.text)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun list(phoneNumber: String): List<Bookmark> {
        val key = PhoneNumberKey.normalize(phoneNumber).orEmpty().ifBlank { "unknown" }
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    add(Bookmark(o.optLong("ts"), if (o.has("caller")) o.optBoolean("caller") else null, o.optString("text")))
                }
            }
        }.getOrDefault(emptyList())
    }
}

/** Human-readable, uncertainty-aware explanation of live signal scores. */
object SignalExplanation {
    fun lines(state: LiveSignalState.State): List<String> = buildList {
        add(scoreLine("ACOUSTIC", state.acoustic, "voice/acoustic pattern changed", "voice pattern near baseline"))
        add(scoreLine("LINGUISTIC", state.linguistic, "wording or cognitive-load cues increased", "wording cues near baseline"))
        add(scoreLine("FACTUAL", state.factual, "possible conflict with saved conversation context", "saved context broadly consistent"))
        if (state.context.isNotBlank()) add("CONTEXT · ${state.context.take(180)}")
        add("These are uncertain conversation cues, not proof that anyone is lying or being deceptive.")
    }

    private fun scoreLine(label: String, score: Int, elevated: String, normal: String): String {
        val marker = when {
            score >= 75 -> "↑↑"
            score >= 55 -> "↑"
            else -> "↔"
        }
        return "$label $marker $score% · ${if (score >= 55) elevated else normal}"
    }
}

/** Pending AI-learned facts that require user review before becoming permanent caller memory. */
class MemoryProposalStore(context: Context) {
    data class Proposal(val learned: CallerMemoryAiExtractor.Learned, val createdAtMs: Long)
    private val prefs = context.applicationContext.getSharedPreferences("memory_proposals_v1", Context.MODE_PRIVATE)

    fun save(phoneNumber: String, learned: CallerMemoryAiExtractor.Learned) {
        val key = PhoneNumberKey.normalize(phoneNumber).orEmpty().ifBlank { return }
        prefs.edit().putString(key, JSONObject().apply {
            put("created", System.currentTimeMillis())
            put("likes", JSONArray(learned.likes)); put("dislikes", JSONArray(learned.dislikes)); put("facts", JSONArray(learned.facts))
            put("topics", JSONArray(learned.topics)); put("starters", JSONArray(learned.starters)); put("open", JSONArray(learned.unresolved))
            put("style", learned.preferredStyle); put("summary", learned.summary); put("provider", learned.provider)
        }.toString()).apply()
    }

    fun load(phoneNumber: String): Proposal? {
        val key = PhoneNumberKey.normalize(phoneNumber).orEmpty().ifBlank { return null }
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            fun strings(name: String): List<String> {
                val a = o.optJSONArray(name) ?: return emptyList()
                return buildList { for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
            }
            Proposal(
                CallerMemoryAiExtractor.Learned(
                    likes = strings("likes"), dislikes = strings("dislikes"), facts = strings("facts"), topics = strings("topics"),
                    starters = strings("starters"), unresolved = strings("open"), preferredStyle = o.optString("style"),
                    summary = o.optString("summary"), provider = o.optString("provider"),
                ),
                o.optLong("created", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    fun clear(phoneNumber: String) {
        val key = PhoneNumberKey.normalize(phoneNumber).orEmpty().ifBlank { return }
        prefs.edit().remove(key).apply()
    }

    fun itemCount(proposal: Proposal): Int = with(proposal.learned) {
        likes.size + dislikes.size + facts.size + topics.size + starters.size + unresolved.size + if (preferredStyle.isNotBlank()) 1 else 0
    }
}
