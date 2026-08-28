package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Horizontal ranked alternatives strip shown beneath the single BEST coach response. */
class ResponseCoachCardsView(context: Context) : HorizontalScrollView(context) {
    private val cards = LinearLayout(context).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(cards, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun render(alternatives: List<LiveResponseEngine.Suggestion>) {
        cards.removeAllViews()
        visibility = if (alternatives.isEmpty()) View.GONE else View.VISIBLE
        alternatives.forEachIndexed { index, suggestion ->
            cards.addView(card(index + 2, suggestion), LinearLayout.LayoutParams(198.dp(), 70.dp()).apply {
                setMargins(0, 3.dp(), 8.dp(), 3.dp())
            })
        }
        scrollTo(0, 0)
    }

    private fun card(rank: Int, suggestion: LiveResponseEngine.Suggestion): View = TextView(context).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        text = buildString {
            append("#$rank  ${suggestion.mode}  ·  ${suggestion.tone}\n")
            append(suggestion.text)
        }
        setTextColor(RealityVisuals.Colors.Text)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 3
        setLineSpacing(1.5f, 1.06f)
        setPadding(11.dp(), 7.dp(), 11.dp(), 7.dp())
        RealityTypography.display(this, 9.8f)
        background = RealityVisuals.panel(
            context,
            fill = when (suggestion.mode) {
                "BOUNDARY", "DIRECT" -> Color.rgb(31, 23, 50)
                "VALIDATE", "BONDING", "DE_ESCALATE" -> Color.rgb(13, 34, 38)
                else -> RealityVisuals.Colors.PanelStrong
            },
            stroke = when (suggestion.mode) {
                "BOUNDARY", "DIRECT" -> RealityVisuals.Colors.Lilac
                "VALIDATE", "BONDING", "DE_ESCALATE" -> RealityVisuals.Colors.Green
                else -> RealityVisuals.Colors.CyanSoft
            },
            radiusDp = 18f,
            strokeDp = 1,
        )
        contentDescription = "Alternative $rank, ${suggestion.mode}: ${suggestion.text}"
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
