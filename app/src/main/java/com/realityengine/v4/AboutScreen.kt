package com.realityengine.v4

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Branded About surface that shares the same visual system as call, contacts and setup. */
class AboutScreen(private val activity: Activity, private val onBack: () -> Unit) {
    private val bg = RealityVisuals.Colors.Background
    private val panel = RealityVisuals.Colors.Panel
    private val raised = RealityVisuals.Colors.BackgroundRaised
    private val cyan = RealityVisuals.Colors.Cyan
    private val magenta = RealityVisuals.Colors.Magenta
    private val green = RealityVisuals.Colors.Green
    private val primaryText = RealityVisuals.Colors.Text
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

        root.addView(TextView(activity).apply {
            text = "REALITY ENGINE // SYSTEM"
            RealityVisuals.styleMicroLabel(this, magenta)
        }, LinearLayout.LayoutParams(-1, dp(28)))
        root.addView(TextView(activity).apply {
            text = AboutContent.TITLE
            setTextColor(primaryText)
            RealityTypography.displayMedium(this, 28f)
        })
        root.addView(TextView(activity).apply {
            text = AboutContent.DEVELOPER.uppercase()
            setPadding(0, dp(7), 0, dp(14))
            RealityVisuals.styleMicroLabel(this, cyan)
        })

        val identity = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = RealityVisuals.panel(activity, fill = raised, stroke = cyan, radiusDp = 14f)
        }
        identity.addView(TextView(activity).apply {
            text = "LIVE CONVERSATION COPILOT"
            RealityVisuals.styleMicroLabel(this, green)
        })
        identity.addView(TextView(activity).apply {
            text = AboutContent.INTRO
            setTextColor(Color.rgb(198, 229, 235))
            setPadding(0, dp(6), 0, 0)
            RealityTypography.display(this, 14f)
            setLineSpacing(2f, 1.14f)
        })
        root.addView(identity, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(12))
        })

        val scroll = ScrollView(activity)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(TextView(activity).apply {
            text = "CAPABILITIES"
            setPadding(dp(3), dp(5), 0, dp(6))
            RealityVisuals.styleMicroLabel(this, magenta)
        })

        AboutContent.capabilities.forEachIndexed { index, capability ->
            val accent = when (index % 3) {
                0 -> cyan
                1 -> magenta
                else -> green
            }
            body.addView(capabilityCard(index + 1, capability, accent))
        }

        val guidance = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = RealityVisuals.panel(activity, fill = raised, stroke = RealityVisuals.Colors.Border, radiusDp = 12f)
        }
        guidance.addView(TextView(activity).apply {
            text = "SIGNAL GUIDANCE"
            RealityVisuals.styleMicroLabel(this, magenta)
        })
        guidance.addView(TextView(activity).apply {
            text = "Reality Engine provides conversation assistance and context. Its signals are not proof of deception; treat them as cues to review alongside the conversation itself."
            setTextColor(muted)
            setPadding(0, dp(6), 0, 0)
            RealityTypography.display(this, 12.5f)
            setLineSpacing(1f, 1.12f)
        })
        body.addView(guidance, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(12), 0, dp(12))
        })

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(activity).apply {
            text = "Back to Settings"
            setTextColor(Color.rgb(0, 26, 31))
            RealityTypography.technical(this, 12f)
            background = RealityVisuals.panel(activity, fill = cyan, stroke = cyan, radiusDp = 14f)
            stateListAnimator = null
            gravity = Gravity.CENTER
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        return root
    }

    private fun capabilityCard(
        number: Int,
        capability: AboutContent.Capability,
        accent: Int,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(dp(11), dp(11), dp(12), dp(11))
        background = RealityVisuals.panel(activity, fill = panel, stroke = RealityVisuals.Colors.Border, radiusDp = 12f)

        addView(TextView(activity).apply {
            text = String.format("%02d", number)
            gravity = Gravity.CENTER
            background = RealityVisuals.circle(activity, fill = raised, stroke = accent)
            RealityVisuals.styleMicroLabel(this, accent)
        }, LinearLayout.LayoutParams(dp(38), dp(38)))

        val stack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        stack.addView(TextView(activity).apply {
            text = capability.title
            setTextColor(primaryText)
            RealityTypography.displayMedium(this, 14f)
        })
        stack.addView(TextView(activity).apply {
            text = capability.description
            setTextColor(Color.rgb(174, 207, 216))
            setPadding(0, dp(4), 0, 0)
            RealityTypography.display(this, 11.5f)
            setLineSpacing(1f, 1.1f)
        })
        addView(stack, LinearLayout.LayoutParams(0, -2, 1f))
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(4), 0, dp(4))
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
