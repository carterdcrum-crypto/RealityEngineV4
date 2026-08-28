package com.realityengine.v4

import android.app.Application

/**
 * Application entry point for Reality Engine V4.
 * Presentation observers stay behavior-safe while keeping the visual system, vector icon guard,
 * Conversation OS intelligence layer, and saved-audio discovery available across the app.
 */
class RealityEngineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(RealityOperatorSkin.callbacks)
        registerActivityLifecycleCallbacks(RealityVectorIconGuard.callbacks)
        registerActivityLifecycleCallbacks(ConversationOSOverlay.callbacks)
        registerActivityLifecycleCallbacks(RecordingDiscoveryOverlay.callbacks)
    }
}
