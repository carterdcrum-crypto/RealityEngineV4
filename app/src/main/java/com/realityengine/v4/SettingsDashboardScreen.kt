package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.telecom.TelecomManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Visual settings dashboard for Reality Engine V4.
 *
 * The screen keeps configuration actions grouped by intent and surfaces the
 * same real readiness signals used by onboarding, so setup state is visible
 * without opening individual rows.
 */
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
    private data class Readiness(
        val label: String,
        val ready: Boolean,
        val detail: String,
        val attention: Boolean = false,
    )

    private val cyan = RealityVisuals.Colors.Cyan
    private val magenta = RealityVisuals.Colors.Magenta
    private val green = RealityVisuals.Colors.Green
    private val amber = RealityVisuals.Colors.Amber
    private val text = RealityVisuals.Colors.Text
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
        root.addView(readinessPanel(), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, dp(12))
        })

        root.addView(section("SETUP"))
        root.addView(row(
            title = "Setup walkthrough",
            subtitle = "Guided phone, permission, audio and AI setup",
            status = "GUIDE",
            statusColor = cyan,
            click = onWalkthrough,
        ))
        root.addView(row(
            title = "Default phone application",
            subtitle = defaultPhoneDetail(),
            status = if (isDefaultPhone()) "READY" else "SETUP",
            statusColor = if (isDefaultPhone()) green else amber,
            click = onDefaultPhone,
        ))
        root.addView(row(
            title = "Shizuku call audio",
            subtitle = shizukuDetail(),
            status = if (shizukuReady()) "READY" else "SETUP",
            statusColor = if (shizukuReady()) green else amber,
            click = onShizuku,
        ))
        val audio = callAudioReadiness()
        root.addView(row(
            title = "Call audio source",
            subtitle = audio.detail,
            status = if (audio.ready) "READY" else if (audio.attention) "BLOCKED" else "CHECK",
            statusColor = if (audio.ready) green else if (audio.attention) magenta else amber,
            click = onCallAudio,
        ))

        root.addView(section("INTELLIGENCE"))
        root.addView(row(
            title = "Groq API",
            subtitle = if (store.groqConfigured()) "API key securely configured" else "Required for response coaching",
            status = if (store.groqConfigured()) "READY" else "KEY",
            statusColor = if (store.groqConfigured()) green else amber,
        ) { editSecret("Groq API key", store.groqApiKey) { store.groqApiKey = it } })
        root.addView(row(
            title = "Groq coach model",
            subtitle = groqModelSummary(),
            status = "MODEL",
            statusColor = cyan,
        ) { chooseGroqModel() })
        root.addView(row(
            title = "Deepgram API",
            subtitle = if (store.deepgramConfigured()) "Live transcription configured" else "Required for live transcription",
            status = if (store.deepgramConfigured()) "READY" else "KEY",
            statusColor = if (store.deepgramConfigured()) green else amber,
        ) { editSecret("Deepgram API key", store.deepgramApiKey) { store.deepgramApiKey = it } })
        root.addView(row(
            title = "Deepgram model",
            subtitle = deepgramModelSummary(),
            status = "MODEL",
            statusColor = cyan,
        ) { chooseDeepgramModel() })
        root.addView(row(
            title = "Supabase caller memory",
            subtitle = if (store.supabaseConfigured()) "Caller profile sync configured" else "Optional cloud caller-profile memory",
            status = if (store.supabaseConfigured()) "READY" else "OPTIONAL",
            statusColor = if (store.supabaseConfigured()) green else muted,
        ) { editSupabase() })

        root.addView(section("LIVE BEHAVIOR"))
        root.addView(toggleRow(
            title = "Response coach",
            subtitle = "Generate ranked response suggestions during calls",
            enabled = store.responseCoachEnabled,
        ) {
            store.responseCoachEnabled = !store.responseCoachEnabled
            onRefresh()
        })
        root.addView(toggleRow(
            title = "Haptic alerts",
            subtitle = "Quiet vibration when multiple signals are elevated",
            enabled = store.hapticsEnabled,
        ) {
            store.hapticsEnabled = !store.hapticsEnabled
            onRefresh()
        })
        root.addView(row(
            title = "Analysis frequency",
            subtitle = "Run coaching every ${store.analysisFrequencyTurns} ${if (store.analysisFrequencyTurns == 1) "turn" else "turns"}",
            status = "${store.analysisFrequencyTurns}×",
            statusColor = cyan,
        ) { chooseAnalysisFrequency() })

        root.addView(section("SYSTEM"))
        root.addView(row(
            title = "Button geometry",
            subtitle = "Visual shape used by primary controls",
            status = buttonShapeLabel.uppercase(),
            statusColor = cyan,
            click = onCycleButtonShape,
        ))
        root.addView(row(
            title = "Android phone settings",
            subtitle = "Manage system calling apps and defaults",
            status = "ANDROID",
            statusColor = muted,
            click = onAndroidPhoneSettings,
        ))

        root.addView(section("APP"))
        root.addView(row(
            title = "App updates",
            subtitle = "Check the private GitHub release for the newest green build",
            status = "CHECK",
            statusColor = cyan,
            click = onCheckUpdate,
        ))
        root.addView(row(
            title = "Private update access",
            subtitle = if (store.privateUpdaterConfigured()) "GitHub updater token configured" else "GitHub token required for private updates",
            status = if (store.privateUpdaterConfigured()) "READY" else "TOKEN",
            statusColor = if (store.privateUpdaterConfigured()) green else amber,
        ) { editSecret("GitHub updater token", store.githubUpdaterToken) { store.githubUpdaterToken = it } })
        root.addView(row(
            title = "About Reality Engine",
            subtitle = "Capabilities, design intent and signal guidance",
            status = "INFO",
            statusColor = magenta,
            click = onAbout,
        ))

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
                setTextColor(text)
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
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = RealityVisuals.panel(activity, fill = raised, stroke = border, radiusDp = 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        wrapper.addView(LinearLayout(activity).apply {
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

        wrapper.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = items.size
            progress = readyCount
            progressTintList = ColorStateList.valueOf(if (readyCount == items.size) green else cyan)
            progressBackgroundTintList = ColorStateList.valueOf(RealityVisuals.Colors.Track)
        }, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0, dp(2), 0, dp(8)) })

        items.forEach { item -> wrapper.addView(readinessLine(item)) }
        return wrapper
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
            setTextColor(text)
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

    private fun toggleRow(
        title: String,
        subtitle: String,
        enabled: Boolean,
        click: () -> Unit,
    ): View = row(
        title = title,
        subtitle = subtitle,
        status = if (enabled) "ON" else "OFF",
        statusColor = if (enabled) green else muted,
        click = click,
    )

    private fun row(
        title: String,
        subtitle: String,
        status: String,
        statusColor: Int,
        click: () -> Unit,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(10), dp(10))
        background = RealityVisuals.panel(activity, fill = panel, stroke = border, radiusDp = 12f)
        isClickable = true
        isFocusable = true

        val textStack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textStack.addView(TextView(activity).apply {
            text = title
            setTextColor(text)
            RealityTypography.displayMedium(this, 14.5f)
        })
        textStack.addView(TextView(activity).apply {
            text = subtitle
            setTextColor(muted)
            setPadding(0, dp(3), dp(8), 0)
            RealityTypography.display(this, 11.5f)
            maxLines = 2
        })
        addView(textStack, LinearLayout.LayoutParams(0, -2, 1f))

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

        setOnClickListener {
            RealityVisuals.reveal(this)
            click()
        }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(4), 0, dp(4))
        }
        it.minimumHeight = dp(72)
    }

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

    private fun defaultPhoneDetail(): String =
        if (isDefaultPhone()) "Reality Engine is the active phone app" else "Choose Reality Engine as the default phone app"

    private fun microphoneReady(): Boolean =
        activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun shizukuReady(): Boolean =
        ShizukuAudioStatus.binderAvailable() && ShizukuAudioStatus.permissionGranted()

    private fun shizukuDetail(): String = when {
        !ShizukuAudioStatus.binderAvailable() -> "Shizuku is offline"
        !ShizukuAudioStatus.permissionGranted() -> "Authorization required"
        else -> "Connected and authorized"
    }

    private fun coachReady(): Boolean = store.groqConfigured() && store.responseCoachEnabled

    private fun callAudioReadiness(): Readiness = when (CallAudioBridge.state(activity)) {
        CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE ->
            Readiness("Call audio", true, "Supported voice-call audio source available")
        CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED ->
            Readiness("Call audio", false, "Microphone permission required")
        CallAudioBridge.State.UNAVAILABLE ->
            Readiness("Call audio", false, "Connect Shizuku first")
        CallAudioBridge.State.SHIZUKU_READY ->
            Readiness("Call audio", false, "Run the call-audio check")
        CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED ->
            Readiness("Call audio", false, "This phone is blocking the voice-call source", attention = true)
    }

    private fun groqModelSummary(): String = when (store.groqModel) {
        "openai/gpt-oss-20b" -> "GPT-OSS 20B · balanced"
        "llama-3.3-70b-versatile" -> "Llama 3.3 70B · higher language quality"
        else -> "Llama 3.1 8B · lowest latency"
    }

    private fun deepgramModelSummary(): String = when (store.deepgramModel) {
        "nova-3" -> "Nova-3 · recommended live transcription"
        else -> "Nova-2 Phonecall · compatibility fallback"
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
        val selected = models.indexOf(store.groqModel).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("Groq coach model")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                store.groqModel = models[which]
                dialog.dismiss()
                onRefresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseDeepgramModel() {
        val models = SettingsStore.DEEPGRAM_MODELS
        val labels = models.map {
            if (it == "nova-3") "Nova-3 — Recommended" else "Nova-2 Phonecall — Compatibility"
        }.toTypedArray()
        val selected = models.indexOf(store.deepgramModel).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("Deepgram transcription model")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                store.deepgramModel = models[which]
                dialog.dismiss()
                onRefresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseAnalysisFrequency() {
        val values = intArrayOf(1, 2, 3, 5, 8, 10)
        val labels = values.map { if (it == 1) "Every turn" else "Every $it turns" }.toTypedArray()
        val selected = values.indexOf(store.analysisFrequencyTurns).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("Analysis frequency")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                store.analysisFrequencyTurns = values[which]
                dialog.dismiss()
                onRefresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editSupabase() {
        val input = EditText(activity).apply {
            setText(store.supabaseUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelection(length())
        }
        AlertDialog.Builder(activity)
            .setTitle("Supabase URL")
            .setMessage("Set the project URL. The anon key can remain in secure storage if already configured.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                store.supabaseUrl = input.text.toString().trim()
                onRefresh()
            }
            .show()
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
            .setPositiveButton("Save") { _, _ ->
                save(input.text.toString().trim())
                onRefresh()
            }
            .show()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
