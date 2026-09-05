package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/** UI bridge for modern contact actions plus Reality Engine caller memory. */
class ContactActionsDialog(
    private val activity: Activity,
    private val manager: ContactManager
) {
    private val lists = ContactListsStore(activity)
    private val duplicates = ContactDuplicateManager(activity)

    fun add(phone: String = "", onDone: () -> Unit) {
        if (!canWrite()) return
        editFields("Add contact", "", phone) { name, number ->
            report(manager.add(name, number), onDone)
        }
    }

    fun manage(contact: ContactResolver.Contact, onDone: () -> Unit) {
        val blocked = manager.isBlocked(contact.number)
        val options = arrayOf(
            "Message",
            "Share contact",
            "Edit contact",
            "Lists / labels",
            "Change ringtone",
            "Reality memory",
            "Delete contact",
            if (blocked) "Unblock number" else "Block number",
        )
        AlertDialog.Builder(activity).setTitle(contact.name).setItems(options) { _, which ->
            when (which) {
                0 -> message(contact.number)
                1 -> share(contact)
                2 -> edit(contact, onDone)
                3 -> manageListsForContact(contact, onDone)
                4 -> chooseRingtone(contact, onDone)
                5 -> openMemory(contact.number, contact.name)
                6 -> confirmDelete(contact, onDone)
                7 -> report(if (blocked) manager.unblock(contact.number) else manager.block(contact.number), onDone)
            }
        }.show()
    }

    fun manageUnsaved(phone: String, onDone: () -> Unit) {
        val blocked = manager.isBlocked(phone)
        val options = arrayOf(
            "Message",
            "Add contact",
            "Reality memory",
            if (blocked) "Unblock number" else "Block number",
        )
        AlertDialog.Builder(activity).setTitle(phone).setItems(options) { _, which ->
            when (which) {
                0 -> message(phone)
                1 -> add(phone, onDone)
                2 -> openMemory(phone, phone)
                else -> report(if (blocked) manager.unblock(phone) else manager.block(phone), onDone)
            }
        }.show()
    }

    fun manageLists(contacts: List<ContactResolver.Contact>, onDone: () -> Unit) {
        val names = lists.names()
        val options = arrayOf("+ Create list", *names.toTypedArray())
        AlertDialog.Builder(activity)
            .setTitle("Contact lists")
            .setItems(options) { _, which ->
                if (which == 0) promptCreateList(null, onDone)
                else editListMembers(names[which - 1], contacts, onDone)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    fun mergeDuplicates(contacts: List<ContactResolver.Contact>, onDone: () -> Unit) {
        if (!canWrite()) return
        val groups = duplicates.findDuplicateGroups(contacts)
        if (groups.isEmpty()) {
            Toast.makeText(activity, "No same-number duplicate contacts found", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = groups.map { group ->
            val first = group.first()
            "${first.name} · ${first.number} · ${group.size} copies"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Merge duplicates")
            .setItems(labels) { _, which -> confirmMerge(groups[which], onDone) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmMerge(group: List<ContactResolver.Contact>, onDone: () -> Unit) {
        val summary = group.joinToString("\n") { "• ${it.name}  ${it.number}" }
        AlertDialog.Builder(activity)
            .setTitle("Merge these contacts?")
            .setMessage("Phone found the same normalized phone number on these Android contacts:\n\n$summary")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Merge") { _, _ ->
                val result = duplicates.merge(group)
                Toast.makeText(activity, if (result.success) "Contacts merged" else result.message, Toast.LENGTH_SHORT).show()
                if (result.success) onDone()
            }
            .show()
    }

    private fun manageListsForContact(contact: ContactResolver.Contact, onDone: () -> Unit) {
        val names = lists.names()
        if (names.isEmpty()) {
            promptCreateList(contact, onDone)
            return
        }
        val checked = BooleanArray(names.size) { index -> lists.contains(names[index], contact.number) }
        AlertDialog.Builder(activity)
            .setTitle("Lists for ${contact.name}")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, value -> checked[which] = value }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("New list") { _, _ -> promptCreateList(contact, onDone) }
            .setPositiveButton("Save") { _, _ ->
                names.forEachIndexed { index, name -> lists.setMember(name, contact.number, checked[index]) }
                onDone()
            }
            .show()
    }

    private fun promptCreateList(contact: ContactResolver.Contact?, onDone: () -> Unit) {
        val input = EditText(activity).apply {
            hint = "List name"
            isSingleLine = true
            setPadding(36, 8, 36, 8)
        }
        AlertDialog.Builder(activity)
            .setTitle("Create contact list")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (!lists.create(name)) {
                    Toast.makeText(activity, "Use a unique non-empty list name", Toast.LENGTH_SHORT).show()
                } else {
                    contact?.let { lists.setMember(name, it.number, true) }
                    Toast.makeText(activity, "List created", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
            .show()
    }

    private fun editListMembers(name: String, contacts: List<ContactResolver.Contact>, onDone: () -> Unit) {
        val sorted = contacts.sortedBy { it.name.lowercase() }
        val labels = sorted.map { "${it.name}  ·  ${it.number}" }.toTypedArray()
        val checked = BooleanArray(sorted.size) { index -> lists.contains(name, sorted[index].number) }
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete list") { _, _ ->
                lists.delete(name)
                Toast.makeText(activity, "List deleted", Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setPositiveButton("Save members") { _, _ ->
                sorted.forEachIndexed { index, contact -> lists.setMember(name, contact.number, checked[index]) }
                onDone()
            }
            .show()
    }

    private fun message(phone: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null))
        runCatching { activity.startActivity(intent) }
            .onFailure { Toast.makeText(activity, "No messaging app available", Toast.LENGTH_SHORT).show() }
    }

    private fun share(contact: ContactResolver.Contact) {
        val vcardUri = contactVcardUri(contact.contactId)
        val intent = if (vcardUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
                putExtra(Intent.EXTRA_STREAM, vcardUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${contact.name}\n${contact.number}")
            }
        }
        runCatching { activity.startActivity(Intent.createChooser(intent, "Share contact")) }
            .onFailure { Toast.makeText(activity, "Could not share contact", Toast.LENGTH_SHORT).show() }
    }

    private fun contactVcardUri(contactId: Long): Uri? {
        if (contactId < 0L) return null
        return runCatching {
            var lookup: String? = null
            activity.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                "${ContactsContract.Contacts._ID}=?",
                arrayOf(contactId.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) lookup = cursor.getString(0)
            }
            lookup?.takeIf { it.isNotBlank() }?.let {
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, it)
            }
        }.getOrNull()
    }

    private fun openMemory(phone: String, name: String) {
        activity.startActivity(Intent(activity, CallerMemoryActivity::class.java).apply {
            putExtra(CallerMemoryActivity.EXTRA_PHONE, phone)
            putExtra(CallerMemoryActivity.EXTRA_NAME, name)
        })
    }

    private fun edit(contact: ContactResolver.Contact, onDone: () -> Unit) {
        if (!canWrite() || contact.contactId < 0) return
        editFields("Edit contact", contact.name, contact.number) { name, phone ->
            report(manager.update(contact.contactId, name, phone), onDone)
        }
    }

    private fun chooseRingtone(contact: ContactResolver.Contact, onDone: () -> Unit) {
        if (!canWrite() || contact.contactId < 0L) return
        val choices = ContactMediaStore.ringtoneChoices(activity)
        if (choices.isEmpty()) {
            Toast.makeText(activity, "No ringtones available", Toast.LENGTH_SHORT).show()
            return
        }
        val current = ContactMediaStore.customRingtoneUri(activity, contact.contactId)?.toString()
        val selected = choices.indexOfFirst { it.uri?.toString() == current }.coerceAtLeast(0)
        val labels = choices.map { it.title }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Ringtone for ${contact.name}")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val choice = choices[which]
                val success = ContactMediaStore.setCustomRingtone(activity, contact.contactId, choice.uri)
                Toast.makeText(
                    activity,
                    if (success) "Ringtone set to ${choice.title}" else "Could not change ringtone",
                    Toast.LENGTH_SHORT,
                ).show()
                if (success) onDone()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(contact: ContactResolver.Contact, onDone: () -> Unit) {
        if (!canWrite() || contact.contactId < 0) return
        AlertDialog.Builder(activity).setTitle("Delete ${contact.name}?")
            .setMessage("This removes the contact from Android contacts.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> report(manager.delete(contact.contactId), onDone) }
            .show()
    }

    private fun editFields(title: String, initialName: String, initialPhone: String, save: (String, String) -> Unit) {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 12, 48, 0) }
        val name = EditText(activity).apply { hint = "Name"; setText(initialName); isSingleLine = true }
        val phone = EditText(activity).apply { hint = "Phone"; setText(initialPhone); inputType = InputType.TYPE_CLASS_PHONE; isSingleLine = true }
        box.addView(name); box.addView(phone)
        AlertDialog.Builder(activity).setTitle(title).setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim(); val p = phone.text.toString().trim()
                if (n.isBlank() || p.isBlank()) Toast.makeText(activity, "Name and number required", Toast.LENGTH_SHORT).show()
                else save(n, p)
            }.show()
    }

    private fun canWrite(): Boolean {
        if (activity.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) return true
        activity.requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 1006)
        return false
    }

    private fun report(result: ContactManager.Result, onDone: () -> Unit) {
        Toast.makeText(activity, if (result.success) "Done" else result.message, Toast.LENGTH_SHORT).show()
        if (result.success) onDone()
    }
}
