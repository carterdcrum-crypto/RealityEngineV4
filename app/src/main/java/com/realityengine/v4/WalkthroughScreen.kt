package com.realityengine.v4

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** First-launch walkthrough with live setup readiness and beginner-friendly navigation. */
class WalkthroughScreen(
    private val activity: Activity,
    private val state: OnboardingState,
    private val onExit: () -> Unit,
    private val onAction: (WalkthroughContent.Step) -> Unit = {}
) {
    private val nav = WalkthroughNavigator()
    private val settings = SettingsStore(activity)
    private val bg = Color.rgb(3, 7, 12)
    private val panel = Color.rgb(9, 18, 27)
    private val cyan = Color.rgb(40, 224, 255)
    private val green = Color.rgb(75, 255, 165)
    private val amber = Color.rgb(255, 196, 92)
    private val muted = Color.rgb(118, 147, 163)

    fun show() { activity.setContentView(build()) }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(38), dp(22), dp(22))
            setBackgroundColor(bg)
        }
        root.addView(TextView(activity).apply {
            text = "SETUP // ${nav.progressText}"
            setTextColor(cyan)
            RealityTypography.technical(this, 11f)
        }, LinearLayout.LayoutParams(-1, dp(34)))

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val step = nav.current()
        body.addView(TextView(activity).apply {
            text = step.title
            setTextColor(Color.rgb(229, 249, 252))
            RealityTypography.displayMedium(this, 27f)
            setPadding(0, dp(10), 0, dp(18))
        })
        body.addView(TextView(activity).apply {
            text = step.body
            setTextColor(Color.rgb(205, 241, 248))
            RealityTypography.display(this, 16f)
            setLineSpacing(0f, 1.18f)
            setPadding(0, 0, 0, dp(18))
        })
        WalkthroughSetupStatus.forStep(activity, step, settings)?.let { setup ->
            body.addView(TextView(activity).apply {
                text = if (setup.ready) "✓ ${setup.message}" else "○ ${setup.message}"
                setTextColor(if (setup.ready) green else amber)
                RealityTypography.technical(this, 11f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(panel); setStroke(dp(1), if (setup.ready) green else amber); cornerRadius = dp(12).toFloat()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(16)) })
        }
        step.actionLabel?.let { label ->
            body.addView(button(label) { onAction(step) }, LinearLayout.LayoutParams(-1, dp(56)))
            body.addView(textButton("Refresh status") { show() }, LinearLayout.LayoutParams(-1, dp(42)))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val navigation = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        if (!nav.isFirst) navigation.addView(button("Back") { nav.previous(); show() }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        navigation.addView(button(if (nav.isLast) "Start Reality Engine" else "Next") {
            if (nav.isLast) { state.complete(); onExit() } else { nav.next(); show() }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(navigation)

        val dismiss = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        dismiss.addView(textButton("Skip for now") { state.skip(); onExit() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        dismiss.addView(textButton("Don't show this again") { state.dontShowAgain(); onExit() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(dismiss)
        return root
    }

    private fun button(label: String, click: () -> Unit) = Button(activity).apply {
        text = label; setTextColor(cyan); RealityTypography.technical(this, 12f)
        background = GradientDrawable().apply { setColor(panel); setStroke(dp(1), cyan); cornerRadius = dp(14).toFloat() }
        stateListAnimator = null; setOnClickListener { click() }
    }

    private fun textButton(label: String, click: () -> Unit) = Button(activity).apply {
        text = label; setTextColor(muted); RealityTypography.technical(this, 10f)
        setBackgroundColor(Color.TRANSPARENT); stateListAnimator = null; setOnClickListener { click() }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
