package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Dedicated per-contact browser for private user-approved call recordings. */
class SavedRecordingsActivity : Activity() {
    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
    }

    private var phone = ""
    private var displayName = ""
    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
        displayName = intent.getStringExtra(EXTRA_NAME).orEmpty().trim()
        if (phone.isBlank()) {
            finish()
            return
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (phone.isNotBlank()) render()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun render() {
        val recordings = CallRecordingStore.savedFor(this, phone)
        val match = ContactMediaStore.findByNumber(this, phone)
        val label = displayName.ifBlank { match?.name.orEmpty().ifBlank { phone } }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }

        root.addView(TextView(this).apply {
            text = "SAVED AUDIO"
            gravity = Gravity.CENTER
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
        })
        root.addView(TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 25f)
            setPadding(0, dp(8), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "$phone  ·  ${recordings.size} recording${if (recordings.size == 1) "" else "s"}"
            gravity = Gravity.CENTER
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 11f)
            setPadding(0, 0, 0, dp(14))
        })

        if (recordings.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No saved call audio was found for this contact.\n\nRecordings are kept privately on this device and appear here after you choose Save at the end of a recorded call."
                gravity = Gravity.CENTER
                setTextColor(RealityVisuals.Colors.TextDim)
                background = RealityVisuals.panel(
                    this@SavedRecordingsActivity,
                    RealityVisuals.Colors.Panel,
                    RealityVisuals.Colors.Border,
                    20f,
                )
                setPadding(dp(18), dp(24), dp(18), dp(24))
                RealityTypography.display(this, 13f)
            })
        } else {
            recordings.forEachIndexed { index, recording ->
                root.addView(recordingCard(recording, index + 1), LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(4), 0, dp(5))
                })
            }
        }

        root.addView(Button(this).apply {
            text = "Done"
            RealityVisuals.styleControl(this, 0, RealityVisuals.Colors.CyanSoft, radiusDp = 20f)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, dp(14), 0, 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(RealityVisuals.Colors.Background)
            addView(root)
        })
    }

    private fun recordingCard(recording: CallRecordingStore.SavedRecording, number: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RealityVisuals.panel(
                this@SavedRecordingsActivity,
                RealityVisuals.Colors.PanelStrong,
                if (playingPath == recording.file.absolutePath) RealityVisuals.Colors.Green else RealityVisuals.Colors.Border,
                20f,
            )
            setPadding(dp(14), dp(11), dp(14), dp(12))
        }
        val mode = if (recording.channels == 2) "STEREO · CALLER + YOU" else "MONO · MIXED"
        card.addView(TextView(this).apply {
            text = "RECORDING $number  ·  ${formatDate(recording.timestampMs)}"
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
        })
        card.addView(TextView(this).apply {
            text = "${formatDuration(recording.durationSeconds)}  ·  $mode  ·  WAV"
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.display(this, 13f)
            setPadding(0, dp(5), 0, dp(9))
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = if (playingPath == recording.file.absolutePath && player?.isPlaying == true) "Stop" else "Play"
            RealityVisuals.styleControl(this, R.drawable.ic_re_record, RealityVisuals.Colors.Green, radiusDp = 18f)
            setOnClickListener {
                if (playingPath == recording.file.absolutePath && player?.isPlaying == true) stopPlayback() else play(recording)
                render()
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(3), 0) })
        actions.addView(Button(this).apply {
            text = "Delete"
            RealityVisuals.styleControl(this, R.drawable.ic_re_call_end, RealityVisuals.Colors.Magenta, destructive = true, radiusDp = 18f)
            setOnClickListener { confirmDelete(recording) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(3), 0, 0, 0) })
        card.addView(actions)
        return card
    }

    private fun play(recording: CallRecordingStore.SavedRecording) {
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(recording.file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (player === it) {
                        player = null
                        playingPath = null
                        runOnUiThread { render() }
                    }
                }
                prepare()
                start()
            }
            playingPath = recording.file.absolutePath
        } catch (_: Throwable) {
            stopPlayback()
            Toast.makeText(this, "Could not play this recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        val current = player
        if (current != null) {
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.release() }
        }
        player = null
        playingPath = null
    }

    private fun confirmDelete(recording: CallRecordingStore.SavedRecording) {
        AlertDialog.Builder(this)
            .setTitle("Permanently delete recording?")
            .setMessage("This removes the private WAV file from this device. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (playingPath == recording.file.absolutePath) stopPlayback()
                val deleted = CallRecordingStore.deleteSaved(this, phone, recording.file.name)
                Toast.makeText(this, if (deleted) "Recording deleted" else "Could not delete recording", Toast.LENGTH_SHORT).show()
                render()
            }
            .show()
    }

    private fun formatDate(timestampMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))

    private fun formatDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
