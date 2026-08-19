package com.realityengine.v4

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class RealityInCallService : InCallService() {
    companion object {
        @Volatile var instance: RealityInCallService? = null
    }

    @Volatile
    private var audioReadiness: CallAudioBridge.State = CallAudioBridge.State.UNAVAILABLE

    override fun onCreate() {
        super.onCreate()
        instance = this
        ShizukuAudioStatus.requestPermission()
        refreshAudioReadiness()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        refreshAudioReadiness()
        CallSessionRegistry.add(call)
        call.registerCallback(callback)
        launchCallUi()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallSessionRegistry.remove(call)
        refreshAudioReadiness()
        super.onCallRemoved(call)
    }

    fun isMutedNow(): Boolean = callAudioState?.isMuted == true

    fun audioState(): CallAudioBridge.State = audioReadiness

    private fun refreshAudioReadiness() {
        audioReadiness = CallAudioBridge.state(this)
    }

    private fun launchCallUi() {
        startActivity(Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            refreshAudioReadiness()
            if (state == Call.STATE_DISCONNECTED) {
                CallSessionRegistry.removeIfDisconnected(call)
            } else {
                CallSessionRegistry.add(call)
            }
        }
    }
}
