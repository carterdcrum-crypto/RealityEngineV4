package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
 * Keeps the live transcript readable after Conversation OS adds radar/intelligence surfaces.
 *
 * The center call workspace is wrapped in one vertical scroller so the transcript, Pulse Spectrum,
 * and the full ranked response set stay reachable on smaller screens without crowding call controls.
 */
object LiveTranscriptLayoutOverlay {
    private const val CONTROL_TAG = "reality.transcript.layout.control"
    private const val RADAR_TAG = "reality.conversation.radar"
    private const val TRANSLATION_TAG = "reality.conversation.translation"
    private const val SIGNAL_VISUAL_TAG = "reality.signal.visual.pulse"
    private const val WORKSPACE_SCROLL_TAG = "reality.transcript.workspace.scroll"

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
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            sessions.remove(activity)?.destroy()
        }
    }

    private class Session(private val activity: CallActivity) {
        private var focusMode = true
        private var listener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var queued = false

        fun resume() {
            attach()
            queue()
        }

        fun destroy() {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val current = listener
            if (current != null && root?.viewTreeObserver?.isAlive == true) {
                root.viewTreeObserver.removeOnGlobalLayoutListener(current)
            }
            listener = null
        }

        private fun attach() {
            if (listener != null) return
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            listener = ViewTreeObserver.OnGlobalLayoutListener { queue() }.also {
                root.viewTreeObserver.addOnGlobalLayoutListener(it)
            }
        }

        private fun queue() {
            if (queued) return
            queued = true
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            root.post {
                queued = false
                if (activity.isFinishing || activity.isDestroyed) return@post
                applyLayout(root)
            }
        }

        private fun applyLayout(root: ViewGroup) {
            val title = findExactText(root, "LIVE TRANSCRIPT") ?: return
            val header = title.parent as? LinearLayout ?: return
            val workspace = header.parent as? LinearLayout ?: return
            val transcript = findTranscript(workspace) ?: return

            ensureScrollableWorkspace(workspace)
            installToggle(header, title)
            compactEssentialButtons(root)

            val transcriptHeight = if (focusMode) 310 else 230
            transcript.minimumHeight = dp(transcriptHeight)
            transcript.layoutParams = (transcript.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, dp(transcriptHeight))).apply {
                width = -1
                height = dp(transcriptHeight)
                weight = 0f
                setMargins(0, dp(3), 0, dp(6))
            }

            workspace.findViewWithTag<View>(RADAR_TAG)?.let { view ->
                setHeight(view, if (focusMode) 42 else 68)
            }
            workspace.findViewWithTag<View>(SIGNAL_VISUAL_TAG)?.let { view ->
                setHeight(view, if (focusMode) 92 else 176)
            }
            workspace.findViewWithTag<View>(TRANSLATION_TAG)?.let { view ->
                if (view.visibility != View.GONE) setHeight(view, if (focusMode) 36 else 46)
            }

            val coachSnapshot = ResponseCoachState.current()
            val coachLabel = findExactText(workspace, "RESPONSE COACH")
            val coachHeader = coachLabel?.parent as? ViewGroup
            val coachPanel = coachHeader?.parent as? View
            if (coachPanel != null) {
                setWrapContent(coachPanel)
                findCoachBody(coachPanel)?.let { body ->
                    body.minimumHeight = dp(58)
                    body.isVerticalScrollBarEnabled = false
                    body.layoutParams = (body.layoutParams as? LinearLayout.LayoutParams
                        ?: LinearLayout.LayoutParams(-1, -2)).apply {
                        width = -1
                        height = -2
                        weight = 0f
                    }
                }
                val cards = findCoachCards(coachPanel)
                if (cards != null) {
                    setWrapContent(cards)
                    cards.visibility = if (coachSnapshot.alternatives.isNotEmpty()) View.VISIBLE else View.GONE
                }
                if (coachSnapshot.phase == ResponseCoachState.Phase.ERROR) {
                    findTextStarting(coachPanel, "COACH ERROR")?.apply {
                        text = "COACH PAUSED\nAI response format was rejected. The next caller turn will retry."
                        RealityTypography.display(this, 10f)
                    }
                }
            }

            findTextStarting(workspace, "COACH //")
                ?.also { it.visibility = if (focusMode) View.GONE else View.VISIBLE }
                ?: findTextStarting(workspace, "GROQ //")?.also { it.visibility = if (focusMode) View.GONE else View.VISIBLE }

            // Pulse Spectrum is the only live signal surface in both layouts.
            val signalsLabel = findExactText(workspace, "LIVE SIGNALS")
            val signals = signalsLabel?.parent as? View
            if (signals != null) signals.visibility = View.GONE

            findTextStarting(workspace, "NEXT ACTION")?.let { setHeight(it, if (focusMode) 34 else 40) }

            // These are useful tools, but they should not permanently crowd the live transcript.
            setRowVisible(findButton(root, "Unhinged")?.parent as? View, !focusMode)
            setRowVisible(findButton(root, "Objective")?.parent as? View, !focusMode)
        }

        private fun ensureScrollableWorkspace(workspace: LinearLayout) {
            if (workspace.parent is ScrollView) return
            val parent = workspace.parent as? ViewGroup ?: return
            val index = parent.indexOfChild(workspace)
            if (index < 0) return
            val originalParams = workspace.layoutParams
            parent.removeViewAt(index)
            val scroll = ScrollView(activity).apply {
                tag = WORKSPACE_SCROLL_TAG
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                clipToPadding = false
                addView(workspace, ScrollView.LayoutParams(-1, -2))
            }
            parent.addView(scroll, index, originalParams)
        }

        private fun compactEssentialButtons(root: View) {
            val labels = setOf("Mute", "Unmute", "Speaker", "Earpiece", "Bluetooth", "BT", "BT off", "Hold", "Resume")
            walkButtons(root) { button ->
                val label = button.text?.toString().orEmpty()
                if (label !in labels) return@walkButtons
                when (label) {
                    "Bluetooth" -> button.text = "BT"
                    "Earpiece" -> button.text = "Ear"
                }
                button.maxLines = 1
                button.textSize = 9.5f
                button.setPadding(dp(4), 0, dp(4), 0)
            }
        }

        private fun installToggle(header: LinearLayout, title: TextView) {
            val existing = header.findViewWithTag<TextView>(CONTROL_TAG)
            if (existing != null) {
                renderToggle(existing)
                return
            }

            val control = (0 until header.childCount)
                .map { header.getChildAt(it) }
                .filterIsInstance<TextView>()
                .firstOrNull { it !== title }
                ?: TextView(activity).also {
                    header.addView(it, LinearLayout.LayoutParams(-2, dp(22)))
                }

            control.tag = CONTROL_TAG
            control.isClickable = true
            control.isFocusable = true
            control.setPadding(dp(10), 0, dp(2), 0)
            control.setOnClickListener {
                focusMode = !focusMode
                renderToggle(control)
                queue()
                RealityVisuals.pulseOnce(control)
            }
            renderToggle(control)
        }

        private fun renderToggle(control: TextView) {
            control.text = if (focusMode) "INTEL ↓" else "TRANSCRIPT ↑"
            RealityVisuals.styleMicroLabel(
                control,
                if (focusMode) RealityVisuals.Colors.Lilac else RealityVisuals.Colors.CyanSoft,
            )
        }

        private fun setRowVisible(view: View?, visible: Boolean) {
            view ?: return
            val target = if (visible) View.VISIBLE else View.GONE
            if (view.visibility != target) view.visibility = target
        }

        private fun setHeight(view: View, heightDp: Int) {
            val lp = view.layoutParams ?: return
            val target = dp(heightDp)
            if (lp.height != target) {
                lp.height = target
                view.layoutParams = lp
            }
        }

        private fun setWrapContent(view: View) {
            val lp = view.layoutParams ?: return
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                if (lp is LinearLayout.LayoutParams) lp.weight = 0f
                view.layoutParams = lp
            }
        }
    }

    private fun findTranscript(root: View): LiveTranscriptPanelView? {
        if (root is LiveTranscriptPanelView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTranscript(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findCoachCards(root: View): ResponseCoachCardsView? {
        if (root is ResponseCoachCardsView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findCoachCards(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findCoachBody(root: View): TextView? {
        if (root is TextView) {
            val text = root.text?.toString().orEmpty()
            val prefixes = arrayOf("BEST //", "STANDBY", "ANALYZING", "LISTENING", "AI PROVIDER REQUIRED", "COACH DISABLED", "COACH ERROR", "COACH PAUSED", "LAST //")
            if (prefixes.any(text::startsWith)) return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findCoachBody(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findButton(root: View, exact: String): Button? {
        if (root is Button && root.text?.toString() == exact) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findButton(root.getChildAt(i), exact)?.let { return it }
            }
        }
        return null
    }

    private fun walkButtons(root: View, action: (Button) -> Unit) {
        if (root is Button) action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) walkButtons(root.getChildAt(i), action)
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

    private fun findTextStarting(root: View, prefix: String): TextView? {
        if (root is TextView && root.text?.toString()?.startsWith(prefix) == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTextStarting(root.getChildAt(i), prefix)?.let { return it }
            }
        }
        return null
    }

    private fun dp(value: Int): Int =
        (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
