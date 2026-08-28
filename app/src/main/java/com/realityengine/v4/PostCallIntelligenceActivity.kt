package com.realityengine.v4

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Post-call timeline and memory handoff. Signal events are conversation cues, not deception verdicts. */
class PostCallIntelligenceActivity : Activity() {
    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        if (phone.isBlank()) { finish(); return }
        val fallback = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val profile = CallerProfileStore(this).load(phone)
        val match = ContactMediaStore.findByNumber(this, phone)
        val name = profile.displayName.ifBlank { match?.name.orEmpty().ifBlank { fallback.ifBlank { phone } } }
        val transcript = CallTranscriptStore.savedFor(this, phone).firstOrNull()
        val callStartedAtMs = transcript?.timestampMs
        val callEvents = if (callStartedAtMs != null && callStartedAtMs > 0L) {
            profile.evidenceEvents.filter { it.timestampMs >= callStartedAtMs - 3_000L }.takeLast(12)
        } else {
            emptyList()
        }
        val bookmarks = CallBookmarkStore(this).list(phone)
            .filter { callStartedAtMs == null || it.timestampMs >= callStartedAtMs - 3_000L }
            .takeLast(6)
        val proposal = MemoryProposalStore(this).load(phone)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }
        root.addView(TextView(this).apply {
            text = "POST-CALL INTELLIGENCE"
            gravity = Gravity.CENTER
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Magenta)
        })
        root.addView(TextView(this).apply {
            text = name
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 24f)
            setPadding(0, dp(6), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = phone
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.technical(this, 10f)
        })

        if (profile.lastCallSummary.isNotBlank()) {
            root.addView(section("SUMMARY"))
            root.addView(card(profile.lastCallSummary, RealityVisuals.Colors.Cyan))
        }

        root.addView(section("SIGNAL TIMELINE"))
        if (callEvents.isEmpty()) {
            root.addView(card("No elevated signal moments were saved for this completed call.", RealityVisuals.Colors.Border))
        } else {
            val base = callStartedAtMs ?: callEvents.first().timestampMs
            root.addView(SignalTimelineGraphView(this).apply {
                setData(callEvents, base)
                background = RealityVisuals.panel(
                    this@PostCallIntelligenceActivity,
                    RealityVisuals.Colors.Panel,
                    RealityVisuals.Colors.Border,
                    12f,
                )
            }, LinearLayout.LayoutParams(-1, dp(230)).apply { setMargins(0, dp(3), 0, dp(5)) })
            root.addView(TextView(this).apply {
                text = "GRAPH · persisted meaningful samples only · white = fused signal"
                setTextColor(RealityVisuals.Colors.TextDim)
                RealityTypography.technical(this, 9f)
                setPadding(dp(4), dp(2), dp(4), dp(5))
            })

            callEvents.forEach { event ->
                val seconds = ((event.timestampMs - base).coerceAtLeast(0L) / 1000L)
                val marker = "%d:%02d".format(seconds / 60, seconds % 60)
                val text = buildString {
                    append(marker).append(" · FUSED ").append((event.combined * 100).toInt()).append("%")
                    append("\nA ").append((event.acoustic * 100).toInt()).append("  L ").append((event.linguistic * 100).toInt()).append("  F ").append((event.factual * 100).toInt())
                    if (event.context.isNotBlank()) append("\n").append(event.context.take(180))
                }
                root.addView(card(text, if (event.combined >= .70f) RealityVisuals.Colors.Magenta else RealityVisuals.Colors.Cyan))
            }
        }
        root.addView(TextView(this).apply {
            text = "Signal values are uncertain conversation cues and consistency checks—not proof of deception."
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 10.5f)
            setPadding(dp(4), dp(5), dp(4), dp(8))
        })

        if (bookmarks.isNotEmpty()) {
            root.addView(section("BOOKMARKS"))
            bookmarks.forEach { root.addView(card("${if (it.isCaller == false) "YOU" else "THEM"} · ${it.text}", RealityVisuals.Colors.Green)) }
        }

        transcript?.let {
            root.addView(section("TRANSCRIPT"))
            root.addView(card("${it.turnCount} saved turns · This timeline is scoped to this transcript only.", RealityVisuals.Colors.Cyan))
        }

        if (proposal != null) {
            val count = MemoryProposalStore(this).itemCount(proposal)
            root.addView(section("NEW MEMORY"))
            root.addView(card("AI proposed $count memory item${if (count == 1) "" else "s"}. Review them before they become permanent.", RealityVisuals.Colors.Green))
        }

        root.addView(Button(this).apply {
            text = if (proposal != null) "REVIEW CALLER MEMORY · ${MemoryProposalStore(this@PostCallIntelligenceActivity).itemCount(proposal)} NEW" else "OPEN CALLER MEMORY"
            setTextColor(RealityVisuals.Colors.Green)
            background = RealityVisuals.panel(this@PostCallIntelligenceActivity, RealityVisuals.Colors.Panel, RealityVisuals.Colors.Green, 12f)
            RealityTypography.displayMedium(this, 11f)
            setOnClickListener {
                startActivity(Intent(this@PostCallIntelligenceActivity, CallerMemoryActivity::class.java).apply {
                    putExtra(CallerMemoryActivity.EXTRA_PHONE, phone)
                    putExtra(CallerMemoryActivity.EXTRA_NAME, name)
                })
            }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(16), 0, dp(5)) })
        root.addView(Button(this).apply {
            text = "DONE"
            setTextColor(RealityVisuals.Colors.Cyan)
            background = RealityVisuals.panel(this@PostCallIntelligenceActivity, RealityVisuals.Colors.Panel, RealityVisuals.Colors.Cyan, 12f)
            RealityTypography.displayMedium(this, 11f)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(label: String) = TextView(this).apply {
        text = label
        RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Magenta)
        setPadding(dp(3), dp(15), 0, dp(5))
    }

    private fun card(textValue: String, stroke: Int) = TextView(this).apply {
        text = textValue
        setTextColor(RealityVisuals.Colors.Text)
        textSize = 11.5f
        typeface = Typeface.MONOSPACE
        setLineSpacing(2f, 1.05f)
        setPadding(dp(11), dp(9), dp(11), dp(9))
        background = RealityVisuals.panel(this@PostCallIntelligenceActivity, RealityVisuals.Colors.Panel, stroke, 10f)
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(3), 0, dp(3)) } }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
