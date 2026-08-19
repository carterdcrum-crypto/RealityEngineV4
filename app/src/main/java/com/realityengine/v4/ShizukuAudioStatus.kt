package com.realityengine.v4

import android.content.pm.PackageManager
import moe.shizuku.api.Shizuku

object ShizukuAudioStatus {
    const val REQUEST_CODE = 7001

    fun binderAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun permissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (binderAvailable() && !permissionGranted()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (_: Throwable) {
        }
    }
}
