package com.realityengine.v4

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Dedicated caller-memory cloud setup screen with credential and connection testing. */
class SupabaseSetupActivity : Activity() {
    private lateinit var store: SettingsStore
    private lateinit var urlInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var status: TextView

    private val cyan = RealityVisuals.Colors.Cyan
    private val magenta = RealityVisuals.Colors.Magenta
    private val green = RealityVisuals.Colors.Green
    private val amber = RealityVisuals.Colors.Amber
    private val primaryText = RealityVisuals.Colors.Text
    private val muted = RealityVisuals.Colors.TextDim
    private val panel = RealityVisuals.Colors.Panel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)
        buildUi()
    }

    private fun buildUi() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }

        body.addView(TextView(this).apply {
            text = "SUPABASE CALLER MEMORY"
            setTextColor(primaryText)
            RealityTypography.displayMedium(this, 24f)
        })
        body.addView(TextView(this).apply {
            text = "LOCAL-FIRST CLOUD SYNC"
            setPadding(0, dp(3), 0, dp(12))
            RealityVisuals.styleMicroLabel(this, magenta)
        })

        body.addView(TextView(this).apply {
            text = "Syncs caller likes, dislikes, communication style, persona override, topics, conversation starters, facts, unresolved items and last-call memory. Call recordings stay on this phone."
            setTextColor(muted)
            textSize = 12.5f
            setLineSpacing(3f, 1.08f)
            background = RealityVisuals.panel(this@SupabaseSetupActivity, fill = panel, stroke = RealityVisuals.Colors.Border, radiusDp = 12f)
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) })

        body.addView(label("PROJECT URL"))
        urlInput = field(store.supabaseUrl, false).apply {
            hint = "https://your-project.supabase.co"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        body.addView(urlInput, fieldLayout())

        body.addView(label("PUBLISHABLE / ANON KEY"))
        keyInput = field(store.supabaseAnonKey, true).apply {
            hint = "sb_publishable_… or legacy anon key"
        }
        body.addView(keyInput, fieldLayout())

        status = TextView(this).apply {
            when {
                store.supabaseVerified() -> {
                    text = "● SAVED & VERIFIED"
                    setTextColor(green)
                }
                store.supabaseConfigured() -> {
                    text = "● SAVED · TEST CONNECTION ONCE"
                    setTextColor(cyan)
                }
                else -> {
                    text = "○ NOT CONFIGURED"
                    setTextColor(muted)
                }
            }
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = RealityVisuals.panel(this@SupabaseSetupActivity, fill = RealityVisuals.Colors.BackgroundRaised, stroke = RealityVisuals.Colors.Border, radiusDp = 10f)
        }
        body.addView(status, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(12)) })

        val primary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        primary.addView(actionButton("SAVE", cyan) { saveCredentials(showStatus = true) }, buttonLayout())
        primary.addView(actionButton("TEST CONNECTION", green) { testConnection() }, buttonLayout())
        body.addView(primary)

        val secondary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        secondary.addView(actionButton("CLEAR", magenta) { clearConfiguration() }, buttonLayout())
        secondary.addView(actionButton("BACK", RealityVisuals.Colors.Border) { finish() }, buttonLayout())
        body.addView(secondary)

        body.addView(TextView(this).apply {
            text = "SETUP CHECKLIST\n1. Create a Supabase project.\n2. Run supabase/reality_engine_caller_memory.sql in Supabase SQL Editor.\n3. Enable Anonymous Sign-Ins for the current build.\n4. Paste the Project URL and publishable key above.\n5. Tap SAVE once, then TEST CONNECTION once.\n\nThis build uses a saved anonymous Supabase auth session. There is no Supabase email/password login to save yet; permanent account linking can be added later without changing the caller-memory table."
            setTextColor(muted)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setLineSpacing(3f, 1.08f)
            setPadding(dp(4), dp(18), dp(4), 0)
        })

        setContentView(ScrollView(this).apply { addView(body) })
    }

    private fun testConnection() {
        val credentialsChanged = credentialsDifferFromStore()
        if (!saveCredentials(showStatus = false)) return
        if (credentialsChanged) {
            // Only reset auth when the project/key actually changed. Repeated tests reuse the same identity.
            SupabaseMemorySession(applicationContext, store).clear()
        }
        setStatus("● TESTING PROJECT, AUTH + MEMORY TABLE…", amber)
        SupabaseCallerMemorySync(applicationContext).testConnectionAsync { result ->
            runOnUiThread {
                when (result.status) {
                    SupabaseCallerMemorySync.Status.SYNCED -> {
                        store.markSupabaseVerified()
                        setStatus("● SAVED & VERIFIED · ${result.detail}", green)
                    }
                    SupabaseCallerMemorySync.Status.AUTH_REQUIRED -> {
                        store.clearSupabaseVerification()
                        setStatus("○ AUTH FAILED · ${result.detail}", amber)
                    }
                    SupabaseCallerMemorySync.Status.DISABLED -> {
                        store.clearSupabaseVerification()
                        setStatus("○ CONFIG REQUIRED · ${result.detail}", amber)
                    }
                    else -> {
                        store.clearSupabaseVerification()
                        setStatus("○ TEST FAILED · ${result.detail}", magenta)
                    }
                }
            }
        }
    }

    private fun credentialsDifferFromStore(): Boolean {
        val url = urlInput.text.toString().trim().trimEnd('/')
        val key = keyInput.text.toString().trim()
        return url != store.supabaseUrl || key != store.supabaseAnonKey
    }

    private fun saveCredentials(showStatus: Boolean): Boolean {
        val url = urlInput.text.toString().trim().trimEnd('/')
        val key = keyInput.text.toString().trim()
        if (!url.startsWith("https://") || !url.contains(".")) {
            setStatus("○ ENTER A VALID HTTPS SUPABASE PROJECT URL", amber)
            return false
        }
        if (key.isBlank()) {
            setStatus("○ PUBLISHABLE / ANON KEY IS REQUIRED", amber)
            return false
        }

        val changed = url != store.supabaseUrl || key != store.supabaseAnonKey
        if (!store.saveSupabaseCredentials(url, key)) {
            setStatus("○ COULD NOT SAVE SUPABASE SETTINGS", magenta)
            return false
        }
        if (changed) SupabaseMemorySession(applicationContext, store).clear()

        // Re-read from storage so the UI only claims success if the exact values persisted.
        val persisted = SettingsStore(applicationContext)
        if (persisted.supabaseUrl != url || persisted.supabaseAnonKey != key) {
            setStatus("○ SUPABASE SETTINGS DID NOT PERSIST", magenta)
            return false
        }

        if (showStatus) {
            if (store.supabaseVerified() && !changed) setStatus("● SAVED & VERIFIED", green)
            else setStatus("● SAVED · TAP TEST CONNECTION ONCE", cyan)
        }
        return true
    }

    private fun clearConfiguration() {
        store.clearSupabaseCredentials()
        SupabaseMemorySession(applicationContext, store).clear()
        urlInput.setText("")
        keyInput.setText("")
        setStatus("○ SUPABASE CONFIGURATION CLEARED", muted)
    }

    private fun setStatus(message: String, color: Int) {
        status.text = message
        status.setTextColor(color)
        status.background = RealityVisuals.panel(this, fill = RealityVisuals.Colors.BackgroundRaised, stroke = color, radiusDp = 10f)
        RealityVisuals.reveal(status)
    }

    private fun field(value: String, secret: Boolean): EditText = EditText(this).apply {
        setText(value)
        setTextColor(primaryText)
        setHintTextColor(muted)
        textSize = 13f
        setSingleLine(true)
        typeface = Typeface.MONOSPACE
        setPadding(dp(12), 0, dp(12), 0)
        background = RealityVisuals.panel(this@SupabaseSetupActivity, fill = RealityVisuals.Colors.BackgroundRaised, stroke = RealityVisuals.Colors.Border, radiusDp = 10f)
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSelection(length())
    }

    private fun label(value: String): TextView = TextView(this).apply {
        text = value
        RealityVisuals.styleMicroLabel(this, cyan)
        setPadding(dp(3), dp(8), 0, dp(5))
    }

    private fun actionButton(label: String, accent: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(accent)
        textSize = 11f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        background = RealityVisuals.panel(this@SupabaseSetupActivity, fill = panel, stroke = accent, radiusDp = 12f)
        stateListAnimator = null
        setOnClickListener { RealityVisuals.pulseOnce(this); action() }
    }

    private fun fieldLayout() = LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(7)) }
    private fun buttonLayout() = LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
