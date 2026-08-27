package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Horizontal ranked alternatives strip shown beneath the single BEST coach response. */
class ResponseCoachCardsView(context: Context) : HorizontalScrollView(context) {
    private val cards = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(cards, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun render(alternatives: List<LiveResponseEngine.Suggestion>) {
        cards.removeAllViews()
        visibility = if (alternatives.isEmpty()) View.GONE else View.VISIBLE
        alternatives.forEachIndexed { index, suggestion ->
            cards.addView(card(index + 2, suggestion), LinearLayout.LayoutParams(190.dp(), 64.dp()).apply {
                setMargins(0, 2.dp(), 7.dp(), 2.dp())
            })
        }
        scrollTo(0, 0)
    }

    private fun card(rank: Int, suggestion: LiveResponseEngine.Suggestion): View = TextView(context).apply {
        text = buildString {
            append("#$rank  ${suggestion.mode}  ·  ${suggestion.tone}\n")
            append(suggestion.text)
        }
        textSize = 9.5f
        setTextColor(RealityVisuals.Colors.Text)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 3
        setPadding(9.dp(), 5.dp(), 9.dp(), 5.dp())
        background = RealityVisuals.panel(
            context,
            fill = RealityVisuals.Colors.PanelStrong,
            stroke = when (suggestion.mode) {
                "BOUNDARY", "DIRECT" -> RealityVisuals.Colors.Magenta
                "VALIDATE", "BONDING", "DE_ESCALATE" -> RealityVisuals.Colors.Green
                else -> Color.rgb(25, 103, 126)
            },
            radiusDp = 9f,
        )
        contentDescription = "Alternative $rank, ${suggestion.mode}: ${suggestion.text}"
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
