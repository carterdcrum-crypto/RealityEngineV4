package com.realityengine.v4

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** Search + local diagnostics + provider performance dashboard. */
class IntelligenceHubScreen(private val activity: Activity) {
    private val settings = SettingsStore(activity)
    private val search = ConversationSearchIndex(activity)
    private val performance = CoachProviderPerformanceStore(activity)
    private val usage = RuntimeUsageStore(activity)
    private val thermal = ThermalGuard(activity)
    private val cyan = RealityVisuals.Colors.Cyan
    private val green = RealityVisuals.Colors.Green
    private val magenta = RealityVisuals.Colors.Magenta
    private val muted = RealityVisuals.Colors.TextDim
    private val panel = RealityVisuals.Colors.Panel

    fun build(): LinearLayout {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(24))
        }
        root.addView(TextView(activity).apply {
            text = "INTELLIGENCE"
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 24f)
        })
        root.addView(TextView(activity).apply {
            text = "Search your saved call history and inspect the systems powering live coaching. All search happens on-device."
            setTextColor(muted)
            RealityTypography.display(this, 11.5f)
            setPadding(0, dp(3), 0, dp(10))
        })

        val query = EditText(activity).apply {
            hint = "Search calls, memory, bookmarks…"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(RealityVisuals.Colors.Text)
            setHintTextColor(muted)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            background = RealityVisuals.panel(activity, RealityVisuals.Colors.BackgroundRaised, cyan, 12f)
            setPadding(dp(12), 0, dp(12), 0)
        }
        root.addView(query, LinearLayout.LayoutParams(-1, dp(48)))
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val runSearch = {
            renderResults(results, search.search(query.text.toString()))
            Unit
        }
        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }
        root.addView(Button(activity).apply {
            text = "SEARCH LOCAL CONVERSATIONS"
            setTextColor(cyan)
            background = RealityVisuals.panel(activity, panel, cyan, 12f)
            RealityTypography.displayMedium(this, 11f)
            setOnClickListener { runSearch() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(5), 0, dp(5)) })
        root.addView(results)

        root.addView(section("RUNTIME HEALTH"))
        val health = CallSessionHealthState.snapshot()
        root.addView(card(
            health.compact(),
            CallSessionHealthState.diagnosticText(),
            when {
                health.lastError.isNotBlank() -> magenta
                health.audio == CallSessionHealthState.Level.GOOD && health.stt == CallSessionHealthState.Level.GOOD -> green
                else -> cyan
            }
        ))

        root.addView(section("PHONE LOAD"))
        val heat = thermal.snapshot()
        root.addView(card(
            "THERMAL · ${heat.label}",
            if (heat.throttle) "Phone will reduce optional coach refreshes until the device cools." else "Full live-analysis cadence is available.",
            if (heat.throttle) magenta else green,
        ))

        root.addView(section("USAGE"))
        val totals = usage.summary()
        root.addView(card(
            "${totals.calls} CALLS · ${duration(totals.callMs)}",
            "Deepgram ${duration(totals.deepgramMs)} · Coach ${totals.coachRequests} requests · ${compact(totals.inputTokens + totals.outputTokens)} AI tokens",
            cyan,
        ))

        root.addView(section("COACH PROVIDERS"))
        SettingsStore.COACH_FALLBACK_ORDER.forEach { provider ->
            val stats = performance.stats(provider)
            val successRate = if (stats.attempts == 0) "—" else "${(stats.successes * 100 / stats.attempts.coerceAtLeast(1))}%"
            val cooldown = (stats.cooldownUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val detail = buildString {
                append("Success ").append(successRate)
                append(" · ").append(stats.attempts).append(" attempts")
                append(" · avg ").append(if (stats.emaLatencyMs > 0) "${stats.emaLatencyMs}ms" else "—")
                if (cooldown > 0L) append(" · cooldown ").append((cooldown / 1000L).coerceAtLeast(1L)).append("s")
            }
            root.addView(card(
                provider,
                detail,
                if (settings.providerConfigured(provider)) green else muted,
            ))
        }
        return root
    }

    private fun renderResults(host: LinearLayout, items: List<ConversationSearchIndex.Result>) {
        host.removeAllViews()
        if (items.isEmpty()) {
            host.addView(TextView(activity).apply {
                text = "No matching saved conversation data."
                setTextColor(muted)
                RealityTypography.display(this, 11.5f)
                setPadding(dp(8), dp(9), dp(8), dp(9))
            })
            return
        }
        items.take(20).forEach { item ->
            host.addView(Button(activity).apply {
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                text = buildString {
                    append(item.displayName).append(" · ").append(item.source)
                    if (item.timestampMs > 0L) append(" · ").append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestampMs)))
                    append("\n").append(item.snippet)
                }
                setTextColor(RealityVisuals.Colors.Text)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                background = RealityVisuals.panel(activity, panel, RealityVisuals.Colors.Border, 10f)
                setPadding(dp(11), dp(7), dp(11), dp(7))
                setOnClickListener {
                    activity.startActivity(Intent(activity, CallerMemoryActivity::class.java).apply {
                        putExtra(CallerMemoryActivity.EXTRA_PHONE, item.phoneNumber)
                        putExtra(CallerMemoryActivity.EXTRA_NAME, item.displayName)
                    })
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun section(text: String) = TextView(activity).apply {
        this.text = text
        RealityVisuals.styleMicroLabel(this, magenta)
        setPadding(dp(3), dp(16), 0, dp(5))
    }

    private fun card(title: String, detail: String, stroke: Int) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = RealityVisuals.panel(activity, panel, stroke, 10f)
        setPadding(dp(11), dp(8), dp(11), dp(8))
        addView(TextView(activity).apply {
            text = title
            setTextColor(stroke)
            RealityTypography.displayMedium(this, 12.5f)
        })
        addView(TextView(activity).apply {
            text = detail
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.display(this, 10.5f)
            setPadding(0, dp(3), 0, 0)
        })
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(3), 0, dp(3)) } }

    private fun duration(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun compact(value: Long): String = when {
        value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
