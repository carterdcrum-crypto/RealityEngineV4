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
import android.telecom.Call
import android.telecom.CallAudioState
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
    private val finalizedCalls = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call, Boolean>())
    @Volatile private var failedCall: Call? = null
    private var ringtone: Ringtone? = null
    private var ringingCall: Call? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        LiveSignalState.initialize(applicationContext)
        transcription = LiveTranscriptionPipeline(applicationContext)
        audioRouter = AudioCaptureRouter(applicationContext)
        summaryBuilder = CallSummaryBuilder(applicationContext)
        createCallChannel()
        ShizukuAudioStatus.requestPermission()
    }

    override fun onDestroy() {
        stopRingtone()
        cancelCallNotification()
        transcription.stop()
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
        CallSessionRegistry.remove(call)
        if (failedCall === call) failedCall = null
        if (ringingCall === call) stopRingtone()
        finalizeOnce(call, endedNumber)
        if (CallSessionRegistry.primary() != null) {
            syncRinging()
            syncTranscription()
            showCallNotification()
            launchCallUi()
        } else {
            stopRingtone()
            cancelCallNotification()
            transcription.stop()
            clearLiveSession()
        }
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (CallSessionRegistry.primary() != null) {
            syncTranscription()
            showCallNotification()
            launchCallUi()
        }
    }

    fun isMutedNow(): Boolean = callAudioState?.isMuted == true

    @Synchronized
    private fun finalizeOnce(call: Call, phoneNumber: String) {
        if (phoneNumber.isBlank() || !finalizedCalls.add(call)) return
        summaryBuilder.finalize(phoneNumber)
    }

    private fun clearLiveSession() {
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
            LiveTranscriptionPipeline.StartResult.Started -> Unit
            is LiveTranscriptionPipeline.StartResult.Unavailable -> publishFailure(call, result.reason)
        }
    }

    private fun startTwilio(call: Call) {
        when (val result = transcription.startTwilio(onStopped = { reason ->
            runOnMain {
                if (CallSessionRegistry.primary() === call && call.state == Call.STATE_ACTIVE) publishFailure(call, reason)
            }
        })) {
            LiveTranscriptionPipeline.StartResult.Started -> Unit
            is LiveTranscriptionPipeline.StartResult.Unavailable -> publishFailure(call, result.reason)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        android.os.Handler(mainLooper).post(block)
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
        try {
            ringtone?.stop()
        } catch (_: Throwable) {
        }
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
            LiveTranscriptState.clear()
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
    }

    private fun createCallChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Active calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Return to or control an active Reality Engine call"
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
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Reality Engine · Active call")
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
        override fun onStateChanged(call: Call, state: Int) {
            syncRinging()
            if (state == Call.STATE_DISCONNECTED) {
                val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
                CallSessionRegistry.removeIfDisconnected(call)
                if (failedCall === call) failedCall = null
                finalizeOnce(call, endedNumber)
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
                syncRinging()
                syncTranscription()
                showCallNotification()
                launchCallUi()
            }
        }
    }
}
