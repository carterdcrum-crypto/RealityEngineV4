package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Installs the selected Pulse Spectrum signal instrument without touching telephony or STT logic.
 * Transcript Focus gets a compact instrument; INTEL expands the same view instead of showing the
 * legacy progress bars.
 */
object SignalVisualOverlay {
    private const val TAG_VISUAL = "reality.signal.visual.pulse"
    private const val TAG_RADAR = "reality.conversation.radar"
    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            if (activity !is CallActivity) return
            val session = synchronized(sessions) {
                sessions[activity] ?: Session(activity).also { sessions[activity] = it }
            }
            session.resume()
        }
        override fun onActivityPaused(activity: Activity) = sessions[activity]?.pause() ?: Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            sessions.remove(activity)?.destroy()
        }
    }

    private class Session(private val activity: CallActivity) {
        private val handler = Handler(Looper.getMainLooper())
        private val intelligence = ConversationIntelligenceEngine(activity)
        private val factual = FactualSignalAnalyzer(activity)
        private var visual: LiveSignalVisualView? = null
        private var lastInsight = ConversationInsightSnapshot()
        private var listenerAdded = false
        private var installQueued = false
        private var attempts = 0
        private var pulseDeck = false

        private val transcriptListener: (LiveTranscriptState.State) -> Unit = { state ->
            activity.runOnUiThread {
                lastInsight = intelligence.analyze(phone(), state)
                publishFactualPreview(state)
                render(state)
            }
        }

        private val ticker = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                if (visual?.parent == null) {
                    visual = null
                    install()
                }
                if (!pulseDeck) {
                    dedupeVisuals()
                    syncLayoutMode()
                    hideLegacySignals()
                }
                render(LiveTranscriptState.snapshot())
                handler.postDelayed(this, 120L)
            }
        }

        fun resume() {
            if (!listenerAdded) {
                LiveTranscriptState.addListener(transcriptListener)
                listenerAdded = true
            }
            attempts = 0
            install()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        fun pause() {
            handler.removeCallbacks(ticker)
        }

        fun destroy() {
            handler.removeCallbacks(ticker)
            if (listenerAdded) LiveTranscriptState.removeListener(transcriptListener)
            listenerAdded = false
            installQueued = false
            visual = null
            pulseDeck = false
        }

        private fun install() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val pulseDeckRoot = content.findViewWithTag<View>(CallActivity.PULSE_DECK_ROOT_TAG)
            if (pulseDeckRoot != null) {
                pulseDeck = true
                val embedded = collectSignalVisuals(pulseDeckRoot).firstOrNull()
                if (embedded == null) {
                    if (attempts++ < 20) handler.postDelayed({ install() }, 100L)
                    return
                }
                visual = embedded
                embedded.tag = TAG_VISUAL
                embedded.setOnClickListener { showDetails() }
                dedupeVisuals(content)
                render(LiveTranscriptState.snapshot())
                return
            }
            pulseDeck = false
            if (dedupeVisuals(content) != null || installQueued) return

            installQueued = true
            content.post {
                installQueued = false
                if (activity.isFinishing || activity.isDestroyed) return@post

                // Another install may have completed while this runnable was queued. Re-check here
                // so only one Pulse Spectrum can ever be attached to the call workspace.
                if (dedupeVisuals(content) != null) {
                    hideLegacySignals()
                    syncLayoutMode()
                    render(LiveTranscriptState.snapshot())
                    return@post
                }

                val liveTitle = findExactText(content, "LIVE TRANSCRIPT")
                val header = liveTitle?.parent as? ViewGroup
                val workspace = header?.parent as? ViewGroup
                if (workspace == null) {
                    if (attempts++ < 20) handler.postDelayed({ install() }, 100L)
                    return@post
                }

                val panel = LiveSignalVisualView(activity).apply {
                    tag = TAG_VISUAL
                    setOnClickListener { showDetails() }
                }
                val radar = workspace.findViewWithTag<View>(TAG_RADAR)
                val index = if (radar != null) workspace.indexOfChild(radar) + 1 else workspace.indexOfChild(header) + 1
                workspace.addView(
                    panel,
                    index.coerceIn(0, workspace.childCount),
                    LinearLayout.LayoutParams(-1, dp(92)).apply { setMargins(0, dp(2), 0, dp(5)) },
                )
                visual = panel
                dedupeVisuals(content)
                hideLegacySignals()
                syncLayoutMode()
                render(LiveTranscriptState.snapshot())
            }
        }

        /**
         * Keeps exactly one live signal instrument attached. Older builds could queue several
         * install runnables before the first panel reached the view tree, leaving zero-value copies
         * underneath the active spectrum. Prefer the panel this session is already rendering and
         * remove every other LiveSignalVisualView regardless of tag.
         */
        private fun dedupeVisuals(
            content: ViewGroup? = activity.findViewById<ViewGroup>(android.R.id.content),
        ): LiveSignalVisualView? {
            content ?: return null
            val panels = collectSignalVisuals(content)
            if (panels.isEmpty()) {
                if (visual?.parent == null) visual = null
                return null
            }

            val keep = visual?.takeIf { candidate -> candidate.parent != null && panels.any { it === candidate } }
                ?: panels.first()
            keep.tag = TAG_VISUAL
            keep.setOnClickListener { showDetails() }
            panels.forEach { candidate ->
                if (candidate !== keep) (candidate.parent as? ViewGroup)?.removeView(candidate)
            }
            visual = keep
            return keep
        }

        private fun render(state: LiveTranscriptState.State) {
            visual?.render(LiveSignalState.snapshot(), state, lastInsight)
        }

        /**
         * Factual analysis used to wait for the turn-finalization timer. Preview the current caller
         * hypothesis against saved memory and already-finalized caller turns so Pulse Spectrum can
         * repaint immediately. preview() is deliberately non-mutating, so interim STT rewrites do
         * not become remembered claims or create self-conflicts.
         */
        private fun publishFactualPreview(state: LiveTranscriptState.State) {
            if (state.isCaller == false) return
            val current = state.text.trim()
            if (current.isBlank()) return
            val earlierClaims = state.entries.asSequence()
                .filter { it.isCaller != false }
                .map { it.text.trim() }
                .filter { it.isNotBlank() && !it.equals(current, ignoreCase = true) }
                .toList()
            val result = factual.preview(phone(), current, earlierClaims)
            val context = buildString {
                append(current.take(150))
                if (result.reason.isNotBlank()) append(" [consistency: ").append(result.reason).append(']')
            }
            LiveSignalState.publishRealtime(
                factual = result.score,
                context = context.take(220),
            )
        }

        private fun syncLayoutMode() {
            val panel = visual ?: return
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            // LiveTranscriptLayoutOverlay labels the control with the destination: when the label
            // reads TRANSCRIPT, INTEL is currently open and this instrument gets the richer layout.
            val intelOpen = findExactText(content, "TRANSCRIPT ↑") != null
            val target = dp(if (intelOpen) 176 else 92)
            val lp = panel.layoutParams ?: return
            if (lp.height != target) {
                lp.height = target
                panel.layoutParams = lp
                panel.requestLayout()
            }
        }

        private fun hideLegacySignals() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            findExactText(content, "LIVE SIGNALS")?.parent?.let { parent ->
                if (parent is View && parent.visibility != View.GONE) parent.visibility = View.GONE
            }
        }

        private fun showDetails() {
            val state = LiveSignalState.snapshot()
            val transcript = LiveTranscriptState.snapshot()
            val language = if (transcript.isCaller != false) {
                LinguisticSignalAnalyzer.analyze(transcript.text)
            } else {
                LinguisticSignalAnalyzer.Result(state.linguistic, emptyList())
            }
            val factualStatus = when {
                state.factual >= 60 || lastInsight.changes.isNotEmpty() -> "Conflict evidence detected — review the matched claim."
                state.factual >= 35 -> "Some consistency evidence needs review."
                transcript.text.isNotBlank() || transcript.entries.isNotEmpty() -> "No conflict found in the evidence currently available."
                else -> "Waiting for enough caller language to compare."
            }
            val body = buildString {
                append("ACOUSTIC · ").append(state.acoustic).append("%\n")
                append("Live voice-change intensity relative to this call's rolling baseline.\n\n")
                append("LINGUISTIC · ").append(state.linguistic).append("%\n")
                append(language.markers.ifEmpty { listOf("No active language markers") }.joinToString(" · ")).append("\n\n")
                append("FACTUAL · ").append(state.factual).append("%\n")
                append(factualStatus)
                if (lastInsight.changes.isNotEmpty()) {
                    append("\n\n").append(lastInsight.changes.take(3).joinToString("\n"))
                }
            }
            AlertDialog.Builder(activity)
                .setTitle("Live signal field")
                .setMessage(body)
                .setPositiveButton("Close", null)
                .show()
        }

        private fun phone(): String =
            CallSessionRegistry.primary()?.details?.handle?.schemeSpecificPart.orEmpty()
    }

    private fun collectSignalVisuals(root: View): List<LiveSignalVisualView> = buildList {
        appendSignalVisuals(root, this)
    }

    private fun appendSignalVisuals(root: View, output: MutableList<LiveSignalVisualView>) {
        if (root is LiveSignalVisualView) output += root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) appendSignalVisuals(root.getChildAt(i), output)
        }
    }

    private fun findExactText(root: View, exact: String): TextView? {
        if (root is TextView && root.text?.toString() == exact) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findExactText(root.getChildAt(i), exact)?.let { return it }
            }
        }
        return null
    }

    private fun dp(value: Int): Int =
        (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
