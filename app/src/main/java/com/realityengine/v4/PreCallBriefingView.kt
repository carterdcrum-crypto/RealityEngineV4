package com.realityengine.v4

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** Smart pre-call briefing shown while an incoming/outgoing call is connecting. */
class PreCallBriefingView(context: Context) : LinearLayout(context) {
    private val profiles = CallerProfileStore(context.applicationContext)
    private val objectives = ConversationObjectiveStore(context.applicationContext)
    private val title = TextView(context)
    private val body = TextView(context)
    private var boundPhone = ""
    private var boundName = ""

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = VERTICAL
        gravity = Gravity.START
        setPadding(dp(13), dp(10), dp(13), dp(11))
        background = RealityVisuals.panel(context, RealityVisuals.Colors.PanelStrong, RealityVisuals.Colors.Lilac, 18f)
        title.apply {
            text = "PRE-CALL BRIEF"
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Lilac)
        }
        body.apply {
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.display(this, 11.5f)
            setLineSpacing(2f, 1.07f)
        }
        addView(title)
        addView(body, LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, 0) })
    }

    fun bind(phoneNumber: String, displayName: String = "") {
        if (phoneNumber == boundPhone && displayName == boundName) return
        boundPhone = phoneNumber
        boundName = displayName
        refresh()
    }

    fun refresh() {
        val profile = profiles.load(boundPhone)
        val name = profile.displayName.ifBlank { boundName.ifBlank { boundPhone } }
        val history = CallTranscriptStore.savedFor(context, boundPhone)
        val objective = objectives.get(boundPhone)
        title.text = "PRE-CALL BRIEF · ${name.take(28).uppercase()}"
        val lines = buildList {
            if (objective != ConversationObjectiveStore.DEFAULT) add("OBJECTIVE · $objective")
            history.firstOrNull()?.let {
                val whenText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.timestampMs))
                add("HISTORY · ${history.size} saved call${if (history.size == 1) "" else "s"} · last $whenText")
            }
            if (profile.lastCallSummary.isNotBlank()) add("LAST · ${profile.lastCallSummary.take(190)}")
            profile.unresolvedTopics.lastOrNull()?.let { add("FOLLOW UP · ${it.take(130)}") }
            profile.importantFacts.lastOrNull()?.let { add("REMEMBER · ${it.take(130)}") }
            profile.conversationStarters.lastOrNull()?.let { add("OPENER · ${it.take(130)}") }
            if (profile.preferredConversationStyle.isNotBlank()) add("STYLE · ${profile.preferredConversationStyle.take(110)}")
        }
        body.text = if (lines.isEmpty()) "First call with no saved context yet. Phone will build a private conversation history from this call." else lines.take(6).joinToString("\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
