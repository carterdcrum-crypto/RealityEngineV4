package com.realityengine.v4

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Process

/**
 * Attribution context for AudioRecord instances created inside the Shizuku
 * shell-UID UserService. Android 12+ AudioRecord.Builder.setContext() carries
 * this AttributionSource into the platform audio permission checks.
 *
 * Keep this boundary intentionally small: framework/bootstrap workarounds are
 * added separately and can report their own health instead of silently hiding
 * failures here.
 */
class ShellAudioContext(base: Context) : ContextWrapper(base) {
    override fun getPackageName(): String = SHELL_PACKAGE
    override fun getOpPackageName(): String = SHELL_PACKAGE

    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid())
            .setPackageName(SHELL_PACKAGE)
            .build()

    override fun getApplicationContext(): Context = this
    override fun createAttributionContext(attributionTag: String?): Context = this

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"
    }
}
