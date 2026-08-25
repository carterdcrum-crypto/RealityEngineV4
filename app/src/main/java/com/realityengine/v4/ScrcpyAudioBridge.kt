package com.realityengine.v4

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transport boundary for scrcpy-server call audio.
 *
 * This layer deliberately owns lifecycle/configuration only. The next layer
 * supplies the Shizuku shell process and local-abstract socket transport, then
 * forwards raw PCM into the existing transcription pipeline.
 */
class ScrcpyAudioBridge {
    sealed class State {
        data object Idle : State()
        data class Prepared(val scid: Int, val command: List<String>) : State()
        data class Failed(val reason: String) : State()
    }

    private val active = AtomicBoolean(false)
    @Volatile private var currentScid: Int? = null

    fun prepare(): State {
        if (!ShizukuAudioStatus.permissionGranted()) {
            return State.Failed(ShizukuAudioStatus.diagnostic())
        }
        if (!active.compareAndSet(false, true)) {
            return State.Failed("scrcpy audio bridge already active")
        }

        val scid = nextScid()
        currentScid = scid
        return State.Prepared(scid, ScrcpyAudioCaptureConfig.appProcessCommand(scid))
    }

    fun stop() {
        currentScid = null
        active.set(false)
    }

    fun isActive(): Boolean = active.get()

    private fun nextScid(): Int = SecureRandom().nextInt(Int.MAX_VALUE)
}
