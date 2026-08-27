package com.realityengine.v4

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** First-launch walkthrough with live setup readiness and the shared Reality Engine visual language. */
class WalkthroughScreen(
    private val activity: Activity,
    private val state: OnboardingState,
    private val onExit: () -> Unit,
    private val onAction: (WalkthroughContent.Step) -> Unit = {},
) {
    private val nav = WalkthroughNavigator()
    private val settings = SettingsStore(activity)
    private val bg = RealityVisuals.Colors.Background
    private val raised = RealityVisuals.Colors.BackgroundRaised
    private val panel = RealityVisuals.Colors.Panel
    private val cyan = RealityVisuals.Colors.Cyan
    private val magenta = RealityVisuals.Colors.Magenta
    private val green = RealityVisuals.Colors.Green
    private val amber = RealityVisuals.Colors.Amber
    private val text = RealityVisuals.Colors.Text
    private val muted = RealityVisuals.Colors.TextDim

    fun show() {
        activity.setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(18))
            setBackgroundColor(bg)
        }

        root.addView(topBar())
        root.addView(progressRail(), LinearLayout.LayoutParams(-1, dp(6)).apply {
            setMargins(0, dp(2), 0, dp(14))
        })

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val step = nav.current()

        body.addView(TextView(activity).apply {
            text = "SETUP MODULE ${String.format("%02d", nav.index + 1)}"
            RealityVisuals.styleMicroLabel(this, magenta)
            setPadding(0, dp(4), 0, dp(8))
        })
        body.addView(TextView(activity).apply {
            text = step.title
            setTextColor(text)
            RealityTypography.displayMedium(this, 28f)
            setLineSpacing(0f, 1.04f)
            setPadding(0, 0, 0, dp(12))
        })
        body.addView(TextView(activity).apply {
            text = step.body
            setTextColor(Color.rgb(196, 226, 233))
            RealityTypography.display(this, 15.5f)
            setLineSpacing(2f, 1.18f)
            setPadding(0, 0, 0, dp(16))
        })

        WalkthroughSetupStatus.forStep(activity, step, settings)?.let { setup ->
            body.addView(statusCard(setup), LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(14))
            })
        }

        step.actionLabel?.let { label ->
            body.addView(primaryButton(label) { onAction(step) }, LinearLayout.LayoutParams(-1, dp(56)))
            body.addView(textButton("Refresh setup status", cyan) { show() }, LinearLayout.LayoutParams(-1, dp(42)))
        }

        body.addView(TextView(activity).apply {
            text = if (nav.isLast) {
                "SETUP COMPLETE // You can revisit every item from Settings."
            } else {
                "NEXT // ${WalkthroughContent.steps.getOrNull(nav.index + 1)?.title ?: "Ready"}"
            }
            setPadding(0, dp(16), 0, dp(8))
            RealityVisuals.styleMicroLabel(this, muted)
        })

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val navigation = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        if (!nav.isFirst) {
            navigation.addView(secondaryButton("Back") {
                nav.previous()
                show()
            }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        }
        navigation.addView(primaryButton(if (nav.isLast) "Launch Reality Engine" else "Next") {
            if (nav.isLast) {
                state.complete()
                onExit()
            } else {
                nav.next()
                show()
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(navigation)

        val dismiss = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        dismiss.addView(textButton("Skip for now", muted) {
            state.skip()
            onExit()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        dismiss.addView(textButton("Don't show again", muted) {
            state.dontShowAgain()
            onExit()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(dismiss)
        return root
    }

    private fun topBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = "REALITY ENGINE // SETUP"
            RealityVisuals.styleMicroLabel(this, cyan)
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        addView(TextView(activity).apply {
            text = nav.progressText.uppercase()
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(
                activity,
                fill = raised,
                stroke = RealityVisuals.Colors.Border,
                radiusDp = 18f,
            )
            setPadding(dp(9), dp(3), dp(9), dp(3))
            RealityVisuals.styleMicroLabel(this, cyan)
        })
    }

    private fun progressRail(): View = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = WalkthroughContent.steps.size
        progress = nav.index + 1
        progressTintList = ColorStateList.valueOf(cyan)
        progressBackgroundTintList = ColorStateList.valueOf(RealityVisuals.Colors.Track)
    }

    private fun statusCard(setup: WalkthroughSetupStatus.Status): View {
        val accent = if (setup.ready) green else amber
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = RealityVisuals.panel(
                activity,
                fill = if (setup.ready) Color.rgb(6, 28, 22) else panel,
                stroke = accent,
                radiusDp = 12f,
            )
            addView(TextView(activity).apply {
                text = if (setup.ready) "●" else "○"
                setTextColor(accent)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }, LinearLayout.LayoutParams(dp(28), -1))
            val stack = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            stack.addView(TextView(activity).apply {
                text = if (setup.ready) "READY" else "ACTION NEEDED"
                RealityVisuals.styleMicroLabel(this, accent)
            })
            stack.addView(TextView(activity).apply {
                text = setup.message
                setTextColor(text)
                setPadding(0, dp(3), 0, 0)
                RealityTypography.display(this, 12.5f)
            })
            addView(stack, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun primaryButton(label: String, click: () -> Unit) = Button(activity).apply {
        text = label
        setTextColor(Color.rgb(0, 26, 31))
        RealityTypography.technical(this, 12f)
        background = RealityVisuals.panel(activity, fill = cyan, stroke = cyan, radiusDp = 14f)
        stateListAnimator = null
        setOnClickListener {
            RealityVisuals.reveal(this)
            click()
        }
    }

    private fun secondaryButton(label: String, click: () -> Unit) = Button(activity).apply {
        text = label
        setTextColor(cyan)
        RealityTypography.technical(this, 12f)
        background = RealityVisuals.panel(activity, fill = panel, stroke = cyan, radiusDp = 14f)
        stateListAnimator = null
        setOnClickListener { click() }
    }

    private fun textButton(label: String, color: Int, click: () -> Unit) = Button(activity).apply {
        text = label
        setTextColor(color)
        RealityTypography.technical(this, 9.5f)
        setBackgroundColor(Color.TRANSPARENT)
        stateListAnimator = null
        setOnClickListener { click() }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
