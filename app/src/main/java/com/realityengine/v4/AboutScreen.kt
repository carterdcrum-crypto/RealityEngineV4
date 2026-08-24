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

/** Plain-language About screen, intended to be opened from Settings only. */
class AboutScreen(private val activity: Activity, private val onBack: () -> Unit) {
    private val bg = Color.rgb(3, 7, 12)
    private val panel = Color.rgb(9, 18, 27)
    private val cyan = Color.rgb(40, 224, 255)
    private val text = Color.rgb(205, 241, 248)
    private val muted = Color.rgb(118, 147, 163)

    fun show() { activity.setContentView(build()) }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(38), dp(22), dp(22))
            setBackgroundColor(bg)
        }
        root.addView(TextView(activity).apply {
            this.text = AboutContent.TITLE
            setTextColor(Color.rgb(229, 249, 252))
            RealityTypography.displayMedium(this, 27f)
        })
        root.addView(TextView(activity).apply {
            this.text = AboutContent.DEVELOPER
            setTextColor(cyan)
            RealityTypography.technical(this, 12f)
            setPadding(0, dp(8), 0, dp(18))
        })

        val scroll = ScrollView(activity)
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(TextView(activity).apply {
            this.text = AboutContent.INTRO
            setTextColor(text)
            RealityTypography.display(this, 15f)
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(16))
        })
        AboutContent.capabilities.forEach { capability ->
            body.addView(TextView(activity).apply {
                this.text = "${capability.title}\n${capability.description}"
                setTextColor(text)
                RealityTypography.display(this, 14f)
                setLineSpacing(0f, 1.12f)
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    setColor(panel); setStroke(dp(1), Color.rgb(15, 66, 81)); cornerRadius = dp(14).toFloat()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
        }
        body.addView(TextView(activity).apply {
            this.text = "Reality Engine provides conversation assistance and context. Its signals are not proof of deception and should be interpreted by the user."
            setTextColor(muted)
            RealityTypography.display(this, 12f)
            setPadding(0, dp(18), 0, dp(12))
        })
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(activity).apply {
            this.text = "Back to Settings"
            setTextColor(cyan)
            RealityTypography.technical(this, 12f)
            background = GradientDrawable().apply { setColor(panel); setStroke(dp(1), cyan); cornerRadius = dp(14).toFloat() }
            stateListAnimator = null
            gravity = Gravity.CENTER
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        return root
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
