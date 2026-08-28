package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/** Connected intelligence layer that turns the live call into a conversation workspace. */
data class ConversationInsightSnapshot(
    val topics: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val commitments: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val changes: List<String> = emptyList(),
    val objective: String = ConversationObjectiveStore.DEFAULT,
    val objectiveProgress: Int = 0,
) {
    fun compact(): String = buildString {
        val primary = when {
            openQuestions.isNotEmpty() -> "${openQuestions.size} open question${if (openQuestions.size == 1) "" else "s"}"
            changes.isNotEmpty() -> "${changes.size} change${if (changes.size == 1) "" else "s"} detected"
            commitments.isNotEmpty() -> "${commitments.size} commitment${if (commitments.size == 1) "" else "s"}"
            topics.isNotEmpty() -> topics.take(3).joinToString(" · ")
            else -> "Listening for topics, questions and commitments"
        }
        append(primary)
        if (objective != ConversationObjectiveStore.DEFAULT) append("  ·  ${objectiveProgress}% toward $objective")
    }

    fun details(): String = buildString {
        append("OBJECTIVE\n").append(objective)
        if (objective != ConversationObjectiveStore.DEFAULT) append(" · ").append(objectiveProgress).append("% progress")
        append("\n\nTOPICS\n").append(topics.ifEmpty { listOf("None yet") }.joinToString(" · "))
        append("\n\nPEOPLE\n").append(people.ifEmpty { listOf("None yet") }.joinToString(" · "))
        append("\n\nDATES / TIMES\n").append(dates.ifEmpty { listOf("None yet") }.joinToString(" · "))
        append("\n\nCOMMITMENTS\n").append(commitments.ifEmpty { listOf("None yet") }.joinToString("\n"))
        append("\n\nOPEN QUESTIONS\n").append(openQuestions.ifEmpty { listOf("None") }.joinToString("\n"))
        append("\n\nWHAT CHANGED\n").append(changes.ifEmpty { listOf("No meaningful changes detected") }.joinToString("\n"))
    }
}

class ConversationObjectiveStore(context: Context) {
    private val prefs = context.getSharedPreferences("conversation_objectives", Context.MODE_PRIVATE)

    fun get(phone: String = ""): String {
        val key = PhoneNumberKey.normalize(phone).orEmpty()
        return if (key.isNotBlank()) prefs.getString("phone_$key", null) ?: prefs.getString("global", DEFAULT) ?: DEFAULT
        else prefs.getString("global", DEFAULT) ?: DEFAULT
    }

    fun set(phone: String = "", value: String) {
        val clean = value.takeIf { it in OPTIONS } ?: DEFAULT
        val key = PhoneNumberKey.normalize(phone).orEmpty()
        prefs.edit().putString(if (key.isBlank()) "global" else "phone_$key", clean).apply()
    }

    companion object {
        const val DEFAULT = "Open conversation"
        val OPTIONS = listOf(
            DEFAULT,
            "Resolve dispute",
            "Get information",
            "Negotiate",
            "Reconnect",
            "Sell / persuade",
            "Calm situation",
            "Set a boundary",
            "Make a plan",
        )
    }
}

class ConversationIntelligenceEngine(context: Context) {
    private val profiles = CallerProfileStore(context.applicationContext)
    private val objectives = ConversationObjectiveStore(context.applicationContext)
    private val stopWords = setOf(
        "about", "after", "again", "because", "before", "being", "could", "didn't", "doesn't", "going", "have", "just", "like", "maybe",
        "really", "should", "something", "their", "there", "these", "thing", "think", "those", "through", "today", "tomorrow", "want", "would",
        "yeah", "your", "you're", "with", "from", "that", "this", "what", "when", "where", "which", "while", "will", "they", "them", "then"
    )

    fun analyze(phone: String, state: LiveTranscriptState.State): ConversationInsightSnapshot {
        val entries = state.entries.filter { it.isFinal && it.text.isNotBlank() }
        val allText = entries.joinToString(" ") { it.text }
        val words = Regex("[A-Za-z][A-Za-z'-]{3,}").findAll(allText.lowercase(Locale.US)).map { it.value }.toList()
        val topics = words.filterNot { it in stopWords }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key.replaceFirstChar(Char::uppercase) }.take(6)

        val people = entries.flatMap { entry ->
            Regex("\\b[A-Z][a-z]{2,}\\b").findAll(entry.text).map { it.value }.toList()
        }.filterNot { it.lowercase(Locale.US) in stopWords }
            .distinct().take(5)

        val commitments = entries.filter { entry ->
            Regex("\\b(i|we)\\s+(will|can|promise|need to|have to|am going to|are going to|plan to)\\b", RegexOption.IGNORE_CASE).containsMatchIn(entry.text)
        }.map { "${if (it.isCaller == false) "You" else "Them"}: ${it.text.take(150)}" }.takeLast(5)

        val dateRegex = Regex(
            "\\b(today|tomorrow|tonight|yesterday|next\\s+(week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)|" +
                "monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|march|april|may|june|july|august|september|october|november|december|" +
                "\\d{1,2}[:/]\\d{1,2}(?:[:/]\\d{2,4})?|\\d{1,2}:\\d{2}(?:\\s?[ap]m)?)\\b",
            RegexOption.IGNORE_CASE,
        )
        val dates = entries.flatMap { e -> dateRegex.findAll(e.text).map { it.value }.toList() }.distinct().takeLast(6)

        val openQuestions = extractOpenQuestions(entries)
        val changes = detectChanges(phone, entries, dateRegex)
        val objective = objectives.get(phone)
        val progress = objectiveProgress(objective, allText)
        return ConversationInsightSnapshot(topics, people, commitments, dates, openQuestions, changes, objective, progress)
    }

    private fun extractOpenQuestions(entries: List<LiveTranscriptState.Entry>): List<String> {
        val candidates = entries.mapIndexedNotNull { index, entry ->
            val clean = entry.text.trim()
            val lower = clean.lowercase(Locale.US)
            val looksLikeQuestion = clean.endsWith("?") || listOf("who ", "what ", "when ", "where ", "why ", "how ", "did ", "do ", "does ", "can ", "could ", "would ", "are ", "is ", "will ").any(lower::startsWith)
            if (looksLikeQuestion) index to entry else null
        }
        return candidates.filter { (index, question) ->
            val keywords = keywords(question.text)
            entries.drop(index + 1).none { answer ->
                answer.isCaller != question.isCaller && keywords.intersect(keywords(answer.text)).isNotEmpty() && answer.text.length >= 8
            }
        }.map { (_, entry) -> "${if (entry.isCaller == false) "You" else "Them"}: ${entry.text.take(150)}" }.takeLast(5)
    }

    private fun detectChanges(
        phone: String,
        entries: List<LiveTranscriptState.Entry>,
        dateRegex: Regex,
    ): List<String> {
        if (phone.isBlank()) return emptyList()
        val profile = profiles.load(phone)
        val memory = (profile.importantFacts + profile.unresolvedTopics + listOf(profile.lastCallSummary)).filter { it.isNotBlank() }
        if (memory.isEmpty()) return emptyList()
        val callerTurns = entries.filter { it.isCaller != false }.takeLast(10)
        val output = mutableListOf<String>()
        callerTurns.forEach { turn ->
            val currentDates = dateRegex.findAll(turn.text).map { it.value.lowercase(Locale.US) }.toSet()
            val currentKeywords = keywords(turn.text)
            memory.forEach { old ->
                val shared = currentKeywords.intersect(keywords(old))
                if (shared.isEmpty()) return@forEach
                val oldDates = dateRegex.findAll(old).map { it.value.lowercase(Locale.US) }.toSet()
                if (currentDates.isNotEmpty() && oldDates.isNotEmpty() && currentDates != oldDates) {
                    output += "Timeline changed: ${old.take(90)} → ${turn.text.take(100)}"
                } else if (Regex("\\b(actually|instead|no longer|not anymore|changed|cancelled|canceled)\\b", RegexOption.IGNORE_CASE).containsMatchIn(turn.text)) {
                    output += "Update to prior memory: ${turn.text.take(150)}"
                }
            }
        }
        return output.distinct().takeLast(4)
    }

    private fun keywords(text: String): Set<String> = Regex("[A-Za-z][A-Za-z'-]{3,}")
        .findAll(text.lowercase(Locale.US)).map { it.value }.filterNot { it in stopWords }.toSet()

    private fun objectiveProgress(objective: String, text: String): Int {
        if (objective == ConversationObjectiveStore.DEFAULT || text.isBlank()) return 0
        val lower = text.lowercase(Locale.US)
        val positive = when (objective) {
            "Resolve dispute" -> listOf("agree", "understand", "resolved", "fair", "okay", "settled")
            "Get information" -> listOf("because", "the reason", "here's", "it is", "details", "answer")
            "Negotiate" -> listOf("deal", "agree", "offer", "meet", "compromise", "works for me")
            "Reconnect" -> listOf("miss", "good to hear", "glad", "remember", "together")
            "Sell / persuade" -> listOf("interested", "sounds good", "tell me more", "price", "buy", "sign up")
            "Calm situation" -> listOf("calm", "okay", "understand", "sorry", "fine", "work this out")
            "Set a boundary" -> listOf("understand", "respect", "won't", "will not", "okay", "boundary")
            "Make a plan" -> listOf("tomorrow", "next", "meet", "schedule", "plan", "time", "date")
            else -> emptyList()
        }
        val hits = positive.count { lower.contains(it) }
        val turnBonus = (text.length / 180).coerceAtMost(4)
        return ((hits * 17) + (turnBonus * 6)).coerceIn(5, 96)
    }
}

class ConversationRadarView(context: Context) : LinearLayout(context) {
    private val title = TextView(context)
    private val body = TextView(context)
    private var snapshot = ConversationInsightSnapshot()

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = RealityVisuals.panel(context, RealityVisuals.Colors.PanelStrong, RealityVisuals.Colors.Lilac, 18f)
        title.apply {
            text = "CONVERSATION RADAR"
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
        }
        body.apply {
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.display(this, 11f)
            maxLines = 2
        }
        addView(title)
        addView(body, LayoutParams(-1, -2).apply { topMargin = dp(4) })
        setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Conversation radar")
                .setMessage(snapshot.details())
                .setPositiveButton("Close", null)
                .show()
        }
    }

    fun render(value: ConversationInsightSnapshot) {
        snapshot = value
        body.text = value.compact()
        val accent = when {
            value.changes.isNotEmpty() -> RealityVisuals.Colors.Amber
            value.openQuestions.isNotEmpty() -> RealityVisuals.Colors.CyanSoft
            value.objectiveProgress >= 65 -> RealityVisuals.Colors.Green
            else -> RealityVisuals.Colors.Lilac
        }
        background = RealityVisuals.panel(context, RealityVisuals.Colors.PanelStrong, accent, 18f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

class RealityOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var signal = LiveSignalState.State()
    private var insight = ConversationInsightSnapshot()

    fun render(signalState: LiveSignalState.State, insightState: ConversationInsightSnapshot) {
        signal = signalState
        insight = insightState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * .38f
        val accent = when {
            signal.combined >= 72 -> Color.rgb(255, 114, 145)
            signal.combined >= 48 -> RealityVisuals.Colors.Amber
            insight.objectiveProgress >= 68 -> RealityVisuals.Colors.Green
            signal.combined >= 20 -> RealityVisuals.Colors.Lilac
            else -> RealityVisuals.Colors.CyanSoft
        }
        paint.shader = RadialGradient(cx - radius * .25f, cy - radius * .28f, radius * 1.45f,
            intArrayOf(Color.WHITE, accent, Color.rgb(14, 16, 38), Color.TRANSPARENT),
            floatArrayOf(0f, .18f, .70f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        ring.strokeWidth = max(1f, resources.displayMetrics.density)
        ring.color = Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, radius * 1.03f, ring)
        ring.color = Color.argb(50, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius * .78f, ring)
    }
}

object ConversationRewind {
    fun show(activity: Activity, phone: String) {
        val entries = LiveTranscriptState.transcript().takeLast(12)
        val body = if (entries.isNotEmpty()) {
            entries.joinToString("\n\n") { entry ->
                val who = if (entry.isCaller == false) "YOU" else "THEM"
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.updatedAtMs))
                "$time · $who\n${entry.text}"
            }
        } else {
            CallTranscriptStore.savedFor(activity, phone).firstOrNull()?.text?.takeLast(1800)
                ?: "No transcript history is available yet."
        }
        val signal = LiveSignalState.snapshot()
        val best = ResponseCoachState.current().best
        val footer = buildString {
            append("\n\nCURRENT SIGNALS · A ${signal.acoustic} · L ${signal.linguistic} · F ${signal.factual} · FUSED ${signal.combined}%")
            best?.let {
                append("\n\nCURRENT COACH · ${it.mode} / ${it.tone}\n${it.text}")
                if (it.reason.isNotBlank()) append("\nWHY · ${it.reason}")
            }
        }
        AlertDialog.Builder(activity)
            .setTitle("Conversation rewind")
            .setMessage(body + footer)
            .setPositiveButton("Back to call", null)
            .show()
    }
}

class ConversationTranslationStore(context: Context) {
    private val prefs = context.getSharedPreferences("conversation_translation", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()
    var pair: String
        get() = prefs.getString("pair", PAIRS.first()).orEmpty().takeIf { it in PAIRS } ?: PAIRS.first()
        set(value) = prefs.edit().putString("pair", value.takeIf { it in PAIRS } ?: PAIRS.first()).apply()

    companion object {
        val PAIRS = listOf("English ↔ Spanish", "English ↔ French", "English ↔ German", "English ↔ Portuguese", "English ↔ Italian")
    }
}

class ConversationTranslator(context: Context) {
    private val settings = SettingsStore(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor()

    fun translate(text: String, pair: String, callback: (String) -> Unit) {
        val clean = text.trim()
        if (clean.isBlank()) return
        executor.execute {
            val result = runCatching { request(clean, pair) }.getOrElse { "Translation unavailable · ${it.message.orEmpty().take(70)}" }
            callback(result)
        }
    }

    private fun request(text: String, pair: String): String {
        val provider = when {
            settings.coachProvider != SettingsStore.COACH_PROVIDER_AUTO && settings.providerConfigured(settings.coachProvider) -> settings.coachProvider
            else -> SettingsStore.COACH_FALLBACK_ORDER.firstOrNull(settings::providerConfigured)
        } ?: error("configure an AI provider")
        val prompt = "Translate this phone-call utterance for a bilingual $pair conversation. If it is mainly English, translate it to the other language; otherwise translate it to English. Return only the translated sentence, no explanation.\n\n$text"
        return if (provider == SettingsStore.COACH_PROVIDER_GEMINI) requestGemini(prompt) else requestCompatible(provider, prompt)
    }

    private fun requestCompatible(provider: String, prompt: String): String {
        val (endpoint, key, model) = when (provider) {
            SettingsStore.COACH_PROVIDER_GROQ -> Triple("https://api.groq.com/openai/v1/chat/completions", settings.groqApiKey, settings.groqModel)
            SettingsStore.COACH_PROVIDER_CEREBRAS -> Triple("https://api.cerebras.ai/v1/chat/completions", settings.cerebrasApiKey, settings.cerebrasModel)
            SettingsStore.COACH_PROVIDER_MISTRAL -> Triple("https://api.mistral.ai/v1/chat/completions", settings.mistralApiKey, settings.mistralModel)
            SettingsStore.COACH_PROVIDER_OPENROUTER -> Triple("https://openrouter.ai/api/v1/chat/completions", settings.openRouterApiKey, settings.openRouterModel)
            else -> error("provider unavailable")
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 180)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val json = post(endpoint, key, payload)
        return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().trim()
            .ifBlank { error("empty translation") }
    }

    private fun requestGemini(prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${settings.geminiModel}:generateContent?key=${Uri.encode(settings.geminiApiKey)}"
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 9_000; readTimeout = 12_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        if (connection.responseCode !in 200..299) error("Gemini ${connection.responseCode}")
        return json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty().trim()
            .ifBlank { error("empty translation") }
    }

    private fun post(endpoint: String, key: String, payload: JSONObject): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 9_000; readTimeout = 12_000; doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("$providerName ${connection.responseCode}")
        return JSONObject(raw)
    }

    private val providerName: String get() = settings.coachProvider
}

class ConversationHeatmapView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var events: List<CallerProfileStore.EvidenceEvent> = emptyList()

    fun setData(value: List<CallerProfileStore.EvidenceEvent>) {
        events = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.rgb(11, 18, 35), Color.rgb(5, 9, 19), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), paint)
        paint.shader = null
        if (events.isEmpty()) return
        val gap = dp(3f)
        val usable = width - gap * (events.size + 1)
        val block = usable / events.size.coerceAtLeast(1)
        events.forEachIndexed { index, event ->
            val score = (event.combined * 100).toInt()
            val color = when {
                score >= 72 -> Color.rgb(255, 112, 145)
                score >= 48 -> RealityVisuals.Colors.Amber
                score >= 28 -> RealityVisuals.Colors.Lilac
                else -> RealityVisuals.Colors.CyanSoft
            }
            val left = gap + index * (block + gap)
            val top = dp(9f) + (100 - score).coerceIn(0, 100) / 100f * height * .42f
            paint.color = Color.argb(205, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRoundRect(RectF(left, top, left + block, height - dp(9f)), dp(6f), dp(6f), paint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

/** Aggregates calls + memory into a contact-centric history instead of a flat address-book entry. */
class RelationshipTimelineActivity : Activity() {
    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        if (phone.isBlank()) { finish(); return }
        val fallback = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val profile = CallerProfileStore(this).load(phone)
        val match = ContactMediaStore.findByNumber(this, phone)
        val name = profile.displayName.ifBlank { match?.name.orEmpty().ifBlank { fallback.ifBlank { phone } } }
        val transcripts = CallTranscriptStore.savedFor(this, phone)
        val bookmarks = CallBookmarkStore(this).list(phone)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(30))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }
        root.addView(TextView(this).apply { text = "RELATIONSHIP TIMELINE"; RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac) })
        root.addView(TextView(this).apply {
            text = name; setTextColor(RealityVisuals.Colors.Text); RealityTypography.displayMedium(this, 27f); setPadding(0, dp(5), 0, 0)
        })
        root.addView(TextView(this).apply { text = phone; setTextColor(RealityVisuals.Colors.TextDim); RealityTypography.display(this, 11f) })

        if (profile.importantFacts.isNotEmpty() || profile.unresolvedTopics.isNotEmpty()) {
            root.addView(section("PINNED CONTEXT"))
            profile.importantFacts.takeLast(4).forEach { root.addView(card("FACT · $it", RealityVisuals.Colors.CyanSoft)) }
            profile.unresolvedTopics.takeLast(4).forEach { root.addView(card("OPEN · $it", RealityVisuals.Colors.Amber)) }
        }

        root.addView(section("CALL HISTORY · ${transcripts.size}"))
        if (transcripts.isEmpty()) root.addView(card("No saved transcript history yet.", RealityVisuals.Colors.Border))
        transcripts.forEach { saved ->
            val preview = saved.text.lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n").take(520)
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(saved.timestampMs))
            root.addView(card("$date · ${saved.turnCount} turns\n$preview", RealityVisuals.Colors.Lilac))
        }

        if (bookmarks.isNotEmpty()) {
            root.addView(section("BOOKMARKED MOMENTS"))
            bookmarks.takeLast(10).reversed().forEach {
                val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timestampMs))
                root.addView(card("$date · ${if (it.isCaller == false) "YOU" else "THEM"}\n${it.text}", RealityVisuals.Colors.Green))
            }
        }

        root.addView(Button(this).apply {
            text = "Open caller memory"
            RealityVisuals.styleControl(this, 0, RealityVisuals.Colors.CyanSoft, radiusDp = 18f)
            setOnClickListener {
                startActivity(Intent(this@RelationshipTimelineActivity, CallerMemoryActivity::class.java).apply {
                    putExtra(CallerMemoryActivity.EXTRA_PHONE, phone); putExtra(CallerMemoryActivity.EXTRA_NAME, name)
                })
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, dp(18), 0, 0) })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(label: String) = TextView(this).apply {
        text = label; setPadding(dp(3), dp(18), 0, dp(6)); RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
    }

    private fun card(textValue: String, accent: Int) = TextView(this).apply {
        text = textValue; setTextColor(RealityVisuals.Colors.Text); RealityTypography.display(this, 11.5f)
        setLineSpacing(dp(2).toFloat(), 1.04f); setPadding(dp(13), dp(11), dp(13), dp(11))
        background = RealityVisuals.panel(this@RelationshipTimelineActivity, RealityVisuals.Colors.Panel, accent, 16f)
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

object ConversationFollowUp {
    fun textRecap(activity: Activity, phone: String, summary: String) {
        val body = summary.takeIf { it.isNotBlank() } ?: "Thanks for the call — following up on what we discussed."
        activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).apply { putExtra("sms_body", body.take(700)) })
    }

    fun addReminder(activity: Activity, displayName: String) {
        val start = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Follow up with ${displayName.ifBlank { "caller" }}")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30L * 60L * 1000L)
        activity.startActivity(intent)
    }
}
