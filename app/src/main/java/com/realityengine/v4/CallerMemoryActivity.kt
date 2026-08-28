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
        val items = MemoryProposalReview.items(proposal.learned)
        root.addView(TextView(this).apply {
            text = "NEW MEMORY TO REVIEW · ${items.size}"
            setTextColor(green)
            setPadding(dp(3), dp(14), 0, dp(5))
            RealityTypography.displayMedium(this, 13f)
        })

        if (items.isEmpty()) {
            proposals.clear(phone)
            root.addView(TextView(this).apply {
                text = "No permanent memory items are waiting for review. The call summary is stored separately."
                setTextColor(muted)
                background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = RealityVisuals.Colors.Border, radiusDp = 10f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                RealityTypography.display(this, 11.5f)
            })
            return
        }

        root.addView(TextView(this).apply {
            text = "Approve, edit, or ignore each item independently. Nothing here becomes permanent caller memory until you save that specific item."
            setTextColor(muted)
            setPadding(dp(4), dp(2), dp(4), dp(7))
            RealityTypography.display(this, 10.5f)
        })

        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = RealityVisuals.panel(this@CallerMemoryActivity, fill = panel, stroke = green, radiusDp = 10f)
            }
            card.addView(TextView(this).apply {
                text = item.kind.label
                setTextColor(green)
                RealityTypography.displayMedium(this, 10f)
            })
            card.addView(TextView(this).apply {
                text = item.value
                setTextColor(primaryText)
                setPadding(0, dp(4), 0, dp(7))
                RealityTypography.display(this, 12f)
            })

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(reviewButton("SAVE", green) { saveProposalItem(proposal.learned, item, item.value) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, 0, dp(2), 0) })
            actions.addView(reviewButton("EDIT", cyan) { editProposalItem(proposal.learned, item) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            actions.addView(reviewButton("IGNORE", magenta) { ignoreProposalItem(proposal.learned, item) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, 0, 0) })
            card.addView(actions)
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(3), 0, dp(4)) })
        }
    }

    private fun saveProposalItem(
        learned: CallerMemoryAiExtractor.Learned,
        item: MemoryProposalReview.Item,
        value: String,
    ) {
        val clean = value.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return
        val kind = proposalKind(item.kind)
        profiles.update(phone) { profile -> replace(profile, kind, "", clean) }
        persistRemainingProposal(MemoryProposalReview.remove(learned, item))
        build()
        status.text = "MEMORY ITEM SAVED · ${item.kind.label}"
        cloud.pushAsync(phone) { result ->
            runOnUiThread {
                if (::status.isInitialized) status.text = "MEMORY SAVED · ${result.detail.ifBlank { result.status.name }}"
            }
        }
    }

    private fun editProposalItem(
        learned: CallerMemoryAiExtractor.Learned,
        item: MemoryProposalReview.Item,
    ) {
        val input = EditText(this).apply {
            setText(item.value)
            setSelection(text.length)
            hint = item.kind.label
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit proposed ${item.kind.label.lowercase()}")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                saveProposalItem(learned, item, input.text.toString())
            }
            .show()
    }

    private fun ignoreProposalItem(
        learned: CallerMemoryAiExtractor.Learned,
        item: MemoryProposalReview.Item,
    ) {
        persistRemainingProposal(MemoryProposalReview.remove(learned, item))
        build()
        status.text = "IGNORED · ${item.kind.label}"
    }

    private fun persistRemainingProposal(learned: CallerMemoryAiExtractor.Learned) {
        if (MemoryProposalReview.isEmpty(learned)) proposals.clear(phone) else proposals.save(phone, learned)
    }

    private fun proposalKind(kind: MemoryProposalReview.Kind): Kind = when (kind) {
        MemoryProposalReview.Kind.LIKE -> Kind.LIKE
        MemoryProposalReview.Kind.DISLIKE -> Kind.DISLIKE
        MemoryProposalReview.Kind.FACT -> Kind.FACT
        MemoryProposalReview.Kind.TOPIC -> Kind.TOPIC
        MemoryProposalReview.Kind.STARTER -> Kind.STARTER
        MemoryProposalReview.Kind.FOLLOW_UP -> Kind.OPEN
        MemoryProposalReview.Kind.STYLE -> Kind.STYLE
    }

    private fun reviewButton(label: String, stroke: Int, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(stroke)
        background = RealityVisuals.panel(this@CallerMemoryActivity, fill = RealityVisuals.Colors.BackgroundRaised, stroke = stroke, radiusDp = 9f)
        RealityTypography.displayMedium(this, 9.5f)
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { click() }
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
                if (MemoryProposalReview.isEmpty(learned)) proposals.clear(phone) else proposals.save(phone, learned)
                cloud.pushAsync(phone)
                build()
                status.text = if (MemoryProposalReview.isEmpty(learned)) {
                    "NO NEW PERMANENT MEMORY ITEMS"
                } else {
                    "NEW MEMORY READY · REVIEW EACH ITEM"
                }
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
