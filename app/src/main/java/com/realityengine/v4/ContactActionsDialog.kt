package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/** UI bridge for contact CRUD, caller memory, native ringtones, and saved/unsaved number blocking. */
class ContactActionsDialog(
    private val activity: Activity,
    private val manager: ContactManager
) {
    fun add(phone: String = "", onDone: () -> Unit) {
        if (!canWrite()) return
        editFields("Add contact", "", phone) { name, number ->
            report(manager.add(name, number), onDone)
        }
    }

    fun manage(contact: ContactResolver.Contact, onDone: () -> Unit) {
        val blocked = manager.isBlocked(contact.number)
        val options = arrayOf(
            "Edit contact",
            "Change ringtone",
            "Reality memory",
            "Delete contact",
            if (blocked) "Unblock number" else "Block number",
        )
        AlertDialog.Builder(activity).setTitle(contact.name).setItems(options) { _, which ->
            when (which) {
                0 -> edit(contact, onDone)
                1 -> chooseRingtone(contact, onDone)
                2 -> openMemory(contact.number, contact.name)
                3 -> confirmDelete(contact, onDone)
                4 -> report(if (blocked) manager.unblock(contact.number) else manager.block(contact.number), onDone)
            }
        }.show()
    }

    fun manageUnsaved(phone: String, onDone: () -> Unit) {
        val blocked = manager.isBlocked(phone)
        val options = arrayOf("Add contact", "Reality memory", if (blocked) "Unblock number" else "Block number")
        AlertDialog.Builder(activity).setTitle(phone).setItems(options) { _, which ->
            when (which) {
                0 -> add(phone, onDone)
                1 -> openMemory(phone, phone)
                else -> report(if (blocked) manager.unblock(phone) else manager.block(phone), onDone)
            }
        }.show()
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
