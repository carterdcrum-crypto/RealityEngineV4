package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Application entry point for visual-only product skinning.
 *
 * Reality Engine V4's feature code stays inside its existing Activities, services, stores and
 * engines. The application only registers a lifecycle observer that decorates the View tree after
 * each screen has built itself.
 */
class RealityEngineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(RealityOperatorSkin.callbacks)
    }
}
