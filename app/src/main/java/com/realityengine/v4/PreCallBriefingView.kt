package com.realityengine.v4

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Compact memory briefing shown while an incoming/outgoing call is connecting. */
class PreCallBriefingView(context: Context) : LinearLayout(context) {
    private val profiles = CallerProfileStore(context.applicationContext)
    private val title = TextView(context)
    private val body = TextView(context)
    private var boundPhone = ""
    private var boundName = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setPadding(dp(11), dp(8), dp(11), dp(9))
        background = RealityVisuals.panel(context, RealityVisuals.Colors.Panel, RealityVisuals.Colors.Cyan, 10f)
        title.apply {
            text = "PRE-CALL BRIEF"
            RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Cyan)
        }
        body.apply {
            setTextColor(RealityVisuals.Colors.Text)
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setLineSpacing(2f, 1.06f)
        }
        addView(title)
        addView(body, LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, 0) })
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
        title.text = "PRE-CALL BRIEF · ${name.take(28).uppercase()}"
        val lines = buildList {
            if (profile.lastCallSummary.isNotBlank()) add("LAST · ${profile.lastCallSummary.take(190)}")
            profile.unresolvedTopics.lastOrNull()?.let { add("FOLLOW UP · ${it.take(130)}") }
            profile.importantFacts.lastOrNull()?.let { add("REMEMBER · ${it.take(130)}") }
            profile.conversationStarters.lastOrNull()?.let { add("OPENER · ${it.take(130)}") }
            if (profile.preferredConversationStyle.isNotBlank()) add("STYLE · ${profile.preferredConversationStyle.take(110)}")
        }
        body.text = if (lines.isEmpty()) "No caller memory yet. This call can create a first summary." else lines.take(5).joinToString("\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
