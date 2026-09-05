package com.realityengine.v4

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuAudioStatus {
    const val REQUEST_CODE = 7001

    enum class State { READY, BINDER_UNAVAILABLE, PERMISSION_REQUIRED, API_ERROR }

    fun state(): State = try {
        if (!Shizuku.pingBinder()) State.BINDER_UNAVAILABLE
        else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) State.PERMISSION_REQUIRED
        else State.READY
    } catch (_: Throwable) {
        State.API_ERROR
    }

    fun binderAvailable(): Boolean = state() != State.BINDER_UNAVAILABLE && state() != State.API_ERROR
    fun permissionGranted(): Boolean = state() == State.READY

    fun diagnostic(): String = when (state()) {
        State.READY -> "Shizuku ready"
        State.BINDER_UNAVAILABLE -> "Shizuku binder unavailable — start Shizuku first"
        State.PERMISSION_REQUIRED -> "Shizuku permission required for Phone"
        State.API_ERROR -> "Shizuku API unavailable"
    }

    fun requestPermission() {
        try {
            if (state() == State.PERMISSION_REQUIRED) Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }
}
