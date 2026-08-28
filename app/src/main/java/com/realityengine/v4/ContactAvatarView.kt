package com.realityengine.v4

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Reusable Lucid Prism avatar that prefers the Android contact photo and falls back to initials. */
class ContactAvatarView(context: Context) : FrameLayout(context) {
    private val photo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        clipToOutline = true
    }

    private val initials = TextView(context).apply {
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 13.5f
        includeFontPadding = false
    }

    init {
        clipChildren = false
        val inset = dp(2)
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(inset, inset, inset, inset)
        })
        addView(initials, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(inset, inset, inset, inset)
        })
    }

    fun bind(contactId: Long, name: String, accent: Int = RealityVisuals.Colors.Cyan) {
        background = RealityVisuals.circle(
            context,
            fill = RealityVisuals.Colors.PanelStrong,
            stroke = accent,
        )
        val bitmap = ContactMediaStore.loadPhoto(context, contactId)
        if (bitmap != null) {
            photo.setImageBitmap(bitmap)
            photo.visibility = View.VISIBLE
            initials.visibility = View.GONE
        } else {
            photo.setImageDrawable(null)
            photo.visibility = View.GONE
            initials.visibility = View.VISIBLE
            initials.text = initialsFor(name)
            initials.setTextColor(accent)
        }
        contentDescription = if (bitmap != null) "$name contact photo" else "$name initials"
    }

    private fun initialsFor(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "?" else parts.take(2).joinToString("") {
            it.first().uppercaseChar().toString()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
