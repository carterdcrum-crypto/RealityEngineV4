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
import android.telecom.Call
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
        val audio = callAudioReadiness()
        root.addView(row(
            "Call audio source",
            audio.detail,
            if (audio.ready) "READY" else if (audio.attention) "BLOCKED" else "CHECK",
            if (audio.ready) green else if (audio.attention) magenta else amber,
        ) { runCallAudioCheck() })

        root.addView(section("INTELLIGENCE"))
        root.addView(row(
            "Groq API",
            if (store.groqConfigured()) "API key securely configured" else "Required for response coaching",
            if (store.groqConfigured()) "READY" else "KEY",
            if (store.groqConfigured()) green else amber,
        ) { editSecret("Groq API key", store.groqApiKey) { store.groqApiKey = it } })
        root.addView(row("Groq coach model", groqModelSummary(), "MODEL", cyan) { chooseGroqModel() })
        root.addView(row(
            "Deepgram API",
            if (store.deepgramConfigured()) "Live transcription configured" else "Required for live transcription",
            if (store.deepgramConfigured()) "READY" else "KEY",
            if (store.deepgramConfigured()) green else amber,
        ) { editSecret("Deepgram API key", store.deepgramApiKey) { store.deepgramApiKey = it } })
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

        root.addView(section("SYSTEM"))
        root.addView(row("Button geometry", "Visual shape used by primary controls", buttonShapeLabel.uppercase(), cyan, onCycleButtonShape))
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
        Readiness("Response coach", coachReady(), if (coachReady()) "Configured" else "Groq key or coach setting needed"),
        callAudioReadiness().copy(label = "Call audio"),
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
    private fun coachReady() = store.groqConfigured() && store.responseCoachEnabled

    private fun callAudioReadiness(): Readiness = when (CallAudioBridge.state(activity)) {
        CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE -> Readiness("Call audio", true, "Active call detected · tap to inspect live PCM route")
        CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED -> Readiness("Call audio", false, "Microphone permission required")
        CallAudioBridge.State.UNAVAILABLE -> Readiness("Call audio", false, "Connect Shizuku first")
        CallAudioBridge.State.SHIZUKU_READY -> Readiness("Call audio", false, "Shizuku ready · test during an active call")
        CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED -> Readiness("Call audio", false, "This phone is blocking the voice-call source", attention = true)
    }

    private fun runCallAudioCheck() {
        if (!microphoneReady()) {
            onCallAudio()
            return
        }
        if (!ShizukuAudioStatus.binderAvailable()) {
            AlertDialog.Builder(activity)
                .setTitle("Call audio unavailable")
                .setMessage("Shizuku is offline. Start Shizuku, then come back and run the call-audio check again.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Shizuku setup") { _, _ -> onShizuku() }
                .show()
            return
        }
        if (!ShizukuAudioStatus.permissionGranted()) {
            AlertDialog.Builder(activity)
                .setTitle("Shizuku authorization required")
                .setMessage("Reality Engine has not been authorized to use the Shizuku call-audio bridge yet.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Authorize") { _, _ -> onShizuku() }
                .show()
            return
        }

        val activeCall = CallSessionRegistry.primary()?.state == Call.STATE_ACTIVE
        if (!activeCall) {
            AlertDialog.Builder(activity)
                .setTitle("Call-audio check ready")
                .setMessage(
                    "Microphone permission and Shizuku are ready. Android's protected cellular PCM source can only be verified while a cellular call is active.\n\nStart or answer a call, then return to Settings and tap Call audio source again."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AudioRouteState.diagnose(activity)
        val snapshot = AudioRouteState.snapshot()
        val serviceRunning = RealityInCallService.instance != null
        val routeSelected = snapshot.route == AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL && snapshot.canTranscribe
        val pipelineFailed = snapshot.route == AudioCaptureRouter.Route.UNAVAILABLE &&
            snapshot.reason.startsWith("Live transcription stopped", ignoreCase = true)

        val title = when {
            pipelineFailed -> "Call-audio check failed"
            routeSelected && serviceRunning -> "Live call-audio route active"
            !serviceRunning -> "Call service not active"
            else -> "Call-audio route not ready"
        }
        val detail = when {
            pipelineFailed -> snapshot.reason
            routeSelected && serviceRunning -> "Shizuku privileged PCM is the selected live route.\n\n${snapshot.detail}"
            !serviceRunning -> "Reality Engine can see an active call, but its InCallService is not active. Make sure Reality Engine is still the default phone app."
            else -> "${snapshot.reason}\n\n${snapshot.detail}"
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(detail)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun groqModelSummary() = when (store.groqModel) {
        "openai/gpt-oss-20b" -> "GPT-OSS 20B · balanced"
        "llama-3.3-70b-versatile" -> "Llama 3.3 70B · higher language quality"
        else -> "Llama 3.1 8B · lowest latency"
    }

    private fun deepgramModelSummary() = if (store.deepgramModel == "nova-3") "Nova-3 · recommended live transcription" else "Nova-2 Phonecall · compatibility fallback"

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
        val labels = models.map {
            when (it) {
                "openai/gpt-oss-20b" -> "GPT-OSS 20B — Balanced"
                "llama-3.3-70b-versatile" -> "Llama 3.3 70B — Higher quality"
                else -> "Llama 3.1 8B — Fastest"
            }
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Groq coach model")
            .setSingleChoiceItems(labels, models.indexOf(store.groqModel).coerceAtLeast(0)) { dialog, which -> store.groqModel = models[which]; dialog.dismiss(); onRefresh() }
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

    private fun editSecret(title: String, current: String, save: (String) -> Unit) {
        val input = EditText(activity).apply {
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSelection(length())
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> save(input.text.toString().trim()); onRefresh() }
            .show()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
