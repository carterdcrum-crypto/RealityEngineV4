package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi

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
    private lateinit var callScroll: ScrollView
    private lateinit var pulseMenuButton: Button
    private lateinit var answerButton: Button
    private lateinit var rejectButton: Button
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button
    private lateinit var bluetoothButton: Button
    private lateinit var holdButton: Button
    private lateinit var unhingedButton: Button
    private lateinit var flirtButton: Button
    private lateinit var soundboardButton: Button
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
    private lateinit var coachModeChip: TextView
    private lateinit var coachToneChip: TextView
    private lateinit var coachWhyButton: Button
    private lateinit var coachExpandButton: Button
    private lateinit var groqUsage: TextView
    private lateinit var soundboardStore: SoundboardStore
    private lateinit var soundboardPlayer: CallSoundboardPlayer

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
    private var alternativesExpanded = false
    private var observedCall = false
    private var postCallTransitionScheduled = false
    private var postCallTransitionStarted = false

    private val bg = PulseDeckVisuals.Colors.Background
    private val cyan = PulseDeckVisuals.Colors.Cyan
    private val magenta = PulseDeckVisuals.Colors.Coral
    private val green = PulseDeckVisuals.Colors.Green
    private val muted = PulseDeckVisuals.Colors.TextDim

    private val registryListener: () -> Unit = { runOnUiThread { refresh() } }
    private val coachListener: (ResponseCoachState.Snapshot) -> Unit = { snapshot -> runOnUiThread { renderCoach(snapshot) } }
    private val transcriptListener: (LiveTranscriptState.State) -> Unit = { snapshot ->
        runOnUiThread {
            renderTranscript(snapshot)
            renderLiveSignals()
        }
    }
    private val healthListener: (CallSessionHealthState.Snapshot) -> Unit = { snapshot -> runOnUiThread { renderHealth(snapshot) } }
    private val timerTick = object : Runnable {
        override fun run() {
            updateTimer()
            renderLiveSignals()
            refreshRecordingUi()
            refreshAudioButtons()
            renderHealth(CallSessionHealthState.snapshot())
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        soundboardStore = SoundboardStore(this)
        soundboardPlayer = CallSoundboardPlayer(this)
        createdAtElapsed = SystemClock.elapsedRealtime()
        connectedStartedAt = savedInstanceState?.getLong(KEY_CONNECTED_AT)?.takeIf { it > 0 }
        restoreKeypadOpen = savedInstanceState?.getBoolean(KEY_KEYPAD_OPEN, false) == true
        buildPulseDeckUi()
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
        handler.removeCallbacks(finishRunnable)
        handler.removeCallbacks(postCallTransitionRunnable)
        handler.removeCallbacks(timerTick)
        if (::soundboardPlayer.isInitialized) soundboardPlayer.stop()
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
        val service = RealityInCallService.instance
        val audio = service?.callAudioState
        val legacyEarpiece = audio == null || audio.route == CallAudioState.ROUTE_EARPIECE
        val earpiece = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoint = service?.currentCallEndpointSnapshot()
            endpoint?.endpointType == CallEndpoint.TYPE_EARPIECE || (endpoint == null && legacyEarpiece)
        } else {
            legacyEarpiece
        }
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

    private fun pulseCallControl(
        label: String,
        iconRes: Int,
        accent: Int = PulseDeckVisuals.Colors.Text,
        selected: Boolean = false,
        destructive: Boolean = false,
        circular: Boolean = false,
        action: () -> Unit,
    ) = Button(this).apply {
        text = label
        PulseDeckVisuals.styleCallControl(
            this,
            iconRes = iconRes,
            accent = accent,
            selected = selected,
            destructive = destructive,
            circular = circular,
        )
        setOnClickListener { action() }
    }

    private fun pulseUtilityControl(label: String, iconRes: Int, action: () -> Unit) = Button(this).apply {
        text = label
        PulseDeckVisuals.styleUtilityControl(this, iconRes)
        setOnClickListener { action() }
    }

    private fun pulseCoachChip(label: String, accent: Int) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(accent)
        RealityTypography.displayMedium(this, 9.5f)
        letterSpacing = .055f
        background = PulseDeckVisuals.chip(this@CallActivity, accent)
    }

    /** Builds the selected Pulse Deck concept while retaining every existing call action. */
    private fun buildPulseDeckUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        val screen = LinearLayout(this).apply {
            tag = PULSE_DECK_ROOT_TAG
            orientation = LinearLayout.VERTICAL
            background = PulseDeckVisuals.backdrop()
        }
        callScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(11.dp(), 8.dp(), 11.dp(), 14.dp())
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(PulseDeckMarkView(this), LinearLayout.LayoutParams(29.dp(), 39.dp()).apply {
            setMargins(2.dp(), 0, 7.dp(), 0)
        })
        val brandStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@CallActivity).apply {
                text = "PULSE "
                setTextColor(PulseDeckVisuals.Colors.Text)
                RealityTypography.displayMedium(this, 18f)
            })
            addView(TextView(this@CallActivity).apply {
                text = "DECK"
                setTextColor(cyan)
                RealityTypography.displayMedium(this, 18f)
            })
        }
        brandStack.addView(brandTitle)
        brandStack.addView(TextView(this).apply {
            text = "P H O N E"
            setTextColor(muted)
            RealityTypography.displayMedium(this, 7.2f)
            letterSpacing = .08f
        })
        brand.addView(brandStack, LinearLayout.LayoutParams(0, 46.dp(), 1f))

        pulseMenuButton = Button(this).apply {
            text = "LIVE CALL\nINTELLIGENCE  ▮"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(muted)
            RealityTypography.displayMedium(this, 8.2f)
            letterSpacing = .09f
            isAllCaps = true
            setPadding(4.dp(), 0, 3.dp(), 0)
            background = null
            stateListAnimator = null
            contentDescription = "Open Unhinged, Flirt, and Soundboard call tools"
            setOnClickListener { showPulseDeckTools() }
        }
        brand.addView(pulseMenuButton, LinearLayout.LayoutParams(119.dp(), 46.dp()))
        root.addView(brand, LinearLayout.LayoutParams(-1, 48.dp()).apply { setMargins(0, 0, 0, 4.dp()) })

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(7.dp(), 2.dp(), 7.dp(), 2.dp())
        }
        callerAvatar = ContactAvatarView(this).apply { bind(-1L, "?", cyan) }
        identity.addView(callerAvatar, LinearLayout.LayoutParams(64.dp(), 64.dp()).apply {
            setMargins(0, 0, 12.dp(), 0)
        })
        val callerStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        caller = TextView(this).apply {
            text = "UNKNOWN CALLER"
            setTextColor(PulseDeckVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 20f)
            isAllCaps = true
            maxLines = 1
        }
        callerStack.addView(caller, LinearLayout.LayoutParams(-1, -2))
        val callTelemetry = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        state = TextView(this).apply {
            text = "● CONNECTING"
            setTextColor(green)
            RealityTypography.displayMedium(this, 10.5f)
            letterSpacing = .065f
            background = null
        }
        callTelemetry.addView(state, LinearLayout.LayoutParams(-2, 28.dp()))
        callTelemetry.addView(TextView(this).apply {
            text = "  |  "
            setTextColor(PulseDeckVisuals.Colors.Border)
            gravity = Gravity.CENTER
            RealityTypography.display(this, 13f)
        }, LinearLayout.LayoutParams(-2, 28.dp()))
        timer = TextView(this).apply {
            text = "00:00"
            setTextColor(PulseDeckVisuals.Colors.Text)
            gravity = Gravity.CENTER_VERTICAL
            RealityTypography.displayMedium(this, 16f)
        }
        callTelemetry.addView(timer, LinearLayout.LayoutParams(-2, 28.dp()))
        callerStack.addView(callTelemetry)
        identity.addView(callerStack, LinearLayout.LayoutParams(0, 68.dp(), 1f))
        root.addView(identity, LinearLayout.LayoutParams(-1, 74.dp()).apply { setMargins(0, 0, 0, 5.dp()) })

        healthStrip = TextView(this).apply {
            text = "AUDIO  ○     STT  ○     COACH  ○"
            setTextColor(PulseDeckVisuals.Colors.Text)
            gravity = Gravity.CENTER
            RealityTypography.displayMedium(this, 9.5f)
            letterSpacing = .055f
            background = PulseDeckVisuals.panel(this@CallActivity, radiusDp = 14f)
            setOnClickListener {
                AlertDialog.Builder(this@CallActivity)
                    .setTitle("Call session diagnostics")
                    .setMessage(CallSessionHealthState.diagnosticText())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        root.addView(healthStrip, LinearLayout.LayoutParams(-1, 38.dp()).apply { setMargins(0, 0, 0, 7.dp()) })

        incomingHeroAvatar = ContactAvatarView(this).apply {
            bind(-1L, "?", cyan)
            visibility = View.GONE
        }
        root.addView(incomingHeroAvatar, LinearLayout.LayoutParams(108.dp(), 108.dp()).apply {
            setMargins(0, 2.dp(), 0, 7.dp())
        })
        preCallBriefing = PreCallBriefingView(this).apply { visibility = View.GONE }
        root.addView(preCallBriefing, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 7.dp()) })

        transcript = LiveTranscriptPanelView(this)
        root.addView(transcript, LinearLayout.LayoutParams(-1, 205.dp()).apply { setMargins(0, 0, 0, 7.dp()) })

        val translationStrip = TextView(this).apply {
            tag = PULSE_DECK_TRANSLATION_TAG
            visibility = View.GONE
            setTextColor(PulseDeckVisuals.Colors.Text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(11.dp(), 0, 11.dp(), 0)
            RealityTypography.display(this, 11f)
            background = PulseDeckVisuals.panel(
                this@CallActivity,
                start = PulseDeckVisuals.Colors.PanelSoft,
                end = PulseDeckVisuals.Colors.PanelBottom,
                stroke = cyan,
                radiusDp = 14f,
            )
        }
        root.addView(translationStrip, LinearLayout.LayoutParams(-1, 42.dp()).apply { setMargins(0, 0, 0, 7.dp()) })

        val coachPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 9.dp(), 12.dp(), 10.dp())
            background = PulseDeckVisuals.panel(
                this@CallActivity,
                start = Color.rgb(13, 43, 31),
                end = Color.rgb(5, 24, 24),
                stroke = PulseDeckVisuals.Colors.Lime,
                radiusDp = 18f,
                strokeDp = 2,
            )
        }
        coachPanel.addView(TextView(this).apply {
            text = "✦  BEST RESPONSE"
            setTextColor(PulseDeckVisuals.Colors.Lime)
            RealityTypography.displayMedium(this, 10.8f)
            letterSpacing = .05f
        }, LinearLayout.LayoutParams(-1, 25.dp()))
        val responseRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        responseCoach = TextView(this).apply {
            text = "Listening for the next caller turn."
            setTextColor(PulseDeckVisuals.Colors.Text)
            setLineSpacing(2.dp().toFloat(), 1.04f)
            RealityTypography.displayMedium(this, 18f)
        }
        responseRow.addView(responseCoach, LinearLayout.LayoutParams(0, -2, 1f))
        coachExpandButton = Button(this).apply {
            text = "›"
            textSize = 31f
            setTextColor(PulseDeckVisuals.Colors.TextDim)
            gravity = Gravity.CENTER
            background = null
            stateListAnimator = null
            minWidth = 0
            minHeight = 0
            visibility = View.INVISIBLE
            contentDescription = "Show ranked response options"
            setOnClickListener { toggleRankedResponses() }
        }
        responseRow.addView(coachExpandButton, LinearLayout.LayoutParams(38.dp(), 70.dp()))
        coachPanel.addView(responseRow, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 2.dp(), 0, 8.dp()) })

        val coachChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        coachModeChip = pulseCoachChip("READY", cyan)
        coachToneChip = pulseCoachChip("LISTENING", green)
        coachWhyButton = Button(this).apply {
            text = "WHY"
            gravity = Gravity.CENTER
            setTextColor(PulseDeckVisuals.Colors.Amber)
            RealityTypography.displayMedium(this, 9.5f)
            letterSpacing = .055f
            background = PulseDeckVisuals.chip(this@CallActivity, PulseDeckVisuals.Colors.Amber)
            stateListAnimator = null
            minWidth = 0
            minHeight = 0
            setOnClickListener { ConversationOSOverlay.performCallAction(this@CallActivity, ConversationOSOverlay.CallAction.WHY) }
        }
        coachChips.addView(coachModeChip, chipLayout())
        coachChips.addView(coachToneChip, chipLayout())
        coachChips.addView(coachWhyButton, chipLayout())
        coachPanel.addView(coachChips, LinearLayout.LayoutParams(-1, 36.dp()))
        responseCoachCards = ResponseCoachCardsView(this).apply { visibility = View.GONE }
        coachPanel.addView(responseCoachCards, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5.dp(), 0, 0) })
        root.addView(coachPanel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 7.dp()) })

        groqUsage = TextView(this).apply {
            text = "COACH // WAITING"
            visibility = View.GONE
        }
        root.addView(groqUsage, LinearLayout.LayoutParams(0, 0))
        analysis = TextView(this).apply {
            text = "NEXT ACTION // STANDBY"
            visibility = View.GONE
        }
        root.addView(analysis, LinearLayout.LayoutParams(0, 0))

        val signalVisual = LiveSignalVisualView(this).apply {
            tag = PULSE_DECK_SIGNAL_TAG
            setOnClickListener { showSignalExplanation() }
        }
        root.addView(signalVisual, LinearLayout.LayoutParams(-1, 92.dp()).apply { setMargins(0, 0, 0, 7.dp()) })

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        utilityRow.addView(
            pulseUtilityControl("Objective", R.drawable.ic_re_objective) {
                ConversationOSOverlay.performCallAction(this, ConversationOSOverlay.CallAction.OBJECTIVE)
            },
            utilityLayout(),
        )
        utilityRow.addView(
            pulseUtilityControl("Rewind", R.drawable.ic_re_rewind) {
                ConversationOSOverlay.performCallAction(this, ConversationOSOverlay.CallAction.REWIND)
            },
            utilityLayout(),
        )
        utilityRow.addView(
            pulseUtilityControl("Translate", R.drawable.ic_re_translate) {
                ConversationOSOverlay.performCallAction(this, ConversationOSOverlay.CallAction.TRANSLATE)
            },
            utilityLayout(),
        )
        utilityRow.addView(
            pulseUtilityControl("Why", R.drawable.ic_re_help) {
                ConversationOSOverlay.performCallAction(this, ConversationOSOverlay.CallAction.WHY)
            },
            utilityLayout(),
        )
        root.addView(utilityRow, LinearLayout.LayoutParams(-1, 45.dp()).apply { setMargins(0, 0, 0, 6.dp()) })

        val controlDock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(8.dp(), 0, 8.dp(), 4.dp())
        }
        val incoming = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        answerButton = pulseCallControl("Accept", R.drawable.ic_re_call, green, selected = true) {
            call?.takeIf { it.state == Call.STATE_RINGING }?.answer(0)
        }
        rejectButton = pulseCallControl("Decline", R.drawable.ic_re_call_end, magenta, selected = true) {
            call?.takeIf { it.state == Call.STATE_RINGING }?.reject(false, null)
        }
        incoming.addView(answerButton, buttonLayout(60))
        incoming.addView(rejectButton, buttonLayout(60))
        controlDock.addView(incoming)

        val controls = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        muteButton = pulseCallControl("Mute", R.drawable.ic_re_mic) { toggleMute() }
        speakerButton = pulseCallControl("Speaker", R.drawable.ic_re_speaker) { toggleSpeaker() }
        bluetoothButton = pulseCallControl("Audio", R.drawable.ic_re_bluetooth, cyan, selected = true) { showAudioRoutePicker() }
        holdButton = pulseCallControl("Hold", R.drawable.ic_re_hold) { toggleHold() }
        arrayOf(muteButton, speakerButton, bluetoothButton, holdButton).forEach { button ->
            controls.addView(button, GridLayout.LayoutParams().apply {
                width = 0
                height = 72.dp()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            })
        }
        controlDock.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 5.dp()) })

        // Keep the established secondary actions alive behind the header control without adding a
        // row the selected mockup does not contain.
        unhingedButton = pulseCallControl("Unhinged", R.drawable.ic_re_star, magenta) {
            requestQuickCoach(CoachQuickModeCatalog.UNHINGED)
        }
        flirtButton = pulseCallControl("Flirt", R.drawable.ic_re_star, cyan) {
            requestQuickCoach(CoachQuickModeCatalog.FLIRT)
        }
        soundboardButton = pulseCallControl("Sounds", R.drawable.ic_re_speaker, green) { showSoundboard() }
        root.addView(LinearLayout(this).apply {
            visibility = View.GONE
            addView(unhingedButton)
            addView(flirtButton)
            addView(soundboardButton)
        }, LinearLayout.LayoutParams(0, 0))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        recordButton = pulseCallControl("Record", R.drawable.ic_re_record) { requestRecording() }
        keypadButton = pulseCallControl("Keypad", R.drawable.ic_re_dialpad) {
            keypadContainer.visibility = if (keypadContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            RealityVisuals.reveal(keypadContainer)
            if (keypadContainer.visibility == View.VISIBLE) {
                callScroll.post { callScroll.smoothScrollTo(0, root.bottom) }
            }
        }
        endButton = pulseCallControl(
            "End",
            R.drawable.ic_re_call_end,
            PulseDeckVisuals.Colors.Text,
            destructive = true,
            circular = true,
        ) { call?.disconnect() }
        bottom.addView(recordButton, LinearLayout.LayoutParams(0, 82.dp(), 1f).apply { setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp()) })
        bottom.addView(keypadButton, LinearLayout.LayoutParams(0, 82.dp(), 1f).apply { setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp()) })
        bottom.addView(FrameLayout(this).apply {
            addView(endButton, FrameLayout.LayoutParams(74.dp(), 74.dp(), Gravity.CENTER))
        }, LinearLayout.LayoutParams(0, 82.dp(), 1f).apply { setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp()) })
        controlDock.addView(bottom, LinearLayout.LayoutParams(-1, 88.dp()))

        keypadContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (restoreKeypadOpen) View.VISIBLE else View.GONE
            background = PulseDeckVisuals.panel(this@CallActivity, radiusDp = 17f)
            setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
        }
        val digits = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        digits.forEach { digit ->
            grid.addView(Button(this).apply {
                text = digit
                textSize = 20f
                setTextColor(PulseDeckVisuals.Colors.Text)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = PulseDeckVisuals.panel(
                    this@CallActivity,
                    start = PulseDeckVisuals.Colors.PanelSoft,
                    end = PulseDeckVisuals.Colors.PanelBottom,
                    radiusDp = 14f,
                )
                stateListAnimator = null
                minWidth = 0
                minHeight = 0
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { call?.playDtmfTone(digit[0]); true }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { call?.stopDtmfTone(); performClick(); true }
                        else -> false
                    }
                }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = 52.dp()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            })
        }
        keypadContainer.addView(grid)
        root.addView(keypadContainer, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.dp(), 0, 0) })

        callScroll.addView(root, FrameLayout.LayoutParams(-1, -2))
        screen.addView(callScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        screen.addView(controlDock, LinearLayout.LayoutParams(-1, -2))
        setContentView(screen)
        installSafeAreaInsets(screen)
        renderCoach(ResponseCoachState.current())
        renderLiveSignals()
        renderTranscript(LiveTranscriptState.snapshot())
        renderHealth(CallSessionHealthState.snapshot())
    }

    private fun utilityLayout() = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
        setMargins(2.dp(), 1.dp(), 2.dp(), 1.dp())
    }

    private fun chipLayout() = LinearLayout.LayoutParams(0, 32.dp(), 1f).apply {
        setMargins(2.dp(), 1.dp(), 2.dp(), 1.dp())
    }

    private fun toggleRankedResponses() {
        if (ResponseCoachState.current().alternatives.isEmpty()) return
        alternativesExpanded = !alternativesExpanded
        responseCoachCards.visibility = if (alternativesExpanded) View.VISIBLE else View.GONE
        coachExpandButton.text = if (alternativesExpanded) "⌄" else "›"
        coachExpandButton.contentDescription = if (alternativesExpanded) "Hide ranked response options" else "Show ranked response options"
        RealityVisuals.reveal(responseCoachCards)
    }

    private fun showPulseDeckTools() {
        val items = arrayOf("Unhinged coach", "Flirt coach", "Call soundboard")
        AlertDialog.Builder(this)
            .setTitle("Pulse Deck tools")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestQuickCoach(CoachQuickModeCatalog.UNHINGED)
                    1 -> requestQuickCoach(CoachQuickModeCatalog.FLIRT)
                    2 -> showSoundboard()
                }
            }
            .setNegativeButton("Close", null)
            .show()
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
            alternativesExpanded = false
            coachExpandButton.visibility = View.INVISIBLE
            coachExpandButton.text = "›"
            val chosen = snapshot.chosen
            responseCoach.text = when (snapshot.phase) {
                ResponseCoachState.Phase.ANALYZING -> snapshot.message ?: "Generating replies…"
                ResponseCoachState.Phase.LISTENING -> snapshot.message ?: "Waiting for the next caller turn."
                ResponseCoachState.Phase.KEY_REQUIRED -> "Open Settings to configure a coach provider."
                ResponseCoachState.Phase.DISABLED -> "Enable Response Coach in Settings."
                ResponseCoachState.Phase.ERROR -> snapshot.message ?: "The suggestion request failed."
                else -> chosen?.suggestion?.text ?: "Listening for the next caller turn."
            }
            val chipState = when (snapshot.phase) {
                ResponseCoachState.Phase.ANALYZING -> "ANALYZING" to "WORKING"
                ResponseCoachState.Phase.KEY_REQUIRED -> "SETUP" to "REQUIRED"
                ResponseCoachState.Phase.DISABLED -> "COACH" to "DISABLED"
                ResponseCoachState.Phase.ERROR -> "COACH" to "ERROR"
                else -> "READY" to "LISTENING"
            }
            coachModeChip.text = chipState.first
            coachToneChip.text = chipState.second
            if (phaseChanged && snapshot.phase == ResponseCoachState.Phase.ANALYZING) RealityVisuals.pulseOnce(responseCoach)
            return
        }
        responseCoach.text = best.text
        coachModeChip.text = best.mode.replace('_', ' ').uppercase()
        coachToneChip.text = best.tone.replace('_', ' ').uppercase()
        responseCoachCards.render(snapshot.alternatives)
        responseCoachCards.visibility = if (alternativesExpanded && snapshot.alternatives.isNotEmpty()) View.VISIBLE else View.GONE
        coachExpandButton.visibility = if (snapshot.alternatives.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        coachExpandButton.text = if (alternativesExpanded) "⌄" else "›"
        analysis.text = "NEXT ACTION  // ${best.mode} · ${best.tone}"
        if (lastBestSuggestion != best.text) {
            lastBestSuggestion = best.text
            RealityVisuals.reveal(responseCoach)
            RealityVisuals.reveal(coachModeChip)
            RealityVisuals.reveal(coachToneChip)
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
        val content = SpannableString("AUDIO  ●     STT  ●     COACH  ●")
        val levels = arrayOf(snapshot.audio, snapshot.stt, snapshot.coach)
        var searchFrom = 0
        levels.forEach { level ->
            val index = content.indexOf('●', searchFrom)
            if (index >= 0) {
                val color = when (level) {
                    CallSessionHealthState.Level.GOOD -> PulseDeckVisuals.Colors.Green
                    CallSessionHealthState.Level.DEGRADED -> PulseDeckVisuals.Colors.Amber
                    CallSessionHealthState.Level.ERROR -> PulseDeckVisuals.Colors.Coral
                    CallSessionHealthState.Level.WAITING -> PulseDeckVisuals.Colors.TextDim
                }
                content.setSpan(ForegroundColorSpan(color), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                searchFrom = index + 1
            }
        }
        healthStrip.text = content
        healthStrip.setTextColor(PulseDeckVisuals.Colors.Text)
        healthStrip.contentDescription = snapshot.compact()
        healthStrip.background = PulseDeckVisuals.panel(this, radiusDp = 14f)
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
            LiveSignalState.publishRealtime(acoustic, linguistic, factual)
            analysis.text = "NEXT ACTION  // ${nextAction?.takeIf { it.isNotBlank() } ?: "STANDBY"}"
        }
    }

    /**
     * Android 15 lays target-SDK 35 activities edge-to-edge. Apply the real system-bar and cutout
     * insets to the whole screen so the header and the pinned call controls always occupy the safe
     * viewport instead of relying on device-specific top or bottom margins.
     */
    private fun installSafeAreaInsets(screen: View) {
        // Pre-Android 11 decor already fits the content view inside system bars because we leave
        // the platform's default decor-fitting behavior enabled there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val baseLeft = screen.paddingLeft
        val baseTop = screen.paddingTop
        val baseRight = screen.paddingRight
        val baseBottom = screen.paddingBottom
        screen.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            view.setPadding(
                baseLeft + safe.left,
                baseTop + safe.top,
                baseRight + safe.right,
                baseBottom + safe.bottom,
            )
            insets
        }
        screen.requestApplyInsets()
        screen.post { screen.requestApplyInsets() }
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
            if (::pulseMenuButton.isInitialized) RealityVisuals.pulseOnce(pulseMenuButton)
            Toast.makeText(this, "${mode.label} coach refresh", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Response coach session is not ready yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSoundboard() {
        val current = call
        if (current?.state != Call.STATE_ACTIVE) {
            Toast.makeText(this, "Soundboard is available during an active call", Toast.LENGTH_SHORT).show()
            return
        }
        val entries = soundboardStore.all()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Soundboard is empty")
                .setMessage("Add audio in Settings → Call soundboard.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open soundboard settings") { _, _ ->
                    startActivity(Intent(this, SoundboardSettingsActivity::class.java))
                }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (soundboardPlayer.isPlaying()) "Soundboard · playing" else "Soundboard")
            .setItems(entries.map { it.name }.toTypedArray()) { _, which ->
                val entry = entries[which]
                if (RealityInCallService.instance?.isMutedNow() == true) {
                    Toast.makeText(this, "Unmute first if you want the caller to hear speaker-coupled playback", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                if (soundboardPlayer.play(entry) {
                        runOnUiThread {
                            soundboardButton.text = "Sounds"
                            refreshAudioButtons()
                            updateProximityRegistration()
                        }
                    }) {
                    soundboardButton.text = "■ ${entry.name.take(8)}"
                    RealityVisuals.pulseOnce(soundboardButton)
                    refreshAudioButtons()
                    updateProximityRegistration()
                } else {
                    Toast.makeText(this, "Could not play ${entry.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(if (soundboardPlayer.isPlaying()) "Stop" else "Manage") { _, _ ->
                if (soundboardPlayer.isPlaying()) {
                    soundboardPlayer.stop()
                    soundboardButton.text = "Sounds"
                    refreshAudioButtons()
                    updateProximityRegistration()
                } else {
                    startActivity(Intent(this, SoundboardSettingsActivity::class.java))
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun requestRecording() {
        val service = RealityInCallService.instance ?: return
        if (service.recordingActive()) {
            Toast.makeText(this, "REC is active. Save or delete it after the call.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Record this call?")
            .setMessage("Phone will visibly record the call-audio stream until hangup, then ask you to Save or Permanently Delete it. Only record where recording is permitted.")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpointType = if (service.currentCallEndpointSnapshot()?.endpointType == CallEndpoint.TYPE_SPEAKER) CallEndpoint.TYPE_EARPIECE else CallEndpoint.TYPE_SPEAKER
            val endpoint = service.availableCallEndpointsSnapshot().firstOrNull { it.endpointType == endpointType }
            if (endpoint != null) {
                service.selectCallEndpoint(endpoint) { error -> showAudioRouteResult(error) }
                return
            }
        }
        val audio = service.callAudioState ?: return
        val target = if (audio.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        if (audio.supportedRouteMask and target != 0) {
            service.setAudioRoute(target)
        }
        refreshAudioButtons(); updateProximityRegistration()
    }

    private fun showAudioRoutePicker() {
        val service = RealityInCallService.instance ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoints = service.availableCallEndpointsSnapshot()
                .sortedWith(compareBy(::endpointOrder, { it.endpointName.toString() }))
            if (endpoints.isNotEmpty()) {
                val currentId = service.currentCallEndpointSnapshot()?.identifier
                val selected = endpoints.indexOfFirst { it.identifier == currentId }
                AlertDialog.Builder(this)
                    .setTitle("Call audio")
                    .setSingleChoiceItems(endpoints.map(::endpointLabel).toTypedArray(), selected) { dialog, which ->
                        service.selectCallEndpoint(endpoints[which]) { error -> showAudioRouteResult(error) }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        val audio = service.callAudioState
        if (audio == null) {
            Toast.makeText(this, "Call audio routes are still loading", Toast.LENGTH_SHORT).show()
            return
        }
        val routes = buildList {
            fun addRoute(route: Int, label: String) {
                if (audio.supportedRouteMask and route != 0) add(route to label)
            }
            addRoute(CallAudioState.ROUTE_EARPIECE, "Phone earpiece")
            addRoute(CallAudioState.ROUTE_SPEAKER, "Phone speaker")
            addRoute(CallAudioState.ROUTE_WIRED_HEADSET, "Wired headset")
            addRoute(CallAudioState.ROUTE_BLUETOOTH, "Bluetooth")
        }
        if (routes.isEmpty()) {
            Toast.makeText(this, "No call audio routes are available", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = routes.indexOfFirst { it.first == audio.route }
        AlertDialog.Builder(this)
            .setTitle("Call audio")
            .setSingleChoiceItems(routes.map { it.second }.toTypedArray(), selected) { dialog, which ->
                service.setAudioRoute(routes[which].first)
                dialog.dismiss()
                refreshAudioButtons()
                updateProximityRegistration()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAudioRouteResult(error: String?) = runOnUiThread {
        if (error != null) Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        refreshAudioButtons()
        updateProximityRegistration()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun endpointLabel(endpoint: CallEndpoint): String {
        val fallback = when (endpoint.endpointType) {
            CallEndpoint.TYPE_EARPIECE -> "Phone earpiece"
            CallEndpoint.TYPE_SPEAKER -> "Phone speaker"
            CallEndpoint.TYPE_WIRED_HEADSET -> "Wired headset"
            CallEndpoint.TYPE_BLUETOOTH -> "Bluetooth"
            CallEndpoint.TYPE_STREAMING -> "Call streaming"
            else -> "Call audio"
        }
        return endpoint.endpointName.toString().trim().ifBlank { fallback }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun endpointOrder(endpoint: CallEndpoint): Int = when (endpoint.endpointType) {
        CallEndpoint.TYPE_EARPIECE -> 0
        CallEndpoint.TYPE_SPEAKER -> 1
        CallEndpoint.TYPE_WIRED_HEADSET -> 2
        CallEndpoint.TYPE_BLUETOOTH -> 3
        CallEndpoint.TYPE_STREAMING -> 4
        else -> 5
    }

    private fun toggleHold() {
        val current = call ?: return
        when (current.state) { Call.STATE_ACTIVE -> current.hold(); Call.STATE_HOLDING -> current.unhold() }
        updateProximityRegistration()
    }

    private fun refresh() {
        call = CallSessionRegistry.primary()
        val current = call
        if (current == null) {
            unregisterProximity()
            restoreScreen()
            if (observedCall) transitionAfterCall() else scheduleFinish()
            return
        }
        observedCall = true
        handler.removeCallbacks(finishRunnable)
        finishScheduled = false
        handler.removeCallbacks(postCallTransitionRunnable)
        postCallTransitionScheduled = false
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
        unhingedButton.isEnabled = interactive; flirtButton.isEnabled = interactive; soundboardButton.isEnabled = current.state == Call.STATE_ACTIVE
        pulseMenuButton.isEnabled = interactive
        pulseMenuButton.alpha = if (interactive) 1f else .42f
        unhingedButton.alpha = if (interactive) 1f else .42f; flirtButton.alpha = if (interactive) 1f else .42f; soundboardButton.alpha = if (soundboardButton.isEnabled) 1f else .42f
        recordButton.isEnabled = current.state == Call.STATE_ACTIVE || RealityInCallService.instance?.recordingActive() == true
        if (!interactive) keypadContainer.visibility = View.GONE
        holdButton.text = if (current.state == Call.STATE_HOLDING) "Resume" else "Hold"; setControlActive(holdButton, current.state == Call.STATE_HOLDING)
        endButton.isEnabled = current.state != Call.STATE_DISCONNECTED
        endButton.alpha = if (endButton.isEnabled) 1f else .42f

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
        PulseDeckVisuals.styleCallControl(
            recordButton,
            iconRes = R.drawable.ic_re_record,
            accent = if (active) magenta else PulseDeckVisuals.Colors.Text,
            selected = active,
        )
        recordButton.alpha = if (recordButton.isEnabled) 1f else .42f
        val current = call
        val base = when (current?.state) {
            Call.STATE_RINGING -> "● INCOMING"
            Call.STATE_DIALING -> "● DIALING"
            Call.STATE_CONNECTING -> "● CONNECTING"
            Call.STATE_ACTIVE -> "● CONNECTED"
            Call.STATE_HOLDING -> "● ON HOLD"
            Call.STATE_DISCONNECTED -> "● ENDED"
            else -> "● CALL"
        }
        state.text = if (active) "$base · REC" else base
        state.setTextColor(if (active) magenta else green)
        state.background = null
    }

    private fun refreshAudioButtons() {
        val service = RealityInCallService.instance
        val audio = service?.callAudioState
        val legacySpeaker = audio?.route == CallAudioState.ROUTE_SPEAKER
        val speakerOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoint = service?.currentCallEndpointSnapshot()
            endpoint?.endpointType == CallEndpoint.TYPE_SPEAKER || (endpoint == null && legacySpeaker)
        } else {
            legacySpeaker
        }
        speakerButton.text = if (speakerOn) "Earpiece" else "Speaker"; bluetoothButton.text = "Audio"
        val interactive = call?.state == Call.STATE_ACTIVE || call?.state == Call.STATE_HOLDING
        val endpointCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) service?.availableCallEndpointsSnapshot()?.size ?: 0 else 0
        bluetoothButton.isEnabled = interactive && (endpointCount > 0 || (audio?.supportedRouteMask ?: 0) != 0)
        setControlActive(speakerButton, speakerOn); setControlActive(bluetoothButton, true)
    }

    private fun setControlActive(button: Button, active: Boolean) {
        val isAudio = button === bluetoothButton
        val accent = when {
            isAudio -> cyan
            active -> green
            else -> PulseDeckVisuals.Colors.Text
        }
        val icon = when (button) {
            muteButton -> R.drawable.ic_re_mic
            speakerButton -> R.drawable.ic_re_speaker
            bluetoothButton -> R.drawable.ic_re_bluetooth
            holdButton -> R.drawable.ic_re_hold
            else -> 0
        }
        PulseDeckVisuals.styleCallControl(
            button,
            iconRes = icon,
            accent = accent,
            selected = active || isAudio,
        )
        button.alpha = if (button.isEnabled) 1f else .42f
    }

    private val finishRunnable = Runnable { finishScheduled = false; if (CallSessionRegistry.primary() == null && !isFinishing) finish() }

    /**
     * Keep Phone in the foreground while Telecom tears the call down. When finalization has queued
     * a review or caller profile, open it directly from this foreground Activity. A short fallback
     * delay covers devices that report DISCONNECTING before the service receives DISCONNECTED.
     */
    private fun transitionAfterCall() {
        handler.removeCallbacks(finishRunnable)
        finishScheduled = false
        if (postCallTransitionStarted || isFinishing || isDestroyed) return
        if (PostCallReviewHandoff.launchIfPending(this)) {
            postCallTransitionStarted = true
            finish()
            return
        }
        if (postCallTransitionScheduled) return
        postCallTransitionScheduled = true
        handler.postDelayed(postCallTransitionRunnable, POST_CALL_FINALIZE_GRACE_MS)
    }

    private val postCallTransitionRunnable = object : Runnable {
        override fun run() {
            postCallTransitionScheduled = false
            if (CallSessionRegistry.primary() != null || isFinishing || isDestroyed) return
            if (PostCallReviewHandoff.launchIfPending(this@CallActivity)) {
                postCallTransitionStarted = true
                finish()
                return
            }
            val openedHome = runCatching {
                startActivity(Intent(this@CallActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                true
            }.getOrDefault(false)
            if (openedHome) {
                postCallTransitionStarted = true
                finish()
            } else {
                transitionAfterCall()
            }
        }
    }

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
        const val PULSE_DECK_ROOT_TAG = "realityengine.pulse.deck.root"
        const val PULSE_DECK_TRANSLATION_TAG = "reality.conversation.translation"
        const val PULSE_DECK_SIGNAL_TAG = "reality.signal.visual.pulse"
        private const val POST_CALL_FINALIZE_GRACE_MS = 350L
        private const val KEY_CONNECTED_AT = "connected_started_at"
        private const val KEY_KEYPAD_OPEN = "keypad_open"
    }
}
