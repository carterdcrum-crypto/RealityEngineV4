package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Lets the user explicitly override the global coach persona for individual saved contacts. */
class CoachPersonaManagerActivity : Activity() {
    private lateinit var index: ContactIndex
    private lateinit var profiles: CallerProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = ContactIndex(this)
        profiles = CallerProfileStore(this)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(30), dp(18), dp(18))
            setBackgroundColor(RealityVisuals.Colors.Background)
        }
        root.addView(TextView(this).apply {
            text = "CALLER PERSONAS"
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 24f)
        })
        root.addView(TextView(this).apply {
            text = "AUTO inherits the default coach persona. With Adaptive selected, Reality Engine can use the caller's learned communication style."
            setTextColor(RealityVisuals.Colors.TextDim)
            RealityTypography.display(this, 12f)
            setPadding(0, dp(6), 0, dp(14))
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            list.addView(message("Contacts permission is required to manage caller personas."))
            return
        }

        val contacts = index.all()
        if (contacts.isEmpty()) {
            list.addView(message("No contacts found."))
            return
        }
        contacts.forEach { contact -> list.addView(contactRow(contact)) }
    }

    private fun contactRow(contact: ContactResolver.Contact): LinearLayout {
        val profile = profiles.load(contact.number)
        val override = profile.coachPersonaId
        val personaLabel = if (override == "AUTO") "AUTO" else CoachPersonaCatalog.byId(override).label.uppercase()
        val learned = profile.preferredConversationStyle.takeIf { it.isNotBlank() } ?: "No learned style yet"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = RealityVisuals.panel(
                this@CoachPersonaManagerActivity,
                fill = RealityVisuals.Colors.Panel,
                stroke = RealityVisuals.Colors.Border,
                radiusDp = 12f,
            )

            addView(ContactAvatarView(this@CoachPersonaManagerActivity).apply {
                bind(contact.contactId, contact.name, RealityVisuals.Colors.Cyan)
            }, LinearLayout.LayoutParams(dp(46), dp(46)))

            addView(LinearLayout(this@CoachPersonaManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
                addView(TextView(this@CoachPersonaManagerActivity).apply {
                    text = contact.name
                    setTextColor(RealityVisuals.Colors.Text)
                    RealityTypography.displayMedium(this, 14f)
                    maxLines = 1
                })
                addView(TextView(this@CoachPersonaManagerActivity).apply {
                    text = learned
                    setTextColor(RealityVisuals.Colors.TextDim)
                    RealityTypography.display(this, 10.5f)
                    maxLines = 2
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))

            addView(TextView(this@CoachPersonaManagerActivity).apply {
                text = personaLabel
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = RealityVisuals.panel(
                    this@CoachPersonaManagerActivity,
                    fill = RealityVisuals.Colors.BackgroundRaised,
                    stroke = RealityVisuals.Colors.Magenta,
                    radiusDp = 18f,
                )
                RealityVisuals.styleMicroLabel(this, RealityVisuals.Colors.Magenta)
            })

            setOnClickListener { choose(contact.number, contact.name) }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
            it.minimumHeight = dp(70)
        }
    }

    private fun choose(number: String, name: String) {
        val choices = CoachPersonaCatalog.contactChoices()
        val current = profiles.load(number).coachPersonaId
        val selected = choices.indexOfFirst { it.first == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Coach persona · $name")
            .setSingleChoiceItems(choices.map { it.second }.toTypedArray(), selected) { dialog, which ->
                profiles.setCoachPersona(number, choices[which].first)
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun message(textValue: String) = TextView(this).apply {
        text = textValue
        setTextColor(Color.rgb(190, 220, 228))
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(30), dp(18), dp(30))
        RealityTypography.display(this, 13f)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
