package com.realityengine.v4

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class RealityInCallService : InCallService() {
    companion object { @Volatile var instance: RealityInCallService? = null }

    private lateinit var transcription: LiveTranscriptionPipeline
    private lateinit var audioRouter: AudioCaptureRouter
    private lateinit var summaryBuilder: CallSummaryBuilder

    override fun onCreate() {
        super.onCreate()
        instance = this
        LiveSignalState.initialize(applicationContext)
        transcription = LiveTranscriptionPipeline(applicationContext)
        audioRouter = AudioCaptureRouter(applicationContext)
        summaryBuilder = CallSummaryBuilder(applicationContext)
        ShizukuAudioStatus.requestPermission()
    }

    override fun onDestroy() {
        transcription.stop()
        LiveSignalState.clear()
        AudioRouteState.clear()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (CallSessionRegistry.primary() == null) LiveSignalState.clear()
        CallSessionRegistry.add(call)
        call.registerCallback(callback)
        syncTranscription()
        launchCallUi()
    }

    override fun onCallRemoved(call: Call) {
        val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
        call.unregisterCallback(callback)
        CallSessionRegistry.remove(call)
        if (endedNumber.isNotBlank()) summaryBuilder.finalize(endedNumber)
        if (CallSessionRegistry.primary() != null) { syncTranscription(); launchCallUi() }
        else { transcription.stop(); LiveSignalState.clear(); AudioRouteState.clear() }
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (CallSessionRegistry.primary() != null) { syncTranscription(); launchCallUi() }
    }

    fun isMutedNow(): Boolean = callAudioState?.isMuted == true

    private fun syncTranscription() {
        val call = CallSessionRegistry.primary() ?: run { transcription.stop(); AudioRouteState.clear(); return }
        if (call.state != Call.STATE_ACTIVE) {
            if (transcription.isRunning()) transcription.stop()
            AudioRouteState.clear()
            return
        }

        val decision = audioRouter.decide(twilioCallActive = TwilioFallbackState.isActive())
        AudioRouteState.publish(decision)
        when (decision.route) {
            AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL,
            AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> if (!transcription.isRunning()) transcription.start()
            else -> if (transcription.isRunning()) transcription.stop()
        }
    }

    private fun launchCallUi() {
        startActivity(Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) {
                val endedNumber = CallSessionRegistry.numberFor(call).orEmpty()
                CallSessionRegistry.removeIfDisconnected(call)
                if (endedNumber.isNotBlank()) summaryBuilder.finalize(endedNumber)
                if (CallSessionRegistry.primary() == null) { transcription.stop(); LiveSignalState.clear(); AudioRouteState.clear() }
                else syncTranscription()
            } else {
                CallSessionRegistry.add(call)
                syncTranscription()
                launchCallUi()
            }
        }
    }
}
