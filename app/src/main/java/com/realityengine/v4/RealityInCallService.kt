package com.realityengine.v4

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class RealityInCallService : InCallService() {
    companion object { @Volatile var instance: RealityInCallService? = null }

    private lateinit var transcription: LiveTranscriptionPipeline
    private lateinit var audioRouter: AudioCaptureRouter

    override fun onCreate() {
        super.onCreate()
        instance = this
        transcription = LiveTranscriptionPipeline(applicationContext)
        audioRouter = AudioCaptureRouter(applicationContext)
        ShizukuAudioStatus.requestPermission()
    }

    override fun onDestroy() {
        transcription.stop()
        LiveSignalState.clear()
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
        call.unregisterCallback(callback)
        CallSessionRegistry.remove(call)
        if (CallSessionRegistry.primary() != null) { syncTranscription(); launchCallUi() }
        else { transcription.stop(); LiveSignalState.clear() }
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (CallSessionRegistry.primary() != null) { syncTranscription(); launchCallUi() }
    }

    fun isMutedNow(): Boolean = callAudioState?.isMuted == true

    private fun syncTranscription() {
        val call = CallSessionRegistry.primary() ?: run { transcription.stop(); return }
        if (call.state != Call.STATE_ACTIVE) { if (transcription.isRunning()) transcription.stop(); return }
        if (transcription.isRunning()) return

        when (audioRouter.decide(twilioCallActive = false).route) {
            AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> transcription.start()
            else -> transcription.stop()
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
                CallSessionRegistry.removeIfDisconnected(call)
                if (CallSessionRegistry.primary() == null) { transcription.stop(); LiveSignalState.clear() }
                else syncTranscription()
            } else {
                CallSessionRegistry.add(call)
                syncTranscription()
                launchCallUi()
            }
        }
    }
}
