package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Forces an explicit Save or Permanently Delete decision for each completed recording. */
class PostCallReviewActivity : Activity() {
    private var pending: CallRecordingStore.PendingRecording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending = CallRecordingState.peek()
        if (pending == null) {
            finish()
            return
        }
        buildUi()
    }

    private fun buildUi() {
        val recording = pending ?: return
        val match = ContactMediaStore.findByNumber(this, recording.phoneNumber)
        val displayName = match?.name?.takeIf { it.isNotBlank() }
            ?: recording.displayName.takeIf { it.isNotBlank() }
            ?: recording.phoneNumber.takeIf { it.isNotBlank() }
            ?: "Unknown caller"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22.dp(), 30.dp(), 22.dp(), 24.dp())
            setBackgroundColor(RealityVisuals.Colors.Background)
        }

        root.addView(TextView(this).apply {
            text = "CALL RECORDING REVIEW"
            gravity = Gravity.CENTER
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Magenta)
        }, LinearLayout.LayoutParams(-1, 34.dp()))

        root.addView(ContactAvatarView(this).apply {
            bind(match?.contactId ?: -1L, displayName, RealityVisuals.Colors.Cyan)
        }, LinearLayout.LayoutParams(118.dp(), 118.dp()).apply { setMargins(0, 14.dp(), 0, 12.dp()) })

        root.addView(TextView(this).apply {
            text = displayName
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 23f)
        })
        root.addView(TextView(this).apply {
            text = recording.phoneNumber.ifBlank { "Unknown / withheld number" }
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.technical(this, 10f)
        })

        val format = if (recording.channels == 2) {
            "STEREO · CALLER + YOU"
        } else {
            "MONO · MIXED CALL AUDIO"
        }
        root.addView(TextView(this).apply {
            text = "${format}\n${formatDuration(recording.durationSeconds)} · ${recording.sampleRate / 1000} kHz · WAV"
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.Cyan)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            background = RealityVisuals.panel(
                this@PostCallReviewActivity,
                fill = RealityVisuals.Colors.PanelStrong,
                stroke = RealityVisuals.Colors.Cyan,
                radiusDp = 12f,
            )
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 18.dp(), 0, 14.dp()) })

        root.addView(TextView(this).apply {
            text = "This temporary recording is private on this device. Save it under this caller, or permanently delete it. Keep recordings only where recording is permitted."
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 12f)
            setPadding(10.dp(), 4.dp(), 10.dp(), 18.dp())
        })

        val save = Button(this).apply {
            text = if (match != null) "Save under ${displayName.take(24)}" else "Save under this number"
            RealityVisuals.styleControl(
                this,
                R.drawable.ic_re_record,
                accent = RealityVisuals.Colors.Green,
                radiusDp = 14f,
            )
            setOnClickListener { saveRecording() }
        }
        root.addView(save, LinearLayout.LayoutParams(-1, 54.dp()).apply { setMargins(0, 4.dp(), 0, 5.dp()) })

        val delete = Button(this).apply {
            text = "Permanently delete"
            RealityVisuals.styleControl(
                this,
                R.drawable.ic_re_call_end,
                accent = RealityVisuals.Colors.Magenta,
                destructive = true,
                radiusDp = 14f,
            )
            setOnClickListener { confirmDelete() }
        }
        root.addView(delete, LinearLayout.LayoutParams(-1, 54.dp()).apply { setMargins(0, 5.dp(), 0, 0) })

        setContentView(root)
    }

    private fun saveRecording() {
        val recording = pending ?: return
        val saved = CallRecordingStore.savePending(this, recording)
        if (saved == null) {
            Toast.makeText(this, "Could not save recording", Toast.LENGTH_SHORT).show()
            return
        }
        CallRecordingState.clear(recording)
        pending = null
        Toast.makeText(this, "Recording saved privately", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val recording = pending ?: return
        AlertDialog.Builder(this)
            .setTitle("Permanently delete recording?")
            .setMessage("This cannot be undone.")
            .setNegativeButton("Keep reviewing", null)
            .setPositiveButton("Delete") { _, _ ->
                CallRecordingStore.deletePending(recording)
                CallRecordingState.clear(recording)
                pending = null
                Toast.makeText(this, "Recording permanently deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    override fun onBackPressed() {
        if (pending == null) {
            super.onBackPressed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Recording still needs a decision")
            .setMessage("Choose Save or Permanently Delete before leaving this review.")
            .setPositiveButton("Continue reviewing", null)
            .show()
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainder = seconds % 60
        return "%d:%02d".format(minutes, remainder)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
