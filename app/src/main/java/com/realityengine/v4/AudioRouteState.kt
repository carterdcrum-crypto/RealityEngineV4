package com.realityengine.v4

import android.content.Context

/** Process-local telemetry for the currently selected live transcription route.
 * Live diagnostics never open competing AudioRecord sources while Shizuku owns call PCM. */
object AudioRouteState {
    data class Snapshot(
        val route: AudioCaptureRouter.Route = AudioCaptureRouter.Route.UNAVAILABLE,
        val reason: String = "Audio route not evaluated",
        val canTranscribe: Boolean = false,
        val diagnostic: String = "",
        val updatedAtMs: Long = 0L
    ) {
        val label: String get() = AudioRoutePresenter.label(this)
        val detail: String get() { val base=AudioRoutePresenter.detail(this);return if(diagnostic.isBlank())base else "$base · ${diagnostic.take(160)}" }
    }
    @Volatile private var current=Snapshot()
    fun publish(decision:AudioCaptureRouter.Decision){current=Snapshot(route=decision.route,reason=decision.reason,canTranscribe=decision.canTranscribe,updatedAtMs=System.currentTimeMillis())}
    fun diagnose(context:Context){
        val binder=ShizukuAudioStatus.binderAvailable();val granted=ShizukuAudioStatus.permissionGranted();val active=CallSessionRegistry.primary()?.state==android.telecom.Call.STATE_ACTIVE
        val previous=current;current=previous.copy(diagnostic=buildString{append(if(active)"Active cellular call" else "Waiting for active call");append(" · SHIZUKU ");append(if(binder&&granted)"OK" else "NO");if(previous.route==AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL)append(" · privileged PCM selected")},updatedAtMs=System.currentTimeMillis())
    }
    fun snapshot():Snapshot=current
    fun clear(){current=Snapshot()}
}
