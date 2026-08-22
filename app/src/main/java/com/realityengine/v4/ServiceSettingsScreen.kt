package com.realityengine.v4

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Reference-style provider/settings surface kept out of MainActivity. */
class ServiceSettingsScreen(
    private val context: Context,
    private val store: SettingsStore,
    private val onRefresh: () -> Unit
) {
    private val cyan = Color.rgb(47, 231, 247)
    private val text = Color.rgb(233, 244, 248)
    private val muted = Color.rgb(120, 143, 158)
    private val panel = Color.rgb(8, 20, 32)
    private val border = Color.rgb(20, 66, 86)

    fun build(): View {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(20))
        }
        list.addView(title("AI + transcription"))
        list.addView(card("Groq API key", configured(store.groqConfigured())) {
            editSecret("Groq API key", store.groqApiKey) { store.groqApiKey = it }
        })
        list.addView(card("Groq model", store.groqModel) { chooseGroqModel() })
        list.addView(card("Deepgram API key", configured(store.deepgramConfigured())) {
            editSecret("Deepgram API key", store.deepgramApiKey) { store.deepgramApiKey = it }
        })
        list.addView(card("Deepgram model", store.deepgramModel) { chooseDeepgramModel() })

        list.addView(title("Caller memory"))
        list.addView(card("Supabase URL", if (store.supabaseUrl.isBlank()) "Not configured" else "Configured") {
            editText("Supabase URL", store.supabaseUrl, false) { store.supabaseUrl = it }
        })
        list.addView(card("Supabase anon key", if (store.supabaseAnonKey.isBlank()) "Not configured" else "Configured") {
            editSecret("Supabase anon key", store.supabaseAnonKey) { store.supabaseAnonKey = it }
        })

        list.addView(title("Live analysis"))
        list.addView(card("Response coach", if (store.responseCoachEnabled) "Enabled" else "Disabled") {
            store.responseCoachEnabled = !store.responseCoachEnabled
            onRefresh()
        })
        list.addView(card("Haptic feedback", if (store.hapticsEnabled) "Enabled" else "Disabled") {
            store.hapticsEnabled = !store.hapticsEnabled
            onRefresh()
        })
        list.addView(card("Analysis interval", "Every ${store.analysisFrequencySeconds} seconds") {
            chooseAnalysisInterval()
        })

        return ScrollView(context).apply { addView(list) }
    }

    private fun title(value: String) = TextView(context).apply {
        text = value
        setTextColor(cyan)
        setPadding(dp(8), dp(18), dp(8), dp(8))
        RealityTypography.technical(this, 11f)
    }

    private fun card(label: String, value: String, click: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = roundedPanel()
        addView(TextView(context).apply {
            text = label
            setTextColor(text)
            RealityTypography.displayMedium(this, 15f)
        })
        addView(TextView(context).apply {
            text = value
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
            RealityTypography.display(this, 12f)
        })
        setOnClickListener { click() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, dp(68)).apply { setMargins(0, dp(4), 0, dp(4)) }
    }

    private fun chooseGroqModel() {
        val items = arrayOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile", "openai/gpt-oss-20b")
        AlertDialog.Builder(context).setTitle("Groq model").setItems(items) { _, which ->
            store.groqModel = items[which]
            onRefresh()
        }.show()
    }

    private fun chooseDeepgramModel() {
        val items = arrayOf("nova-2-phonecall", "nova-2", "nova-3")
        AlertDialog.Builder(context).setTitle("Deepgram model").setItems(items) { _, which ->
            store.deepgramModel = items[which]
            onRefresh()
        }.show()
    }

    private fun chooseAnalysisInterval() {
        val values = intArrayOf(1, 2, 3, 5, 8, 10)
        val labels = values.map { "$it seconds" }.toTypedArray()
        AlertDialog.Builder(context).setTitle("Analysis interval").setItems(labels) { _, which ->
            store.analysisFrequencySeconds = values[which]
            onRefresh()
        }.show()
    }

    private fun editSecret(title: String, current: String, save: (String) -> Unit) =
        editText(title, current, true, save)

    private fun editText(title: String, current: String, secret: Boolean, save: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI
            setSelection(length())
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> save(input.text.toString()); onRefresh() }
            .show()
    }

    private fun configured(value: Boolean) = if (value) "Configured" else "API key required"

    private fun roundedPanel() = GradientDrawable().apply {
        setColor(panel)
        setStroke(dp(1), border)
        cornerRadius = dp(16).toFloat()
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
