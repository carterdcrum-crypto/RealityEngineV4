package com.realityengine.v4

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.telecom.Call
import android.telecom.InCallService
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CallActivity : Activity() {
    private var call: Call? = null
    private lateinit var caller: TextView
    private lateinit var state: TextView
    private val registryListener: () -> Unit = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        CallSessionRegistry.addListener(registryListener)
        refresh()
    }

    override fun onPause() {
        CallSessionRegistry.removeListener(registryListener)
        super.onPause()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.rgb(10, 10, 14))
        }
        caller = TextView(this).apply { textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        root.addView(caller, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        state = TextView(this).apply { textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0,16,0,32) }
        root.addView(state)
        root.addView(Button(this).apply { text = "ANSWER"; setOnClickListener { call?.takeIf { it.state == Call.STATE_RINGING }?.answer(0) } })
        root.addView(Button(this).apply { text = "MUTE / UNMUTE" })
        root.addView(Button(this).apply { text = "SPEAKER" })
        root.addView(Button(this).apply { text = "END CALL"; setOnClickListener { call?.disconnect() } })
        setContentView(root)
    }

    private fun refresh() {
        call = CallSessionRegistry.primary()
        val current = call
        if (current == null) {
            finish()
            return
        }
        caller.text = current.details?.handle?.schemeSpecificPart ?: "UNKNOWN CALLER"
        state.text = when (current.state) {
            Call.STATE_RINGING -> "INCOMING CALL"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_ACTIVE -> "ACTIVE CALL"
            Call.STATE_HOLDING -> "ON HOLD"
            Call.STATE_DISCONNECTED -> "CALL ENDED"
            else -> "CALL"
        }
    }
}
