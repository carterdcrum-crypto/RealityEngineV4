package com.realityengine.v4

import android.graphics.Typeface
import android.view.View
import android.widget.TextView

/**
 * Reality Engine typography system.
 *
 * Keeps typography decisions centralized so the UI can migrate away from the
 * terminal-style monospace look without scattering font configuration through
 * every Activity. Uses Android's modern system sans families so there is no
 * external font dependency or licensing/build risk.
 */
object RealityTypography {
    private val displayTypeface: Typeface by lazy {
        Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val displayMediumTypeface: Typeface by lazy {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val technicalTypeface: Typeface by lazy {
        Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    fun display(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = displayTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = -0.01f
        includeFontPadding = false
    }

    fun displayMedium(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = displayMediumTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = 0.01f
        includeFontPadding = false
    }

    fun technical(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = technicalTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = 0.08f
        includeFontPadding = false
    }

    fun signal(view: TextView, sizeSp: Float? = null) = view.apply {
        typeface = displayMediumTypeface
        sizeSp?.let { textSize = it }
        letterSpacing = 0.12f
        includeFontPadding = false
        textAlignment = View.TEXT_ALIGNMENT_INHERIT
    }
}
