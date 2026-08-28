package com.realityengine.v4

import android.app.Application

/**
 * Application entry point for the Reality Engine V4 presentation layer.
 *
 * Feature behavior stays in the existing Activities, services, stores and engines. The lifecycle
 * observers only apply the operator skin and guarantee that functional iconography uses packaged
 * vector drawables rather than OEM-dependent Unicode/emoji glyphs.
 */
class RealityEngineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(RealityOperatorSkin.callbacks)
        registerActivityLifecycleCallbacks(RealityVectorIconGuard.callbacks)
    }
}
