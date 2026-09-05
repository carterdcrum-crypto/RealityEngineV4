package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * Pulse Deck transcript surface.
 *
 * The default focus view leads with the latest confirmed caller turn and keeps the user's latest
 * turn directly beneath it. Tapping the caller timestamp opens the complete searchable transcript;
 * long-pressing any finalized bubble keeps the existing bookmark behavior.
 */
class LiveTranscriptPanelView(context: Context) : LinearLayout(context) {
    private val tools = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val host = LinearLayout(context)
    private val query = EditText(context)
    private val closeHistory = Button(context)
    private val bookmarkStore = CallBookmarkStore(context)
    private var phoneNumber = ""
    private var current = LiveTranscriptState.State()
    private var followLatest = true
    private var historyMode = false
    private var searchText = ""
    private var primaryCardKey = ""
    private var primaryCardScrollY = 0
    private var youCardKey = ""
    private var youCardScrollY = 0

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = VERTICAL
        setPadding(dp(9), dp(8), dp(9), dp(8))
        background = PulseDeckVisuals.panel(context, radiusDp = 18f)

        tools.apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        query.apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            hint = "Search full transcript"
            setSingleLine(true)
            setTextColor(PulseDeckVisuals.Colors.Text)
            setHintTextColor(PulseDeckVisuals.Colors.TextDim)
            background = PulseDeckVisuals.panel(
                context,
                start = PulseDeckVisuals.Colors.PanelSoft,
                end = PulseDeckVisuals.Colors.PanelBottom,
                radiusDp = 14f,
            )
            setPadding(dp(11), 0, dp(11), 0)
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
        tools.addView(query, LayoutParams(0, dp(38), 1f))
        closeHistory.apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = "DONE"
            setTextColor(PulseDeckVisuals.Colors.Cyan)
            background = PulseDeckVisuals.panel(
                context,
                start = PulseDeckVisuals.Colors.PanelSoft,
                end = PulseDeckVisuals.Colors.PanelBottom,
                stroke = PulseDeckVisuals.Colors.Cyan,
                radiusDp = 14f,
            )
            RealityTypography.displayMedium(this, 9.5f)
            setOnClickListener { showFocus() }
        }
        tools.addView(closeHistory, LayoutParams(dp(68), dp(38)).apply { setMargins(dp(7), 0, 0, 0) })
        addView(tools, LayoutParams(-1, dp(40)))

        host.orientation = VERTICAL
        scroll.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(host, LayoutParams(-1, -2))
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) followLatest = false
                false
            }
        }
        addView(scroll, LayoutParams(-1, 0, 1f).apply { setMargins(0, dp(3), 0, 0) })
    }

    fun bindPhone(phone: String) {
        if (phoneNumber != phone) {
            primaryCardKey = ""
            primaryCardScrollY = 0
            youCardKey = ""
            youCardScrollY = 0
        }
        phoneNumber = phone
    }

    fun render(snapshot: LiveTranscriptState.State) {
        current = snapshot
        rebuild()
        if (followLatest && historyMode) scroll.post { scroll.fullScroll(FOCUS_DOWN) }
    }

    private fun rebuild() {
        host.removeAllViews()
        if (historyMode) rebuildHistory() else rebuildFocus()
    }

    private fun rebuildFocus() {
        val entries = focusEntries()
        val latestCaller = entries.lastOrNull { it.isCaller == true }
        val latestMine = entries.lastOrNull { it.isCaller == false }
        val latestUnknown = entries.lastOrNull { it.isCaller == null }

        when {
            latestCaller != null -> {
                addFocusBubble(
                    latestCaller,
                    label = "THEM",
                    primary = true,
                    scrollKey = "THEM:${entries.count { it.isCaller == true }}",
                )
                addWaveform(latestCaller)
            }
            latestUnknown != null -> {
                addFocusBubble(
                    latestUnknown,
                    label = "VOICE",
                    primary = true,
                    scrollKey = "VOICE:${entries.count { it.isCaller == null }}",
                )
                addWaveform(latestUnknown)
            }
            else -> addWaitingState()
        }

        if (latestMine != null) {
            addFocusBubble(
                latestMine,
                label = "YOU",
                primary = false,
                scrollKey = "YOU:${entries.count { it.isCaller == false }}",
            )
        } else {
            addFocusPlaceholder("YOU", "Waiting for your reply…", primary = false)
        }
    }

    private fun rebuildHistory() {
        val needle = searchText.lowercase()
        val entries = focusEntries()
            .takeLast(120)
            .filter { needle.isBlank() || it.text.lowercase().contains(needle) }
        if (entries.isEmpty()) {
            host.addView(TextView(context).apply {
                text = if (needle.isBlank()) "Awaiting audio stream…" else "No transcript matches"
                setTextColor(PulseDeckVisuals.Colors.TextDim)
                setPadding(dp(9), dp(12), dp(9), dp(12))
                RealityTypography.display(this, 11.5f)
            })
            return
        }
        entries.forEach { entry ->
            val label = when (entry.isCaller) {
                true -> "THEM"
                false -> "YOU"
                null -> "VOICE"
            }
            addHistoryBubble(entry, label)
        }
    }

    private fun focusEntries(): List<LiveTranscriptState.Entry> {
        val entries = current.entries.toMutableList()
        if (!current.isFinal && current.text.isNotBlank()) {
            val duplicate = entries.lastOrNull()?.let {
                it.text.equals(current.text.trim(), ignoreCase = true) && it.isCaller == current.isCaller
            } == true
            if (!duplicate) {
                entries += LiveTranscriptState.Entry(
                    text = current.text,
                    isFinal = false,
                    updatedAtMs = current.updatedAtMs,
                    isCaller = current.isCaller,
                )
            }
        }
        return entries
    }

    private fun addFocusBubble(
        entry: LiveTranscriptState.Entry,
        label: String,
        primary: Boolean,
        scrollKey: String,
    ) {
        val card = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = VERTICAL
            setPadding(dp(12), if (primary) dp(9) else dp(8), dp(12), if (primary) dp(10) else dp(8))
            background = PulseDeckVisuals.panel(
                context,
                start = if (primary) Color.rgb(14, 35, 43) else Color.rgb(21, 39, 48),
                end = if (primary) Color.rgb(8, 24, 31) else Color.rgb(14, 30, 38),
                stroke = if (primary) PulseDeckVisuals.Colors.Border else Color.rgb(42, 65, 75),
                radiusDp = if (primary) 15f else 13f,
            )
        }
        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(context).apply {
            text = if (!entry.isFinal) "$label · LISTENING" else label
            setTextColor(if (primary) PulseDeckVisuals.Colors.Cyan else PulseDeckVisuals.Colors.Green)
            RealityTypography.displayMedium(this, if (primary) 10.5f else 9.5f)
            letterSpacing = .06f
        }, LayoutParams(0, dp(22), 1f))
        heading.addView(TextView(context).apply {
            text = time(entry.updatedAtMs)
            setTextColor(PulseDeckVisuals.Colors.TextDim)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            RealityTypography.display(this, 9f)
            isClickable = primary
            isFocusable = primary
            contentDescription = if (primary) "Open full searchable transcript" else null
            if (primary) setOnClickListener { showHistory() }
        }, LayoutParams(dp(58), dp(22)))
        card.addView(heading)
        card.addView(TextView(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = entry.text.trim()
            setTextColor(if (entry.isFinal) PulseDeckVisuals.Colors.Text else PulseDeckVisuals.Colors.TextDim)
            setLineSpacing(dp(1).toFloat(), if (primary) 1.04f else 1.02f)
            RealityTypography.displayMedium(this, if (primary) 18f else 13.5f)
            setPadding(0, 0, dp(5), 0)
            enableCardScrolling(primary, scrollKey)
            if (entry.isFinal) installBookmark(entry)
        }, LayoutParams(-1, dp(36)))
        host.addView(card, LayoutParams(-1, -2).apply {
            setMargins(0, if (primary) 0 else dp(5), 0, if (primary) dp(4) else 0)
        })
    }

    private fun addWaveform(entry: LiveTranscriptState.Entry) {
        host.addView(PulseDeckWaveformView(context).apply {
            render(
                isActive = !entry.isFinal && entry.isCaller != false,
                acoustic = LiveSignalState.snapshot().acoustic,
                timestampMs = entry.updatedAtMs,
            )
        }, LayoutParams(-1, dp(20)).apply { setMargins(dp(5), 0, dp(5), dp(2)) })
    }

    private fun addHistoryBubble(entry: LiveTranscriptState.Entry, label: String) {
        val caller = entry.isCaller == true
        val unknown = entry.isCaller == null
        val wrap = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = VERTICAL
            gravity = if (entry.isCaller == false) Gravity.END else Gravity.START
            setPadding(if (entry.isCaller == false) dp(32) else 0, dp(3), if (entry.isCaller == false) 0 else dp(32), dp(3))
        }
        val heading = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(context).apply {
            text = if (!entry.isFinal) "$label · LISTENING" else label
            setTextColor(
                when {
                    caller -> PulseDeckVisuals.Colors.Cyan
                    unknown -> PulseDeckVisuals.Colors.CyanDim
                    else -> PulseDeckVisuals.Colors.TextDim
                },
            )
            RealityTypography.displayMedium(this, 8.8f)
        }, LayoutParams(0, dp(19), 1f))
        heading.addView(TextView(context).apply {
            text = time(entry.updatedAtMs)
            setTextColor(PulseDeckVisuals.Colors.TextDim)
            gravity = Gravity.END
            RealityTypography.display(this, 8.4f)
        }, LayoutParams(dp(54), dp(19)))
        wrap.addView(heading)
        wrap.addView(TextView(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = entry.text.trim()
            setTextColor(if (entry.isFinal) PulseDeckVisuals.Colors.Text else PulseDeckVisuals.Colors.TextDim)
            setLineSpacing(dp(1).toFloat(), 1.04f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            RealityTypography.display(this, 11.5f)
            background = PulseDeckVisuals.panel(
                context,
                start = if (entry.isCaller == false) Color.rgb(20, 37, 46) else Color.rgb(12, 31, 39),
                end = PulseDeckVisuals.Colors.PanelBottom,
                stroke = if (caller) PulseDeckVisuals.Colors.CyanDim else PulseDeckVisuals.Colors.Border,
                radiusDp = 13f,
            )
            if (entry.isFinal) installBookmark(entry)
        }, LayoutParams(-1, -2))
        host.addView(wrap, LayoutParams(-1, -2))
    }

    private fun TextView.installBookmark(entry: LiveTranscriptState.Entry) {
        isLongClickable = true
        setOnLongClickListener {
            bookmarkStore.add(phoneNumber, entry)
            Toast.makeText(context, "Transcript moment bookmarked", Toast.LENGTH_SHORT).show()
            true
        }
    }

    /**
     * Gives each focus card its own transcript viewport. The role header remains fixed while the
     * body scrolls, and touch control is handed back to the surrounding call screen at either edge.
     */
    private fun TextView.enableCardScrolling(primary: Boolean, scrollKey: String) {
        val savedScrollY = if (primary) {
            if (primaryCardKey != scrollKey) {
                primaryCardKey = scrollKey
                primaryCardScrollY = 0
            }
            primaryCardScrollY
        } else {
            if (youCardKey != scrollKey) {
                youCardKey = scrollKey
                youCardScrollY = 0
            }
            youCardScrollY
        }

        movementMethod = ScrollingMovementMethod.getInstance()
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isFocusable = true

        var lastTouchY = 0f
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = event.y
                    view.parent?.requestDisallowInterceptTouchEvent(
                        view.canScrollVertically(-1) || view.canScrollVertically(1),
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    val direction = if (event.y < lastTouchY) 1 else -1
                    view.parent?.requestDisallowInterceptTouchEvent(view.canScrollVertically(direction))
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        setOnScrollChangeListener { _, _, nextY, _, _ ->
            if (primary) primaryCardScrollY = nextY else youCardScrollY = nextY
        }
        post {
            val textHeight = layout?.height ?: 0
            val viewportHeight = (height - compoundPaddingTop - compoundPaddingBottom).coerceAtLeast(0)
            scrollTo(0, savedScrollY.coerceIn(0, (textHeight - viewportHeight).coerceAtLeast(0)))
        }
    }

    private fun addWaitingState() {
        addFocusPlaceholder("THEM", "Listening for the caller…", primary = true)
    }

    private fun addFocusPlaceholder(label: String, message: String, primary: Boolean) {
        val card = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = VERTICAL
            setPadding(dp(12), if (primary) dp(9) else dp(8), dp(12), if (primary) dp(10) else dp(8))
            background = PulseDeckVisuals.panel(
                context,
                start = if (primary) Color.rgb(14, 35, 43) else Color.rgb(21, 39, 48),
                end = if (primary) Color.rgb(8, 24, 31) else Color.rgb(14, 30, 38),
                stroke = if (primary) PulseDeckVisuals.Colors.Border else Color.rgb(42, 65, 75),
                radiusDp = if (primary) 15f else 13f,
            )
        }
        card.addView(TextView(context).apply {
            text = if (primary) "$label · LISTENING" else label
            setTextColor(if (primary) PulseDeckVisuals.Colors.Cyan else PulseDeckVisuals.Colors.Green)
            RealityTypography.displayMedium(this, if (primary) 10.5f else 9.5f)
            letterSpacing = .06f
        }, LayoutParams(-1, dp(22)))
        card.addView(TextView(context).apply {
            text = message
            setTextColor(PulseDeckVisuals.Colors.TextDim)
            RealityTypography.displayMedium(this, if (primary) 14f else 12.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(-1, -2))
        host.addView(card, LayoutParams(-1, -2).apply {
            setMargins(0, if (primary) 0 else dp(5), 0, if (primary) dp(4) else 0)
        })
    }

    private fun showHistory() {
        historyMode = true
        followLatest = true
        tools.visibility = View.VISIBLE
        rebuild()
        query.requestFocus()
        scroll.post { scroll.fullScroll(FOCUS_DOWN) }
    }

    private fun showFocus() {
        historyMode = false
        followLatest = true
        searchText = ""
        query.setText("")
        tools.visibility = View.GONE
        rebuild()
        scroll.post { scroll.fullScroll(FOCUS_UP) }
    }

    private fun time(timestampMs: Long): String = if (timestampMs <= 0L) {
        "--:--"
    } else {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampMs))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
