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

/** Reusable circular avatar that prefers the Android contact photo and falls back to initials. */
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
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 14f
    }

    init {
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(initials, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(contactId: Long, name: String, accent: Int = RealityVisuals.Colors.Cyan) {
        val bitmap = ContactMediaStore.loadPhoto(context, contactId)
        if (bitmap != null) {
            photo.setImageBitmap(bitmap)
            photo.visibility = View.VISIBLE
            initials.visibility = View.GONE
            background = RealityVisuals.circle(context, fill = RealityVisuals.Colors.PanelStrong, stroke = accent)
        } else {
            photo.setImageDrawable(null)
            photo.visibility = View.GONE
            initials.visibility = View.VISIBLE
            initials.text = initialsFor(name)
            initials.setTextColor(accent)
            background = RealityVisuals.circle(context, fill = RealityVisuals.Colors.PanelStrong, stroke = accent)
        }
        contentDescription = if (bitmap != null) "$name contact photo" else "$name initials"
    }

    private fun initialsFor(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "?" else parts.take(2).joinToString("") {
            it.first().uppercaseChar().toString()
        }
    }
}
