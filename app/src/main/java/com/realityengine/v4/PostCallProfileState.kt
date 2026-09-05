package com.realityengine.v4

import android.content.Context
import android.content.Intent

/** One-shot navigation handoff so completed calls land on their caller memory after review. */
object PostCallProfileState {
    data class Pending(val phoneNumber: String, val displayName: String)

    private var pending: Pending? = null

    @Synchronized
    fun queue(phoneNumber: String, displayName: String) {
        val phone = PhoneNumberKey.normalize(phoneNumber).orEmpty()
        if (phone.isBlank()) return
        pending = Pending(phone, displayName.trim().ifBlank { phone })
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    @Synchronized
    fun peek(): Pending? = pending

    fun launchIfPending(context: Context): Boolean {
        val item = synchronized(this) {
            pending ?: return false
        }
        return runCatching {
            context.startActivity(Intent(context, PostCallIntelligenceActivity::class.java).apply {
                putExtra(PostCallIntelligenceActivity.EXTRA_PHONE, item.phoneNumber)
                putExtra(PostCallIntelligenceActivity.EXTRA_NAME, item.displayName)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            synchronized(this) {
                if (pending == item) pending = null
            }
            true
        }.getOrDefault(false)
    }
}
