package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Owns the user-facing live translation control without touching telephony, audio, STT, or call state.
 * Translation starts OFF for every new Telecom Call object. The user explicitly enables it and
 * chooses a supported language from the in-call Translate button.
 */
object TranslationControlOverlay {
    private const val TRANSLATION_TAG = "reality.conversation.translation"
    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())
    private var initializedCallIdentity: Int? = null

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            if (activity !is CallActivity) return
            val session = synchronized(sessions) {
                sessions[activity] ?: Session(activity).also { sessions[activity] = it }
            }
            session.resume()
        }
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            sessions.remove(activity)?.destroy()
        }
    }

    private class Session(private val activity: CallActivity) {
        private val translation = ConversationTranslationStore(activity)
        private var listener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var queued = false

        fun resume() {
            resetForNewCallIfNeeded()
            attach()
            queue()
        }

        fun destroy() {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val current = listener
            if (current != null && root?.viewTreeObserver?.isAlive == true) {
                root.viewTreeObserver.removeOnGlobalLayoutListener(current)
            }
            listener = null
        }

        private fun resetForNewCallIfNeeded() {
            val call = CallSessionRegistry.primary() ?: return
            val identity = System.identityHashCode(call)
            if (initializedCallIdentity != identity) {
                initializedCallIdentity = identity
                translation.enabled = false
            }
        }

        private fun attach() {
            if (listener != null) return
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            listener = ViewTreeObserver.OnGlobalLayoutListener {
                resetForNewCallIfNeeded()
                queue()
            }.also { root.viewTreeObserver.addOnGlobalLayoutListener(it) }
        }

        private fun queue() {
            if (queued) return
            queued = true
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            root.post {
                queued = false
                if (activity.isFinishing || activity.isDestroyed) return@post
                installControl(root)
            }
        }

        private fun installControl(root: ViewGroup) {
            val button = findTranslateButton(root) ?: return
            renderButton(button)
            button.setOnClickListener { showLanguagePicker(button) }
            syncStrip(root)
        }

        private fun showLanguagePicker(button: Button) {
            val pairs = ConversationTranslationStore.PAIRS
            val labels = listOf("Off") + pairs.map(::languageName)
            val selected = if (!translation.enabled) {
                0
            } else {
                pairs.indexOf(translation.pair).coerceAtLeast(0) + 1
            }

            AlertDialog.Builder(activity)
                .setTitle("Translate call")
                .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                    if (which == 0) {
                        translation.enabled = false
                    } else {
                        translation.pair = pairs[which - 1]
                        translation.enabled = true
                    }
                    renderButton(button)
                    syncStrip(activity.findViewById(android.R.id.content))
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun renderButton(button: Button) {
            if (translation.enabled) {
                button.text = "Translate · ${languageCode(translation.pair)}"
                button.setTextColor(RealityVisuals.Colors.Green)
                button.contentDescription = "Translation on: ${languageName(translation.pair)}"
            } else {
                button.text = "Translate"
                button.setTextColor(RealityVisuals.Colors.CyanSoft)
                button.contentDescription = "Turn on live translation"
            }
        }

        private fun syncStrip(root: ViewGroup?) {
            root ?: return
            val strip = root.findViewWithTag<TextView>(TRANSLATION_TAG) ?: return
            if (!translation.enabled) {
                strip.visibility = View.GONE
                strip.text = ""
            } else if (strip.text.isNullOrBlank()) {
                strip.visibility = View.VISIBLE
                strip.text = "TRANSLATION ON · ${languageName(translation.pair)}"
            }
        }
    }

    private fun languageName(pair: String): String = when {
        pair.contains("Spanish", ignoreCase = true) -> "Spanish"
        pair.contains("French", ignoreCase = true) -> "French"
        pair.contains("German", ignoreCase = true) -> "German"
        pair.contains("Portuguese", ignoreCase = true) -> "Portuguese"
        pair.contains("Italian", ignoreCase = true) -> "Italian"
        else -> pair.substringAfter("↔").trim().ifBlank { pair }
    }

    private fun languageCode(pair: String): String = when (languageName(pair)) {
        "Spanish" -> "ES"
        "French" -> "FR"
        "German" -> "DE"
        "Portuguese" -> "PT"
        "Italian" -> "IT"
        else -> "ON"
    }

    private fun findTranslateButton(root: View): Button? {
        if (root is Button && root.text?.toString()?.startsWith("Translate", ignoreCase = true) == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTranslateButton(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
