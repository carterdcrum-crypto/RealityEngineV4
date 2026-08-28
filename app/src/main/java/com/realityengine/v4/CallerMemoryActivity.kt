package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Visible per-caller memory editor backed by local storage + optional Supabase sync. */
class CallerMemoryActivity : Activity() {
    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
    }

    private enum class Kind(val label: String, val addLabel: String) {
        LIKE("LIKES", "Add like"),
        DISLIKE("DISLIKES", "Add dislike"),
        FACT("IMPORTANT FACTS", "Add fact"),
        TOPIC("RECENT TOPICS", "Add topic"),
        STARTER("BEST STARTERS", "Add starter"),
        OPEN("OPEN / FOLLOW-UP", "Add open topic"),
        STYLE("CONVERSATION STYLE", "Set style"),
        SUMMARY("LAST CALL SUMMARY", "Set summary"),
    }

    private lateinit var profiles: CallerProfileStore
    private lateinit var cloud: SupabaseCallerMemorySync
    private lateinit var ai: CallerMemoryAiExtractor
    private lateinit var proposals: MemoryProposalStore
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var phone = ""
    private var fallbackName = ""

    private val bg = RealityVisuals.Colors.Background
    private val panel = RealityVisuals.Colors.Panel
    private val cyan = RealityVisuals.Colors.Cyan
    private val green = RealityVisuals.Colors.Green
    private val magenta = RealityVisuals.Colors.Magenta
    private val primaryText = RealityVisuals.Colors.Text
    private val muted = RealityVisuals.Colors.TextDim

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profiles = CallerProfileStore(this)
        cloud = SupabaseCallerMemorySync(this)
        ai = CallerMemoryAiExtractor(this)
        proposals = MemoryProposalStore(this)
        phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
        fallbackName = intent.getStringExtra(EXTRA_NAME).orEmpty().trim()
        if (phone.isBlank()) {
            finish()
            return
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            setBackgroundColor(bg)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root, ViewGroup.LayoutParams(-1, -2))
        })
        build()
    }

    private fun build() {
        root.removeAllViews()
        val profile = profiles.load(phone)
        val match = ContactMediaStore.findByNumber(this, phone)
        val name = profile.displayName.ifBlank { match?.name.orEmpty().ifBlank { fallbackName.ifBlank { phone } } }

        root.addView(ContactAvatarView(this).apply {
            bind(match?.contactId ?: -1L, name, cyan)
        }, LinearLayout.LayoutParams(dp(92), dp(92)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, dp(8), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = name
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            RealityTypography.displayMedium(this, 23f)
        })
        root.addView(TextView(this).apply {
            text = phone
            gravity = Gravity.CENTER
            setTextColor(muted)
            RealityTypography.display(this, 12f)
            setPadding(0, dp(3), 0, dp(10))
        })

        status = TextView(this).apply {
            text = "LOCAL-FIRST MEMORY · SUPABASE ${if (SettingsStore(this@CallerMemoryActivity).supabaseVerified()) "READY" else "OPTIONAL"}"
            gravity = Gravity.CENTER
            setTextColor(if (SettingsStore(this@CallerMemoryActivity).supabaseVerified()) green else cyan)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = cyan, radiusDp = 12f)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            RealityTypography.display(this, 11f)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })

        root.addView(actionButton("LEARN FROM LAST TRANSCRIPT", magenta) { relearnLastTranscript() })
        root.addView(actionButton("SYNC MEMORY NOW", cyan) { syncNow() })
        proposals.load(phone)?.let { addProposalReview(it) }

        root.addView(TextView(this).apply {
            text = "AI learns explicit everyday preferences, facts, topics and follow-ups from completed caller speech. Empty categories stay visible so you can tell what has and has not been learned."
            setTextColor(muted)
            setPadding(dp(4), dp(9), dp(4), dp(12))
            RealityTypography.display(this, 11f)
        })

        Kind.entries.forEach { kind -> addCategory(profile, kind) }

        root.addView(actionButton("DELETE ALL MEMORY FOR THIS CALLER", RealityVisuals.Colors.DangerFill) {
            AlertDialog.Builder(this)
                .setTitle("Delete all Reality memory?")
                .setMessage("This removes learned caller memory. Contacts, transcripts, call log and saved recordings are separate.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    profiles.deleteProfile(phone)
                    cloud.pushAsync(phone)
                    build()
                }
                .show()
        }.apply { setTextColor(Color.WHITE) })
    }

    private fun addProposalReview(proposal: MemoryProposalStore.Proposal) {
        val learned = proposal.learned
        val lines = buildList {
            learned.likes.forEach { add("LIKE · $it") }
            learned.dislikes.forEach { add("DISLIKE · $it") }
            learned.facts.forEach { add("FACT · $it") }
            learned.topics.forEach { add("TOPIC · $it") }
            learned.unresolved.forEach { add("FOLLOW UP · $it") }
            learned.starters.forEach { add("STARTER · $it") }
            if (learned.preferredStyle.isNotBlank()) add("STYLE · ${learned.preferredStyle}")
        }
        root.addView(TextView(this).apply {
            text = "NEW MEMORY TO REVIEW · ${proposals.itemCount(proposal)}"
            setTextColor(green)
            setPadding(dp(3), dp(14), 0, dp(5))
            RealityTypography.displayMedium(this, 13f)
        })
        root.addView(TextView(this).apply {
            text = if (lines.isEmpty()) "No permanent facts proposed; only the call summary changed." else lines.joinToString("\n")
            setTextColor(primaryText)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = green, radiusDp = 10f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            RealityTypography.display(this, 11.5f)
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "SAVE"
            setTextColor(green)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = green, radiusDp = 10f)
            setOnClickListener {
                profiles.update(phone) { CallSummaryBuilder.merge(it, learned) }
                proposals.clear(phone)
                pushAndRefresh()
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, dp(4), dp(3), dp(3)) })
        actions.addView(Button(this).apply {
            text = "IGNORE"
            setTextColor(magenta)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = magenta, radiusDp = 10f)
            setOnClickListener { proposals.clear(phone); build() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(4), 0, dp(3)) })
        root.addView(actions)
    }

    private fun addCategory(profile: CallerProfileStore.CallerProfile, kind: Kind) {
        root.addView(TextView(this).apply {
            text = kind.label
            setTextColor(cyan)
            setPadding(dp(3), dp(14), 0, dp(5))
            RealityTypography.displayMedium(this, 13f)
        })

        val values = values(profile, kind)
        if (values.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Nothing learned yet"
                setTextColor(muted)
                background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = RealityVisuals.Colors.Border, radiusDp = 10f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                RealityTypography.display(this, 12f)
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(4)) })
        } else {
            values.forEach { value ->
                root.addView(Button(this).apply {
                    text = value
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTextColor(primaryText)
                    background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = RealityVisuals.Colors.Border, radiusDp = 10f)
                    isAllCaps = false
                    setPadding(dp(12), 0, dp(12), 0)
                    RealityTypography.display(this, 12f)
                    setOnClickListener { showMemoryActions(kind, value) }
                }, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(4)) })
            }
        }

        root.addView(Button(this).apply {
            text = "+ ${kind.addLabel}"
            setTextColor(green)
            background = RealityVisuals.panel(this@CallerMemoryActivity, fill = RealityVisuals.Colors.BackgroundRaised, stroke = green, radiusDp = 10f)
            isAllCaps = false
            RealityTypography.display(this, 11f)
            setOnClickListener { editMemory(kind, "") }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(0, 0, 0, dp(2)) })
    }

    private fun values(profile: CallerProfileStore.CallerProfile, kind: Kind): List<String> = when (kind) {
        Kind.LIKE -> profile.likes
        Kind.DISLIKE -> profile.dislikes
        Kind.FACT -> profile.importantFacts
        Kind.TOPIC -> profile.topics
        Kind.STARTER -> profile.conversationStarters
        Kind.OPEN -> profile.unresolvedTopics
        Kind.STYLE -> listOfNotNull(profile.preferredConversationStyle.takeIf { it.isNotBlank() })
        Kind.SUMMARY -> listOfNotNull(profile.lastCallSummary.takeIf { it.isNotBlank() })
    }

    private fun showMemoryActions(kind: Kind, value: String) {
        AlertDialog.Builder(this)
            .setTitle(kind.label)
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                if (which == 0) editMemory(kind, value) else deleteMemory(kind, value)
            }
            .show()
    }

    private fun editMemory(kind: Kind, original: String) {
        val input = EditText(this).apply {
            setText(original)
            hint = kind.addLabel
            setSingleLine(kind != Kind.SUMMARY)
            if (kind == Kind.SUMMARY) minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle(if (original.isBlank()) kind.addLabel else "Edit ${kind.label.lowercase()}")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val next = input.text.toString().trim().replace(Regex("\\s+"), " ")
                if (next.isBlank()) return@setPositiveButton
                profiles.update(phone) { profile -> replace(profile, kind, original, next) }
                pushAndRefresh()
            }
            .show()
    }

    private fun deleteMemory(kind: Kind, value: String) {
        profiles.update(phone) { profile -> remove(profile, kind, value) }
        pushAndRefresh()
    }

    private fun replace(profile: CallerProfileStore.CallerProfile, kind: Kind, original: String, next: String) {
        when (kind) {
            Kind.LIKE -> replaceIn(profile.likes, original, next)
            Kind.DISLIKE -> replaceIn(profile.dislikes, original, next)
            Kind.FACT -> replaceIn(profile.importantFacts, original, next)
            Kind.TOPIC -> replaceIn(profile.topics, original, next)
            Kind.STARTER -> replaceIn(profile.conversationStarters, original, next)
            Kind.OPEN -> replaceIn(profile.unresolvedTopics, original, next)
            Kind.STYLE -> profile.preferredConversationStyle = next.take(160)
            Kind.SUMMARY -> profile.lastCallSummary = next.take(700)
        }
    }

    private fun remove(profile: CallerProfileStore.CallerProfile, kind: Kind, value: String) {
        when (kind) {
            Kind.LIKE -> removeFrom(profile.likes, value)
            Kind.DISLIKE -> removeFrom(profile.dislikes, value)
            Kind.FACT -> removeFrom(profile.importantFacts, value)
            Kind.TOPIC -> removeFrom(profile.topics, value)
            Kind.STARTER -> removeFrom(profile.conversationStarters, value)
            Kind.OPEN -> removeFrom(profile.unresolvedTopics, value)
            Kind.STYLE -> profile.preferredConversationStyle = ""
            Kind.SUMMARY -> profile.lastCallSummary = ""
        }
    }

    private fun replaceIn(list: MutableList<String>, original: String, next: String) {
        if (original.isBlank()) {
            if (list.none { it.equals(next, true) }) list.add(next)
            return
        }
        val index = list.indexOfFirst { it.equals(original, true) }
        if (index >= 0) list[index] = next else if (list.none { it.equals(next, true) }) list.add(next)
    }

    private fun removeFrom(list: MutableList<String>, value: String) {
        list.removeAll { it.equals(value, true) }
    }

    private fun pushAndRefresh() {
        build()
        cloud.pushAsync(phone) { result ->
            runOnUiThread {
                if (::status.isInitialized) status.text = "MEMORY SAVED · ${result.detail.ifBlank { result.status.name }}"
            }
        }
    }

    private fun syncNow() {
        status.text = "SYNCING CALLER MEMORY…"
        cloud.syncAsync(phone) { result ->
            runOnUiThread {
                build()
                status.text = "${result.status.name} · ${result.detail}"
            }
        }
    }

    private fun relearnLastTranscript() {
        val saved = CallTranscriptStore.savedFor(this, phone).firstOrNull()
        if (saved == null) {
            Toast.makeText(this, "No saved transcript for this caller yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ai.configured()) {
            Toast.makeText(this, "Configure at least one AI coach provider first", Toast.LENGTH_LONG).show()
            return
        }
        status.text = "AI READING LAST TRANSCRIPT…"
        ai.extractAsync(phone, saved.text) { learned ->
            runOnUiThread {
                if (learned == null) {
                    status.text = "AI MEMORY UPDATE FAILED"
                    return@runOnUiThread
                }
                if (learned.summary.isNotBlank()) profiles.update(phone) { it.lastCallSummary = learned.summary }
                proposals.save(phone, learned)
                cloud.pushAsync(phone)
                build()
                status.text = "NEW MEMORY READY · REVIEW SAVE / IGNORE"
            }
        }
    }

    private fun actionButton(label: String, stroke: Int, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(if (stroke == RealityVisuals.Colors.DangerFill) Color.WHITE else stroke)
        background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = stroke, radiusDp = 12f)
        RealityTypography.displayMedium(this, 11f)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
