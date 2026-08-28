package com.realityengine.v4

import android.graphics.Typeface
import android.widget.TextView
import java.io.File

/** Central typography primitives for the Lucid Prism visual system. */
object RealityTypography {
    // Samsung font themes can remap generic family names. Prefer the actual platform font files so
    // Reality Engine keeps its intended clean UI typography, then fall back safely on other devices.
    private val displayTypeface by lazy {
        platformTypeface(
            paths = listOf(
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/RobotoFlex-Regular.ttf",
                "/system/fonts/NotoSans-Regular.ttf",
            ),
            fallbackFamily = "sans-serif",
            fallbackStyle = Typeface.NORMAL,
        )
    }
    private val displayMediumTypeface by lazy {
        platformTypeface(
            paths = listOf(
                "/system/fonts/Roboto-Medium.ttf",
                "/system/fonts/RobotoFlex-Regular.ttf",
                "/system/fonts/NotoSans-Medium.ttf",
                "/system/fonts/NotoSans-Regular.ttf",
            ),
            fallbackFamily = "sans-serif-medium",
            fallbackStyle = Typeface.NORMAL,
        )
    }
    private val technicalTypeface by lazy { displayMediumTypeface }

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

    private fun platformTypeface(
        paths: List<String>,
        fallbackFamily: String,
        fallbackStyle: Int,
    ): Typeface {
        for (path in paths) {
            val file = File(path)
            if (!file.isFile || !file.canRead()) continue
            runCatching { Typeface.createFromFile(file) }.getOrNull()?.let { return it }
        }
        return Typeface.create(fallbackFamily, fallbackStyle)
    }
}
