package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class TranscriptLibraryActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(18))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }
        root.addView(TextView(this).apply {
            text = "SAVED TRANSCRIPTS"
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 24f)
        })
        root.addView(TextView(this).apply {
            text = "Completed call transcripts are saved privately on this device."
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 12f)
            setPadding(0, dp(6), 0, dp(12))
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val transcripts = CallTranscriptStore.savedAll(this)
        if (transcripts.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "NO SAVED TRANSCRIPTS YET"
                gravity = Gravity.CENTER
                setTextColor(RealityVisuals.Colors.TextDim)
                RealityTypography.displayMedium(this, 13f)
                setPadding(0, dp(32), 0, dp(32))
            })
            return
        }
        transcripts.forEach { saved ->
            val title = ContactMediaStore.findByNumber(this, saved.phoneNumber)?.name?.takeIf { it.isNotBlank() }
                ?: saved.phoneNumber
            list.addView(Button(this).apply {
                text = "$title\n${format(saved.timestampMs)} · ${saved.turnCount} turns"
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(RealityVisuals.Colors.Text)
                background = RealityVisuals.panel(this@TranscriptLibraryActivity, fill = RealityVisuals.Colors.Panel, stroke = RealityVisuals.Colors.Border, radiusDp = 12f)
                stateListAnimator = null
                setOnClickListener { showTranscript(saved, title) }
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { setMargins(0, dp(3), 0, dp(3)) })
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
                    .setPositiveButton("Delete") { _, _ -> CallTranscriptStore.delete(saved); render() }
                    .show()
            }
            .show()
    }

    private fun format(timestampMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
