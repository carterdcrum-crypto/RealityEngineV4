package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Keeps the live transcript readable after Conversation OS adds radar/intelligence surfaces.
 *
 * The call screen defaults to Transcript Focus: the transcript owns the majority of the flexible
 * workspace, while coach/radar remain useful and redundant telemetry collapses. Tapping the small
 * transcript-header control restores the full intelligence stack on demand.
 */
object LiveTranscriptLayoutOverlay {
    private const val CONTROL_TAG = "reality.transcript.layout.control"
    private const val RADAR_TAG = "reality.conversation.radar"
    private const val TRANSLATION_TAG = "reality.conversation.translation"

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

            installToggle(header, title)

            transcript.minimumHeight = dp(if (focusMode) 238 else 112)
            transcript.layoutParams = (transcript.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, 0, 1f)).apply {
                width = -1
                height = 0
                weight = 1f
                setMargins(0, dp(3), 0, dp(6))
            }

            workspace.findViewWithTag<View>(RADAR_TAG)?.let { view ->
                setHeight(view, if (focusMode) 44 else 68)
            }
            workspace.findViewWithTag<View>(TRANSLATION_TAG)?.let { view ->
                if (view.visibility != View.GONE) setHeight(view, if (focusMode) 38 else 46)
            }

            val coachLabel = findExactText(workspace, "RESPONSE COACH")
            val coachHeader = coachLabel?.parent as? ViewGroup
            val coachPanel = coachHeader?.parent as? View
            if (coachPanel != null) {
                setHeight(coachPanel, if (focusMode) 108 else 184)
                val cards = findCoachCards(coachPanel)
                if (cards != null) {
                    cards.visibility = if (!focusMode && ResponseCoachState.current().alternatives.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }

            findTextStarting(workspace, "COACH //")
                ?.also { it.visibility = if (focusMode) View.GONE else View.VISIBLE }
                ?: findTextStarting(workspace, "GROQ //")?.also { it.visibility = if (focusMode) View.GONE else View.VISIBLE }

            val signalsLabel = findExactText(workspace, "LIVE SIGNALS")
            val signals = signalsLabel?.parent as? View
            if (signals != null) {
                signals.visibility = if (focusMode) View.GONE else View.VISIBLE
                if (!focusMode) setHeight(signals, 96)
            }

            findTextStarting(workspace, "NEXT ACTION")?.let { setHeight(it, if (focusMode) 36 else 40) }
        }

        private fun installToggle(header: LinearLayout, title: TextView) {
            val existing = header.findViewWithTag<TextView>(CONTROL_TAG)
            if (existing != null) {
                renderToggle(existing)
                return
            }

            // Reuse the existing secondary header label so we do not steal any extra vertical space.
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

        private fun setHeight(view: View, heightDp: Int) {
            val lp = view.layoutParams ?: return
            val target = dp(heightDp)
            if (lp.height != target) {
                lp.height = target
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
