package com.realityengine.v4

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Full ranked alternatives stack shown beneath the BEST coach response. */
class ResponseCoachCardsView(context: Context) : LinearLayout(context) {
    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    fun render(alternatives: List<LiveResponseEngine.Suggestion>) {
        removeAllViews()
        visibility = if (alternatives.isEmpty()) View.GONE else View.VISIBLE
        alternatives.forEachIndexed { index, suggestion ->
            addView(card(index + 2, suggestion), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 3.dp(), 0, 6.dp())
            })
        }
    }

    private fun card(rank: Int, suggestion: LiveResponseEngine.Suggestion): View = TextView(context).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        text = buildString {
            append("#$rank  ${suggestion.mode.replace('_', ' ')}  ·  ${suggestion.tone}\n")
            append(suggestion.text)
            if (suggestion.reason.isNotBlank()) append("\nWHY // ${suggestion.reason}")
        }
        setTextColor(PulseDeckVisuals.Colors.Text)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 6
        minHeight = 66.dp()
        setLineSpacing(1.5f, 1.06f)
        setPadding(11.dp(), 8.dp(), 11.dp(), 8.dp())
        RealityTypography.display(this, 10.2f)
        val accent = when (suggestion.mode) {
            "BOUNDARY", "DIRECT" -> PulseDeckVisuals.Colors.Amber
            "VALIDATE", "BONDING", "DE_ESCALATE" -> PulseDeckVisuals.Colors.Green
            else -> PulseDeckVisuals.Colors.Cyan
        }
        background = PulseDeckVisuals.panel(
            context,
            start = PulseDeckVisuals.Colors.PanelSoft,
            end = PulseDeckVisuals.Colors.PanelBottom,
            stroke = accent,
            radiusDp = 18f,
            strokeDp = 1,
        )
        contentDescription = "Alternative $rank, ${suggestion.mode}: ${suggestion.text}"
        isClickable = true
        isFocusable = true
        setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Why #$rank ${suggestion.mode}?")
                .setMessage(buildString {
                    append(suggestion.text)
                    append("\n\nWHY\n")
                    append(suggestion.reason.ifBlank { "This is a ranked alternate strategy for the same live caller turn." })
                    append("\n\nTONE · ${suggestion.tone}")
                })
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
