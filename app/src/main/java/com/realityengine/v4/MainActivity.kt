package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var shizukuStatus: TextView
    private lateinit var audioStatus: TextView
    private lateinit var number: EditText
    private lateinit var contactMatch: TextView
    private lateinit var error: TextView
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout

    private lateinit var contactIndex: ContactIndex
    private lateinit var callHistory: CallHistoryIndex
    private lateinit var settingsStore: SettingsStore
    private lateinit var profileView: CallerProfileView
    private lateinit var profileStore: CallerProfileStore
    private lateinit var contactManager: ContactManager
    private lateinit var contactActions: ContactActionsDialog
    private lateinit var contactPanel: ContactManagementPanel
    private lateinit var contactFavorites: ContactFavoritesStore
    private lateinit var onboardingState: OnboardingState
    private lateinit var appUpdater: AppUpdater

    private var recordingPlayer: MediaPlayer? = null
    private var dialScreen: DialScreen? = null
    private var pendingNumber = ""
    private var screen = "DIAL"
    private var buttonShape = 1

    private val bg = Color.rgb(3, 7, 12)
    private val panel = Color.rgb(9, 18, 27)
    private val soft = Color.rgb(13, 27, 39)
    private val cyan = Color.rgb(40, 224, 255)
    private val green = Color.rgb(75, 255, 165)
    private val magenta = RealityVisuals.Colors.Magenta
    private val muted = Color.rgb(118, 147, 163)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactIndex = ContactIndex(this)
        callHistory = CallHistoryIndex(this, contactIndex)
        settingsStore = SettingsStore(this)
        profileView = CallerProfileView(this)
        profileStore = CallerProfileStore(this)
        contactManager = ContactManager(this)
        contactActions = ContactActionsDialog(this, contactManager)
        contactPanel = ContactManagementPanel(this, contactIndex, contactManager, contactActions)
        contactFavorites = ContactFavoritesStore(this)
        onboardingState = OnboardingState(this)
        appUpdater = AppUpdater(this, settingsStore)
        buttonShape = getPreferences(MODE_PRIVATE).getInt("buttonShape", 1)
        pendingNumber = savedInstanceState?.getString("pendingNumber").orEmpty()
        buildPhoneUi()
        if (savedInstanceState == null && onboardingState.shouldShowOnLaunch()) showWalkthrough()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        capturePendingNumber()
        outState.putString("pendingNumber", pendingNumber)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            updateRoleStatus()
            updateShizukuStatus()
            updateAudioStatus()
            if (screen == "SETTINGS" && ::content.isInitialized) showSettings()
            if (screen == "INTEL" && ::content.isInitialized) showIntel()
        }
    }

    override fun onDestroy() {
        stopRecordingPlayback()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (screen != "DIAL") showPhone() else super.onBackPressed()
    }

    private fun capturePendingNumber() {
        if (::number.isInitialized) pendingNumber = number.text.toString()
    }

    private fun radius() = when (buttonShape) {
        0 -> 3f
        2 -> 30f
        else -> 14f
    }

    private fun neon(fill: Int = panel, stroke: Int = cyan, r: Float = radius()) =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(1.dp(), stroke)
            cornerRadius = r.dpF()
        }

    private fun cyberButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(cyan)
        RealityTypography.technical(this, 12f)
        background = neon(soft, Color.rgb(24, 91, 111))
        setOnClickListener { click() }
        stateListAnimator = null
    }

    private fun destructiveButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(magenta)
        RealityTypography.technical(this, 11f)
        background = neon(Color.rgb(30, 8, 22), magenta)
        setOnClickListener { click() }
        stateListAnimator = null
    }

    private fun navItem(icon: String, label: String, target: String, click: () -> Unit): LinearLayout {
        val active = screen == target
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(3.dp(), 2.dp(), 3.dp(), 2.dp())
            addView(TextView(this@MainActivity).apply {
                text = icon
                gravity = Gravity.CENTER
                setTextColor(if (active) Color.rgb(0, 28, 34) else muted)
                RealityTypography.displayMedium(this, 18f)
                background = if (active) neon(cyan, cyan, 22f) else null
            }, LinearLayout.LayoutParams(54.dp(), 28.dp()))
            addView(TextView(this@MainActivity).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(if (active) cyan else muted)
                RealityTypography.technical(this, 9f)
            }, LinearLayout.LayoutParams(-1, 20.dp()))
            setOnClickListener {
                capturePendingNumber()
                click()
            }
        }
    }

    private fun refreshNav() {
        if (!::nav.isInitialized) return
        nav.removeAllViews()
        nav.addView(navItem("⌁", "Phone", "DIAL") { showPhone() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("◴", "Traffic", "TRAFFIC") { showRecents() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("◇", "Intel", "INTEL") { showIntel() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("▣", "Index", "INDEX") { showContacts() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
        nav.addView(navItem("⚙", "Settings", "SETTINGS") { showSettings() }, LinearLayout.LayoutParams(0, 54.dp(), 1f))
    }

    private fun buildPhoneUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 34.dp(), 18.dp(), 8.dp())
            setBackgroundColor(bg)
        }
        val utility = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        status = TextView(this).apply {
            setTextColor(green)
            gravity = Gravity.CENTER_VERTICAL
            RealityTypography.signal(this, 11f)
        }
        utility.addView(status, LinearLayout.LayoutParams(0, 42.dp(), 1f))
        root.addView(utility)
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(18, 75, 91)) }, LinearLayout.LayoutParams(-1, 1.dp()).apply { setMargins(0, 2.dp(), 0, 4.dp()) })
        shizukuStatus = TextView(this).apply { visibility = View.GONE }
        audioStatus = TextView(this).apply { visibility = View.GONE }
        root.addView(shizukuStatus)
        root.addView(audioStatus)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 4.dp(), 0, 0)
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, 58.dp()))
        setContentView(root)
        updateRoleStatus()
        updateShizukuStatus()
        updateAudioStatus()
        showPhone()
    }

    private fun showWalkthrough() {
        WalkthroughScreen(
            this,
            onboardingState,
            onExit = { buildPhoneUi() },
            onAction = { step -> handleWalkthroughAction(step) },
        ).show()
    }

    private fun handleWalkthroughAction(step: WalkthroughContent.Step) {
        when (WalkthroughActionResolver.resolve(step)) {
            WalkthroughAction.DEFAULT_PHONE -> requestDefaultPhoneRole()
            WalkthroughAction.PERMISSIONS -> requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
                ),
                REQ_AUDIO,
            )
            WalkthroughAction.SHIZUKU -> requestShizuku()
            WalkthroughAction.TRANSCRIPTION_SETTINGS, WalkthroughAction.COACH_SETTINGS -> {
                buildPhoneUi()
                showSettings()
            }
            WalkthroughAction.CALL_AUDIO -> checkCallAudio()
            WalkthroughAction.NONE -> Unit
        }
    }

    private fun showAbout() {
        AboutScreen(this) {
            buildPhoneUi()
            showSettings()
        }.show()
    }

    private fun showPhone() {
        stopRecordingPlayback()
        screen = "DIAL"
        content.removeAllViews()
        content.gravity = Gravity.BOTTOM
        val dial = DialScreen(this, contactIndex) { placeCall(it) }
        dialScreen = dial
        number = dial.number
        contactMatch = dial.contactMatch
        error = dial.error
        content.addView(dial.build(), LinearLayout.LayoutParams(-1, -1))
        if (pendingNumber.isNotEmpty()) dial.setNumber(pendingNumber)
        refreshNav()
    }

    private fun showRecents() {
        stopRecordingPlayback()
        screen = "TRAFFIC"
        content.gravity = Gravity.TOP
        content.removeAllViews()
        sectionTitle("Traffic")

        if (!callHistory.hasPermission()) {
            content.addView(cyberButton("Authorize call history") {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
                    REQ_CALL_LOG,
                )
            })
            refreshNav()
            return
        }

        val entries = callHistory.recent()
        if (!callHistory.hasWritePermission()) {
            content.addView(cyberButton("Authorize call deletion") {
                requestPermissions(arrayOf(Manifest.permission.WRITE_CALL_LOG), REQ_CALL_LOG)
            }, LinearLayout.LayoutParams(-1, 48.dp()).apply { setMargins(0, 0, 0, 6.dp()) })
        } else if (entries.isNotEmpty()) {
            content.addView(destructiveButton("Clear entire call history") { confirmClearCallHistory() }, LinearLayout.LayoutParams(-1, 48.dp()).apply { setMargins(0, 0, 0, 6.dp()) })
        }

        content.addView(TextView(this).apply {
            text = "Tap a call for its caller profile. Hold a call to delete that row or clear its Reality Engine memory."
            setTextColor(muted)
            RealityTypography.display(this, 11f)
            setPadding(8.dp(), 4.dp(), 8.dp(), 10.dp())
        })

        if (entries.isEmpty()) {
            emptyMessage("NO CALL TRAFFIC YET", "New incoming and outgoing calls will appear here.")
            refreshNav()
            return
        }

        entries.forEach { entry ->
            val summary = entry.realitySummary.takeIf { it.isNotBlank() }?.let { "\nRE // ${it.take(210)}" }.orEmpty()
            content.addView(listButton(
                "${entry.displayName}\n${entry.direction}  •  ${entry.number}  •  ${entry.durationSeconds}s$summary",
            ) { showCallerProfile(entry.number, entry.displayName) }.apply {
                minHeight = if (summary.isBlank()) 70.dp() else 112.dp()
                setOnLongClickListener {
                    showHistoryActions(entry)
                    true
                }
            })
        }
        refreshNav()
    }

    private fun showHistoryActions(entry: CallHistoryEntry) {
        val options = mutableListOf<String>()
        options += if (callHistory.hasWritePermission()) "Delete this call" else "Authorize call deletion"
        options += "Delete recent topics"
        options += "Delete last-call summary"
        options += "Delete call signal history"
        options += "Number / contact actions"
        AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> if (callHistory.hasWritePermission()) confirmDeleteCall(entry) else requestPermissions(arrayOf(Manifest.permission.WRITE_CALL_LOG), REQ_CALL_LOG)
                    1 -> {
                        profileStore.clearRecentTopics(entry.number)
                        Toast.makeText(this, "Recent topics deleted", Toast.LENGTH_SHORT).show()
                        showRecents()
                    }
                    2 -> {
                        profileStore.clearLastCall(entry.number)
                        Toast.makeText(this, "Last-call summary deleted", Toast.LENGTH_SHORT).show()
                        showRecents()
                    }
                    3 -> {
                        profileStore.clearCallEvidence(entry.number)
                        Toast.makeText(this, "Call signal history deleted", Toast.LENGTH_SHORT).show()
                        showRecents()
                    }
                    else -> contactPanel.unsavedNumberActions(entry.number) { showRecents() }
                }
            }
            .show()
    }

    private fun confirmDeleteCall(entry: CallHistoryEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete this call?")
            .setMessage("This removes this call-log row from the phone. Saved recordings and Reality Engine caller memory stay unless you delete them separately.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val deleted = callHistory.delete(entry)
                Toast.makeText(this, if (deleted) "Call deleted" else "Could not delete call", Toast.LENGTH_SHORT).show()
                showRecents()
            }
            .show()
    }

    private fun confirmClearCallHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear entire call history?")
            .setMessage("This removes Android call-log entries. Reality Engine caller memory and saved recordings stay unless you delete them separately.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear history") { _, _ ->
                val removed = callHistory.clearAll()
                Toast.makeText(this, "$removed call${if (removed == 1) "" else "s"} deleted", Toast.LENGTH_SHORT).show()
                showRecents()
            }
            .show()
    }

    private fun showCallerProfile(phone: String, name: String) {
        stopRecordingPlayback()
        screen = "PROFILE"
        content.gravity = Gravity.TOP
        content.removeAllViews()
        val p = profileView.load(phone, name)
        val match = ContactMediaStore.findByNumber(this, phone)
        val label = p.name.ifBlank { p.phoneNumber.ifBlank { "Unknown caller" } }

        content.addView(ContactAvatarView(this).apply {
            bind(match?.contactId ?: -1L, label, cyan)
        }, LinearLayout.LayoutParams(96.dp(), 96.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 16.dp(), 0, 0)
        })
        sectionTitle(label)
        profileLine("NUMBER", p.phoneNumber)
        profileLine("CONVERSATION STYLE", p.preferredStyle)
        profileLine("LIKES", p.likes.joinToString(" • "))
        profileLine("DISLIKES", p.dislikes.joinToString(" • "))
        profileLine("RECENT TOPICS", p.recentTopics.joinToString(" • "))
        if (p.recentTopics.isNotEmpty()) {
            content.addView(destructiveButton("Delete recent topics") {
                profileStore.clearRecentTopics(phone)
                showCallerProfile(phone, name)
            }, compactActionLayout())
        }
        profileLine("BEST STARTERS", p.starters.joinToString("\n"))
        profileLine("IMPORTANT", p.importantFacts.joinToString(" • "))
        if (p.peakCombined > 0) {
            profileLine("PEAK SIGNAL", "${p.peakCombined}%${if (p.peakContext.isBlank()) "" else " • ${p.peakContext}"}")
            content.addView(destructiveButton("Delete call signal history") {
                profileStore.clearCallEvidence(phone)
                showCallerProfile(phone, name)
            }, compactActionLayout())
        }
        profileLine("LAST CALL", p.lastCallSummary)
        if (p.lastCallSummary.isNotBlank()) {
            content.addView(destructiveButton("Delete last-call summary") {
                profileStore.clearLastCall(phone)
                showCallerProfile(phone, name)
            }, compactActionLayout())
        }

        showSavedRecordings(phone, name)
        content.addView(cyberButton("Open Reality memory") {
            startActivity(Intent(this, CallerMemoryActivity::class.java).apply {
                putExtra(CallerMemoryActivity.EXTRA_PHONE, phone)
                putExtra(CallerMemoryActivity.EXTRA_NAME, label)
            })
        }, LinearLayout.LayoutParams(-1, 48.dp()).apply { setMargins(0, 8.dp(), 0, 2.dp()) })

        content.addView(cyberButton("Call $label") { placeCall(phone) }, LinearLayout.LayoutParams(-1, 50.dp()).apply { setMargins(0, 10.dp(), 0, 4.dp()) })
        content.addView(contactPanel.blockButton(phone) { showCallerProfile(phone, name) })
        content.addView(destructiveButton("Delete all Reality memory for this caller") { confirmDeleteCallerMemory(phone, name) }, LinearLayout.LayoutParams(-1, 50.dp()).apply { setMargins(0, 8.dp(), 0, 14.dp()) })
        refreshNav()
    }

    private fun showSavedRecordings(phone: String, name: String) {
        val recordings = CallRecordingStore.savedFor(this, phone)
        if (recordings.isEmpty()) return
        profileLine("SAVED RECORDINGS", "${recordings.size} private on-device recording${if (recordings.size == 1) "" else "s"}")
        recordings.forEach { recording ->
            val mode = if (recording.channels == 2) "STEREO · CALLER + YOU" else "MONO · MIXED"
            content.addView(listButton(
                "${formatRecordingDate(recording.timestampMs)}\n${formatRecordingDuration(recording.durationSeconds)}  •  $mode  •  WAV",
            ) { showRecordingActions(phone, name, recording) }.apply { minHeight = 76.dp() })
        }
    }

    private fun showRecordingActions(phone: String, name: String, recording: CallRecordingStore.SavedRecording) {
        val playingThis = recordingPlayer?.isPlaying == true
        val options = arrayOf(if (playingThis) "Stop playback" else "Play recording", "Permanently delete recording")
        AlertDialog.Builder(this)
            .setTitle(formatRecordingDate(recording.timestampMs))
            .setItems(options) { _, which ->
                if (which == 0) {
                    if (recordingPlayer?.isPlaying == true) stopRecordingPlayback() else playRecording(recording)
                } else {
                    confirmDeleteRecording(phone, name, recording)
                }
            }
            .show()
    }

    private fun playRecording(recording: CallRecordingStore.SavedRecording) {
        stopRecordingPlayback()
        try {
            recordingPlayer = MediaPlayer().apply {
                setDataSource(recording.file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (recordingPlayer === it) recordingPlayer = null
                }
                prepare()
                start()
            }
            Toast.makeText(this, "Playing saved call recording", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            stopRecordingPlayback()
            Toast.makeText(this, "Could not play recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingPlayback() {
        val player = recordingPlayer ?: return
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.release() }
        recordingPlayer = null
    }

    private fun confirmDeleteRecording(phone: String, name: String, recording: CallRecordingStore.SavedRecording) {
        AlertDialog.Builder(this)
            .setTitle("Permanently delete recording?")
            .setMessage("This WAV file will be removed from Reality Engine private storage and cannot be recovered.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete permanently") { _, _ ->
                stopRecordingPlayback()
                val deleted = CallRecordingStore.deleteSaved(this, phone, recording.file.name)
                Toast.makeText(this, if (deleted) "Recording permanently deleted" else "Could not delete recording", Toast.LENGTH_SHORT).show()
                showCallerProfile(phone, name)
            }
            .show()
    }

    private fun confirmDeleteCallerMemory(phone: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete all Reality memory?")
            .setMessage("This removes saved topics, summaries, preferences, facts and signal history for this caller. It does not delete the Android contact, call log, or saved audio recordings.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete memory") { _, _ ->
                profileStore.deleteProfile(phone)
                showCallerProfile(phone, name)
            }
            .show()
    }

    private fun profileLine(label: String, value: String) {
        if (value.isBlank()) return
        content.addView(TextView(this).apply {
            text = "$label\n$value"
            setTextColor(Color.rgb(205, 241, 248))
            setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
            background = neon(panel, Color.rgb(15, 66, 81))
            RealityTypography.display(this, 13f)
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.dp(), 0, 4.dp()) })
    }

    private fun compactActionLayout() = LinearLayout.LayoutParams(-1, 44.dp()).apply { setMargins(0, 0, 0, 5.dp()) }

    private fun emptyMessage(title: String, detail: String) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = neon(soft, RealityVisuals.Colors.Border, 14f)
            setPadding(18.dp(), 26.dp(), 18.dp(), 26.dp())
            addView(TextView(this@MainActivity).apply {
                text = title
                gravity = Gravity.CENTER
                RealityVisuals.styleMicroLabel(this, magenta)
            })
            addView(TextView(this@MainActivity).apply {
                text = detail
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(0, 8.dp(), 0, 0)
                RealityTypography.display(this, 12f)
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 6.dp(), 0, 6.dp()) })
    }

    private fun formatRecordingDate(timestampMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))

    private fun formatRecordingDuration(seconds: Long): String =
        "%d:%02d".format(seconds / 60, seconds % 60)

    private fun showContacts(query: String = "") {
        stopRecordingPlayback()
        screen = "INDEX"
        content.gravity = Gravity.TOP
        content.removeAllViews()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            sectionTitle("Index")
            content.addView(cyberButton("Authorize contacts") {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            })
            refreshNav()
            return
        }

        val contactsScreen = ContactsScreen(
            activity = this,
            index = contactIndex,
            callHistory = callHistory,
            favorites = contactFavorites,
            management = contactPanel,
            onDialContact = { numberFromContact(it) },
            onRequestContactsPermission = {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            },
        )
        content.addView(contactsScreen.build(initialQuery = query), LinearLayout.LayoutParams(-1, -2))
        refreshNav()
    }

    private fun showIntel() {
        stopRecordingPlayback()
        screen = "INTEL"
        content.gravity = Gravity.TOP
        content.removeAllViews()
        content.addView(IntelligenceHubScreen(this).build(), LinearLayout.LayoutParams(-1, -2))
        refreshNav()
    }

    private fun showSettings() {
        stopRecordingPlayback()
        screen = "SETTINGS"
        content.gravity = Gravity.TOP
        content.removeAllViews()
        val dashboard = SettingsDashboardScreen(
            activity = this,
            store = settingsStore,
            buttonShapeLabel = shapeName(),
            onRefresh = { showSettings() },
            onWalkthrough = { showWalkthrough() },
            onAbout = { showAbout() },
            onCheckUpdate = { checkForUpdate() },
            onDefaultPhone = { requestDefaultPhoneRole() },
            onCycleButtonShape = { cycleButtonShape() },
            onShizuku = { requestShizuku() },
            onCallAudio = { checkCallAudio() },
            onAndroidPhoneSettings = { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
        )
        content.addView(dashboard.build(), LinearLayout.LayoutParams(-1, -2))
        refreshNav()
    }

    private fun checkForUpdate() {
        Toast.makeText(this, "Checking private update…", Toast.LENGTH_SHORT).show()
        appUpdater.check { result ->
            runOnUiThread {
                when (result) {
                    is AppUpdater.CheckResult.Current -> AlertDialog.Builder(this)
                        .setTitle("Reality Engine is current")
                        .setMessage("Installed version ${result.versionName} is the newest green build.")
                        .setPositiveButton("OK", null)
                        .show()
                    is AppUpdater.CheckResult.Failed -> AlertDialog.Builder(this)
                        .setTitle("Update check failed")
                        .setMessage(result.reason)
                        .setPositiveButton("OK", null)
                        .show()
                    is AppUpdater.CheckResult.Available -> AlertDialog.Builder(this)
                        .setTitle("Update available")
                        .setMessage("${result.info.versionName} · ${result.info.buildId}\n\nDownload and install this private green build now?")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Install") { _, _ ->
                            if (!appUpdater.canInstallPackages()) {
                                Toast.makeText(this, "Allow Reality Engine to install updates, then tap App updates again.", Toast.LENGTH_LONG).show()
                                appUpdater.openInstallPermission()
                            } else {
                                Toast.makeText(this, "Downloading private update…", Toast.LENGTH_SHORT).show()
                                appUpdater.downloadAndInstall(result.info) { message ->
                                    runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
                                }
                            }
                        }
                        .show()
                }
            }
        }
    }

    private fun shapeName() = when (buttonShape) {
        0 -> "Angular · 03"
        2 -> "Rounded · 30"
        else -> "Tactical · 14"
    }

    private fun cycleButtonShape() {
        buttonShape = (buttonShape + 1) % 3
        getPreferences(MODE_PRIVATE).edit().putInt("buttonShape", buttonShape).apply()
        buildPhoneUi()
        showSettings()
    }

    private fun sectionTitle(t: String) {
        content.addView(TextView(this).apply {
            text = t
            setTextColor(Color.rgb(229, 249, 252))
            setPadding(2.dp(), 20.dp(), 0, 16.dp())
            RealityTypography.displayMedium(this, 22f)
        })
    }

    private fun listButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(205, 241, 248))
        RealityTypography.display(this, 14f)
        background = neon(panel, Color.rgb(15, 66, 81))
        stateListAnimator = null
        setPadding(16.dp(), 0, 16.dp(), 0)
        setOnClickListener { click() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, 70.dp()).apply { setMargins(0, 4.dp(), 0, 4.dp()) }
    }

    private fun updateShizukuStatus() {
        shizukuStatus.text = when {
            !ShizukuAudioStatus.binderAvailable() -> "Offline"
            !ShizukuAudioStatus.permissionGranted() -> "Authorization required"
            else -> "Connected"
        }
    }

    private fun updateAudioStatus() {
        audioStatus.text = when (CallAudioBridge.state(this)) {
            CallAudioBridge.State.UNAVAILABLE -> "Waiting for Shizuku"
            CallAudioBridge.State.SHIZUKU_READY -> "Shizuku ready"
            CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED -> "Microphone authorization required"
            CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE -> "Voice call available"
            CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED -> "Voice call blocked"
        }
    }

    private fun checkCallAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        updateAudioStatus()
        showSettings()
    }

    private fun requestShizuku() {
        if (ShizukuAudioStatus.binderAvailable() && !ShizukuAudioStatus.permissionGranted()) {
            ShizukuAudioStatus.requestPermission()
        }
        updateShizukuStatus()
        updateAudioStatus()
        showSettings()
    }

    private fun numberFromContact(phone: String) {
        pendingNumber = phone
        showPhone()
    }

    private fun updateRoleStatus() {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        status.text = if (telecom.defaultDialerPackage == packageName) "● Native cellular phone" else "● Default phone app needed"
    }

    private fun requestDefaultPhoneRole() {
        val role = getSystemService(RoleManager::class.java)
        if (role != null && role.isRoleAvailable(RoleManager.ROLE_DIALER) && !role.isRoleHeld(RoleManager.ROLE_DIALER)) {
            startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQ_ROLE)
        } else {
            updateRoleStatus()
        }
    }

    private fun placeCall(value: String) {
        if (value.isEmpty()) {
            if (::error.isInitialized) error.text = "No target number"
            return
        }
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (telecom.defaultDialerPackage == packageName) {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
                return
            }
            try {
                telecom.placeCall(Uri.fromParts("tel", value, null), null)
            } catch (_: Exception) {
                if (::error.isInitialized) error.text = "Connection failed"
            }
        } else if (::error.isInitialized) {
            error.text = "Default phone app required"
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        when (rc) {
            REQ_CALL -> if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED && ::number.isInitialized) placeCall(number.text.toString().trim())
            REQ_CALL_LOG -> showRecents()
            REQ_CONTACTS -> showContacts()
            REQ_AUDIO -> {
                updateAudioStatus()
                if (::content.isInitialized) showSettings()
            }
            REQ_CONTACT_WRITE -> showContacts()
        }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF() = this * resources.displayMetrics.density

    companion object {
        private const val REQ_ROLE = 1001
        private const val REQ_CALL = 1002
        private const val REQ_CALL_LOG = 1003
        private const val REQ_CONTACTS = 1004
        private const val REQ_AUDIO = 1005
        private const val REQ_CONTACT_WRITE = 1006
    }
}
