package com.realityengine.v4

import android.app.Application

/**
 * Application entry point for Reality Engine V4.
 * Presentation observers stay behavior-safe while keeping the visual system, vector icon guard,
 * Conversation OS intelligence layer, readable live transcripts, live signal instrument,
 * saved-audio discovery, and mandatory post-call recording review available across the app.
 */
class RealityEngineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(RealityOperatorSkin.callbacks)
        registerActivityLifecycleCallbacks(RealityVectorIconGuard.callbacks)
        registerActivityLifecycleCallbacks(ConversationOSNonCallCallbacks.callbacks)
        registerActivityLifecycleCallbacks(LiveTranscriptLayoutOverlay.callbacks)
        registerActivityLifecycleCallbacks(PulseDeckRuntime.callbacks)
        registerActivityLifecycleCallbacks(RecordingDiscoveryOverlay.callbacks)
        registerActivityLifecycleCallbacks(PostCallReviewHandoff.callbacks)
    }
}
