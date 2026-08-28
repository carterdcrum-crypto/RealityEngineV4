package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
        orientation = VERTICAL
        setPadding(dp(8), dp(7), dp(8), dp(7))
        background = RealityVisuals.panel(context, RealityVisuals.Colors.BackgroundRaised, RealityVisuals.Colors.Border, 10f)

        val tools = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        query.apply {
            hint = "Search live transcript"
            setSingleLine(true)
            setTextColor(RealityVisuals.Colors.Text)
            setHintTextColor(RealityVisuals.Colors.TextDim)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            background = RealityVisuals.panel(context, RealityVisuals.Colors.Panel, RealityVisuals.Colors.Border, 12f)
            setPadding(dp(10), 0, dp(10), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchText = s?.toString().orEmpty().trim()
                    rebuild()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        tools.addView(query, LayoutParams(0, dp(38), 1f))
        resume.apply {
            text = "LIVE ↓"
            visibility = View.GONE
            isAllCaps = false
            setTextColor(RealityVisuals.Colors.Green)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = RealityVisuals.panel(context, RealityVisuals.Colors.Panel, RealityVisuals.Colors.Green, 12f)
            setOnClickListener {
                followLatest = true
                visibility = View.GONE
                scroll.post { scroll.fullScroll(FOCUS_DOWN) }
            }
        }
        tools.addView(resume, LayoutParams(dp(76), dp(38)).apply { setMargins(dp(6), 0, 0, 0) })
        addView(tools, LayoutParams(-1, dp(40)))

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
        addView(scroll, LayoutParams(-1, 0, 1f).apply { setMargins(0, dp(4), 0, 0) })
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
                text = "AWAITING AUDIO STREAM…"
                setTextColor(RealityVisuals.Colors.TextDim)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(dp(7), dp(10), dp(7), dp(10))
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
            orientation = VERTICAL
            gravity = if (callerSide) Gravity.START else Gravity.END
            setPadding(if (callerSide) 0 else dp(34), dp(3), if (callerSide) dp(34) else 0, dp(3))
        }
        val label = TextView(context).apply {
            text = when (entry.isCaller) {
                true -> if (interim) "THEM · listening…" else "THEM"
                false -> if (interim) "YOU · listening…" else "YOU"
                null -> if (interim) "VOICE · listening…" else "VOICE"
            }
            RealityVisuals.styleMicroLabel(this, if (callerSide) RealityVisuals.Colors.Cyan else RealityVisuals.Colors.Green)
        }
        wrap.addView(label)
        val bubble = TextView(context).apply {
            text = entry.text.trim()
            setTextColor(if (interim) Color.rgb(126, 176, 188) else RealityVisuals.Colors.Text)
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setLineSpacing(2f, 1.05f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = RealityVisuals.panel(
                context,
                fill = if (callerSide) Color.rgb(7, 20, 29) else Color.rgb(6, 27, 23),
                stroke = if (interim) RealityVisuals.Colors.TextDim else if (callerSide) RealityVisuals.Colors.Cyan else RealityVisuals.Colors.Green,
                radiusDp = 11f,
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
