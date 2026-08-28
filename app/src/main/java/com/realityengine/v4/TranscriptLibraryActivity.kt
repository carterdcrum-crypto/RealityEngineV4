package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class TranscriptLibraryActivity : Activity() {
    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
    }

    private lateinit var list: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private val phoneFilter: String by lazy { intent.getStringExtra(EXTRA_PHONE).orEmpty().trim() }
    private val nameFilter: String by lazy { intent.getStringExtra(EXTRA_NAME).orEmpty().trim() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(18))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }
        titleView = TextView(this).apply {
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 24f)
        }
        root.addView(titleView)
        subtitleView = TextView(this).apply {
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 12f)
            setPadding(0, dp(6), 0, dp(12))
        }
        root.addView(subtitleView)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val scoped = phoneFilter.isNotBlank()
        val displayName = if (scoped) {
            nameFilter.ifBlank {
                ContactMediaStore.findByNumber(this, phoneFilter)?.name?.takeIf { it.isNotBlank() }
                    ?: phoneFilter
            }
        } else ""

        titleView.text = if (scoped) "CALL TRANSCRIPTS" else "SAVED TRANSCRIPTS"
        subtitleView.text = if (scoped) {
            "$displayName · full completed-call transcript history stored privately on this device."
        } else {
            "Completed call transcripts are saved privately on this device."
        }

        val transcripts = if (scoped) CallTranscriptStore.savedFor(this, phoneFilter) else CallTranscriptStore.savedAll(this)
        if (transcripts.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (scoped) "NO SAVED TRANSCRIPTS FOR THIS CALLER YET" else "NO SAVED TRANSCRIPTS YET"
                gravity = Gravity.CENTER
                setTextColor(RealityVisuals.Colors.TextDim)
                RealityTypography.displayMedium(this, 13f)
                setPadding(0, dp(32), 0, dp(32))
            })
            return
        }
        transcripts.forEach { saved ->
            val title = if (scoped) displayName else {
                ContactMediaStore.findByNumber(this, saved.phoneNumber)?.name?.takeIf { it.isNotBlank() }
                    ?: saved.phoneNumber
            }
            list.addView(Button(this).apply {
                text = if (scoped) {
                    "${format(saved.timestampMs)}\n${saved.turnCount} turns · FULL TRANSCRIPT"
                } else {
                    "$title\n${format(saved.timestampMs)} · ${saved.turnCount} turns"
                }
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(RealityVisuals.Colors.Text)
                background = RealityVisuals.panel(
                    this@TranscriptLibraryActivity,
                    fill = RealityVisuals.Colors.Panel,
                    stroke = RealityVisuals.Colors.Border,
                    radiusDp = 16f,
                )
                stateListAnimator = null
                setOnClickListener { showTranscript(saved, title) }
            }, LinearLayout.LayoutParams(-1, dp(74)).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun showTranscript(saved: CallTranscriptStore.SavedTranscript, title: String) {
        AlertDialog.Builder(this)
            .setTitle("$title · ${format(saved.timestampMs)}")
            .setMessage(saved.text)
            .setNegativeButton("Close", null)
            .setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete transcript?")
                    .setMessage("This permanently removes this saved transcript from Reality Engine private storage.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        CallTranscriptStore.delete(saved)
                        render()
                    }
                    .show()
            }
            .show()
    }

    private fun format(timestampMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
