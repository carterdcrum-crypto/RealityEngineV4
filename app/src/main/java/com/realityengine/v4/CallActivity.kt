package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CallActivity : Activity(), SensorEventListener {
    private var call: Call? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityRegistered = false

    private lateinit var callerAvatar: ContactAvatarView
    private lateinit var incomingHeroAvatar: ContactAvatarView
    private lateinit var caller: TextView
    private lateinit var state: TextView
    private lateinit var timer: TextView
    private lateinit var answerButton: Button
    private lateinit var rejectButton: Button
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button
    private lateinit var bluetoothButton: Button
    private lateinit var holdButton: Button
    private lateinit var unhingedButton: Button
    private lateinit var flirtButton: Button
    private lateinit var recordButton: Button
    private lateinit var keypadButton: Button
    private lateinit var endButton: Button
    private lateinit var keypadContainer: LinearLayout
    private lateinit var transcript: LiveTranscriptPanelView
    private lateinit var healthStrip: TextView
    private lateinit var preCallBriefing: PreCallBriefingView
    private lateinit var analysis: TextView
    private lateinit var responseCoach: TextView
    private lateinit var responseCoachCards: ResponseCoachCardsView
    private lateinit var groqUsage: TextView
    private lateinit var acousticBar: ProgressBar
    private lateinit var linguisticBar: ProgressBar
    private lateinit var factualBar: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var connectedStartedAt: Long? = null
    private var finishScheduled = false
    private var lastNumber: String? = null
    private var restoreKeypadOpen = false
    private var createdAtElapsed = 0L
    private var lastCoachPhase: ResponseCoachState.Phase? = null
    private var lastBestSuggestion: String? = null
    private var lastRenderedCallState: Int? = null
    private var usageRecorded = false

    private val bg = RealityVisuals.Colors.Background
    private val panel = RealityVisuals.Colors.Panel
    private val cyan = RealityVisuals.Colors.Cyan
    private val magenta = RealityVisuals.Colors.Magenta
    private val green = RealityVisuals.Colors.Green
    private val muted = RealityVisuals.Colors.TextDim

    private val registryListener: () -> Unit = { runOnUiThread { refresh() } }
    private val coachListener: (ResponseCoachState.Snapshot) -> Unit = { snapshot -> runOnUiThread { renderCoach(snapshot) } }
    private val transcriptListener: (LiveTranscriptState.State) -> Unit = { snapshot -> runOnUiThread { renderTranscript(snapshot) } }
    private val healthListener: (CallSessionHealthState.Snapshot) -> Unit = { snapshot -> runOnUiThread { renderHealth(snapshot) } }
    private val timerTick = object : Runnable {
        override fun run() {
            updateTimer()
            renderLiveSignals()
            refreshRecordingUi()
            renderHealth(CallSessionHealthState.snapshot())
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        createdAtElapsed = SystemClock.elapsedRealtime()
        connectedStartedAt = savedInstanceState?.getLong(KEY_CONNECTED_AT)?.takeIf { it > 0 }
        restoreKeypadOpen = savedInstanceState?.getBoolean(KEY_KEYPAD_OPEN, false) == true
        buildUi()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        connectedStartedAt?.let { outState.putLong(KEY_CONNECTED_AT, it) }
        outState.putBoolean(KEY_KEYPAD_OPEN, ::keypadContainer.isInitialized && keypadContainer.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        CallSessionRegistry.addListener(registryListener)
        ResponseCoachState.addListener(coachListener)
        LiveTranscriptState.addListener(transcriptListener)
        CallSessionHealthState.addListener(healthListener)
        refresh()
        handler.removeCallbacks(timerTick)
        handler.post(timerTick)
        updateProximityRegistration()
    }

    override fun onPause() {
        call?.stopDtmfTone()
        unregisterProximity()
        restoreScreen()
        handler.removeCallbacks(timerTick)
        CallSessionRegistry.removeListener(registryListener)
        ResponseCoachState.removeListener(coachListener)
        LiveTranscriptState.removeListener(transcriptListener)
        CallSessionHealthState.removeListener(healthListener)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val near = event.values.firstOrNull()?.let { it < event.sensor.maximumRange } == true
        if (shouldUseProximity()) setScreenDimmed(near) else restoreScreen()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun shouldUseProximity(): Boolean {
        val current = call ?: return false
        val audio = RealityInCallService.instance?.callAudioState
        val earpiece = audio == null || audio.route == CallAudioState.ROUTE_EARPIECE
        return current.state == Call.STATE_ACTIVE && earpiece
    }

    private fun updateProximityRegistration() {
        if (shouldUseProximity()) {
            if (!proximityRegistered) {
                proximitySensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                    proximityRegistered = true
                }
            }
        } else {
            unregisterProximity()
            restoreScreen()
        }
    }

    private fun unregisterProximity() {
        if (proximityRegistered) {
            sensorManager?.unregisterListener(this)
            proximityRegistered = false
        }
    }

    private fun setScreenDimmed(dim: Boolean) {
        val lp = window.attributes
        val target = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (lp.screenBrightness != target) {
            lp.screenBrightness = target
            window.attributes = lp
        }
    }

    private fun restoreScreen() = setScreenDimmed(false)

    private fun shapeRadius() = when (getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("buttonShape", 1)) {
        0 -> 3f
        2 -> 30f
        else -> 14f
    }

    private fun neon(fill: Int = panel, stroke: Int = cyan, r: Float = shapeRadius()) =
        RealityVisuals.panel(this, fill = fill, stroke = stroke, radiusDp = r)

    private fun control(label: String, iconRes: Int, stroke: Int = RealityVisuals.Colors.Border, destructive: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        RealityVisuals.styleControl(this, iconRes, accent = stroke, destructive = destructive, radiusDp = shapeRadius())
        setOnClickListener { action() }
    }

    private fun signalRow(label: String, accent: Int): Pair<LinearLayout, ProgressBar> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = "●  $label"
            RealityVisuals.styleMicroLabel(this, accent)
        }, LinearLayout.LayoutParams(94.dp(), 24.dp()))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progress = 0
            RealityVisuals.styleSignal(this, accent)
        }
        row.addView(bar, LinearLayout.LayoutParams(0, 7.dp(), 1f))
        return row to bar
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            setBackgroundColor(bg)
        }

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = neon(fill = RealityVisuals.Colors.BackgroundRaised, stroke = RealityVisuals.Colors.Border, r = 14f)
            setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
        }
        callerAvatar = ContactAvatarView(this).apply { bind(-1L, "?", cyan) }
        identity.addView(callerAvatar, LinearLayout.LayoutParams(42.dp(), 42.dp()))

        val callerStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp(), 0, 6.dp(), 0)
        }
        callerStack.addView(TextView(this).apply {
            text = "ACTIVE CONTACT"
            RealityVisuals.styleMicroLabel(this, magenta)
        })
        caller = TextView(this).apply {
            textSize = 17f
            setTextColor(cyan)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            maxLines = 1
        }
        callerStack.addView(caller)
        identity.addView(callerStack, LinearLayout.LayoutParams(0, 52.dp(), 1f))

        val telemetry = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        state = TextView(this).apply {
            textSize = 8.5f
            letterSpacing = .09f
            setTextColor(green)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(this@CallActivity, fill = Color.rgb(6, 28, 22), stroke = green, radiusDp = 20f)
            setPadding(8.dp(), 3.dp(), 8.dp(), 3.dp())
        }
        timer = TextView(this).apply {
            text = "00:00"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            setPadding(0, 3.dp(), 0, 0)
        }
        telemetry.addView(state)
        telemetry.addView(timer)
        identity.addView(telemetry, LinearLayout.LayoutParams(118.dp(), 52.dp()))
        root.addView(identity, LinearLayout.LayoutParams(-1, 66.dp()).apply { setMargins(0, 0, 0, 8.dp()) })

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

        incomingHeroAvatar = ContactAvatarView(this).apply {
            bind(-1L, "?", cyan)
            visibility = View.GONE
        }
        root.addView(incomingHeroAvatar, LinearLayout.LayoutParams(118.dp(), 118.dp()).apply { setMargins(0, 3.dp(), 0, 8.dp()) })
        preCallBriefing = PreCallBriefingView(this).apply { visibility = View.GONE }
        root.addView(preCallBriefing, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 7.dp()) })

        val workspace = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val transcriptHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
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

        val coachPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = neon(fill = Color.rgb(7, 15, 24), stroke = magenta, r = 10f)
            setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
        }
        val coachHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        coachHeader.addView(TextView(this).apply {
            text = "RESPONSE COACH"
            RealityVisuals.styleMicroLabel(this, magenta)
        }, LinearLayout.LayoutParams(0, 20.dp(), 1f))
        coachHeader.addView(TextView(this).apply {
            text = "BEST + RANKED OPTIONS"
            RealityVisuals.styleMicroLabel(this, muted)
            gravity = Gravity.END
        })
        coachPanel.addView(coachHeader)

        responseCoach = TextView(this).apply {
            text = "STANDBY\nListening for the next caller turn."
            textSize = 10.5f
            setTextColor(RealityVisuals.Colors.Text)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP
            movementMethod = ScrollingMovementMethod.getInstance()
            isVerticalScrollBarEnabled = true
            setLineSpacing(2f, 1.06f)
        }
        coachPanel.addView(responseCoach, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(0, 4.dp(), 0, 2.dp()) })
        responseCoachCards = ResponseCoachCardsView(this).apply { visibility = View.GONE }
        coachPanel.addView(responseCoachCards, LinearLayout.LayoutParams(-1, 70.dp()))
        workspace.addView(coachPanel, LinearLayout.LayoutParams(-1, 184.dp()).apply { setMargins(0, 0, 0, 5.dp()) })

        groqUsage = TextView(this).apply {
            text = "GROQ // WAITING FOR FIRST COACH REQUEST"
            textSize = 8.5f
            letterSpacing = .04f
            setTextColor(Color.rgb(151, 218, 232))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL
            background = neon(RealityVisuals.Colors.BackgroundRaised, Color.rgb(15, 65, 80), 8f)
            setPadding(10.dp(), 0, 10.dp(), 0)
        }
        workspace.addView(groqUsage, LinearLayout.LayoutParams(-1, 30.dp()).apply { setMargins(0, 0, 0, 6.dp()) })

        val signals = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = neon(RealityVisuals.Colors.Panel, RealityVisuals.Colors.Border, 10f)
            setPadding(10.dp(), 6.dp(), 10.dp(), 5.dp())
            isClickable = true
            setOnClickListener { showSignalExplanation() }
        }
        signals.addView(TextView(this).apply {
            text = "LIVE SIGNALS"
            RealityVisuals.styleMicroLabel(this, cyan)
        }, LinearLayout.LayoutParams(-1, 18.dp()))
        val acoustic = signalRow("ACOUSTIC", cyan); acousticBar = acoustic.second; signals.addView(acoustic.first)
        val linguistic = signalRow("LINGUISTIC", magenta); linguisticBar = linguistic.second; signals.addView(linguistic.first)
        val factual = signalRow("FACTUAL", green); factualBar = factual.second; signals.addView(factual.first)
        workspace.addView(signals, LinearLayout.LayoutParams(-1, 96.dp()).apply { setMargins(0, 0, 0, 5.dp()) })

        analysis = TextView(this).apply {
            text = "NEXT ACTION  // STANDBY"
            textSize = 10f
            letterSpacing = .04f
            setTextColor(cyan)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            background = neon(RealityVisuals.Colors.PanelStrong, cyan, 10f)
            setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
        }
        workspace.addView(analysis, LinearLayout.LayoutParams(-1, 40.dp()))
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))

        val incoming = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        answerButton = control("Accept", R.drawable.ic_re_call, green) { call?.takeIf { it.state == Call.STATE_RINGING }?.answer(0) }
        rejectButton = control("Decline", R.drawable.ic_re_call_end, magenta, destructive = true) { call?.takeIf { it.state == Call.STATE_RINGING }?.reject(false, null) }
        incoming.addView(answerButton, buttonLayout(50)); incoming.addView(rejectButton, buttonLayout(50)); root.addView(incoming)

        val controls = GridLayout(this).apply { columnCount = 4; alignmentMode = GridLayout.ALIGN_BOUNDS; useDefaultMargins = false }
        muteButton = control("Mute", R.drawable.ic_re_mic) { toggleMute() }
        speakerButton = control("Speaker", R.drawable.ic_re_speaker) { toggleSpeaker() }
        bluetoothButton = control("Bluetooth", R.drawable.ic_re_bluetooth) { toggleBluetooth() }
        holdButton = control("Hold", R.drawable.ic_re_hold) { toggleHold() }
        arrayOf(muteButton, speakerButton, bluetoothButton, holdButton).forEach {
            controls.addView(it, GridLayout.LayoutParams().apply {
                width = 0; height = 48.dp(); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            })
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val quickActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        unhingedButton = control("Unhinged", R.drawable.ic_re_star, magenta) { requestQuickCoach(CoachQuickModeCatalog.UNHINGED) }
        flirtButton = control("Flirt", R.drawable.ic_re_star, cyan) { requestQuickCoach(CoachQuickModeCatalog.FLIRT) }
        quickActions.addView(unhingedButton, buttonLayout(48)); quickActions.addView(flirtButton, buttonLayout(48)); root.addView(quickActions)

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        recordButton = control("Record", R.drawable.ic_re_record, magenta) { requestRecording() }
        keypadButton = control("Keypad", R.drawable.ic_re_dialpad) {
            keypadContainer.visibility = if (keypadContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            RealityVisuals.reveal(keypadContainer)
        }
        endButton = control("End call", R.drawable.ic_re_call_end, magenta, destructive = true) { call?.disconnect() }
        bottom.addView(recordButton, buttonLayout(50)); bottom.addView(keypadButton, buttonLayout(50)); bottom.addView(endButton, buttonLayout(50)); root.addView(bottom)

        keypadContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (restoreKeypadOpen) View.VISIBLE else View.GONE
            background = neon(RealityVisuals.Colors.BackgroundRaised, RealityVisuals.Colors.Border, 10f)
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
        }
        val digits = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        digits.forEach { digit ->
            grid.addView(Button(this).apply {
                text = digit; textSize = 17f; setTextColor(cyan); typeface = Typeface.MONOSPACE
                background = neon(panel, RealityVisuals.Colors.Border); stateListAnimator = null; minWidth = 0; minHeight = 0
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { call?.playDtmfTone(digit[0]); true }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { call?.stopDtmfTone(); performClick(); true }
                        else -> false
                    }
                }
            }, GridLayout.LayoutParams().apply {
                width = 0; height = 44.dp(); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(2.dp(), 2.dp(), 2.dp(), 2.dp())
            })
        }
        keypadContainer.addView(grid)
        root.addView(keypadContainer)

        setContentView(root)
        renderCoach(ResponseCoachState.current())
        renderLiveSignals()
        renderTranscript(LiveTranscriptState.snapshot())
    }

    private fun buttonLayout(heightDp: Int) = LinearLayout.LayoutParams(0, heightDp.dp(), 1f).apply { setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp()) }

    private fun renderTranscript(snapshot: LiveTranscriptState.State) {
        if (!::transcript.isInitialized) return
        transcript.render(snapshot)
    }

    private fun renderCoach(snapshot: ResponseCoachState.Snapshot) {
        renderGroqUsage(snapshot)
        val phaseChanged = lastCoachPhase != snapshot.phase
        lastCoachPhase = snapshot.phase
        val best = snapshot.best
        if (best == null) {
            responseCoachCards.render(emptyList())
            val chosen = snapshot.chosen
            responseCoach.text = when (snapshot.phase) {
                ResponseCoachState.Phase.ANALYZING -> "ANALYZING\n${snapshot.message ?: "Generating replies…"}"
                ResponseCoachState.Phase.LISTENING -> "LISTENING\n${snapshot.message ?: "Waiting for the next caller turn"}"
                ResponseCoachState.Phase.KEY_REQUIRED -> "AI PROVIDER REQUIRED\nOpen Settings → Coach provider"
                ResponseCoachState.Phase.DISABLED -> "COACH DISABLED\nEnable Response Coach in Settings"
                ResponseCoachState.Phase.ERROR -> "COACH ERROR\n${snapshot.message ?: "Suggestion request failed"}"
                else -> if (chosen?.suggestion != null) "LAST // ${chosen.classification} · ${chosen.suggestion.mode}\n${chosen.suggestion.text}" else "STANDBY\nListening for the next caller turn."
            }
            if (phaseChanged && snapshot.phase == ResponseCoachState.Phase.ANALYZING) RealityVisuals.pulseOnce(responseCoach)
            return
        }
        responseCoach.scrollTo(0, 0)
        responseCoach.text = buildString {
            append("BEST // ${best.mode} · TONE ${best.tone}\n"); append(best.text)
            if (best.reason.isNotBlank()) append("\nWHY // ${best.reason}")
        }
        responseCoachCards.render(snapshot.alternatives)
        analysis.text = "NEXT ACTION  // ${best.mode} · ${best.tone}"
        if (lastBestSuggestion != best.text) {
            lastBestSuggestion = best.text
            RealityVisuals.reveal(responseCoach); RealityVisuals.reveal(responseCoachCards); RealityVisuals.reveal(analysis)
        }
    }

    private fun renderGroqUsage(snapshot: ResponseCoachState.Snapshot) {
        if (!::groqUsage.isInitialized) return
        val provider = snapshot.provider.ifBlank { CallSessionHealthState.snapshot().coachProvider }.ifBlank { "WAITING" }
        val model = shortGroqModel(snapshot.groqModel)
        val latency = snapshot.coachLatencyMs.takeIf { it > 0 }?.let { " · ${it}ms" }.orEmpty()
        val rate = if (provider.equals("GROQ", true) && snapshot.groqRemainingTokens != null && snapshot.groqLimitTokens != null) " · TPM ${compactTokens(snapshot.groqRemainingTokens)}/${compactTokens(snapshot.groqLimitTokens)}" else ""
        groqUsage.text = "COACH // $provider · $model$latency$rate · CALL ${compactTokens(snapshot.callTotalTokens)}"
    }

    private fun shortGroqModel(model: String) = when (model) {
        "openai/gpt-oss-20b" -> "GPT-OSS 20B"
        "llama-3.3-70b-versatile" -> "LLAMA 3.3 70B"
        "llama-3.1-8b-instant" -> "LLAMA 3.1 8B"
        "" -> "WAITING"
        else -> model.substringAfterLast('/').uppercase().take(22)
    }

    private fun compactTokens(value: Int): String = when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format("%.1fK", value / 1_000f)
        else -> value.toString()
    }

    private fun renderHealth(snapshot: CallSessionHealthState.Snapshot) {
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
            .setMessage(SignalExplanation.lines(LiveSignalState.snapshot()).joinToString("\n\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderLiveSignals() {
        val signal = LiveSignalState.snapshot()
        RealityVisuals.animateSignal(acousticBar, signal.acoustic); RealityVisuals.animateSignal(linguisticBar, signal.linguistic); RealityVisuals.animateSignal(factualBar, signal.factual)
        val coach = ResponseCoachState.current(); val best = coach.best
        analysis.text = when {
            best != null -> "NEXT ACTION  // ${best.mode} · ${best.tone}  |  FUSED ${signal.combined}%"
            coach.phase == ResponseCoachState.Phase.ANALYZING -> "NEXT ACTION  // ANALYZING  |  FUSED ${signal.combined}%"
            coach.phase == ResponseCoachState.Phase.ERROR -> "NEXT ACTION  // COACH ERROR  |  FUSED ${signal.combined}%"
            coach.phase == ResponseCoachState.Phase.KEY_REQUIRED -> "NEXT ACTION  // GROQ KEY REQUIRED"
            coach.phase == ResponseCoachState.Phase.DISABLED -> "NEXT ACTION  // COACH DISABLED"
            else -> "NEXT ACTION  // FUSED ${signal.combined}% · ${signal.elevatedStreams}/3 ELEVATED"
        }
    }

    fun updateLiveSignals(acoustic: Int, linguistic: Int, factual: Int, nextAction: String? = null) {
        runOnUiThread {
            RealityVisuals.animateSignal(acousticBar, acoustic); RealityVisuals.animateSignal(linguisticBar, linguistic); RealityVisuals.animateSignal(factualBar, factual)
            analysis.text = "NEXT ACTION  // ${nextAction?.takeIf { it.isNotBlank() } ?: "STANDBY"}"
        }
    }

    private fun requestQuickCoach(modeId: String) {
        val current = call
        if (current?.state != Call.STATE_ACTIVE && current?.state != Call.STATE_HOLDING) {
            Toast.makeText(this, "Quick coach is available once the call is connected", Toast.LENGTH_SHORT).show()
            return
        }
        val mode = CoachQuickModeCatalog.byId(modeId) ?: return
        if (LiveCoachQuickActions.request(mode.id)) {
            val button = if (mode.id == CoachQuickModeCatalog.UNHINGED) unhingedButton else flirtButton
            RealityVisuals.pulseOnce(button)
            Toast.makeText(this, "${mode.label} coach refresh", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Response coach session is not ready yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestRecording() {
        val service = RealityInCallService.instance ?: return
        if (service.recordingActive()) {
            Toast.makeText(this, "REC is active. Save or delete it after the call.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Record this call?")
            .setMessage("Reality Engine will visibly record the call-audio stream until hangup, then ask you to Save or Permanently Delete it. Only record where recording is permitted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start recording") { _, _ ->
                if (!service.startRecording()) Toast.makeText(this, "Call audio recording is not available right now", Toast.LENGTH_SHORT).show()
                refreshRecordingUi()
            }
            .show()
    }

    private fun toggleMute() {
        val service = RealityInCallService.instance ?: return
        val isNowMuted = !service.isMutedNow(); service.setMuted(isNowMuted); muteButton.text = if (isNowMuted) "Unmute" else "Mute"; setControlActive(muteButton, isNowMuted)
    }

    private fun toggleSpeaker() {
        val service = RealityInCallService.instance ?: return
        val audio = service.callAudioState ?: return
        val target = if (audio.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        if (audio.supportedRouteMask and target != 0) service.setAudioRoute(target)
        refreshAudioButtons(); updateProximityRegistration()
    }

    private fun toggleBluetooth() {
        val service = RealityInCallService.instance ?: return
        val audio = service.callAudioState ?: return
        val bluetooth = CallAudioState.ROUTE_BLUETOOTH
        val fallback = if (audio.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        val target = if (audio.route == bluetooth) fallback else bluetooth
        if (audio.supportedRouteMask and target != 0) service.setAudioRoute(target)
        refreshAudioButtons(); updateProximityRegistration()
    }

    private fun toggleHold() {
        val current = call ?: return
        when (current.state) { Call.STATE_ACTIVE -> current.hold(); Call.STATE_HOLDING -> current.unhold() }
        updateProximityRegistration()
    }

    private fun refresh() {
        call = CallSessionRegistry.primary()
        val current = call
        if (current == null) { unregisterProximity(); restoreScreen(); scheduleFinish(); return }
        handler.removeCallbacks(finishRunnable); finishScheduled = false
        val number = current.details?.handle?.schemeSpecificPart ?: "UNKNOWN CALLER"
        if (number != lastNumber) {
            lastNumber = number
            val match = ContactMediaStore.findByNumber(this, number)
            val label = match?.name?.takeIf { it.isNotBlank() } ?: number
            caller.text = label
            callerAvatar.bind(match?.contactId ?: -1L, label, cyan)
            incomingHeroAvatar.bind(match?.contactId ?: -1L, label, cyan)
            RealityVisuals.reveal(callerAvatar)
            transcript.bindPhone(number)
            preCallBriefing.bind(number, label)
        }

        if (lastRenderedCallState != current.state) { lastRenderedCallState = current.state; RealityVisuals.pulseOnce(state) }
        if (current.state == Call.STATE_DISCONNECTED) {
            if (!usageRecorded) {
                connectedStartedAt?.let { RuntimeUsageStore(this).recordCall(SystemClock.elapsedRealtime() - it) }
                usageRecorded = true
            }
            connectedStartedAt = null
            scheduleFinish()
        } else finishScheduled = false

        val ringing = current.state == Call.STATE_RINGING
        val preConnect = ringing || current.state == Call.STATE_DIALING || current.state == Call.STATE_CONNECTING
        preCallBriefing.visibility = if (preConnect) View.VISIBLE else View.GONE
        incomingHeroAvatar.visibility = if (ringing) View.VISIBLE else View.GONE
        answerButton.visibility = if (ringing) View.VISIBLE else View.GONE
        rejectButton.visibility = if (ringing) View.VISIBLE else View.GONE
        if (ringing) RealityVisuals.reveal(incomingHeroAvatar)

        val interactive = current.state == Call.STATE_ACTIVE || current.state == Call.STATE_HOLDING
        muteButton.isEnabled = interactive; speakerButton.isEnabled = interactive; keypadButton.isEnabled = interactive; holdButton.isEnabled = interactive
        unhingedButton.isEnabled = interactive; flirtButton.isEnabled = interactive
        unhingedButton.alpha = if (interactive) 1f else .42f; flirtButton.alpha = if (interactive) 1f else .42f
        recordButton.isEnabled = current.state == Call.STATE_ACTIVE || RealityInCallService.instance?.recordingActive() == true
        if (!interactive) keypadContainer.visibility = View.GONE
        holdButton.text = if (current.state == Call.STATE_HOLDING) "Resume" else "Hold"; setControlActive(holdButton, current.state == Call.STATE_HOLDING)
        endButton.isEnabled = current.state != Call.STATE_DISCONNECTED

        if ((current.state == Call.STATE_ACTIVE || current.state == Call.STATE_HOLDING) && connectedStartedAt == null) connectedStartedAt = SystemClock.elapsedRealtime()
        updateTimer(); renderLiveSignals(); renderTranscript(LiveTranscriptState.snapshot()); renderGroqUsage(ResponseCoachState.current()); renderHealth(CallSessionHealthState.snapshot())
        val service = RealityInCallService.instance
        val mutedNow = service?.isMutedNow() == true
        muteButton.text = if (mutedNow) "Unmute" else "Mute"; setControlActive(muteButton, mutedNow)
        refreshAudioButtons(); refreshRecordingUi(); updateProximityRegistration()
    }

    private fun refreshRecordingUi() {
        if (!::recordButton.isInitialized || !::state.isInitialized) return
        val active = RealityInCallService.instance?.recordingActive() == true
        recordButton.text = if (active) "● REC" else "Record"
        val accent = if (active) magenta else cyan
        recordButton.setTextColor(accent)
        recordButton.compoundDrawableTintList = ColorStateList.valueOf(accent)
        recordButton.background = RealityVisuals.panel(this, fill = if (active) Color.rgb(36, 8, 25) else panel, stroke = if (active) magenta else RealityVisuals.Colors.Border, radiusDp = shapeRadius())
        recordButton.alpha = if (recordButton.isEnabled) 1f else .42f
        val current = call
        val base = when (current?.state) {
            Call.STATE_RINGING -> "● INCOMING"
            Call.STATE_DIALING -> "● DIALING"
            Call.STATE_CONNECTING -> "● CONNECTING"
            Call.STATE_ACTIVE -> "● LIVE"
            Call.STATE_HOLDING -> "● HOLD"
            Call.STATE_DISCONNECTED -> "● CLOSED"
            else -> "● CALL"
        }
        state.text = if (active) "$base · REC" else base
        state.setTextColor(if (active) magenta else green)
        state.background = RealityVisuals.panel(this, fill = if (active) Color.rgb(36, 8, 25) else Color.rgb(6, 28, 22), stroke = if (active) magenta else green, radiusDp = 20f)
    }

    private fun refreshAudioButtons() {
        val audio = RealityInCallService.instance?.callAudioState
        val speakerOn = audio?.route == CallAudioState.ROUTE_SPEAKER; val bluetoothOn = audio?.route == CallAudioState.ROUTE_BLUETOOTH
        speakerButton.text = if (speakerOn) "Earpiece" else "Speaker"; bluetoothButton.text = if (bluetoothOn) "BT off" else "Bluetooth"
        setControlActive(speakerButton, speakerOn); setControlActive(bluetoothButton, bluetoothOn)
        val interactive = call?.state == Call.STATE_ACTIVE || call?.state == Call.STATE_HOLDING
        bluetoothButton.isEnabled = interactive && ((audio?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0)
    }

    private fun setControlActive(button: Button, active: Boolean) {
        val accent = if (active) green else cyan
        button.setTextColor(accent); button.compoundDrawableTintList = ColorStateList.valueOf(accent)
        button.background = RealityVisuals.panel(this, fill = if (active) Color.rgb(7, 28, 24) else panel, stroke = if (active) green else RealityVisuals.Colors.Border, radiusDp = shapeRadius())
        button.alpha = if (button.isEnabled) 1f else .42f
    }

    private val finishRunnable = Runnable { finishScheduled = false; if (CallSessionRegistry.primary() == null && !isFinishing) finish() }
    private fun scheduleFinish() {
        if (finishScheduled) return
        finishScheduled = true
        val age = SystemClock.elapsedRealtime() - createdAtElapsed
        val delay = maxOf(2500L - age, 1200L)
        handler.removeCallbacks(finishRunnable); handler.postDelayed(finishRunnable, delay)
    }

    private fun updateTimer() {
        val started = connectedStartedAt
        if (started == null) { timer.text = "00:00"; return }
        val total = (SystemClock.elapsedRealtime() - started) / 1000L
        val hours = total / 3600L; val minutes = (total % 3600L) / 60L; val seconds = total % 60L
        timer.text = if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_CONNECTED_AT = "connected_started_at"
        private const val KEY_KEYPAD_OPEN = "keypad_open"
    }
}
