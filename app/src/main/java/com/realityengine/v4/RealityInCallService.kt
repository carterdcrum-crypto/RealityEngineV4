package com.realityengine.v4

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.core.app.NotificationCompat

class RealityInCallService : InCallService() {
    companion object {
        @Volatile var instance: RealityInCallService? = null
        private const val CHANNEL = "reality_active_call"
        private const val NOTIFICATION_ID = 4104
        const val ACTION_END_CALL = "com.realityengine.v4.END_CALL"
    }

    /*
     * Keep the Telecom bind path deliberately tiny. Android falls back to the preloaded dialer if
     * the default dialer's InCallService cannot bind. Call intelligence is therefore initialized
     * only after Telecom has delivered a Call and Phone has registered/launched its core call UI.
     */
    private var transcription: LiveTranscriptionPipeline? = null
    private var audioRouter: AudioCaptureRouter? = null
    private var summaryBuilder: CallSummaryBuilder? = null
    private var settings: SettingsStore? = null
    @Volatile private var engineInitAttempted = false
    @Volatile private var engineInitFailure: String? = null

    private val finalizedCalls = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call, Boolean>())
    private val finalizedRecordings = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call, Boolean>())
    private val mainHandler by lazy { Handler(mainLooper) }
    @Volatile private var failedCall: Call? = null
    private var ringtone: Ringtone? = null
    private var ringingCall: Call? = null
    @Volatile private var currentEndpoint: CallEndpoint? = null
    @Volatile private var availableEndpoints: List<CallEndpoint> = emptyList()

    override fun onCreate() {
        super.onCreate()
        instance = this
        runCatching { createCallChannel() }
    }

    override fun onDestroy() {
        stopRingtone()
        runCatching { cancelCallNotification() }
        runCatching { transcription?.stop() }
        runCatching { transcription?.discardRecording() }
        runCatching { clearLiveSession() }
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_CALL) CallSessionRegistry.primary()?.disconnect()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        if (CallSessionRegistry.primary() == null) return
        showCallNotification()
        launchCallUi()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        finalizedCalls.remove(call)
        finalizedRecordings.remove(call)
        failedCall = null
        if (CallSessionRegistry.primary() == null) clearLiveSession()

        // Once Phone owns ROLE_DIALER, Telecom owns the handoff. Launch the call UI once and let the
        // Activity/registry update in place; repeatedly re-launching it during callbacks can race
        // Samsung's task manager and kill the default-dialer process.
        CallSessionRegistry.add(call)
        call.registerCallback(callback)
        showCallNotification()
        launchCallUi()
        syncRinging()

        if (ensureEnginesReady()) {
            runCatching { syncTranscription() }
        }
    }

    override fun onCallRemoved(call: Call) {
        val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
        call.unregisterCallback(callback)
        if (failedCall === call) failedCall = null
        if (ringingCall === call) stopRingtone()
        runCatching { finalizeCallEnd(call, endedNumber) }
        CallSessionRegistry.remove(call)
        if (CallSessionRegistry.primary() != null) {
            syncRinging()
            if (ensureEnginesReady()) runCatching { syncTranscription() }
            showCallNotification()
        } else {
            stopRingtone()
            cancelCallNotification()
            runCatching { transcription?.stop() }
            currentEndpoint = null
            availableEndpoints = emptyList()
            clearLiveSession()
        }
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        val primary = CallSessionRegistry.primary()
        if (primary != null) {
            if (primary.state == Call.STATE_ACTIVE && failedCall === primary && transcription?.isRunning() != true) {
                failedCall = null
            }
            if (ensureEnginesReady()) runCatching { syncTranscription() }
            showCallNotification()
        }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        currentEndpoint = callEndpoint
        val primary = CallSessionRegistry.primary()
        if (primary != null) {
            if (primary.state == Call.STATE_ACTIVE && failedCall === primary && transcription?.isRunning() != true) {
                failedCall = null
            }
            if (ensureEnginesReady()) runCatching { syncTranscription() }
            showCallNotification()
        }
    }

    override fun onAvailableCallEndpointsChanged(endpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(endpoints)
        availableEndpoints = endpoints.toList()
    }

    fun availableCallEndpointsSnapshot(): List<CallEndpoint> = availableEndpoints.toList()

    fun currentCallEndpointSnapshot(): CallEndpoint? = currentEndpoint

    fun selectCallEndpoint(endpoint: CallEndpoint, callback: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            callback("Call endpoint selection requires Android 14 or newer")
            return
        }
        requestCallEndpointChange(
            endpoint,
            mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = callback(null)
                override fun onError(error: CallEndpointException) {
                    callback(error.message?.takeIf { it.isNotBlank() } ?: "Android could not switch call audio")
                }
            },
        )
    }

    fun isMutedNow(): Boolean = callAudioState?.isMuted == true
    fun recordingActive(): Boolean = transcription?.isRecording() == true

    /** Starts a visible user-requested recording on the already-authorized call-audio stream. */
    fun startRecording(): Boolean {
        val call = CallSessionRegistry.primary() ?: return false
        if (call.state != Call.STATE_ACTIVE) return false
        if (!ensureEnginesReady()) return false
        val pipeline = transcription ?: return false
        val number = CallSessionRegistry.numberFor(call).orEmpty().ifBlank { "Unknown" }
        val match = ContactMediaStore.findByNumber(this, number)
        val name = match?.name?.takeIf { it.isNotBlank() } ?: number
        val started = pipeline.startRecording(number, name)
        if (started) showCallNotification()
        return started
    }

    private fun ensureEnginesReady(): Boolean {
        if (transcription != null && audioRouter != null && summaryBuilder != null && settings != null) return true
        if (engineInitAttempted && engineInitFailure != null) return false
        engineInitAttempted = true
        return try {
            LiveSignalState.initialize(applicationContext)
            val newSettings = SettingsStore(applicationContext)
            val newTranscription = LiveTranscriptionPipeline(applicationContext)
            val newAudioRouter = AudioCaptureRouter(applicationContext)
            val newSummaryBuilder = CallSummaryBuilder(applicationContext)
            settings = newSettings
            transcription = newTranscription
            audioRouter = newAudioRouter
            summaryBuilder = newSummaryBuilder
            engineInitFailure = null
            true
        } catch (t: Throwable) {
            engineInitFailure = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            AudioRouteState.publish(
                AudioCaptureRouter.Decision(
                    AudioCaptureRouter.Route.UNAVAILABLE,
                    "Call intelligence unavailable: ${engineInitFailure.orEmpty().take(120)}",
                    false,
                ),
            )
            false
        }
    }

    @Synchronized
    private fun finalizeOnce(call: Call, phoneNumber: String): Boolean {
        if (!finalizedCalls.add(call)) return false
        val transcriptKey = phoneNumber.ifBlank { "Unknown" }
        val savedTranscript = CallTranscriptStore.save(applicationContext, transcriptKey, LiveTranscriptState.transcript())
        if (phoneNumber.isNotBlank()) runCatching { summaryBuilder?.finalize(phoneNumber, savedTranscript?.text.orEmpty()) }
        return true
    }

    @Synchronized
    private fun finalizeRecordingOnce(call: Call, phoneNumber: String): Boolean {
        if (!finalizedRecordings.add(call)) return false
        val recording = transcription?.finishRecording() ?: return false
        CallRecordingState.publish(recording)
        startActivity(Intent(this, PostCallReviewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        return true
    }

    private fun finalizeCallEnd(call: Call, phoneNumber: String) {
        val firstFinalize = finalizeOnce(call, phoneNumber)
        val openPostCallProfile = firstFinalize && phoneNumber.isNotBlank()
        if (openPostCallProfile) {
            val match = ContactMediaStore.findByNumber(this, phoneNumber)
            val name = match?.name?.takeIf { it.isNotBlank() } ?: phoneNumber
            PostCallProfileState.queue(phoneNumber, name)
        }
        val reviewStarted = finalizeRecordingOnce(call, phoneNumber)
        if (openPostCallProfile && !reviewStarted) {
            mainHandler.postDelayed(
                { PostCallProfileState.launchIfPending(this) },
                700L,
            )
        }
    }

    private fun maybeStartAutoRecording(call: Call) {
        val currentSettings = settings ?: return
        val pipeline = transcription ?: return
        if (!currentSettings.autoRecordCalls || pipeline.isRecording() || call.state != Call.STATE_ACTIVE) return
        val number = CallSessionRegistry.numberFor(call).orEmpty().ifBlank { "Unknown" }
        val match = ContactMediaStore.findByNumber(this, number)
        val name = match?.name?.takeIf { it.isNotBlank() } ?: number
        pipeline.startRecording(number, name)
    }

    private fun clearLiveSession() {
        runCatching { transcription?.clearConversation() }
        LiveTranscriptState.clear()
        LiveSignalState.clear()
        AudioRouteState.clear()
        ResponseCoachState.clearCall()
    }

    private fun publishFailure(call: Call, reason: String?) {
        failedCall = call
        val detail = reason?.takeIf { it.isNotBlank() } ?: "Audio stream ended"
        AudioRouteState.publish(
            AudioCaptureRouter.Decision(
                AudioCaptureRouter.Route.UNAVAILABLE,
                "Live transcription stopped: ${detail.take(120)}",
                false,
            ),
        )
        showCallNotification()
    }

    private fun startNative(call: Call) {
        val pipeline = transcription ?: return
        when (val result = pipeline.start(onStopped = { reason ->
            runOnMain {
                if (CallSessionRegistry.primary() === call && call.state == Call.STATE_ACTIVE) publishFailure(call, reason)
            }
        })) {
            LiveTranscriptionPipeline.StartResult.Started -> maybeStartAutoRecording(call)
            is LiveTranscriptionPipeline.StartResult.Unavailable -> publishFailure(call, result.reason)
        }
    }

    private fun startTwilio(call: Call) {
        val pipeline = transcription ?: return
        when (val result = pipeline.startTwilio(onStopped = { reason ->
            runOnMain {
                if (CallSessionRegistry.primary() === call && call.state == Call.STATE_ACTIVE) publishFailure(call, reason)
            }
        })) {
            LiveTranscriptionPipeline.StartResult.Started -> maybeStartAutoRecording(call)
            is LiveTranscriptionPipeline.StartResult.Unavailable -> publishFailure(call, result.reason)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    /**
     * Telecom can announce ACTIVE before Samsung finishes switching the call-audio route. Outgoing
     * calls usually have the route by then; freshly answered incoming calls sometimes do not. Retry
     * a few times while the same call remains active so one early capture miss cannot disable STT
     * for the entire call.
     */
    private fun armTranscriptionWarmup(call: Call) {
        val delays = longArrayOf(250L, 700L, 1_400L, 2_800L)
        delays.forEach { delay ->
            mainHandler.postDelayed({
                val pipeline = transcription ?: return@postDelayed
                if (CallSessionRegistry.primary() !== call || call.state != Call.STATE_ACTIVE || pipeline.isRunning()) {
                    return@postDelayed
                }
                if (failedCall === call) failedCall = null
                runCatching { syncTranscription() }
            }, delay)
        }
    }

    private fun syncRinging() {
        val call = CallSessionRegistry.primary()
        if (call == null || call.state != Call.STATE_RINGING) {
            stopRingtone()
            return
        }
        if (ringingCall === call && ringtone?.isPlaying == true) return

        stopRingtone()
        val number = CallSessionRegistry.numberFor(call).orEmpty()
        val match = ContactMediaStore.findByNumber(this, number)
        val uri = match?.let { ContactMediaStore.customRingtoneUri(this, it.contactId) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
            ringingCall = call
        } catch (_: Throwable) {
            ringtone = null
            ringingCall = call
        }
    }

    private fun stopRingtone() {
        try { ringtone?.stop() } catch (_: Throwable) {}
        ringtone = null
        ringingCall = null
    }

    private fun syncTranscription() {
        val pipeline = transcription ?: return
        val router = audioRouter ?: return
        val call = CallSessionRegistry.primary() ?: run {
            pipeline.stop()
            failedCall = null
            clearLiveSession()
            return
        }
        if (call.state != Call.STATE_ACTIVE) {
            if (pipeline.isRunning()) pipeline.stop()
            if (call.state == Call.STATE_DISCONNECTED) failedCall = null
            AudioRouteState.clear()
            showCallNotification()
            return
        }
        if (failedCall === call && !pipeline.isRunning()) return
        val decision = router.decide(twilioCallActive = TwilioFallbackState.isActive())
        AudioRouteState.publish(decision)
        AudioRouteState.diagnose(applicationContext)
        when (decision.route) {
            AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> if (!pipeline.isRunning()) startNative(call)
            AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> if (!pipeline.isRunning()) startTwilio(call)
            else -> if (pipeline.isRunning()) pipeline.stop()
        }
        if (pipeline.isRunning()) maybeStartAutoRecording(call)
    }

    private fun createCallChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Active calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Return to or control an active Phone call"
                    setSound(null, null)
                },
            )
        }
    }

    private fun showCallNotification() {
        runCatching {
            val call = CallSessionRegistry.primary() ?: return@runCatching
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@runCatching
            val open = PendingIntent.getActivity(
                this,
                1,
                Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val end = PendingIntent.getService(
                this,
                2,
                Intent(this, RealityInCallService::class.java).setAction(ACTION_END_CALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val number = CallSessionRegistry.numberFor(call).orEmpty().ifBlank { "Active call" }
            val rec = if (recordingActive()) " · REC" else ""
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Phone · Active call$rec")
                .setContentText(number)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .addAction(android.R.drawable.sym_action_call, "Return to call", open)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End call", end)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun cancelCallNotification() {
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }

    private fun launchCallUi() {
        runCatching {
            startActivity(Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
    }

    private val callback = object : Call.Callback() {
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallSessionRegistry.refreshDetails(call)
            syncRinging()
            showCallNotification()
        }

        override fun onStateChanged(call: Call, state: Int) {
            CallSessionRegistry.refreshDetails(call)
            syncRinging()
            if (state == Call.STATE_DISCONNECTED) {
                val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
                runCatching { finalizeCallEnd(call, endedNumber) }
                CallSessionRegistry.removeIfDisconnected(call)
                if (failedCall === call) failedCall = null
                if (CallSessionRegistry.primary() == null) {
                    stopRingtone()
                    cancelCallNotification()
                    runCatching { transcription?.stop() }
                    clearLiveSession()
                } else {
                    syncRinging()
                    if (ensureEnginesReady()) runCatching { syncTranscription() }
                    showCallNotification()
                }
            } else {
                CallSessionRegistry.add(call)
                if (state == Call.STATE_ACTIVE) {
                    if (failedCall === call && transcription?.isRunning() != true) failedCall = null
                    if (ensureEnginesReady()) {
                        runCatching { syncTranscription() }
                        armTranscriptionWarmup(call)
                    }
                } else if (ensureEnginesReady()) {
                    runCatching { syncTranscription() }
                }
                syncRinging()
                showCallNotification()
            }
        }
    }
}
