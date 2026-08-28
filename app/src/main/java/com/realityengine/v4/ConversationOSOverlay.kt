package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Presentation-only integration layer for Conversation OS.
 * It plugs into existing V4 screens after they build so telephony/audio/AI behavior stays untouched.
 */
object ConversationOSOverlay {
    private const val TAG_RADAR = "reality.conversation.radar"
    private const val TAG_TOOLS = "reality.conversation.tools"
    private const val TAG_ORB = "reality.conversation.orb"
    private const val TAG_TRANSLATION = "reality.conversation.translation"
    private const val TAG_DIAL_OBJECTIVE = "reality.conversation.dial.objective"
    private const val TAG_POST = "reality.conversation.post"
    private const val TAG_TIMELINE_BUTTON = "reality.conversation.timeline.button"

    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            val session = synchronized(sessions) {
                sessions[activity] ?: createSession(activity)?.also { sessions[activity] = it }
            }
            session?.resume()
        }
        override fun onActivityPaused(activity: Activity) = sessions[activity]?.pause() ?: Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            sessions.remove(activity)?.destroy()
        }
    }

    private fun createSession(activity: Activity): Session? = when (activity) {
        is CallActivity -> CallSession(activity)
        is MainActivity -> MainSession(activity)
        is PostCallIntelligenceActivity -> PostCallSession(activity)
        is CallerMemoryActivity -> MemorySession(activity)
        else -> null
    }

    private interface Session {
        fun resume()
        fun pause() = Unit
        fun destroy() = Unit
    }

    private class CallSession(private val activity: CallActivity) : Session {
        private val handler = Handler(Looper.getMainLooper())
        private val engine = ConversationIntelligenceEngine(activity)
        private val objectives = ConversationObjectiveStore(activity)
        private val translation = ConversationTranslationStore(activity)
        private val translator = ConversationTranslator(activity)
        private var radar: ConversationRadarView? = null
        private var orb: RealityOrbView? = null
        private var translationStrip: TextView? = null
        private var lastInsight = ConversationInsightSnapshot()
        private var lastTranslatedSource = ""
        private var installed = false
        private var listenerAdded = false

        private val transcriptListener: (LiveTranscriptState.State) -> Unit = { state ->
            activity.runOnUiThread { render(state) }
        }
        private val ticker = object : Runnable {
            override fun run() {
                orb?.render(LiveSignalState.snapshot(), lastInsight)
                handler.postDelayed(this, 650L)
            }
        }

        override fun resume() {
            install()
            if (!listenerAdded) {
                LiveTranscriptState.addListener(transcriptListener)
                listenerAdded = true
            }
            handler.removeCallbacks(ticker)
            handler.post(ticker)
            render(LiveTranscriptState.snapshot())
        }

        override fun pause() {
            handler.removeCallbacks(ticker)
        }

        override fun destroy() {
            handler.removeCallbacks(ticker)
            if (listenerAdded) LiveTranscriptState.removeListener(transcriptListener)
            listenerAdded = false
        }

        private fun install() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            content.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val headerText = findText(content, "LIVE TRANSCRIPT")
                val transcriptHeader = headerText?.parent as? ViewGroup
                val workspace = transcriptHeader?.parent as? ViewGroup
                if (workspace != null && workspace.findViewWithTag<View>(TAG_RADAR) == null) {
                    val r = ConversationRadarView(activity).apply { tag = TAG_RADAR }
                    val index = workspace.indexOfChild(transcriptHeader) + 1
                    workspace.addView(r, index, LinearLayout.LayoutParams(-1, dp(68)).apply { setMargins(0, 2, 0, 5) })
                    radar = r
                    val strip = TextView(activity).apply {
                        tag = TAG_TRANSLATION
                        visibility = View.GONE
                        setTextColor(RealityVisuals.Colors.Text)
                        RealityTypography.display(this, 11f)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(11), 0, dp(11), 0)
                        background = RealityVisuals.panel(activity, RealityVisuals.Colors.PanelStrong, RealityVisuals.Colors.CyanSoft, 16f)
                    }
                    workspace.addView(strip, index + 1, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, 0, 0, 5) })
                    translationStrip = strip
                } else {
                    radar = workspace?.findViewWithTag(TAG_RADAR)
                    translationStrip = workspace?.findViewWithTag(TAG_TRANSLATION)
                }

                val activeContact = findText(content, "ACTIVE CONTACT")
                val callerStack = activeContact?.parent as? ViewGroup
                val identity = callerStack?.parent as? ViewGroup
                if (identity != null && identity.findViewWithTag<View>(TAG_ORB) == null) {
                    val o = RealityOrbView(activity).apply {
                        tag = TAG_ORB
                        contentDescription = "Live Reality Orb"
                        setOnClickListener { showRadarDetails() }
                    }
                    identity.addView(o, (identity.childCount - 1).coerceAtLeast(0), LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(3), 0, dp(5), 0) })
                    orb = o
                } else orb = identity?.findViewWithTag(TAG_ORB)

                val quick = findButton(content, "Unhinged")?.parent as? ViewGroup
                val root = quick?.parent as? ViewGroup
                if (root != null && root.findViewWithTag<View>(TAG_TOOLS) == null) {
                    val tools = buildTools().apply { tag = TAG_TOOLS }
                    root.addView(tools, root.indexOfChild(quick) + 1, LinearLayout.LayoutParams(-1, dp(46)))
                }
                installed = true
                render(LiveTranscriptState.snapshot())
            }
        }

        private fun buildTools(): View = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(tool("Objective", RealityVisuals.Colors.Lilac) { pickObjective() }, toolLp())
            addView(tool("Rewind", RealityVisuals.Colors.CyanSoft) { ConversationRewind.show(activity, phone()) }, toolLp())
            addView(tool("Translate", RealityVisuals.Colors.CyanSoft) { pickTranslation() }, toolLp())
            addView(tool("Why", RealityVisuals.Colors.Green) { showWhy() }, toolLp())
        }

        private fun tool(label: String, accent: Int, action: () -> Unit) = Button(activity).apply {
            text = label
            minWidth = 0
            minHeight = 0
            RealityVisuals.styleControl(this, 0, accent, radiusDp = 16f)
            setPadding(dp(3), 0, dp(3), 0)
            setOnClickListener { action() }
        }

        private fun toolLp() = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }

        private fun render(state: LiveTranscriptState.State) {
            if (!installed) {
                install()
                return
            }
            val currentPhone = phone()
            lastInsight = engine.analyze(currentPhone, state)
            radar?.render(lastInsight)
            orb?.render(LiveSignalState.snapshot(), lastInsight)
            maybeTranslate(state)
        }

        private fun maybeTranslate(state: LiveTranscriptState.State) {
            val strip = translationStrip ?: return
            if (!translation.enabled) {
                strip.visibility = View.GONE
                return
            }
            strip.visibility = View.VISIBLE
            if (!state.isFinal || state.text.isBlank() || state.text == lastTranslatedSource) return
            lastTranslatedSource = state.text
            strip.text = "TRANSLATING · ${translation.pair}"
            translator.translate(state.text, translation.pair) { translated ->
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        strip.text = "${if (state.isCaller == false) "YOU" else "THEM"} · $translated"
                        RealityVisuals.reveal(strip)
                    }
                }
            }
        }

        private fun pickObjective() {
            val phone = phone()
            val current = objectives.get(phone)
            val selected = ConversationObjectiveStore.OPTIONS.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(activity)
                .setTitle("Conversation objective")
                .setSingleChoiceItems(ConversationObjectiveStore.OPTIONS.toTypedArray(), selected) { dialog, which ->
                    objectives.set(phone, ConversationObjectiveStore.OPTIONS[which])
                    dialog.dismiss()
                    render(LiveTranscriptState.snapshot())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun pickTranslation() {
            val labels = listOf("Off") + ConversationTranslationStore.PAIRS
            val selected = if (!translation.enabled) 0 else ConversationTranslationStore.PAIRS.indexOf(translation.pair).coerceAtLeast(0) + 1
            AlertDialog.Builder(activity)
                .setTitle("Live bilingual translation")
                .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                    if (which == 0) translation.enabled = false
                    else {
                        translation.enabled = true
                        translation.pair = ConversationTranslationStore.PAIRS[which - 1]
                    }
                    lastTranslatedSource = ""
                    dialog.dismiss()
                    render(LiveTranscriptState.snapshot())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showWhy() {
            val snapshot = ResponseCoachState.current()
            val best = snapshot.best
            val message = if (best == null) {
                "No active suggestion yet. Reality Engine will explain the next recommendation when the coach produces one."
            } else buildString {
                append("BEST · ${best.mode} / ${best.tone}\n${best.text}\n\nWHY\n")
                append(best.reason.ifBlank { "This suggestion ranked highest against the current call context and selected persona." })
                snapshot.alternatives.take(3).forEachIndexed { i, suggestion ->
                    append("\n\n#${i + 2} ${suggestion.mode}\n${suggestion.reason.ifBlank { "Alternative strategy for the same caller turn." }}")
                }
            }
            AlertDialog.Builder(activity).setTitle("Why this suggestion?").setMessage(message).setPositiveButton("Close", null).show()
        }

        private fun showRadarDetails() {
            AlertDialog.Builder(activity).setTitle("Conversation radar").setMessage(lastInsight.details()).setPositiveButton("Close", null).show()
        }

        private fun phone(): String = CallSessionRegistry.primary()?.details?.handle?.schemeSpecificPart.orEmpty()
    }

    /** Adds objective selection directly to the dialer before a call is placed. */
    private class MainSession(private val activity: MainActivity) : Session {
        private val objectives = ConversationObjectiveStore(activity)
        private var listener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var queued = false

        override fun resume() {
            attach()
            queue()
        }

        override fun destroy() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val l = listener
            if (l != null && content?.viewTreeObserver?.isAlive == true) content.viewTreeObserver.removeOnGlobalLayoutListener(l)
            listener = null
        }

        private fun attach() {
            if (listener != null) return
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            listener = ViewTreeObserver.OnGlobalLayoutListener { queue() }.also { content.viewTreeObserver.addOnGlobalLayoutListener(it) }
        }

        private fun queue() {
            if (queued) return
            queued = true
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            content.post {
                queued = false
                if (activity.isFinishing || activity.isDestroyed) return@post
                val heading = findText(content, "SECURE DIAL // NATIVE CELLULAR") ?: return@post
                val dialRoot = heading.parent as? ViewGroup ?: return@post
                if (dialRoot.findViewWithTag<View>(TAG_DIAL_OBJECTIVE) != null) return@post
                val chip = TextView(activity).apply {
                    tag = TAG_DIAL_OBJECTIVE
                    gravity = Gravity.CENTER
                    setTextColor(RealityVisuals.Colors.Lilac)
                    RealityTypography.displayMedium(this, 10.5f)
                    background = RealityVisuals.panel(activity, RealityVisuals.Colors.BackgroundRaised, RealityVisuals.Colors.Border, 18f)
                    text = "Objective · ${objectives.get()}"
                    setOnClickListener { pick(this) }
                }
                dialRoot.addView(chip, 1, LinearLayout.LayoutParams(-1, dp(36)).apply { setMargins(dp(22), 0, dp(22), dp(8)) })
            }
        }

        private fun pick(chip: TextView) {
            val current = objectives.get()
            AlertDialog.Builder(activity)
                .setTitle("Goal for the next call")
                .setSingleChoiceItems(ConversationObjectiveStore.OPTIONS.toTypedArray(), ConversationObjectiveStore.OPTIONS.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                    objectives.set(value = ConversationObjectiveStore.OPTIONS[which])
                    chip.text = "Objective · ${objectives.get()}"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private class PostCallSession(private val activity: PostCallIntelligenceActivity) : Session {
        override fun resume() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            content.post {
                if (content.findViewWithTag<View>(TAG_POST) != null) return@post
                val heading = findText(content, "POST-CALL INTELLIGENCE") ?: return@post
                val root = heading.parent as? LinearLayout ?: return@post
                val phone = activity.intent.getStringExtra(PostCallIntelligenceActivity.EXTRA_PHONE).orEmpty()
                if (phone.isBlank()) return@post
                val fallback = activity.intent.getStringExtra(PostCallIntelligenceActivity.EXTRA_NAME).orEmpty()
                val profile = CallerProfileStore(activity).load(phone)
                val name = profile.displayName.ifBlank { fallback.ifBlank { phone } }
                val latest = CallTranscriptStore.savedFor(activity, phone).firstOrNull()
                val base = latest?.timestampMs
                val events = if (base != null) profile.evidenceEvents.filter { it.timestampMs >= base - 3_000L }.takeLast(20) else profile.evidenceEvents.takeLast(20)

                val panel = LinearLayout(activity).apply {
                    tag = TAG_POST
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                    addView(TextView(activity).apply {
                        text = "CONVERSATION HEATMAP"
                        RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
                        setPadding(dp(3), dp(8), 0, dp(5))
                    })
                    addView(ConversationHeatmapView(activity).apply { setData(events) }, LinearLayout.LayoutParams(-1, dp(78)))
                    addView(TextView(activity).apply {
                        val objective = ConversationObjectiveStore(activity).get(phone)
                        text = "OBJECTIVE · $objective  ·  ${events.size} meaningful signal moment${if (events.size == 1) "" else "s"}"
                        setTextColor(RealityVisuals.Colors.TextDim)
                        RealityTypography.display(this, 10.5f)
                        setPadding(dp(3), dp(6), 0, dp(8))
                    })
                    addView(TextView(activity).apply {
                        text = "ONE-TAP FOLLOW-UP"
                        RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
                        setPadding(dp(3), dp(8), 0, dp(5))
                    })
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(action("Text recap", RealityVisuals.Colors.CyanSoft) { ConversationFollowUp.textRecap(activity, phone, profile.lastCallSummary) }, actionLp())
                        addView(action("Remind me", RealityVisuals.Colors.Amber) { ConversationFollowUp.addReminder(activity, name) }, actionLp())
                        addView(action("Timeline", RealityVisuals.Colors.Green) { openTimeline(activity, phone, name) }, actionLp())
                    })
                }
                val done = findButton(root, "DONE")
                val index = done?.let(root::indexOfChild)?.takeIf { it >= 0 } ?: root.childCount
                root.addView(panel, index)
            }
        }

        private fun action(label: String, accent: Int, click: () -> Unit) = Button(activity).apply {
            text = label
            RealityVisuals.styleControl(this, 0, accent, radiusDp = 17f)
            setOnClickListener { click() }
        }

        private fun actionLp() = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
    }

    private class MemorySession(private val activity: CallerMemoryActivity) : Session {
        override fun resume() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            content.post {
                if (content.findViewWithTag<View>(TAG_TIMELINE_BUTTON) != null) return@post
                val phone = activity.intent.getStringExtra(CallerMemoryActivity.EXTRA_PHONE).orEmpty()
                if (phone.isBlank()) return@post
                val name = activity.intent.getStringExtra(CallerMemoryActivity.EXTRA_NAME).orEmpty()
                val scroll = firstScrollView(content) ?: return@post
                val root = scroll.getChildAt(0) as? LinearLayout ?: return@post
                val button = Button(activity).apply {
                    tag = TAG_TIMELINE_BUTTON
                    text = "Relationship timeline"
                    RealityVisuals.styleControl(this, 0, RealityVisuals.Colors.Lilac, radiusDp = 18f)
                    setOnClickListener { openTimeline(activity, phone, name) }
                }
                root.addView(button, minOf(5, root.childCount), LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(4), 0, dp(5)) })
            }
        }
    }

    private fun openTimeline(activity: Activity, phone: String, name: String) {
        activity.startActivity(android.content.Intent(activity, RelationshipTimelineActivity::class.java).apply {
            putExtra(RelationshipTimelineActivity.EXTRA_PHONE, phone)
            putExtra(RelationshipTimelineActivity.EXTRA_NAME, name)
        })
    }

    private fun findText(root: View, exact: String): TextView? {
        if (root is TextView && root.text?.toString() == exact) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findText(root.getChildAt(i), exact)?.let { return it }
        return null
    }

    private fun findButton(root: View, exact: String): Button? {
        if (root is Button && root.text?.toString() == exact) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findButton(root.getChildAt(i), exact)?.let { return it }
        return null
    }

    private fun firstScrollView(root: View): ScrollView? {
        if (root is ScrollView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                firstScrollView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun dp(v: Int) = (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
