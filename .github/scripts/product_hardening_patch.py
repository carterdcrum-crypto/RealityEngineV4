from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/realityengine/v4")


def get(name: str) -> str:
    return (ROOT / name).read_text()


def put(name: str, text: str) -> None:
    (ROOT / name).write_text(text)


def required_replace(name: str, old: str, new: str, label: str) -> None:
    text = get(name)
    if old not in text:
        raise SystemExit(f"{label}: anchor not found in {name}")
    put(name, text.replace(old, new, 1))


def required_regex(name: str, pattern: str, replacement: str, label: str, flags: int = 0) -> None:
    text = get(name)
    text2, count = re.subn(pattern, lambda _: replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{label}: regex matched {count} times in {name}")
    put(name, text2)


# CallerProfileStore: enumerate profiles for local Intel search and restore normal indentation.
required_regex(
    "CallerProfileStore.kt",
    r"    @Synchronized\nfun save\(profile: CallerProfile\) \{",
    """    @Synchronized
    fun allProfiles(): List<CallerProfile> = prefs.all.mapNotNull { (key, raw) ->
        val value = raw as? String ?: return@mapNotNull null
        runCatching { fromJson(JSONObject(value), key) }.getOrNull()
    }.sortedByDescending { it.updatedAtMs }

    @Synchronized
    fun save(profile: CallerProfile) {""",
    "caller profile enumeration",
)

# Explainable live signal context.
required_replace(
    "LiveSignalState.kt",
    """        val elevatedStreams: Int = 0,
        val updatedAtMs: Long = 0L
    )""",
    """        val elevatedStreams: Int = 0,
        val updatedAtMs: Long = 0L,
        val context: String = "",
        val cognitiveStress: Int = 0,
    )""",
    "live signal fields",
)
required_replace(
    "LiveSignalState.kt",
    """            elevatedStreams = snapshot.elevatedStreams.coerceIn(0, 3),
            updatedAtMs = snapshot.timestampMs
        )""",
    """            elevatedStreams = snapshot.elevatedStreams.coerceIn(0, 3),
            updatedAtMs = snapshot.timestampMs,
            context = snapshot.context,
            cognitiveStress = snapshot.cognitiveStress,
        )""",
    "live signal publish",
)

required_replace(
    "LiveEvidenceEngine.kt",
    """        val logOdds: Double = 0.0,
        val timestampMs: Long = System.currentTimeMillis()
    )""",
    """        val logOdds: Double = 0.0,
        val timestampMs: Long = System.currentTimeMillis(),
        val context: String = "",
    )""",
    "evidence snapshot context field",
)
required_replace(
    "LiveEvidenceEngine.kt",
    "val snapshot=Snapshot(cleanPhone,(result.acoustic*100).toInt(),(result.linguistic*100).toInt(),(result.factual*100).toInt(),(result.combined*100).toInt(),result.elevatedStreams,shouldPersist,(cognitiveStressScore.coerceIn(0f,1f)*100).toInt(),result.logOdds,now)",
    "val snapshot=Snapshot(cleanPhone,(result.acoustic*100).toInt(),(result.linguistic*100).toInt(),(result.factual*100).toInt(),(result.combined*100).toInt(),result.elevatedStreams,shouldPersist,(cognitiveStressScore.coerceIn(0f,1f)*100).toInt(),result.logOdds,now,transcriptContext.trim().replace(Regex(\"\\\\s+\"), \" \").take(220))",
    "evidence snapshot context population",
)

# Response coach observable state: provider/latency and non-blanking errors.
required_replace(
    "ResponseCoachState.kt",
    """        val groqModel: String = "",
        val groqRemainingTokens: Int? = null,""",
    """        val groqModel: String = "",
        val provider: String = "",
        val coachLatencyMs: Long = 0L,
        val groqRemainingTokens: Int? = null,""",
    "coach state telemetry fields",
)
required_replace(
    "ResponseCoachState.kt",
    """            groqModel = result.model,
            updatedAt = System.currentTimeMillis(),""",
    """            groqModel = result.model,
            provider = result.provider,
            coachLatencyMs = result.latencyMs,
            updatedAt = System.currentTimeMillis(),""",
    "coach state publish telemetry",
)
required_replace(
    "ResponseCoachState.kt",
    """    @Synchronized fun publishStatus(phase: Phase, message: String? = null, clearSuggestions: Boolean = false) {
        snapshot = snapshot.copy(""",
    """    @Synchronized fun publishStatus(phase: Phase, message: String? = null, clearSuggestions: Boolean = false) {
        if (phase == Phase.ANALYZING) CallSessionHealthState.markCoachAnalyzing()
        snapshot = snapshot.copy(""",
    "coach analyzing health",
)
required_regex(
    "ResponseCoachState.kt",
    r"    @Synchronized fun publishError\(message: String\) \{\n        snapshot = snapshot.copy\(\n            best = null,\n            alternatives = emptyList\(\),",
    """    @Synchronized fun publishError(message: String) {
        CallSessionHealthState.markCoachError(message)
        snapshot = snapshot.copy(""",
    "coach error preserve cards",
)

# LiveResponseEngine: thermal cadence, provider latency/usage, keep cards visible while refreshing.
required_replace(
    "LiveResponseEngine.kt",
    """        val outputTokens: Int,
        val model: String = SettingsStore.DEFAULT_GROQ_MODEL
    )""",
    """        val outputTokens: Int,
        val model: String = SettingsStore.DEFAULT_GROQ_MODEL,
        val provider: String = "",
        val latencyMs: Long = 0L,
    )""",
    "coach result telemetry fields",
)
required_replace(
    "LiveResponseEngine.kt",
    """    private val providerPerformance = appContext?.applicationContext?.let(::CoachProviderPerformanceStore)
    private val gemini""",
    """    private val providerPerformance = appContext?.applicationContext?.let(::CoachProviderPerformanceStore)
    private val runtimeUsage = appContext?.applicationContext?.let(::RuntimeUsageStore)
    private val thermalGuard = appContext?.applicationContext?.let(::ThermalGuard)
    private val gemini""",
    "coach usage thermal fields",
)
required_replace(
    "LiveResponseEngine.kt",
    """        var status: Pair<ResponseCoachState.Phase, String>? = null
        var shouldLaunch = false
        synchronized(this) {
            if (clean == lastCallerTurn) return""",
    """        var status: Pair<ResponseCoachState.Phase, String>? = null
        var shouldLaunch = false
        val requiredTurns = effectiveAnalysisFrequency()
        synchronized(this) {
            if (clean == lastCallerTurn) return""",
    "effective coach frequency declaration",
)
required_replace(
    "LiveResponseEngine.kt",
    """                callerTurnsSinceAnalysis < settings.analysisFrequencyTurns -> {
                    status = ResponseCoachState.Phase.LISTENING to "Caller turn $callerTurnsSinceAnalysis/${settings.analysisFrequencyTurns}"""",
    """                callerTurnsSinceAnalysis < requiredTurns -> {
                    status = ResponseCoachState.Phase.LISTENING to "Caller turn $callerTurnsSinceAnalysis/$requiredTurns"""",
    "effective coach frequency use",
)
text = get("LiveResponseEngine.kt")
text = text.replace("            clearSuggestions = true\n        )\n        executor.execute { executeAnalysis(ticket, callback) }", "            clearSuggestions = false\n        )\n        executor.execute { executeAnalysis(ticket, callback) }", 1)
text = text.replace("                clearSuggestions = true\n            )\n            executor.execute { executeAnalysis(nextTicket, {}) }", "                clearSuggestions = false\n            )\n            executor.execute { executeAnalysis(nextTicket, {}) }", 1)
put("LiveResponseEngine.kt", text)
required_replace(
    "LiveResponseEngine.kt",
    """            } else if (failure != null) {
                ResponseCoachState.publishError(failure!!)
            }""",
    """            } else if (failure != null) {
                CallSessionHealthState.markCoachError(failure!!)
                ResponseCoachState.publishError(failure!!)
            }""",
    "coach health failure",
)
required_replace(
    "LiveResponseEngine.kt",
    """        return try {
            val result = requestProvider(provider, snapshot, quickModeId)
            providerPerformance?.recordSuccess(provider, elapsedMs(started))
            result
        } catch (t: Throwable) {""",
    """        return try {
            val result = requestProvider(provider, snapshot, quickModeId)
            val elapsed = elapsedMs(started)
            providerPerformance?.recordSuccess(provider, elapsed)
            runtimeUsage?.recordCoach(result.inputTokens, result.outputTokens)
            CallSessionHealthState.markCoachReady(provider, elapsed)
            result.copy(provider = provider, latencyMs = elapsed)
        } catch (t: Throwable) {""",
    "measured provider telemetry",
)
required_replace(
    "LiveResponseEngine.kt",
    "    private fun elapsedMs(startedNanos: Long): Long =",
    """    private fun effectiveAnalysisFrequency(): Int {
        val base = settings.analysisFrequencyTurns.coerceAtLeast(1)
        return if (thermalGuard?.snapshot()?.throttle == true) maxOf(base, 2) else base
    }

    private fun elapsedMs(startedNanos: Long): Long =""",
    "thermal analysis cadence",
)

# LiveTranscriptionPipeline health heartbeats + Deepgram usage.
required_replace(
    "LiveTranscriptionPipeline.kt",
    """    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)""",
    """    private val appContext = context.applicationContext
    private val runtimeUsage = RuntimeUsageStore(appContext)
    private val settings = SettingsStore(appContext)""",
    "transcription usage store",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    "    @Volatile private var activeChannels = 0\n",
    "    @Volatile private var activeChannels = 0\n    @Volatile private var deepgramStartedAtMs = 0L\n",
    "deepgram timer field",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """                if (running.get() && !stopping.get()) {
                    when (direction) {""",
    """                if (running.get() && !stopping.get()) {
                    CallSessionHealthState.markAudioFrame(length)
                    when (direction) {""",
    "native pcm heartbeat",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """        acousticScore = acoustic.analyze(frame.pcm16).score
        recordAudio(frame.pcm16, frame.pcm16.size)""",
    """        acousticScore = acoustic.analyze(frame.pcm16).score
        CallSessionHealthState.markAudioFrame(frame.pcm16.size)
        recordAudio(frame.pcm16, frame.pcm16.size)""",
    "twilio pcm heartbeat",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """        conversation.bindActiveCaller()
    }

    private fun connectDeepgram""",
    """        conversation.bindActiveCaller()
        deepgramStartedAtMs = 0L
        CallSessionHealthState.beginSession()
    }

    private fun connectDeepgram""",
    "session health start",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """    private fun connectDeepgram(sampleRate: Int, channels: Int, multi: Boolean): Boolean {
        multichannel = multi
        return deepgram.connect(""",
    """    private fun connectDeepgram(sampleRate: Int, channels: Int, multi: Boolean): Boolean {
        multichannel = multi
        CallSessionHealthState.markSttConnecting()
        return deepgram.connect(""",
    "stt connecting heartbeat",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """                val callerSide = isCaller != false
                LiveTranscriptState.publish(result.text, result.isFinal, isCaller)""",
    """                val callerSide = isCaller != false
                CallSessionHealthState.markTranscript(result.isFinal)
                LiveTranscriptState.publish(result.text, result.isFinal, isCaller)""",
    "stt transcript heartbeat",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """            onSpeechEvent = { event -> handleSpeechEvent(event) },
            onClosed = { reason -> finishShutdown(reason) },
        ).also { started -> if (started) primeDeepgram(sampleRate, channels) }""",
    """            onSpeechEvent = { event -> handleSpeechEvent(event) },
            onClosed = { reason ->
                CallSessionHealthState.markSttClosed(reason)
                finishShutdown(reason)
            },
        ).also { started ->
            if (started) {
                deepgramStartedAtMs = System.currentTimeMillis()
                CallSessionHealthState.markSttReady()
                primeDeepgram(sampleRate, channels)
            }
        }""",
    "stt connected close heartbeat",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """        if (text.isBlank()) return
        if (isCaller) {""",
    """        if (text.isBlank()) return
        CallSessionHealthState.markTurnDelivered(isCaller)
        if (isCaller) {""",
    "turn delivery heartbeat",
)
required_replace(
    "LiveTranscriptionPipeline.kt",
    """        stopping.set(false)
        val callback = stoppedCallback""",
    """        stopping.set(false)
        val started = deepgramStartedAtMs
        if (started > 0L) runtimeUsage.recordDeepgram(System.currentTimeMillis() - started)
        deepgramStartedAtMs = 0L
        CallSessionHealthState.finishSession()
        val callback = stoppedCallback""",
    "deepgram usage finish",
)

# AI-learned permanent facts become explicit proposals; summaries still update automatically.
required_replace(
    "CallSummaryBuilder.kt",
    "    private val aiMemory = CallerMemoryAiExtractor(appContext)\n",
    "    private val aiMemory = CallerMemoryAiExtractor(appContext)\n    private val proposals = MemoryProposalStore(appContext)\n",
    "memory proposal store",
)
required_replace(
    "CallSummaryBuilder.kt",
    """                profiles.update(phoneNumber) { target -> merge(target, learned) }
                cloud.pushAsync(phoneNumber)""",
    """                if (learned.summary.isNotBlank()) profiles.update(phoneNumber) { target -> target.lastCallSummary = learned.summary }
                proposals.save(phoneNumber, learned)
                cloud.pushAsync(phoneNumber)""",
    "automatic memory proposal",
)

# CallerMemoryActivity SAVE/IGNORE review.
required_replace(
    "CallerMemoryActivity.kt",
    """    private lateinit var ai: CallerMemoryAiExtractor
    private lateinit var root: LinearLayout""",
    """    private lateinit var ai: CallerMemoryAiExtractor
    private lateinit var proposals: MemoryProposalStore
    private lateinit var root: LinearLayout""",
    "proposal activity field",
)
required_replace(
    "CallerMemoryActivity.kt",
    """        ai = CallerMemoryAiExtractor(this)
        phone =""",
    """        ai = CallerMemoryAiExtractor(this)
        proposals = MemoryProposalStore(this)
        phone =""",
    "proposal activity init",
)
required_replace(
    "CallerMemoryActivity.kt",
    """        root.addView(actionButton("SYNC MEMORY NOW", cyan) { syncNow() })

        root.addView(TextView(this).apply {""",
    """        root.addView(actionButton("SYNC MEMORY NOW", cyan) { syncNow() })
        proposals.load(phone)?.let { addProposalReview(it) }

        root.addView(TextView(this).apply {""",
    "proposal card placement",
)
required_replace(
    "CallerMemoryActivity.kt",
    "    private fun addCategory(profile: CallerProfileStore.CallerProfile, kind: Kind) {",
    """    private fun addProposalReview(proposal: MemoryProposalStore.Proposal) {
        val learned = proposal.learned
        val lines = buildList {
            learned.likes.forEach { add("LIKE · $it") }
            learned.dislikes.forEach { add("DISLIKE · $it") }
            learned.facts.forEach { add("FACT · $it") }
            learned.topics.forEach { add("TOPIC · $it") }
            learned.unresolved.forEach { add("FOLLOW UP · $it") }
            learned.starters.forEach { add("STARTER · $it") }
            if (learned.preferredStyle.isNotBlank()) add("STYLE · ${learned.preferredStyle}")
        }
        root.addView(TextView(this).apply {
            text = "NEW MEMORY TO REVIEW · ${proposals.itemCount(proposal)}"
            setTextColor(green)
            setPadding(dp(3), dp(14), 0, dp(5))
            RealityTypography.displayMedium(this, 13f)
        })
        root.addView(TextView(this).apply {
            text = if (lines.isEmpty()) "No permanent facts proposed; only the call summary changed." else lines.joinToString("\\n")
            setTextColor(primaryText)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = green, radiusDp = 10f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            RealityTypography.display(this, 11.5f)
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "SAVE"
            setTextColor(green)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = green, radiusDp = 10f)
            setOnClickListener {
                profiles.update(phone) { CallSummaryBuilder.merge(it, learned) }
                proposals.clear(phone)
                pushAndRefresh()
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, dp(4), dp(3), dp(3)) })
        actions.addView(Button(this).apply {
            text = "IGNORE"
            setTextColor(magenta)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = magenta, radiusDp = 10f)
            setOnClickListener { proposals.clear(phone); build() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(4), 0, dp(3)) })
        root.addView(actions)
    }

    private fun addCategory(profile: CallerProfileStore.CallerProfile, kind: Kind) {""",
    "proposal review block",
)
required_regex(
    "CallerMemoryActivity.kt",
    r"                profiles.update\(phone\) \{ CallSummaryBuilder.merge\(it, learned\) \}\n                status.text = \"LEARNED WITH \$\{learned.provider\} · SYNCING…\"\n                cloud.pushAsync\(phone\) \{ result ->\n                    runOnUiThread \{\n                        build\(\)\n                        status.text = \"MEMORY UPDATED · \$\{result.detail.ifBlank \{ result.status.name \}\}\"\n                    \}\n                \}",
    """                if (learned.summary.isNotBlank()) profiles.update(phone) { it.lastCallSummary = learned.summary }
                proposals.save(phone, learned)
                cloud.pushAsync(phone)
                build()
                status.text = "NEW MEMORY READY · REVIEW SAVE / IGNORE"""",
    "manual memory proposal behavior",
)

# Incoming/outgoing post-call handoff goes to the intelligence timeline first.
required_replace(
    "PostCallProfileState.kt",
    """        context.startActivity(Intent(context, CallerMemoryActivity::class.java).apply {
            putExtra(CallerMemoryActivity.EXTRA_PHONE, item.phoneNumber)
            putExtra(CallerMemoryActivity.EXTRA_NAME, item.displayName)""",
    """        context.startActivity(Intent(context, PostCallIntelligenceActivity::class.java).apply {
            putExtra(PostCallIntelligenceActivity.EXTRA_PHONE, item.phoneNumber)
            putExtra(PostCallIntelligenceActivity.EXTRA_NAME, item.displayName)""",
    "post call intelligence destination",
)

# MainActivity Intel tab + direct caller memory access.
required_replace(
    "MainActivity.kt",
    """            if (screen == "SETTINGS" && ::content.isInitialized) showSettings()
        }""",
    """            if (screen == "SETTINGS" && ::content.isInitialized) showSettings()
            if (screen == "INTEL" && ::content.isInitialized) showIntel()
        }""",
    "intel resume refresh",
)
required_replace(
    "MainActivity.kt",
    """        nav.addView(navItem("⌁", "Phone", "DIAL") { showPhone() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("◴", "Traffic", "TRAFFIC") { showRecents() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("▣", "Index", "INDEX") { showContacts() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("⚙", "Settings", "SETTINGS") { showSettings() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))""",
    """        nav.addView(navItem("⌁", "Phone", "DIAL") { showPhone() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("◴", "Traffic", "TRAFFIC") { showRecents() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("◇", "Intel", "INTEL") { showIntel() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("▣", "Index", "INDEX") { showContacts() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("⚙", "Settings", "SETTINGS") { showSettings() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))""",
    "intel nav item",
)
required_replace(
    "MainActivity.kt",
    "    private fun showSettings() {",
    """    private fun showIntel() {
        stopRecordingPlayback()
        screen = "INTEL"
        content.gravity = Gravity.TOP
        content.removeAllViews()
        content.addView(IntelligenceHubScreen(this).build(), LinearLayout.LayoutParams(-1, -2))
        refreshNav()
    }

    private fun showSettings() {""",
    "show intel screen",
)
required_replace(
    "MainActivity.kt",
    """        showSavedRecordings(phone, name)

        content.addView(cyberButton("Call $label")""",
    """        showSavedRecordings(phone, name)
        content.addView(cyberButton("Open Reality memory") {
            startActivity(Intent(this, CallerMemoryActivity::class.java).apply {
                putExtra(CallerMemoryActivity.EXTRA_PHONE, phone)
                putExtra(CallerMemoryActivity.EXTRA_NAME, label)
            })
        }, LinearLayout.LayoutParams(-1, 48.dp()).apply { setMargins(0, 8.dp(), 0, 2.dp()) })

        content.addView(cyberButton("Call $label")""",
    "caller memory shortcut",
)

# CallActivity live UI integration.
required_replace(
    "CallActivity.kt",
    "    private lateinit var transcript: TextView\n",
    "    private lateinit var transcript: LiveTranscriptPanelView\n    private lateinit var healthStrip: TextView\n    private lateinit var preCallBriefing: PreCallBriefingView\n",
    "call transcript view fields",
)
required_replace(
    "CallActivity.kt",
    "    private var lastRenderedCallState: Int? = null\n",
    "    private var lastRenderedCallState: Int? = null\n    private var usageRecorded = false\n",
    "call usage field",
)
required_replace(
    "CallActivity.kt",
    "    private val transcriptListener: (LiveTranscriptState.State) -> Unit = { snapshot -> runOnUiThread { renderTranscript(snapshot) } }\n",
    "    private val transcriptListener: (LiveTranscriptState.State) -> Unit = { snapshot -> runOnUiThread { renderTranscript(snapshot) } }\n    private val healthListener: (CallSessionHealthState.Snapshot) -> Unit = { snapshot -> runOnUiThread { renderHealth(snapshot) } }\n",
    "call health listener field",
)
required_replace(
    "CallActivity.kt",
    """            refreshRecordingUi()
            handler.postDelayed(this, 500L)""",
    """            refreshRecordingUi()
            renderHealth(CallSessionHealthState.snapshot())
            handler.postDelayed(this, 500L)""",
    "call health timer",
)
required_replace(
    "CallActivity.kt",
    "        LiveTranscriptState.addListener(transcriptListener)\n",
    "        LiveTranscriptState.addListener(transcriptListener)\n        CallSessionHealthState.addListener(healthListener)\n",
    "call health listener add",
)
required_replace(
    "CallActivity.kt",
    "        LiveTranscriptState.removeListener(transcriptListener)\n",
    "        LiveTranscriptState.removeListener(transcriptListener)\n        CallSessionHealthState.removeListener(healthListener)\n",
    "call health listener remove",
)
required_replace(
    "CallActivity.kt",
    "        root.addView(identity, LinearLayout.LayoutParams(-1, 66.dp()).apply { setMargins(0, 0, 0, 8.dp()) })\n",
    """        root.addView(identity, LinearLayout.LayoutParams(-1, 66.dp()).apply { setMargins(0, 0, 0, 8.dp()) })

        healthStrip = TextView(this).apply {
            text = "AUDIO ○  STT ○  COACH ○"
            setTextColor(cyan)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = neon(RealityVisuals.Colors.BackgroundRaised, RealityVisuals.Colors.Border, 9f)
            setOnClickListener {
                AlertDialog.Builder(this@CallActivity)
                    .setTitle("Call session diagnostics")
                    .setMessage(CallSessionHealthState.diagnosticText())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        root.addView(healthStrip, LinearLayout.LayoutParams(-1, 28.dp()).apply { setMargins(0, 0, 0, 6.dp()) })
""",
    "call health strip",
)
required_replace(
    "CallActivity.kt",
    "        root.addView(incomingHeroAvatar, LinearLayout.LayoutParams(118.dp(), 118.dp()).apply { setMargins(0, 3.dp(), 0, 8.dp()) })\n",
    """        root.addView(incomingHeroAvatar, LinearLayout.LayoutParams(118.dp(), 118.dp()).apply { setMargins(0, 3.dp(), 0, 8.dp()) })
        preCallBriefing = PreCallBriefingView(this).apply { visibility = View.GONE }
        root.addView(preCallBriefing, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 7.dp()) })
""",
    "pre call briefing placement",
)
text = get("CallActivity.kt")
start = text.find("        val transcriptHeader = LinearLayout(this).apply")
end = text.find("        val coachPanel = LinearLayout(this).apply", start)
if start < 0 or end < 0:
    raise SystemExit("live transcript block boundaries missing")
new_block = """        val transcriptHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        transcriptHeader.addView(TextView(this).apply {
            text = "LIVE TRANSCRIPT"
            RealityVisuals.styleMicroLabel(this, magenta)
        }, LinearLayout.LayoutParams(0, 22.dp(), 1f))
        transcriptHeader.addView(TextView(this).apply {
            text = "SEARCH · HOLD TO BOOKMARK"
            RealityVisuals.styleMicroLabel(this, muted)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })
        workspace.addView(transcriptHeader)
        transcript = LiveTranscriptPanelView(this)
        workspace.addView(transcript, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(0, 3.dp(), 0, 7.dp()) })

"""
text = text[:start] + new_block + text[end:]
put("CallActivity.kt", text)
required_regex(
    "CallActivity.kt",
    r"    private fun renderTranscript\(snapshot: LiveTranscriptState\.State\) \{.*?\n    \}\n\n    private fun renderCoach",
    """    private fun renderTranscript(snapshot: LiveTranscriptState.State) {
        if (!::transcript.isInitialized) return
        transcript.render(snapshot)
    }

    private fun renderCoach""",
    "render bubble transcript",
    re.S,
)
text = get("CallActivity.kt")
text = text.replace('ResponseCoachState.Phase.KEY_REQUIRED -> "GROQ KEY REQUIRED\\nOpen Settings → Groq"', 'ResponseCoachState.Phase.KEY_REQUIRED -> "AI PROVIDER REQUIRED\\nOpen Settings → Coach provider"', 1)
put("CallActivity.kt", text)
required_regex(
    "CallActivity.kt",
    r"    private fun renderGroqUsage\(snapshot: ResponseCoachState\.Snapshot\) \{.*?\n    \}\n\n    private fun shortGroqModel",
    """    private fun renderGroqUsage(snapshot: ResponseCoachState.Snapshot) {
        if (!::groqUsage.isInitialized) return
        val provider = snapshot.provider.ifBlank { CallSessionHealthState.snapshot().coachProvider }.ifBlank { "WAITING" }
        val model = shortGroqModel(snapshot.groqModel)
        val latency = snapshot.coachLatencyMs.takeIf { it > 0 }?.let { " · ${it}ms" }.orEmpty()
        val rate = if (provider.equals("GROQ", true) && snapshot.groqRemainingTokens != null && snapshot.groqLimitTokens != null) " · TPM ${compactTokens(snapshot.groqRemainingTokens)}/${compactTokens(snapshot.groqLimitTokens)}" else ""
        groqUsage.text = "COACH // $provider · $model$latency$rate · CALL ${compactTokens(snapshot.callTotalTokens)}"
    }

    private fun shortGroqModel""",
    "generic provider usage strip",
    re.S,
)
required_replace(
    "CallActivity.kt",
    "    private fun renderLiveSignals() {",
    """    private fun renderHealth(snapshot: CallSessionHealthState.Snapshot) {
        if (!::healthStrip.isInitialized) return
        healthStrip.text = snapshot.compact()
        val accent = when {
            snapshot.lastError.isNotBlank() -> magenta
            snapshot.audio == CallSessionHealthState.Level.GOOD && snapshot.stt == CallSessionHealthState.Level.GOOD && snapshot.coach != CallSessionHealthState.Level.ERROR -> green
            else -> cyan
        }
        healthStrip.setTextColor(accent)
        healthStrip.background = neon(RealityVisuals.Colors.BackgroundRaised, accent, 9f)
    }

    private fun showSignalExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Live signal explanation")
            .setMessage(SignalExplanation.lines(LiveSignalState.snapshot()).joinToString("\\n\\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderLiveSignals() {""",
    "call health and signal explanation methods",
)
text = get("CallActivity.kt")
signal_anchor = "            setPadding(10.dp(), 6.dp(), 10.dp(), 5.dp())\n        }\n        signals.addView"
if signal_anchor in text:
    text = text.replace(signal_anchor, "            setPadding(10.dp(), 6.dp(), 10.dp(), 5.dp())\n            isClickable = true\n            setOnClickListener { showSignalExplanation() }\n        }\n        signals.addView", 1)
put("CallActivity.kt", text)
required_replace(
    "CallActivity.kt",
    """            RealityVisuals.reveal(callerAvatar)
        }""",
    """            RealityVisuals.reveal(callerAvatar)
            transcript.bindPhone(number)
            preCallBriefing.bind(number, label)
        }""",
    "call bind briefing and transcript phone",
)
required_replace(
    "CallActivity.kt",
    "        if (current.state == Call.STATE_DISCONNECTED) { connectedStartedAt = null; scheduleFinish() } else finishScheduled = false",
    """        if (current.state == Call.STATE_DISCONNECTED) {
            if (!usageRecorded) {
                connectedStartedAt?.let { RuntimeUsageStore(this).recordCall(SystemClock.elapsedRealtime() - it) }
                usageRecorded = true
            }
            connectedStartedAt = null
            scheduleFinish()
        } else finishScheduled = false""",
    "record call usage",
)
required_replace(
    "CallActivity.kt",
    "        val ringing = current.state == Call.STATE_RINGING\n",
    "        val ringing = current.state == Call.STATE_RINGING\n        val preConnect = ringing || current.state == Call.STATE_DIALING || current.state == Call.STATE_CONNECTING\n        preCallBriefing.visibility = if (preConnect) View.VISIBLE else View.GONE\n",
    "briefing visibility",
)
required_replace(
    "CallActivity.kt",
    "        updateTimer(); renderLiveSignals(); renderTranscript(LiveTranscriptState.snapshot()); renderGroqUsage(ResponseCoachState.current())",
    "        updateTimer(); renderLiveSignals(); renderTranscript(LiveTranscriptState.snapshot()); renderGroqUsage(ResponseCoachState.current()); renderHealth(CallSessionHealthState.snapshot())",
    "call health initial render",
)

# Manifest activity registration.
manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text()
old = '        <activity android:name=".PostCallReviewActivity" android:exported="false" android:excludeFromRecents="true" />'
if old not in m:
    raise SystemExit("manifest post-call review anchor missing")
m = m.replace(old, old + '\n        <activity android:name=".PostCallIntelligenceActivity" android:exported="false" />', 1)
manifest.write_text(m)

print("Product hardening integration patch applied successfully")
