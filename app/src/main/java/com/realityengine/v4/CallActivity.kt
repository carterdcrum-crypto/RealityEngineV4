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

        caller = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(caller, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        state = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }
        root.addView(state)

        val answer = Button(this).apply {
            text = "ANSWER"
            setOnClickListener { call?.answer(0) }
        }
        root.addView(answer)

        val mute = Button(this).apply {
            text = "MUTE / UNMUTE"
            setOnClickListener {
                val service = getSystemService(InCallService::class.java)
                if (service != null) {
                    // The system InCallService instance controls mute; this activity
                    // intentionally does not assume it owns the service object.
                }
            }
        }
        root.addView(mute)

        val speaker = Button(this).apply {
            text = "SPEAKER"
            setOnClickListener {
                // Audio routing will be wired through the InCallService in the next
                // telephony step; this button is intentionally non-destructive here.
            }
        }
        root.addView(speaker)

        val end = Button(this).apply {
            text = "END CALL"
            setOnClickListener { call?.disconnect() }
        }
        root.addView(end)

        setContentView(root)
    }

    private fun refresh() {
        call = CallSessionRegistry.primary()
        val current = call
        if (current == null) {
            caller.text = "NO ACTIVE CALL"
            state.text = "Reality Engine is ready for a phone call."
            return
        }

        val handle = current.details?.handle
        caller.text = handle?.schemeSpecificPart ?: "UNKNOWN CALLER"
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
