package com.realityengine.v4

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Modern Lucid Prism contact Index with search, favorites, recent contacts, lists, messaging and merge tools. */
class ContactsScreen(
    private val activity: Activity,
    private val index: ContactIndex,
    private val callHistory: CallHistoryIndex,
    private val favorites: ContactFavoritesStore,
    private val management: ContactManagementPanel,
    private val onDialContact: (String) -> Unit,
    private val onRequestContactsPermission: () -> Unit,
) {
    enum class Filter { ALL, FAVORITES, RECENT, LIST }

    private val ice = RealityVisuals.Colors.Cyan
    private val iceSoft = RealityVisuals.Colors.CyanSoft
    private val lilac = RealityVisuals.Colors.Lilac
    private val green = RealityVisuals.Colors.Green
    private val muted = RealityVisuals.Colors.TextDim
    private val panel = RealityVisuals.Colors.Panel
    private val lists = ContactListsStore(activity)

    private lateinit var listHost: LinearLayout
    private lateinit var allChip: Button
    private lateinit var favoritesChip: Button
    private lateinit var recentChip: Button
    private lateinit var listChip: Button
    private var filter = Filter.ALL
    private var query = ""
    private var activeListName: String? = null

    fun build(initialQuery: String = "", initialFilter: Filter = Filter.ALL): View {
        query = initialQuery
        filter = initialFilter

        val root = LinearLayout(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(2.dp(), 9.dp(), 2.dp(), 18.dp())
        }

        root.addView(header())
        root.addView(searchField(initialQuery), LinearLayout.LayoutParams(-1, 56.dp()).apply {
            setMargins(0, 10.dp(), 0, 10.dp())
        })
        root.addView(filterRow(), LinearLayout.LayoutParams(-1, 44.dp()).apply {
            setMargins(0, 0, 0, 10.dp())
        })

        val utilityRow = LinearLayout(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val addButton = management.addContactButton { renderList() }.apply {
            text = "ADD"
            RealityVisuals.styleControl(this, R.drawable.ic_re_person_add, accent = ice, radiusDp = 18f)
        }
        utilityRow.addView(addButton, utilityLayout())

        val listsButton = management.listsButton {
            if (filter == Filter.LIST && activeListName !in lists.names()) {
                filter = Filter.ALL
                activeListName = null
                refreshChips()
            }
            renderList()
        }.apply {
            text = "LISTS"
            RealityVisuals.styleControl(this, 0, accent = lilac, radiusDp = 18f)
        }
        utilityRow.addView(listsButton, utilityLayout())

        val mergeButton = management.mergeDuplicatesButton { renderList() }.apply {
            text = "MERGE"
            RealityVisuals.styleControl(this, 0, accent = iceSoft, radiusDp = 18f)
        }
        utilityRow.addView(mergeButton, utilityLayout())
        root.addView(utilityRow, LinearLayout.LayoutParams(-1, 46.dp()))

        val count = TextView(activity).apply {
            tag = COUNT_TAG
            text = ""
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 3.dp(), 6.dp(), 0)
            RealityVisuals.styleMicroLabel(this, muted)
        }
        root.addView(count, LinearLayout.LayoutParams(-1, 25.dp()))

        listHost = LinearLayout(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        root.addView(listHost, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 4.dp(), 0, 0)
        })

        refreshChips()
        renderList()
        return root
    }

    private fun header(): View = LinearLayout(activity).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = "CONTACT INDEX"
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 23f)
        }, LinearLayout.LayoutParams(0, 50.dp(), 1f))
        addView(TextView(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = "LOCAL"
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(
                activity,
                fill = Color.rgb(13, 47, 36),
                stroke = green,
                radiusDp = 20f,
                strokeDp = 1,
            )
            setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp())
            RealityVisuals.styleMicroLabel(this, green)
        })
    }

    private fun searchField(initialQuery: String): EditText = EditText(activity).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        hint = "Search contacts, numbers, lists"
        setHintTextColor(muted)
        setTextColor(RealityVisuals.Colors.Text)
        setText(initialQuery)
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        background = RealityVisuals.panel(
            activity,
            fill = RealityVisuals.Colors.BackgroundRaised,
            stroke = RealityVisuals.Colors.Border,
            radiusDp = 19f,
            strokeDp = 1,
        )
        setPadding(15.dp(), 0, 15.dp(), 0)
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_re_search, 0, 0, 0)
        compoundDrawableTintList = ColorStateList.valueOf(iceSoft)
        compoundDrawablePadding = 10.dp()
        RealityTypography.display(this, 15f)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                if (::listHost.isInitialized) renderList()
            }
        })
    }

    private fun filterRow(): View = LinearLayout(activity).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        allChip = chip("All", Filter.ALL)
        favoritesChip = chip("Favorites", Filter.FAVORITES)
        recentChip = chip("Recent", Filter.RECENT)
        listChip = chip("Lists", Filter.LIST)
        addView(allChip, chipLayout())
        addView(favoritesChip, chipLayout())
        addView(recentChip, chipLayout())
        addView(listChip, chipLayout())
    }

    private fun chip(label: String, target: Filter): Button = Button(activity).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        text = label
        minWidth = 0
        minHeight = 0
        stateListAnimator = null
        setOnClickListener {
            if (target == Filter.LIST) chooseListFilter()
            else {
                filter = target
                refreshChips()
                renderList()
            }
        }
    }

    private fun chooseListFilter() {
        val names = lists.names()
        if (names.isEmpty()) {
            Toast.makeText(activity, "Create a contact list with LISTS first", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("View contact list")
            .setItems(names.toTypedArray()) { _, which ->
                activeListName = names[which]
                filter = Filter.LIST
                refreshChips()
                renderList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chipLayout() = LinearLayout.LayoutParams(0, 40.dp(), 1f).apply {
        setMargins(3.dp(), 0, 3.dp(), 0)
    }

    private fun utilityLayout() = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
        setMargins(3.dp(), 0, 3.dp(), 0)
    }

    private fun refreshChips() {
        if (!::allChip.isInitialized) return
        listChip.text = activeListName?.take(10)?.uppercase() ?: "Lists"
        styleChip(allChip, filter == Filter.ALL)
        styleChip(favoritesChip, filter == Filter.FAVORITES)
        styleChip(recentChip, filter == Filter.RECENT)
        styleChip(listChip, filter == Filter.LIST)
    }

    private fun styleChip(button: Button, active: Boolean) {
        button.setTextColor(if (active) RealityVisuals.Colors.Text else muted)
        button.background = RealityVisuals.panel(
            activity,
            fill = if (active) Color.rgb(29, 28, 60) else RealityVisuals.Colors.BackgroundRaised,
            stroke = if (active) lilac else RealityVisuals.Colors.Border,
            radiusDp = 20f,
            strokeDp = 1,
        )
        RealityTypography.displayMedium(button, 10f)
    }

    private fun renderList() {
        if (!::listHost.isInitialized) return
        listHost.removeAllViews()

        val all = index.search(query)
        val recentOrder = recentNumberOrder()
        val visible = when (filter) {
            Filter.ALL -> all
            Filter.FAVORITES -> favorites.filter(all)
            Filter.RECENT -> all
                .filter { normalize(it.number) in recentOrder }
                .sortedBy { recentOrder[normalize(it.number)] ?: Int.MAX_VALUE }
            Filter.LIST -> activeListName?.let { lists.filter(it, all) }.orEmpty()
        }

        ((listHost.parent as? LinearLayout)?.findViewWithTag<TextView>(COUNT_TAG))?.text =
            "${visible.size} CONTACT${if (visible.size == 1) "" else "S"}"

        if (visible.isEmpty()) {
            listHost.addView(emptyState())
            return
        }

        if (filter == Filter.RECENT) {
            visible.forEach { contact -> listHost.addView(contactRow(contact, recentOrder)) }
            return
        }

        visible.groupBy { sectionLabel(it) }
            .toSortedMap(sectionComparator)
            .forEach { (section, contacts) ->
                listHost.addView(sectionHeader(section))
                contacts.forEach { contact -> listHost.addView(contactRow(contact, recentOrder)) }
            }
    }

    private fun sectionHeader(label: String): View = TextView(activity).apply {
        text = label
        setTextColor(lilac)
        setPadding(7.dp(), 14.dp(), 0, 5.dp())
        RealityVisuals.styleMicroLabel(this, lilac)
    }

    private fun contactRow(
        contact: ContactResolver.Contact,
        recentOrder: Map<String, Int>,
    ): View {
        val favorite = favorites.isFavorite(contact.number)
        val recent = normalize(contact.number) in recentOrder
        val contactLists = lists.listsFor(contact.number)

        val row = LinearLayout(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = RealityVisuals.panel(
                activity,
                fill = panel,
                stroke = if (favorite) lilac else RealityVisuals.Colors.Border,
                radiusDp = 19f,
                strokeDp = 1,
            )
            setPadding(11.dp(), 9.dp(), 9.dp(), 9.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener { onDialContact(contact.number) }
        }
        management.bindContactActions(row, contact) { renderList() }

        val avatar = ContactAvatarView(activity).apply {
            bind(
                contactId = contact.contactId,
                name = contact.name,
                accent = if (favorite) lilac else iceSoft,
            )
        }
        row.addView(avatar, LinearLayout.LayoutParams(46.dp(), 46.dp()))

        val identity = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 0, 7.dp(), 0)
        }
        identity.addView(TextView(activity).apply {
            text = contact.name
            maxLines = 1
            setTextColor(RealityVisuals.Colors.Text)
            RealityTypography.displayMedium(this, 14.5f)
        })
        identity.addView(TextView(activity).apply {
            text = buildString {
                append(contact.number)
                if (favorite) append("  ·  FAVORITE")
                else if (recent) append("  ·  RECENT")
                if (contactLists.isNotEmpty()) append("  ·  ").append(contactLists.first().uppercase())
            }
            maxLines = 1
            setTextColor(if (favorite) lilac else muted)
            RealityTypography.display(this, 9f)
        })
        row.addView(identity, LinearLayout.LayoutParams(0, 56.dp(), 1f))

        val messageButton = Button(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = "TXT"
            minWidth = 0
            minHeight = 0
            contentDescription = "Message ${contact.name}"
            RealityVisuals.styleControl(this, 0, accent = iceSoft, radiusDp = 19f)
            setPadding(2.dp(), 0, 2.dp(), 0)
            setOnClickListener {
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.number, null)))
                }.onFailure {
                    Toast.makeText(activity, "No messaging app available", Toast.LENGTH_SHORT).show()
                }
            }
        }
        row.addView(messageButton, LinearLayout.LayoutParams(46.dp(), 42.dp()).apply { setMargins(0, 0, 7.dp(), 0) })

        val favoriteButton = ImageButton(activity).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            contentDescription = if (favorite) "Remove favorite" else "Add favorite"
            setImageResource(R.drawable.ic_re_star)
            imageTintList = ColorStateList.valueOf(if (favorite) lilac else muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = RealityVisuals.panel(
                activity,
                fill = if (favorite) Color.rgb(29, 27, 57) else RealityVisuals.Colors.Panel,
                stroke = if (favorite) lilac else RealityVisuals.Colors.Border,
                radiusDp = 19f,
                strokeDp = 1,
            )
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener {
                favorites.toggle(contact.number)
                renderList()
            }
        }
        row.addView(favoriteButton, LinearLayout.LayoutParams(42.dp(), 42.dp()))

        return row.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, 76.dp()).apply {
                setMargins(0, 4.dp(), 0, 4.dp())
            }
        }
    }

    private fun emptyState(): View = LinearLayout(activity).apply {
        tag = RealityVisuals.HUD_OWNED_TAG
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = RealityVisuals.panel(
            activity,
            fill = RealityVisuals.Colors.BackgroundRaised,
            stroke = RealityVisuals.Colors.Border,
            radiusDp = 19f,
        )
        setPadding(20.dp(), 30.dp(), 20.dp(), 30.dp())
        addView(TextView(activity).apply {
            text = when (filter) {
                Filter.FAVORITES -> "NO FAVORITES YET"
                Filter.RECENT -> if (callHistory.hasPermission()) "NO RECENT CONTACTS" else "CALL HISTORY ACCESS NEEDED"
                Filter.LIST -> "NO CONTACTS IN ${activeListName?.uppercase() ?: "THIS LIST"}"
                Filter.ALL -> "NO CONTACT MATCHES"
            }
            gravity = Gravity.CENTER
            RealityVisuals.styleMicroLabel(this, lilac)
        })
        addView(TextView(activity).apply {
            text = when (filter) {
                Filter.FAVORITES -> "Tap the star beside a contact to pin it here."
                Filter.RECENT -> if (callHistory.hasPermission()) {
                    "Contacts from your newest calls will appear here."
                } else {
                    "Authorize call history from Traffic to enable recent-contact ordering."
                }
                Filter.LIST -> "Use LISTS to add members or choose another list."
                Filter.ALL -> "Try a different name or phone number."
            }
            gravity = Gravity.CENTER
            setPadding(0, 9.dp(), 0, 0)
            setTextColor(muted)
            RealityTypography.display(this, 12f)
        })
    }

    private fun recentNumberOrder(): Map<String, Int> {
        if (!callHistory.hasPermission()) return emptyMap()
        val order = LinkedHashMap<String, Int>()
        callHistory.recent(60).forEach { entry ->
            val key = normalize(entry.number)
            if (key.isNotBlank() && key !in order) order[key] = order.size
        }
        return order
    }

    private fun sectionLabel(contact: ContactResolver.Contact): String {
        val first = contact.name.trim().firstOrNull()?.uppercaseChar()
        return if (first != null && first.isLetter()) first.toString() else "#"
    }

    private fun normalize(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    private val sectionComparator = Comparator<String> { left, right ->
        when {
            left == right -> 0
            left == "#" -> 1
            right == "#" -> -1
            else -> left.compareTo(right)
        }
    }

    private fun Int.dp(): Int = (this * activity.resources.displayMetrics.density).toInt()

    companion object {
        private const val COUNT_TAG = "contact_count"
    }
}
