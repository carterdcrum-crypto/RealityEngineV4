package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Bubble transcript with interim text, search, bookmarks, and user-controlled auto-scroll. */
class LiveTranscriptPanelView(context: Context) : LinearLayout(context) {
    private val scroll = ScrollView(context)
    private val host = LinearLayout(context)
    private val query = EditText(context)
    private val resume = Button(context)
    private val bookmarkStore = CallBookmarkStore(context)
    private var phoneNumber = ""
    private var current = LiveTranscriptState.State()
    private var followLatest = true
    private var searchText = ""

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = VERTICAL
        setPadding(dp(10), dp(9), dp(10), dp(9))
        background = RealityVisuals.panel(
            context,
            RealityVisuals.Colors.BackgroundRaised,
            RealityVisuals.Colors.Border,
            19f,
        )

        val tools = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        query.apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            hint = "Search live transcript"
            setSingleLine(true)
            setTextColor(RealityVisuals.Colors.Text)
            setHintTextColor(RealityVisuals.Colors.TextDim)
            background = RealityVisuals.panel(
                context,
                RealityVisuals.Colors.Panel,
                RealityVisuals.Colors.Border,
                18f,
            )
            setPadding(dp(12), 0, dp(12), 0)
            RealityTypography.display(this, 11.5f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchText = s?.toString().orEmpty().trim()
                    rebuild()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        tools.addView(query, LayoutParams(0, dp(40), 1f))
        resume.apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = "LIVE ↓"
            visibility = View.GONE
            isAllCaps = false
            setTextColor(RealityVisuals.Colors.Green)
            background = RealityVisuals.panel(
                context,
                Color.rgb(12, 48, 34),
                RealityVisuals.Colors.Green,
                18f,
            )
            RealityTypography.displayMedium(this, 10f)
            setOnClickListener {
                followLatest = true
                visibility = View.GONE
                scroll.post { scroll.fullScroll(FOCUS_DOWN) }
            }
        }
        tools.addView(resume, LayoutParams(dp(78), dp(40)).apply { setMargins(dp(7), 0, 0, 0) })
        addView(tools, LayoutParams(-1, dp(42)))

        host.orientation = VERTICAL
        scroll.apply {
            isFillViewport = true
            addView(host, LayoutParams(-1, -2))
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    followLatest = false
                    resume.visibility = View.VISIBLE
                }
                false
            }
        }
        addView(scroll, LayoutParams(-1, 0, 1f).apply { setMargins(0, dp(6), 0, 0) })
    }

    fun bindPhone(phone: String) {
        phoneNumber = phone
    }

    fun render(snapshot: LiveTranscriptState.State) {
        current = snapshot
        rebuild()
        if (followLatest) scroll.post { scroll.fullScroll(FOCUS_DOWN) }
    }

    private fun rebuild() {
        host.removeAllViews()
        val needle = searchText.lowercase()
        val entries = current.entries.takeLast(120).filter { needle.isBlank() || it.text.lowercase().contains(needle) }
        if (entries.isEmpty() && current.text.isBlank()) {
            host.addView(TextView(context).apply {
                text = "Awaiting audio stream…"
                setTextColor(RealityVisuals.Colors.TextDim)
                setPadding(dp(9), dp(12), dp(9), dp(12))
                RealityTypography.display(this, 11.5f)
            })
        }
        entries.forEach { addBubble(it, interim = false) }
        if (!current.isFinal && current.text.isNotBlank() && (needle.isBlank() || current.text.lowercase().contains(needle))) {
            val duplicate = entries.lastOrNull()?.text?.equals(current.text.trim(), true) == true
            if (!duplicate) addBubble(LiveTranscriptState.Entry(current.text, false, current.updatedAtMs, current.isCaller), interim = true)
        }
    }

    private fun addBubble(entry: LiveTranscriptState.Entry, interim: Boolean) {
        val callerSide = entry.isCaller != false
        val wrap = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = VERTICAL
            gravity = if (callerSide) Gravity.START else Gravity.END
            setPadding(if (callerSide) 0 else dp(38), dp(4), if (callerSide) dp(38) else 0, dp(4))
        }
        val label = TextView(context).apply {
            text = when (entry.isCaller) {
                true -> if (interim) "THEM · listening…" else "THEM"
                false -> if (interim) "YOU · listening…" else "YOU"
                null -> if (interim) "VOICE · listening…" else "VOICE"
            }
            RealityVisuals.styleMicroLabel(
                this,
                if (callerSide) RealityVisuals.Colors.CyanSoft else RealityVisuals.Colors.Lilac,
            )
        }
        wrap.addView(label)
        val bubble = TextView(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = entry.text.trim()
            setTextColor(if (interim) Color.rgb(154, 165, 193) else RealityVisuals.Colors.Text)
            setLineSpacing(2.5f, 1.08f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            RealityTypography.display(this, 12.5f)
            background = RealityVisuals.panel(
                context,
                fill = if (callerSide) Color.rgb(11, 20, 40) else Color.rgb(27, 24, 53),
                stroke = when {
                    interim -> RealityVisuals.Colors.TextDim
                    callerSide -> RealityVisuals.Colors.CyanSoft
                    else -> RealityVisuals.Colors.Lilac
                },
                radiusDp = 18f,
                strokeDp = 1,
            )
            if (!interim) {
                isLongClickable = true
                setOnLongClickListener {
                    bookmarkStore.add(phoneNumber, entry)
                    Toast.makeText(context, "Transcript moment bookmarked", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
        wrap.addView(bubble, LayoutParams(-1, -2))
        host.addView(wrap, LayoutParams(-1, -2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
