package com.realityengine.v4

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class RealityInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSessionRegistry.add(call)
        call.registerCallback(callback)
        launchCallUi()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallSessionRegistry.remove(call)
        super.onCallRemoved(call)
    }

    private fun launchCallUi() {
        startActivity(
            Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallSessionRegistry.add(call)
        }
    }
}
