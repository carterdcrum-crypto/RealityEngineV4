package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

/** Makes saved call audio and transcripts discoverable from the contact/recent surfaces users already visit. */
object RecordingDiscoveryOverlay {
    private const val TAG_PROFILE_AUDIO = "reality.recordings.profile.audio"
    private const val TAG_PROFILE_TRANSCRIPTS = "reality.transcripts.profile.history"
    private const val TAG_CONTACT_AUDIO = "reality.recordings.contact.audio"
    private const val RECENT_AUDIO_MARKER = "AUDIO ·"
    private const val RECENT_TRANSCRIPT_MARKER = "TRANSCRIPT ·"
    private const val CONTACT_AUDIO_MARKER = "AUDIO "

    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            if (activity !is MainActivity) return
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

    private class Session(private val activity: MainActivity) {
        private var listener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var queued = false

        fun resume() {
            attach()
            queue()
        }

        fun destroy() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val current = listener
            if (current != null && content?.viewTreeObserver?.isAlive == true) {
                content.viewTreeObserver.removeOnGlobalLayoutListener(current)
            }
            listener = null
        }

        private fun attach() {
            if (listener != null) return
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            listener = ViewTreeObserver.OnGlobalLayoutListener { queue() }.also {
                content.viewTreeObserver.addOnGlobalLayoutListener(it)
            }
        }

        private fun queue() {
            if (queued) return
            queued = true
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            content.post {
                queued = false
                if (activity.isFinishing || activity.isDestroyed) return@post
                decorateRecentRows(content)
                decorateProfile(content)
                decorateContactRows(content)
            }
        }

        private fun decorateRecentRows(root: ViewGroup) {
            if (findExactText(root, "Traffic") == null) return
            allButtons(root).forEach { button ->
                val raw = button.text?.toString().orEmpty()
                if (!raw.contains('•')) return@forEach
                val phone = recentPhone(raw) ?: return@forEach
                var decorated = raw

                val audioCount = CallRecordingStore.savedCountFor(activity, phone)
                if (audioCount > 0 && !decorated.contains(RECENT_AUDIO_MARKER)) {
                    decorated += "\n$RECENT_AUDIO_MARKER $audioCount SAVED"
                }

                val transcriptCount = CallTranscriptStore.savedFor(activity, phone).size
                if (transcriptCount > 0 && !decorated.contains(RECENT_TRANSCRIPT_MARKER)) {
                    decorated += "\n$RECENT_TRANSCRIPT_MARKER $transcriptCount SAVED"
                }

                if (decorated != raw) {
                    button.text = decorated
                    button.minHeight = maxOf(button.minHeight, dp(if (audioCount > 0 && transcriptCount > 0) 116 else 94))
                }
            }
        }

        private fun decorateProfile(root: ViewGroup) {
            val numberView = allTextViews(root).firstOrNull { it.text?.toString()?.startsWith("NUMBER\n") == true } ?: return
            val parent = numberView.parent as? LinearLayout ?: return
            val phone = numberView.text.toString().substringAfter('\n').trim()
            val name = ContactMediaStore.findByNumber(activity, phone)?.name.orEmpty()
            var insertIndex = parent.indexOfChild(numberView) + 1

            val transcriptCount = CallTranscriptStore.savedFor(activity, phone).size
            if (transcriptCount > 0 && parent.findViewWithTag<View>(TAG_PROFILE_TRANSCRIPTS) == null) {
                val transcriptButton = Button(activity).apply {
                    tag = TAG_PROFILE_TRANSCRIPTS
                    text = "Call transcripts · $transcriptCount"
                    gravity = Gravity.CENTER
                    contentDescription = "Open $transcriptCount saved transcript${if (transcriptCount == 1) "" else "s"} for this caller"
                    RealityVisuals.styleControl(this, R.drawable.ic_re_intel, RealityVisuals.Colors.Cyan, radiusDp = 20f)
                    setOnClickListener { openTranscripts(phone, name) }
                }
                parent.addView(transcriptButton, insertIndex, LinearLayout.LayoutParams(-1, dp(52)).apply {
                    setMargins(0, dp(5), 0, dp(6))
                })
                insertIndex += 1
            } else if (parent.findViewWithTag<View>(TAG_PROFILE_TRANSCRIPTS) != null) {
                insertIndex += 1
            }

            val audioCount = CallRecordingStore.savedCountFor(activity, phone)
            if (audioCount > 0 && parent.findViewWithTag<View>(TAG_PROFILE_AUDIO) == null) {
                val audioButton = Button(activity).apply {
                    tag = TAG_PROFILE_AUDIO
                    text = "Saved audio · $audioCount"
                    gravity = Gravity.CENTER
                    RealityVisuals.styleControl(this, R.drawable.ic_re_record, RealityVisuals.Colors.Lilac, radiusDp = 20f)
                    setOnClickListener { openRecordings(phone, name) }
                }
                parent.addView(audioButton, insertIndex, LinearLayout.LayoutParams(-1, dp(52)).apply {
                    setMargins(0, dp(5), 0, dp(6))
                })
            }
        }

        private fun decorateContactRows(root: ViewGroup) {
            allLinearLayouts(root).forEach { row ->
                if (row.findViewWithTag<View>(TAG_CONTACT_AUDIO) != null) return@forEach
                val phoneView = directIdentityPhoneView(row) ?: return@forEach
                val raw = phoneView.text?.toString().orEmpty()
                val phone = raw.substringBefore('·').trim()
                if (phone.filter(Char::isDigit).length < 7) return@forEach
                val count = CallRecordingStore.savedCountFor(activity, phone)
                if (count <= 0) return@forEach

                if (!raw.contains(CONTACT_AUDIO_MARKER)) {
                    phoneView.text = "$raw  ·  AUDIO $count"
                }

                val textButtonIndex = (0 until row.childCount).firstOrNull { index ->
                    (row.getChildAt(index) as? Button)?.text?.toString() == "TXT"
                } ?: return@forEach

                val label = ContactMediaStore.findByNumber(activity, phone)?.name.orEmpty()
                val audio = Button(activity).apply {
                    tag = TAG_CONTACT_AUDIO
                    text = "REC"
                    minWidth = 0
                    minHeight = 0
                    contentDescription = "Open $count saved recording${if (count == 1) "" else "s"}"
                    RealityVisuals.styleControl(this, R.drawable.ic_re_record, RealityVisuals.Colors.Lilac, radiusDp = 19f)
                    setPadding(dp(1), 0, dp(1), 0)
                    setOnClickListener { openRecordings(phone, label) }
                }
                row.addView(audio, textButtonIndex, LinearLayout.LayoutParams(dp(50), dp(42)).apply {
                    setMargins(0, 0, dp(7), 0)
                })
            }
        }

        private fun directIdentityPhoneView(row: LinearLayout): TextView? {
            for (i in 0 until row.childCount) {
                val identity = row.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until identity.childCount) {
                    val text = identity.getChildAt(j) as? TextView ?: continue
                    val raw = text.text?.toString().orEmpty().trim()
                    val first = raw.substringBefore('·').trim()
                    val digitCount = first.count(Char::isDigit)
                    if (digitCount >= 7 && first.all { it.isDigit() || it in "+-(). " }) return text
                }
            }
            return null
        }

        private fun recentPhone(raw: String): String? {
            val lines = raw.lines()
            if (lines.size < 2) return null
            val pieces = lines[1].split('•').map(String::trim)
            return pieces.getOrNull(1)?.takeIf { it.count(Char::isDigit) >= 7 }
        }

        private fun openRecordings(phone: String, name: String) {
            activity.startActivity(Intent(activity, SavedRecordingsActivity::class.java).apply {
                putExtra(SavedRecordingsActivity.EXTRA_PHONE, phone)
                putExtra(SavedRecordingsActivity.EXTRA_NAME, name)
            })
        }

        private fun openTranscripts(phone: String, name: String) {
            activity.startActivity(Intent(activity, TranscriptLibraryActivity::class.java).apply {
                putExtra(TranscriptLibraryActivity.EXTRA_PHONE, phone)
                putExtra(TranscriptLibraryActivity.EXTRA_NAME, name)
            })
        }
    }

    private fun findExactText(root: View, exact: String): TextView? {
        if (root is TextView && root.text?.toString() == exact) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findExactText(root.getChildAt(i), exact)?.let { return it }
        }
        return null
    }

    private fun allButtons(root: View): List<Button> = buildList { collect(root, this) }
    private fun collect(root: View, out: MutableList<Button>) {
        if (root is Button) out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) collect(root.getChildAt(i), out)
    }

    private fun allTextViews(root: View): List<TextView> = buildList { collectText(root, this) }
    private fun collectText(root: View, out: MutableList<TextView>) {
        if (root is TextView) out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) collectText(root.getChildAt(i), out)
    }

    private fun allLinearLayouts(root: View): List<LinearLayout> = buildList { collectLayouts(root, this) }
    private fun collectLayouts(root: View, out: MutableList<LinearLayout>) {
        if (root is LinearLayout) out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) collectLayouts(root.getChildAt(i), out)
    }

    private fun dp(value: Int): Int = (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
