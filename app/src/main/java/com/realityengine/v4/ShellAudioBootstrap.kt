package com.realityengine.v4

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process

/**
 * Best-effort framework bootstrap for the Shizuku shell-UID audio process.
 *
 * This is intentionally isolated from capture so we can diagnose Android/OEM
 * framework compatibility independently of AudioRecord source behavior.
 */
internal object ShellAudioBootstrap {
    enum class Health { FAILED, DEGRADED, FULL }

    data class Result(
        val context: Context,
        val health: Health,
        val appliedPatches: Int,
        val expectedPatches: Int = 4,
    )

    fun install(base: Context): Result {
        val shellContext = ShellAudioContext(base)
        var applied = 0

        runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val thread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                ?: activityThreadClass.getMethod("systemMain").invoke(null)

            runCatching {
                activityThreadClass.getDeclaredField("sCurrentActivityThread").apply {
                    isAccessible = true
                }.set(null, thread)
                applied++
            }

            runCatching {
                activityThreadClass.getDeclaredField("mSystemThread").apply {
                    isAccessible = true
                }.setBoolean(thread, true)
                applied++
            }

            val shellApplication = runCatching {
                Instrumentation.newApplication(Application::class.java, shellContext)
            }.getOrNull()
            if (shellApplication != null) {
                runCatching {
                    activityThreadClass.getDeclaredField("mInitialApplication").apply {
                        isAccessible = true
                    }.set(thread, shellApplication)
                    applied++
                }
            }

            runCatching {
                val boundField = activityThreadClass.getDeclaredField("mBoundApplication").apply {
                    isAccessible = true
                }
                val bindDataClass = Class.forName("android.app.ActivityThread\$AppBindData")
                val bound = boundField.get(thread) ?: bindDataClass.getDeclaredConstructor().apply {
                    isAccessible = true
                }.newInstance().also { boundField.set(thread, it) }
                bindDataClass.getDeclaredField("appInfo").apply {
                    isAccessible = true
                }.set(bound, ApplicationInfo().apply {
                    packageName = ShellAudioContext.SHELL_PACKAGE
                    uid = Process.myUid()
                })
                applied++
            }
        }

        val health = when (applied) {
            4 -> Health.FULL
            0 -> Health.FAILED
            else -> Health.DEGRADED
        }
        return Result(shellContext, health, applied)
    }
}
