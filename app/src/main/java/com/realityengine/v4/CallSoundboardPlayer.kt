package com.realityengine.v4

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.telecom.CallAudioState

/**
 * Plays a selected sound while a native call is active.
 * Android does not expose a third-party direct uplink injector, so this uses the
 * active call's local speaker route as an acoustic fallback and restores the prior route.
 */
class CallSoundboardPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var restoreRoute: Int? = null

    fun play(entry: SoundboardStore.Entry, onComplete: (() -> Unit)? = null): Boolean {
        stop()
        val service = RealityInCallService.instance ?: return false
        val audio = service.callAudioState
        restoreRoute = audio?.route
        if (audio != null && audio.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) {
            runCatching { service.setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
        }
        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, entry.uri)
                setVolume(1f, 1f)
                setOnCompletionListener {
                    stop()
                    onComplete?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onComplete?.invoke()
                    true
                }
                prepare()
                start()
            }
            true
        } catch (_: Throwable) {
            stop()
            false
        }
    }

    fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun stop() {
        val active = player
        player = null
        runCatching { if (active?.isPlaying == true) active.stop() }
        runCatching { active?.release() }
        val route = restoreRoute
        restoreRoute = null
        if (route != null) {
            val service = RealityInCallService.instance
            val audio = service?.callAudioState
            if (audio != null && audio.supportedRouteMask and route != 0) runCatching { service.setAudioRoute(route) }
        }
    }
}
