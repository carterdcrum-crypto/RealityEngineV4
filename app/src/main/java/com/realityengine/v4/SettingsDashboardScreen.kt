package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.telecom.TelecomManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Visual settings dashboard for Reality Engine V4. */
class SettingsDashboardScreen(
    private val activity: Activity,
    private val store: SettingsStore,
    private val buttonShapeLabel: String,
    private val onRefresh: () -> Unit,
    private val onWalkthrough: () -> Unit,
    private val onAbout: () -> Unit,
    private val onCheckUpdate: () -> Unit,
    private val onDefaultPhone: () -> Unit,
    private val onCycleButtonShape: () -> Unit,
    private val onShizuku: () -> Unit,
    private val onCallAudio: () -> Unit,
    private val onAndroidPhoneSettings: () -> Unit,
) {
    private data class Readiness(val label: String, val ready: Boolean, val detail: String, val attention: Boolean = false)
    private data class RoutingChoice(val label: String, val lockedProvider: String, val preferredProvider: String)

    private val routingPreference = CoachRoutingPreferenceStore(activity)
    private val cyan = RealityVisuals.Colors.Cyan
    private val magenta = RealityVisuals.Colors.Magenta
    private val green = RealityVisuals.Colors.Green
    private val amber = RealityVisuals.Colors.Amber
    private val primaryText = RealityVisuals.Colors.Text
    private val muted = RealityVisuals.Colors.TextDim
    private val panel = RealityVisuals.Colors.Panel
    private val raised = RealityVisuals.Colors.BackgroundRaised
    private val border = RealityVisuals.Colors.Border

    fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(0, dp(8), 0, dp(22))
        }

        root.addView(header())
        root.addView(readinessPanel(), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(12)) })

        root.addView(section("SETUP"))
        root.addView(row("Setup walkthrough", "Guided phone, permission, audio and AI setup", "GUIDE", cyan, onWalkthrough))
        root.addView(row(
            "Default phone application",
            defaultPhoneDetail(),
            if (isDefaultPhone()) "READY" else "SETUP",
            if (isDefaultPhone()) green else amber,
            onDefaultPhone,
        ))
        root.addView(row(
            "Shizuku call audio",
            shizukuDetail(),
            if (shizukuReady()) "READY" else "SETUP",
            if (shizukuReady()) green else amber,
            onShizuku,
        ))

        root.addView(section("INTELLIGENCE"))
        root.addView(row(
            "AI routing",
            coachProviderSummary(),
            coachRoutingBadge(),
            if (store.coachConfigured()) green else amber,
        ) { chooseCoachProvider() })
        root.addView(row(
            "Groq API",
            if (store.groqConfigured()) "Configured · available to Best, Prefer, or Only routing" else "Add a Groq key for GPT-OSS 20B",
            if (store.groqConfigured()) "READY" else "KEY",
            if (store.groqConfigured()) green else muted,
        ) {
            editSecret("Groq API key", store.groqApiKey, "Get a Groq API key", "https://console.groq.com/keys") {
                store.groqApiKey = it
            }
        })
        root.addView(row("Groq coach model", groqModelSummary(), "MODEL", cyan) { chooseGroqModel() })
        root.addView(row(
            "Gemini API",
            if (store.geminiConfigured()) "Configured · available to Best, Prefer, or Only routing" else "Add a Google AI Studio key · free-tier prompts may be used to improve Google products",
            if (store.geminiConfigured()) "READY" else "KEY",
            if (store.geminiConfigured()) green else muted,
        ) {
            editSecret("Gemini API key", store.geminiApiKey, "Get a Gemini API key", "https://aistudio.google.com/app/apikey") {
                store.geminiApiKey = it
            }
        })
        root.addView(row("Gemini coach model", geminiModelSummary(), "MODEL", cyan) { chooseGeminiModel() })
        root.addView(row(
            "Cerebras API",
            if (store.cerebrasConfigured()) "Configured · available to Best, Prefer, or Only routing" else "Optional fast GPT-OSS 120B provider · free access is trial/limited",
            if (store.cerebrasConfigured()) "READY" else "KEY",
            if (store.cerebrasConfigured()) green else muted,
        ) {
            editSecret("Cerebras API key", store.cerebrasApiKey, "Open Cerebras Cloud", "https://cloud.cerebras.ai/") {
                store.cerebrasApiKey = it
            }
        })
        root.addView(row(
            "Mistral API",
            if (store.mistralConfigured()) "Configured · available to Best, Prefer, or Only routing" else "Optional Mistral Small 4 provider",
            if (store.mistralConfigured()) "READY" else "KEY",
            if (store.mistralConfigured()) green else muted,
        ) {
            editSecret("Mistral API key", store.mistralApiKey, "Get a Mistral API key", "https://console.mistral.ai/api-keys/") {
                store.mistralApiKey = it
            }
        })
        root.addView(row(
            "OpenRouter API",
            if (store.openRouterConfigured()) "Configured · available to Best, Prefer, or Only routing" else "Optional free-model router",
            if (store.openRouterConfigured()) "READY" else "KEY",
            if (store.openRouterConfigured()) green else muted,
        ) {
            editSecret("OpenRouter API key", store.openRouterApiKey, "Get an OpenRouter API key", "https://openrouter.ai/settings/keys") {
                store.openRouterApiKey = it
            }
        })
        root.addView(row(
            "Deepgram API",
            if (store.deepgramConfigured()) "Live transcription configured" else "Required for live transcription",
            if (store.deepgramConfigured()) "READY" else "KEY",
            if (store.deepgramConfigured()) green else amber,
        ) {
            editSecret("Deepgram API key", store.deepgramApiKey, "Get a Deepgram API key", "https://console.deepgram.com/signup?jump=keys") {
                store.deepgramApiKey = it
            }
        })
        root.addView(row("Deepgram model", deepgramModelSummary(), "MODEL", cyan) { chooseDeepgramModel() })
        val supabaseVerified = store.supabaseConfigured() && store.supabaseVerifiedAtMs > 0L
        root.addView(row(
            "Supabase caller memory",
            when {
                supabaseVerified -> "Caller-memory cloud sync verified · tap to view or retest"
                store.supabaseConfigured() -> "URL + publishable key saved · connection not verified"
                else -> "Set URL, publishable key and test caller-memory sync"
            },
            when {
                supabaseVerified -> "READY"
                store.supabaseConfigured() -> "CONFIG"
                else -> "SETUP"
            },
            when {
                supabaseVerified -> green
                store.supabaseConfigured() -> cyan
                else -> amber
            },
        ) { editSupabase() })
        val transcriptCount = CallTranscriptStore.savedAll(activity).size
        root.addView(row(
            "Saved transcripts",
            "Private completed-call transcripts stored on this device",
            transcriptCount.toString(),
            cyan,
        ) { activity.startActivity(Intent(activity, TranscriptLibraryActivity::class.java)) })

        root.addView(section("LIVE BEHAVIOR"))
        root.addView(toggleRow("Response coach", "Generate ranked response suggestions during calls", store.responseCoachEnabled) {
            store.responseCoachEnabled = !store.responseCoachEnabled
            onRefresh()
        })
        val persona = CoachPersonaCatalog.byId(store.coachPersonaId)
        root.addView(row(
            "Coach persona",
            "${persona.description} Caller overrides are available inside the picker.",
            persona.label.uppercase(),
            magenta,
        ) { chooseCoachPersona() })
        root.addView(toggleRow("Haptic alerts", "Quiet vibration when multiple signals are elevated", store.hapticsEnabled) {
            store.hapticsEnabled = !store.hapticsEnabled
            onRefresh()
        })
        root.addView(toggleRow(
            "Auto-record calls for review",
            "Show REC while active, then require Save or Permanently Delete after hangup",
            store.autoRecordCalls,
        ) {
            if (store.autoRecordCalls) {
                store.autoRecordCalls = false
                onRefresh()
            } else {
                AlertDialog.Builder(activity)
                    .setTitle("Enable call recording?")
                    .setMessage("Recording will be visibly marked during calls and reviewed after every recorded call. Only record calls where recording is permitted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Enable") { _, _ -> store.autoRecordCalls = true; onRefresh() }
                    .show()
            }
        })
        root.addView(row(
            "Analysis frequency",
            "Run coaching every ${store.analysisFrequencyTurns} ${if (store.analysisFrequencyTurns == 1) "turn" else "turns"}",
            "${store.analysisFrequencyTurns}×",
            cyan,
        ) { chooseAnalysisFrequency() })
        val soundCount = SoundboardStore(activity).count()
        root.addView(row(
            "Call soundboard",
            "Import audio from Downloads, preview it, rename sounds, and manage the in-call library",
            if (soundCount == 0) "SETUP" else "$soundCount",
            if (soundCount == 0) amber else green,
        ) { activity.startActivity(Intent(activity, SoundboardSettingsActivity::class.java)) })

        root.addView(section("SYSTEM"))
        root.addView(row("Android phone settings", "Manage system calling apps and defaults", "ANDROID", muted, onAndroidPhoneSettings))

        root.addView(section("APP"))
        root.addView(row("App updates", "Check the private GitHub release for the newest green build", "CHECK", cyan, onCheckUpdate))
        root.addView(row(
            "Private update access",
            if (store.privateUpdaterConfigured()) "GitHub updater token configured" else "GitHub token required for private updates",
            if (store.privateUpdaterConfigured()) "READY" else "TOKEN",
            if (store.privateUpdaterConfigured()) green else amber,
        ) { editSecret("GitHub updater token", store.githubUpdaterToken) { store.githubUpdaterToken = it } })
        root.addView(row("About Reality Engine", "Capabilities, design intent and signal guidance", "INFO", magenta, onAbout))

        return root
    }

    private fun header(): View {
        val readiness = readinessItems()
        val readyCount = readiness.count { it.ready }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                text = "SETTINGS"
                setTextColor(primaryText)
                RealityTypography.displayMedium(this, 24f)
            }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(TextView(activity).apply {
                text = "$readyCount/${readiness.size} READY"
                gravity = Gravity.CENTER
                background = RealityVisuals.panel(
                    activity,
                    fill = if (readyCount == readiness.size) Color.rgb(6, 28, 22) else raised,
                    stroke = if (readyCount == readiness.size) green else cyan,
                    radiusDp = 20f,
                )
                setPadding(dp(10), dp(4), dp(10), dp(4))
                RealityVisuals.styleMicroLabel(this, if (readyCount == readiness.size) green else cyan)
            })
        }
    }

    private fun readinessPanel(): View {
        val items = readinessItems()
        val readyCount = items.count { it.ready }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = RealityVisuals.panel(activity, fill = raised, stroke = border, radiusDp = 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = "SYSTEM READINESS"
                    RealityVisuals.styleMicroLabel(this, cyan)
                }, LinearLayout.LayoutParams(0, dp(24), 1f))
                addView(TextView(activity).apply {
                    text = if (readyCount == items.size) "ALL SYSTEMS READY" else "${items.size - readyCount} NEED ATTENTION"
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    RealityVisuals.styleMicroLabel(this, if (readyCount == items.size) green else amber)
                })
            })
            addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = items.size
                progress = readyCount
                progressTintList = ColorStateList.valueOf(if (readyCount == items.size) green else cyan)
                progressBackgroundTintList = ColorStateList.valueOf(RealityVisuals.Colors.Track)
            }, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0, dp(2), 0, dp(8)) })
            items.forEach { addView(readinessLine(it)) }
        }
    }

    private fun readinessLine(item: Readiness): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val accent = if (item.ready) green else if (item.attention) magenta else amber
        addView(TextView(activity).apply {
            text = if (item.ready) "●" else "○"
            setTextColor(accent)
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
        }, LinearLayout.LayoutParams(dp(22), dp(30)))
        addView(TextView(activity).apply {
            text = item.label
            setTextColor(primaryText)
            RealityTypography.displayMedium(this, 12.5f)
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        addView(TextView(activity).apply {
            text = if (item.ready) "READY" else if (item.attention) "ATTENTION" else "SETUP"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            RealityVisuals.styleMicroLabel(this, accent)
        })
    }

    private fun section(label: String): View = TextView(activity).apply {
        text = label
        setPadding(dp(4), dp(18), 0, dp(6))
        RealityVisuals.styleMicroLabel(this, magenta)
    }

    private fun toggleRow(title: String, subtitle: String, enabled: Boolean, click: () -> Unit): View =
        row(title, subtitle, if (enabled) "ON" else "OFF", if (enabled) green else muted, click)

    private fun row(title: String, subtitle: String, status: String, statusColor: Int, click: () -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = RealityVisuals.panel(activity, fill = panel, stroke = border, radiusDp = 12f)
            isClickable = true
            isFocusable = true
            minimumHeight = dp(72)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = title
                    setTextColor(primaryText)
                    RealityTypography.displayMedium(this, 14.5f)
                })
                addView(TextView(activity).apply {
                    text = subtitle
                    setTextColor(muted)
                    setPadding(0, dp(3), dp(8), 0)
                    RealityTypography.display(this, 11.5f)
                    maxLines = 2
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(activity).apply {
                text = status
                gravity = Gravity.CENTER
                background = RealityVisuals.panel(
                    activity,
                    fill = if (statusColor == green) Color.rgb(6, 28, 22) else raised,
                    stroke = statusColor,
                    radiusDp = 18f,
                )
                setPadding(dp(8), dp(4), dp(8), dp(4))
                RealityVisuals.styleMicroLabel(this, statusColor)
                maxLines = 1
            })
            setOnClickListener { RealityVisuals.reveal(this); click() }
        }.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) } }

    private fun readinessItems(): List<Readiness> = listOf(
        Readiness("Default phone", isDefaultPhone(), defaultPhoneDetail()),
        Readiness("Microphone", microphoneReady(), if (microphoneReady()) "Allowed" else "Permission required"),
        Readiness("Shizuku", shizukuReady(), shizukuDetail()),
        Readiness("Transcription", store.deepgramConfigured(), if (store.deepgramConfigured()) "Configured" else "Deepgram key required"),
        Readiness("Response coach", coachReady(), if (coachReady()) coachProviderSummary() else "Selected AI routing needs at least one configured provider or coach is disabled"),
    )

    private fun isDefaultPhone(): Boolean {
        val telecom = activity.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecom.defaultDialerPackage == activity.packageName
    }

    private fun defaultPhoneDetail() = if (isDefaultPhone()) "Reality Engine is the active phone app" else "Choose Reality Engine as the default phone app"
    private fun microphoneReady() = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun shizukuReady() = ShizukuAudioStatus.binderAvailable() && ShizukuAudioStatus.permissionGranted()
    private fun shizukuDetail() = when {
        !ShizukuAudioStatus.binderAvailable() -> "Shizuku is offline"
        !ShizukuAudioStatus.permissionGranted() -> "Authorization required"
        else -> "Connected and authorized"
    }
    private fun coachReady() = store.coachConfigured() && store.responseCoachEnabled

    private fun coachProviderName(provider: String): String = provider.lowercase().replaceFirstChar { it.uppercase() }

    private fun coachRoutingBadge(): String = when {
        store.coachProvider != SettingsStore.COACH_PROVIDER_AUTO -> "ONLY ${coachProviderName(store.coachProvider).uppercase()}"
        routingPreference.preferredProvider != CoachRoutingPreferenceStore.BEST -> "PREFER ${coachProviderName(routingPreference.preferredProvider).uppercase()}"
        else -> "BEST"
    }

    private fun coachProviderSummary(): String = when {
        store.coachProvider != SettingsStore.COACH_PROVIDER_AUTO ->
            "Only ${coachProviderName(store.coachProvider)} · no provider fallback"
        routingPreference.preferredProvider != CoachRoutingPreferenceStore.BEST ->
            "Prefer ${coachProviderName(routingPreference.preferredProvider)} while healthy · adaptive fallback if it slows, fails, or rate-limits"
        else ->
            "Best · automatically learns provider speed and reliability, explores alternatives, and fails over when needed"
    }

    private fun groqModelSummary() = "GPT-OSS 20B · low-latency supported model"

    private fun geminiModelSummary() = when (store.geminiModel) {
        "gemini-3.7-flash" -> "Gemini 3.7 Flash · newest quality/speed balance"
        "gemini-2.5-flash" -> "Gemini 2.5 Flash · stable balanced fallback"
        else -> "Gemini 2.5 Flash-Lite · lower-latency lightweight option"
    }

    private fun deepgramModelSummary() = if (store.deepgramModel == "nova-3") "Nova-3 · recommended live transcription" else "Nova-2 Phonecall · compatibility fallback"

    private fun chooseCoachProvider() {
        val choices = buildList {
            add(RoutingChoice("BEST — adaptive speed + reliability + automatic failover", SettingsStore.COACH_PROVIDER_AUTO, CoachRoutingPreferenceStore.BEST))
            SettingsStore.COACH_FALLBACK_ORDER.forEach { provider ->
                add(RoutingChoice("PREFER ${coachProviderName(provider)} — try first, then adaptive fallback", SettingsStore.COACH_PROVIDER_AUTO, provider))
            }
            SettingsStore.COACH_FALLBACK_ORDER.forEach { provider ->
                add(RoutingChoice("ONLY ${coachProviderName(provider)} — hard lock, no fallback", provider, CoachRoutingPreferenceStore.BEST))
            }
        }
        val selected = choices.indexOfFirst {
            it.lockedProvider == store.coachProvider &&
                (store.coachProvider != SettingsStore.COACH_PROVIDER_AUTO || it.preferredProvider == routingPreference.preferredProvider)
        }.coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("AI routing")
            .setSingleChoiceItems(choices.map { it.label }.toTypedArray(), selected) { dialog, which ->
                val choice = choices[which]
                store.coachProvider = choice.lockedProvider
                routingPreference.preferredProvider = choice.preferredProvider
                dialog.dismiss()
                onRefresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseCoachPersona() {
        val personas = CoachPersonaCatalog.all
        val labels = personas.map { "${it.label} — ${it.description}" }.toTypedArray()
        val selected = personas.indexOfFirst { it.id == store.coachPersonaId }.coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("Default coach persona")
            .setSingleChoiceItems(labels, selected) { dialog, which -> store.coachPersonaId = personas[which].id; dialog.dismiss(); onRefresh() }
            .setNeutralButton("Caller overrides") { _, _ -> activity.startActivity(Intent(activity, CoachPersonaManagerActivity::class.java)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseGroqModel() {
        val models = SettingsStore.GROQ_MODELS
        val labels = models.map { "GPT-OSS 20B — Low-latency supported model" }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Groq coach model")
            .setSingleChoiceItems(labels, models.indexOf(store.groqModel).coerceAtLeast(0)) { dialog, which -> store.groqModel = models[which]; dialog.dismiss(); onRefresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseGeminiModel() {
        val models = SettingsStore.GEMINI_MODELS
        val labels = models.map {
            when (it) {
                "gemini-3.7-flash" -> "Gemini 3.7 Flash — Newest Flash"
                "gemini-2.5-flash" -> "Gemini 2.5 Flash — Stable balanced"
                else -> "Gemini 2.5 Flash-Lite — Lightweight"
            }
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Gemini coach model")
            .setSingleChoiceItems(labels, models.indexOf(store.geminiModel).coerceAtLeast(0)) { dialog, which -> store.geminiModel = models[which]; dialog.dismiss(); onRefresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseDeepgramModel() {
        val models = SettingsStore.DEEPGRAM_MODELS
        val labels = models.map { if (it == "nova-3") "Nova-3 — Recommended" else "Nova-2 Phonecall — Compatibility" }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Deepgram transcription model")
            .setSingleChoiceItems(labels, models.indexOf(store.deepgramModel).coerceAtLeast(0)) { dialog, which -> store.deepgramModel = models[which]; dialog.dismiss(); onRefresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseAnalysisFrequency() {
        val values = intArrayOf(1, 2, 3, 5, 8, 10)
        val labels = values.map { if (it == 1) "Every turn" else "Every $it turns" }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Analysis frequency")
            .setSingleChoiceItems(labels, values.indexOf(store.analysisFrequencyTurns).coerceAtLeast(0)) { dialog, which -> store.analysisFrequencyTurns = values[which]; dialog.dismiss(); onRefresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editSupabase() {
        activity.startActivity(Intent(activity, SupabaseSetupActivity::class.java))
    }

    private fun editSecret(
        title: String,
        current: String,
        setupLabel: String? = null,
        setupUrl: String? = null,
        save: (String) -> Unit,
    ) {
        val input = EditText(activity).apply {
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSelection(length())
        }
        val body = if (setupLabel != null && setupUrl != null) {
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(input, LinearLayout.LayoutParams(-1, -2))
                addView(TextView(activity).apply {
                    text = "$setupLabel →"
                    setTextColor(cyan)
                    setPadding(0, dp(10), 0, dp(4))
                    RealityTypography.displayMedium(this, 12.5f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        runCatching {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(setupUrl)))
                        }
                    }
                }, LinearLayout.LayoutParams(-1, dp(42)))
            }
        } else {
            input
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> save(input.text.toString().trim()); onRefresh() }
            .show()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
