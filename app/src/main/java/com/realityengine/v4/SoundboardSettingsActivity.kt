package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SoundboardSettingsActivity : Activity() {
    private lateinit var store: SoundboardStore
    private lateinit var list: LinearLayout
    private var preview: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SoundboardStore(this)
        buildUi()
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(18))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }
        root.addView(TextView(this).apply {
            text = "CALL SOUNDBOARD"
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 24f)
        })
        root.addView(TextView(this).apply {
            text = "Import audio from Downloads or another document provider. Tap a sound to preview, rename, or remove it."
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 12f)
            setPadding(0, dp(6), 0, dp(10))
        })
        root.addView(Button(this).apply {
            text = "Import audio"
            RealityVisuals.styleControl(this, R.drawable.ic_re_record, RealityVisuals.Colors.Cyan, radiusDp = 14f)
            setOnClickListener { chooseAudio() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(10)) })

        root.addView(TextView(this).apply {
            text = "Native cellular calls do not expose direct uplink audio injection to third-party dialers. Reality Engine uses the strongest local call playback route available; caller audibility can vary by device/audio route."
            setTextColor(RealityVisuals.Colors.Amber)
            RealityTypography.display(this, 11f)
            background = RealityVisuals.panel(this@SoundboardSettingsActivity, fill = RealityVisuals.Colors.Panel, stroke = RealityVisuals.Colors.Amber, radiusDp = 10f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun chooseAudio() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, REQ_AUDIO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_AUDIO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = displayName(uri).substringBeforeLast('.', displayName(uri))
        store.add(uri, name)
        render()
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val entries = store.all()
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "NO SOUNDS YET"
                gravity = Gravity.CENTER
                setTextColor(RealityVisuals.Colors.TextDim)
                RealityTypography.displayMedium(this, 13f)
                setPadding(0, dp(30), 0, dp(30))
            })
            return
        }
        entries.forEach { entry ->
            list.addView(Button(this).apply {
                text = entry.name
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(RealityVisuals.Colors.Text)
                background = RealityVisuals.panel(this@SoundboardSettingsActivity, fill = RealityVisuals.Colors.Panel, stroke = RealityVisuals.Colors.Border, radiusDp = 12f)
                stateListAnimator = null
                setOnClickListener { actions(entry) }
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun actions(entry: SoundboardStore.Entry) {
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(arrayOf("Preview", "Rename", "Remove")) { _, which ->
                when (which) {
                    0 -> preview(entry)
                    1 -> rename(entry)
                    2 -> remove(entry)
                }
            }
            .show()
    }

    private fun preview(entry: SoundboardStore.Entry) {
        stopPreview()
        runCatching {
            preview = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@SoundboardSettingsActivity, entry.uri)
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
        }.onFailure {
            stopPreview()
            Toast.makeText(this, "Could not open that audio file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreview() {
        val player = preview ?: return
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.release() }
        preview = null
    }

    private fun rename(entry: SoundboardStore.Entry) {
        val input = EditText(this).apply {
            setText(entry.name)
            setSelection(length())
        }
        AlertDialog.Builder(this)
            .setTitle("Rename sound")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                if (!store.rename(entry.id, input.text.toString())) Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
                render()
            }
            .show()
    }

    private fun remove(entry: SoundboardStore.Entry) {
        AlertDialog.Builder(this)
            .setTitle("Remove ${entry.name}?")
            .setMessage("This removes it from Reality Engine. The original audio file in Downloads is not deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> store.remove(entry.id); render() }
            .show()
    }

    private fun displayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0).orEmpty().ifBlank { "Sound" } else "Sound"
        } catch (_: Throwable) {
            "Sound"
        } finally {
            cursor?.close()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_AUDIO = 7101
    }
}
