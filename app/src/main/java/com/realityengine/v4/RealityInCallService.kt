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

    private lateinit var transcription: LiveTranscriptionPipeline
    private lateinit var audioRouter: AudioCaptureRouter
    private lateinit var summaryBuilder: CallSummaryBuilder
    private lateinit var settings: SettingsStore
    private val finalizedCalls = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call, Boolean>())
    private val finalizedRecordings = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call, Boolean>())
    private val incomingCalls = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Call, Boolean>())
    private val mainHandler by lazy { Handler(mainLooper) }
    @Volatile private var failedCall: Call? = null
    private var ringtone: Ringtone? = null
    private var ringingCall: Call? = null
    @Volatile private var currentEndpoint: CallEndpoint? = null
    @Volatile private var availableEndpoints: List<CallEndpoint> = emptyList()

    override fun onCreate() {
        super.onCreate()
        instance = this
        LiveSignalState.initialize(applicationContext)
        transcription = LiveTranscriptionPipeline(applicationContext)
        audioRouter = AudioCaptureRouter(applicationContext)
        summaryBuilder = CallSummaryBuilder(applicationContext)
        settings = SettingsStore(applicationContext)
        createCallChannel()
        ShizukuAudioStatus.requestPermission()
    }

    override fun onDestroy() {
        stopRingtone()
        cancelCallNotification()
        transcription.stop()
        transcription.discardRecording()
        clearLiveSession()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_CALL) CallSessionRegistry.primary()?.disconnect()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        finalizedCalls.remove(call)
        finalizedRecordings.remove(call)
        incomingCalls[call] = call.state == Call.STATE_RINGING
        failedCall = null
        if (CallSessionRegistry.primary() == null) clearLiveSession()
        CallSessionRegistry.add(call)
        call.registerCallback(callback)
        syncRinging()
        syncTranscription()
        showCallNotification()
        launchCallUi()
    }

    override fun onCallRemoved(call: Call) {
        val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
        call.unregisterCallback(callback)
        if (failedCall === call) failedCall = null
        if (ringingCall === call) stopRingtone()
        finalizeCallEnd(call, endedNumber)
        CallSessionRegistry.remove(call)
        incomingCalls.remove(call)
        if (CallSessionRegistry.primary() != null) {
            syncRinging()
            syncTranscription()
            showCallNotification()
            launchCallUi()
        } else {
            stopRingtone()
            cancelCallNotification()
            transcription.stop()
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
            // Incoming calls can report ACTIVE slightly before the capture route is ready. A later
            // audio-route callback is a valid reason to retry instead of permanently honoring the
            // first startup failure.
            if (primary.state == Call.STATE_ACTIVE && failedCall === primary && !transcription.isRunning()) {
                failedCall = null
            }
            syncTranscription()
            showCallNotification()
            launchCallUi()
        }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        currentEndpoint = callEndpoint
        val primary = CallSessionRegistry.primary()
        if (primary != null) {
            if (primary.state == Call.STATE_ACTIVE && failedCall === primary && !transcription.isRunning()) {
                failedCall = null
            }
            syncTranscription()
            showCallNotification()
            launchCallUi()
        }
    }

    override fun onAvailableCallEndpointsChanged(endpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(endpoints)
        availableEndpoints = endpoints.toList()
        if (CallSessionRegistry.primary() != null) launchCallUi()
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
    fun recordingActive(): Boolean = transcription.isRecording()

    /** Starts a visible user-requested recording on the already-authorized call-audio stream. */
    fun startRecording(): Boolean {
        val call = CallSessionRegistry.primary() ?: return false
        if (call.state != Call.STATE_ACTIVE) return false
        val number = CallSessionRegistry.numberFor(call).orEmpty().ifBlank { "Unknown" }
        val match = ContactMediaStore.findByNumber(this, number)
        val name = match?.name?.takeIf { it.isNotBlank() } ?: number
        val started = transcription.startRecording(number, name)
        if (started) {
            showCallNotification()
            launchCallUi()
        }
        return started
    }

    @Synchronized
    private fun finalizeOnce(call: Call, phoneNumber: String): Boolean {
        if (!finalizedCalls.add(call)) return false
        val transcriptKey = phoneNumber.ifBlank { "Unknown" }
        val savedTranscript = CallTranscriptStore.save(applicationContext, transcriptKey, LiveTranscriptState.transcript())
        if (phoneNumber.isNotBlank()) summaryBuilder.finalize(phoneNumber, savedTranscript?.text.orEmpty())
        return true
    }

    @Synchronized
    private fun finalizeRecordingOnce(call: Call, phoneNumber: String): Boolean {
        if (!finalizedRecordings.add(call)) return false
        val recording = transcription.finishRecording() ?: return false
        CallRecordingState.publish(recording)
        startActivity(Intent(this, PostCallReviewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        return true
    }

    private fun finalizeCallEnd(call: Call, phoneNumber: String) {
        val firstFinalize = finalizeOnce(call, phoneNumber)
        val openIncomingProfile = firstFinalize && incomingCalls[call] == true && phoneNumber.isNotBlank()
        if (openIncomingProfile) {
            val match = ContactMediaStore.findByNumber(this, phoneNumber)
            val name = match?.name?.takeIf { it.isNotBlank() } ?: phoneNumber
            PostCallProfileState.queue(phoneNumber, name)
        }
        val reviewStarted = finalizeRecordingOnce(call, phoneNumber)
        if (openIncomingProfile && !reviewStarted) {
            mainHandler.postDelayed(
                { PostCallProfileState.launchIfPending(this) },
                700L,
            )
        }
    }

    private fun maybeStartAutoRecording(call: Call) {
        if (!settings.autoRecordCalls || transcription.isRecording() || call.state != Call.STATE_ACTIVE) return
        val number = CallSessionRegistry.numberFor(call).orEmpty().ifBlank { "Unknown" }
        val match = ContactMediaStore.findByNumber(this, number)
        val name = match?.name?.takeIf { it.isNotBlank() } ?: number
        transcription.startRecording(number, name)
    }

    private fun clearLiveSession() {
        if (::transcription.isInitialized) transcription.clearConversation()
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
        launchCallUi()
    }

    private fun startNative(call: Call) {
        when (val result = transcription.start(onStopped = { reason ->
            runOnMain {
                if (CallSessionRegistry.primary() === call && call.state == Call.STATE_ACTIVE) publishFailure(call, reason)
            }
        })) {
            LiveTranscriptionPipeline.StartResult.Started -> maybeStartAutoRecording(call)
            is LiveTranscriptionPipeline.StartResult.Unavailable -> publishFailure(call, result.reason)
        }
    }

    private fun startTwilio(call: Call) {
        when (val result = transcription.startTwilio(onStopped = { reason ->
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
                if (CallSessionRegistry.primary() !== call || call.state != Call.STATE_ACTIVE || transcription.isRunning()) {
                    return@postDelayed
                }
                if (failedCall === call) failedCall = null
                syncTranscription()
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
        val call = CallSessionRegistry.primary() ?: run {
            transcription.stop()
            failedCall = null
            clearLiveSession()
            return
        }
        if (call.state != Call.STATE_ACTIVE) {
            if (transcription.isRunning()) transcription.stop()
            if (call.state == Call.STATE_DISCONNECTED) failedCall = null
            AudioRouteState.clear()
            showCallNotification()
            launchCallUi()
            return
        }
        if (failedCall === call && !transcription.isRunning()) return
        val decision = audioRouter.decide(twilioCallActive = TwilioFallbackState.isActive())
        AudioRouteState.publish(decision)
        AudioRouteState.diagnose(applicationContext)
        when (decision.route) {
            AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> if (!transcription.isRunning()) startNative(call)
            AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> if (!transcription.isRunning()) startTwilio(call)
            else -> if (transcription.isRunning()) transcription.stop()
        }
        if (transcription.isRunning()) maybeStartAutoRecording(call)
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
        val call = CallSessionRegistry.primary() ?: return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
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

    private fun cancelCallNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun launchCallUi() {
        startActivity(Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private val callback = object : Call.Callback() {
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallSessionRegistry.refreshDetails(call)
            syncRinging()
            showCallNotification()
            launchCallUi()
        }

        override fun onStateChanged(call: Call, state: Int) {
            CallSessionRegistry.refreshDetails(call)
            syncRinging()
            if (state == Call.STATE_DISCONNECTED) {
                val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
                finalizeCallEnd(call, endedNumber)
                CallSessionRegistry.removeIfDisconnected(call)
                if (failedCall === call) failedCall = null
                if (CallSessionRegistry.primary() == null) {
                    stopRingtone()
                    cancelCallNotification()
                    transcription.stop()
                    clearLiveSession()
                } else {
                    syncRinging()
                    syncTranscription()
                    showCallNotification()
                    launchCallUi()
                }
            } else {
                CallSessionRegistry.add(call)
                if (state == Call.STATE_ACTIVE) {
                    // Treat ACTIVE as a fresh chance even if the first capture attempt happened a few
                    // milliseconds before the route was ready.
                    if (failedCall === call && !transcription.isRunning()) failedCall = null
                    syncTranscription()
                    armTranscriptionWarmup(call)
                } else {
                    syncTranscription()
                }
                syncRinging()
                showCallNotification()
                launchCallUi()
            }
        }
    }
}
