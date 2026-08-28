package com.realityengine.v4

import android.graphics.Typeface
import android.widget.TextView

/** Central typography primitives for the Lucid Prism visual system. */
object RealityTypography {
    private val displayTypeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val displayMediumTypeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val technicalTypeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    fun display(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = displayTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = -0.008f
        includeFontPadding = false
        isAllCaps = false
    }

    fun displayMedium(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = displayMediumTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = 0f
        includeFontPadding = false
        isAllCaps = false
    }

    fun technical(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = technicalTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = 0.075f
        includeFontPadding = false
        isAllCaps = false
    }

    fun signal(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = displayMediumTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = 0.055f
        includeFontPadding = false
        isAllCaps = false
    }

    /** Converts legacy terminal-styled widgets in-place during the UI migration. */
    fun modernize(view: TextView, technical: Boolean = false) = view.apply {
        typeface = if (technical) technicalTypeface else displayMediumTypeface
        includeFontPadding = false
        isAllCaps = false
        letterSpacing = if (technical) 0.075f else 0f
    }
}
