from pathlib import Path


def patch(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"missing patch anchor in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


call = "app/src/main/java/com/realityengine/v4/CallActivity.kt"
patch(
    call,
    "    private lateinit var flirtButton: Button\n    private lateinit var recordButton: Button",
    "    private lateinit var flirtButton: Button\n    private lateinit var soundboardButton: Button\n    private lateinit var recordButton: Button",
)
patch(
    call,
    "    private lateinit var factualBar: ProgressBar\n\n    private val handler",
    "    private lateinit var factualBar: ProgressBar\n    private lateinit var soundboardStore: SoundboardStore\n    private lateinit var soundboardPlayer: CallSoundboardPlayer\n\n    private val handler",
)
patch(
    call,
    "        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)\n        createdAtElapsed = SystemClock.elapsedRealtime()",
    "        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)\n        soundboardStore = SoundboardStore(this)\n        soundboardPlayer = CallSoundboardPlayer(this)\n        createdAtElapsed = SystemClock.elapsedRealtime()",
)
patch(
    call,
    "    override fun onDestroy() {\n        super.onDestroy()\n    }",
    "    override fun onDestroy() {\n        if (::soundboardPlayer.isInitialized) soundboardPlayer.stop()\n        super.onDestroy()\n    }",
)
patch(
    call,
    '''        val quickActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        unhingedButton = control("Unhinged", R.drawable.ic_re_star, magenta) { requestQuickCoach(CoachQuickModeCatalog.UNHINGED) }
        flirtButton = control("Flirt", R.drawable.ic_re_star, cyan) { requestQuickCoach(CoachQuickModeCatalog.FLIRT) }
        quickActions.addView(unhingedButton, buttonLayout(48)); quickActions.addView(flirtButton, buttonLayout(48)); root.addView(quickActions)''',
    '''        val quickActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        unhingedButton = control("Unhinged", R.drawable.ic_re_star, magenta) { requestQuickCoach(CoachQuickModeCatalog.UNHINGED) }
        flirtButton = control("Flirt", R.drawable.ic_re_star, cyan) { requestQuickCoach(CoachQuickModeCatalog.FLIRT) }
        soundboardButton = control("Sounds", R.drawable.ic_re_speaker, green) { showSoundboard() }
        quickActions.addView(unhingedButton, buttonLayout(48)); quickActions.addView(flirtButton, buttonLayout(48)); quickActions.addView(soundboardButton, buttonLayout(48)); root.addView(quickActions)''',
)
patch(
    call,
    "        unhingedButton.isEnabled = interactive; flirtButton.isEnabled = interactive\n        unhingedButton.alpha = if (interactive) 1f else .42f; flirtButton.alpha = if (interactive) 1f else .42f",
    "        unhingedButton.isEnabled = interactive; flirtButton.isEnabled = interactive; soundboardButton.isEnabled = current.state == Call.STATE_ACTIVE\n        unhingedButton.alpha = if (interactive) 1f else .42f; flirtButton.alpha = if (interactive) 1f else .42f; soundboardButton.alpha = if (soundboardButton.isEnabled) 1f else .42f",
)
patch(
    call,
    "    private fun requestRecording() {",
    '''    private fun showSoundboard() {
        val current = call
        if (current?.state != Call.STATE_ACTIVE) {
            Toast.makeText(this, "Soundboard is available during an active call", Toast.LENGTH_SHORT).show()
            return
        }
        val entries = soundboardStore.all()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Soundboard is empty")
                .setMessage("Add audio in Settings → Call soundboard.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open soundboard settings") { _, _ ->
                    startActivity(Intent(this, SoundboardSettingsActivity::class.java))
                }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (soundboardPlayer.isPlaying()) "Soundboard · playing" else "Soundboard")
            .setItems(entries.map { it.name }.toTypedArray()) { _, which ->
                val entry = entries[which]
                if (RealityInCallService.instance?.isMutedNow() == true) {
                    Toast.makeText(this, "Unmute first if you want the caller to hear speaker-coupled playback", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                if (soundboardPlayer.play(entry) {
                        runOnUiThread {
                            soundboardButton.text = "Sounds"
                            refreshAudioButtons()
                        }
                    }) {
                    soundboardButton.text = "■ ${entry.name.take(8)}"
                    RealityVisuals.pulseOnce(soundboardButton)
                    refreshAudioButtons()
                } else {
                    Toast.makeText(this, "Could not play ${entry.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(if (soundboardPlayer.isPlaying()) "Stop" else "Manage") { _, _ ->
                if (soundboardPlayer.isPlaying()) {
                    soundboardPlayer.stop()
                    soundboardButton.text = "Sounds"
                    refreshAudioButtons()
                } else {
                    startActivity(Intent(this, SoundboardSettingsActivity::class.java))
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun requestRecording() {''',
)

settings = "app/src/main/java/com/realityengine/v4/SettingsDashboardScreen.kt"
anchor = '''        root.addView(row(
            "Analysis frequency",
            "Run coaching every ${store.analysisFrequencyTurns} ${if (store.analysisFrequencyTurns == 1) "turn" else "turns"}",
            "${store.analysisFrequencyTurns}×",
            cyan,
        ) { chooseAnalysisFrequency() })'''
addition = anchor + '''
        val soundCount = SoundboardStore(activity).count()
        root.addView(row(
            "Call soundboard",
            "Import audio from Downloads, preview it, rename sounds, and manage the in-call library",
            if (soundCount == 0) "SETUP" else "$soundCount",
            if (soundCount == 0) amber else green,
        ) { activity.startActivity(Intent(activity, SoundboardSettingsActivity::class.java)) })'''
patch(settings, anchor, addition)

workflow = Path(".github/workflows/android.yml")
text = workflow.read_text()
start = "      # SOUND_BOARD_BOOTSTRAP_START\n"
end = "      # SOUND_BOARD_BOOTSTRAP_END\n"
if start in text and end in text:
    before, rest = text.split(start, 1)
    _, after = rest.split(end, 1)
    workflow.write_text(before + after)

Path(".github/workflows/restore-soundboard-once.yml").unlink(missing_ok=True)
Path(".github/scripts/restore_soundboard.py").unlink(missing_ok=True)
