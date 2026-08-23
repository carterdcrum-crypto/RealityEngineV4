package com.realityengine.v4

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SignalHaptics(context: Context) {
    private val app = context.applicationContext
    private val settings = SettingsStore(app)
    private var armed = true

    fun update(acoustic: Int, linguistic: Int, factual: Int) {
        val allElevated = acoustic >= THRESHOLD && linguistic >= THRESHOLD && factual >= THRESHOLD
        if (!allElevated) {
            armed = true
            return
        }
        if (!armed || !settings.hapticsEnabled) return
        armed = false
        pulse()
    }

    private fun pulse() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0L, 70L, 90L, 70L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    companion object { private const val THRESHOLD = 70 }
}
