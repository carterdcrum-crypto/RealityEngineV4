package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Routes the existing presentation observer away from the live call screen so Pulse Deck can skin
 * the proven CallActivity hierarchy without replacing or touching its call lifecycle.
 */
object PresentationSkinRouter {
    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) {
            if (activity is CallActivity) PulseDeckCallSkin.applySafely(activity)
            else RealityOperatorSkin.callbacks.onActivityStarted(activity)
        }
        override fun onActivityResumed(activity: Activity) {
            if (activity is CallActivity) PulseDeckCallSkin.applySafely(activity)
            else RealityOperatorSkin.callbacks.onActivityResumed(activity)
        }
        override fun onActivityPaused(activity: Activity) {
            if (activity !is CallActivity) RealityOperatorSkin.callbacks.onActivityPaused(activity)
        }
        override fun onActivityStopped(activity: Activity) {
            if (activity !is CallActivity) RealityOperatorSkin.callbacks.onActivityStopped(activity)
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            if (activity !is CallActivity) RealityOperatorSkin.callbacks.onActivitySaveInstanceState(activity, outState)
        }
        override fun onActivityDestroyed(activity: Activity) {
            if (activity !is CallActivity) RealityOperatorSkin.callbacks.onActivityDestroyed(activity)
        }
    }
}

/**
 * Presentation-only Pulse Deck skin for the known-good pre-Pulse CallActivity.
 *
 * Important: this class never starts/finishes activities, touches Telecom/InCallService, changes
 * call state, audio routing, transcription, timers, listeners, or click handlers. It only changes
 * drawables, typography, padding and labels on views that CallActivity already created.
 */
object PulseDeckCallSkin {
    private const val APPLIED_TAG = "realityengine.pulsedeck.skin.applied"

    fun applySafely(activity: CallActivity) {
        runCatching {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            if (content.childCount == 0) return
            val root = content.getChildAt(0)
            apply(activity, root)
            root.post { runCatching { apply(activity, root) } }
        }
    }

    private fun apply(activity: CallActivity, root: View) {
        root.tag = APPLIED_TAG
        root.background = PulseDeckVisuals.backdrop()
        activity.window.statusBarColor = PulseDeckVisuals.Colors.Background
        activity.window.navigationBarColor = PulseDeckVisuals.Colors.Background

        styleIdentity(activity, root)
        styleHealth(activity, root)
        styleTranscript(activity, root)
        styleCoach(activity, root)
        styleSignals(activity, root)
        styleNextAction(activity, root)
        styleButtons(activity, root)
        styleKeypad(activity, root)
    }

    private fun styleIdentity(activity: CallActivity, root: View) {
        val label = findText(root) { it.text?.toString()?.trim()?.equals("ACTIVE CONTACT", true) == true } ?: return
        val callerStack = label.parent as? ViewGroup ?: return
        val identity = callerStack.parent as? ViewGroup ?: return

        identity.background = PulseDeckVisuals.panel(activity, radiusDp = 18f)
        if (identity is LinearLayout) identity.setPadding(10.dp(activity), 5.dp(activity), 10.dp(activity), 5.dp(activity))

        label.text = "PULSE DECK  //  PHONE"
        label.setTextColor(PulseDeckVisuals.Colors.Cyan)
        label.textSize = 8.5f
        label.letterSpacing = .075f
        label.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        for (i in 0 until callerStack.childCount) {
            val child = callerStack.getChildAt(i)
            if (child is TextView && child !== label) {
                child.setTextColor(PulseDeckVisuals.Colors.Text)
                child.textSize = 19f
                child.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                child.letterSpacing = .015f
            }
        }

        val telemetry = (0 until identity.childCount)
            .map { identity.getChildAt(it) }
            .filterIsInstance<ViewGroup>()
            .firstOrNull { it !== callerStack }
        telemetry?.let { group ->
            for (i in 0 until group.childCount) {
                val text = group.getChildAt(i) as? TextView ?: continue
                val value = text.text?.toString().orEmpty()
                if (value.matches(Regex("\\d{2}:\\d{2}.*"))) {
                    text.setTextColor(PulseDeckVisuals.Colors.Text)
                    text.textSize = 16f
                    text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                } else {
                    text.setTextColor(PulseDeckVisuals.Colors.Green)
                    text.textSize = 9.5f
                    text.letterSpacing = .065f
                    text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    text.background = null
                }
            }
        }
    }

    private fun styleHealth(activity: CallActivity, root: View) {
        val health = findText(root) {
            val value = it.text?.toString().orEmpty().uppercase()
            value.contains("AUDIO") && value.contains("STT") && value.contains("COACH")
        } ?: return
        health.setTextColor(PulseDeckVisuals.Colors.Text)
        health.textSize = 9.5f
        health.letterSpacing = .055f
        health.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        health.gravity = Gravity.CENTER
        health.background = PulseDeckVisuals.panel(activity, radiusDp = 14f)
    }

    private fun styleTranscript(activity: CallActivity, root: View) {
        val header = findText(root) { it.text?.toString()?.trim()?.equals("LIVE TRANSCRIPT", true) == true }
        header?.apply {
            setTextColor(PulseDeckVisuals.Colors.Cyan)
            textSize = 10f
            letterSpacing = .07f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val panel = findView<LiveTranscriptPanelView>(root)
        panel?.background = PulseDeckVisuals.panel(
            activity,
            start = PulseDeckVisuals.Colors.PanelTop,
            end = PulseDeckVisuals.Colors.PanelBottom,
            stroke = PulseDeckVisuals.Colors.Border,
            radiusDp = 18f,
        )
    }

    private fun styleCoach(activity: CallActivity, root: View) {
        val title = findText(root) { it.text?.toString()?.trim()?.equals("RESPONSE COACH", true) == true } ?: return
        val header = title.parent as? ViewGroup ?: return
        val panel = header.parent as? ViewGroup ?: return

        title.text = "✦  BEST RESPONSE"
        title.setTextColor(PulseDeckVisuals.Colors.Lime)
        title.textSize = 10.8f
        title.letterSpacing = .05f
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        for (i in 0 until header.childCount) {
            val child = header.getChildAt(i) as? TextView ?: continue
            if (child !== title) {
                child.text = "LIVE CALL INTELLIGENCE"
                child.setTextColor(PulseDeckVisuals.Colors.TextDim)
                child.textSize = 8.4f
                child.letterSpacing = .055f
                child.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }

        panel.background = PulseDeckVisuals.panel(
            activity,
            start = Color.rgb(13, 43, 31),
            end = Color.rgb(5, 24, 24),
            stroke = PulseDeckVisuals.Colors.Lime,
            radiusDp = 18f,
            strokeDp = 2,
        )
        if (panel is LinearLayout) panel.setPadding(12.dp(activity), 9.dp(activity), 12.dp(activity), 9.dp(activity))

        for (i in 0 until panel.childCount) {
            val child = panel.getChildAt(i)
            if (child is TextView && child !== title && child.parent === panel) {
                child.setTextColor(PulseDeckVisuals.Colors.Text)
                child.textSize = 16f
                child.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                child.letterSpacing = 0f
                child.setLineSpacing(2.dp(activity).toFloat(), 1.04f)
            }
        }
    }

    private fun styleSignals(activity: CallActivity, root: View) {
        val title = findText(root) { it.text?.toString()?.trim()?.equals("LIVE SIGNALS", true) == true } ?: return
        title.setTextColor(PulseDeckVisuals.Colors.Cyan)
        title.textSize = 9.8f
        title.letterSpacing = .07f
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val panel = title.parent as? ViewGroup ?: return
        panel.background = PulseDeckVisuals.panel(activity, radiusDp = 16f)

        val bars = mutableListOf<ProgressBar>()
        collectViews(panel, bars)
        val accents = intArrayOf(
            PulseDeckVisuals.Colors.Cyan,
            PulseDeckVisuals.Colors.Amber,
            PulseDeckVisuals.Colors.Green,
        )
        bars.take(3).forEachIndexed { index, bar ->
            val accent = accents[index]
            bar.progressTintList = ColorStateList.valueOf(accent)
            bar.progressBackgroundTintList = ColorStateList.valueOf(PulseDeckVisuals.Colors.Track)
        }
    }

    private fun styleNextAction(activity: CallActivity, root: View) {
        val action = findText(root) { it.text?.toString()?.uppercase()?.startsWith("NEXT ACTION") == true } ?: return
        action.setTextColor(PulseDeckVisuals.Colors.Cyan)
        action.textSize = 10f
        action.letterSpacing = .055f
        action.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        action.background = PulseDeckVisuals.panel(
            activity,
            start = PulseDeckVisuals.Colors.PanelSoft,
            end = PulseDeckVisuals.Colors.PanelBottom,
            stroke = PulseDeckVisuals.Colors.Cyan,
            radiusDp = 15f,
        )
    }

    private fun styleButtons(activity: CallActivity, root: View) {
        val buttons = mutableListOf<Button>()
        collectViews(root, buttons)
        buttons.forEach { button ->
            val label = button.text?.toString().orEmpty().trim().uppercase()
            if (label.length == 1 && label[0] in "0123456789*#") return@forEach

            val destructive = label.contains("END") || label.contains("DECLINE")
            val accent = when {
                destructive -> PulseDeckVisuals.Colors.Coral
                label.contains("ACCEPT") -> PulseDeckVisuals.Colors.Green
                label.contains("AUDIO") -> PulseDeckVisuals.Colors.Cyan
                label.contains("FLIRT") -> PulseDeckVisuals.Colors.Cyan
                label.contains("UNHINGED") -> PulseDeckVisuals.Colors.Coral
                label.contains("SOUND") -> PulseDeckVisuals.Colors.Green
                label.contains("RECORD") -> PulseDeckVisuals.Colors.Coral
                else -> PulseDeckVisuals.Colors.Text
            }
            val selected = label.contains("ACCEPT") || label.contains("AUDIO")
            PulseDeckVisuals.styleCallControl(
                button,
                iconRes = 0,
                accent = accent,
                selected = selected,
                destructive = destructive,
                circular = false,
            )
            button.compoundDrawableTintList = ColorStateList.valueOf(accent)
        }
    }

    private fun styleKeypad(activity: CallActivity, root: View) {
        val buttons = mutableListOf<Button>()
        collectViews(root, buttons)
        buttons.forEach { button ->
            val label = button.text?.toString().orEmpty().trim()
            if (label.length != 1 || label[0] !in "0123456789*#") return@forEach
            button.setTextColor(PulseDeckVisuals.Colors.Cyan)
            button.textSize = 18f
            button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            button.background = PulseDeckVisuals.panel(
                activity,
                start = PulseDeckVisuals.Colors.PanelSoft,
                end = PulseDeckVisuals.Colors.PanelBottom,
                stroke = PulseDeckVisuals.Colors.Border,
                radiusDp = 16f,
            )
        }
    }

    private fun findText(root: View, predicate: (TextView) -> Boolean): TextView? {
        if (root is TextView && predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findText(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private inline fun <reified T : View> findView(root: View): T? {
        if (root is T) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView<T>(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private inline fun <reified T : View> collectViews(root: View, output: MutableList<T>) {
        if (root is T) output += root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) collectViews(root.getChildAt(i), output)
        }
    }

    private fun Int.dp(activity: Activity): Int = (this * activity.resources.displayMetrics.density).toInt()
}