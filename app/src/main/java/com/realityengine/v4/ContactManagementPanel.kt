package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** Wires ContactActionsDialog into the contact index without growing MainActivity further. */
class ContactManagementPanel(
    private val activity: Activity,
    private val index: ContactIndex,
    private val manager: ContactManager,
    private val actions: ContactActionsDialog
) {
    fun addContactButton(refresh: () -> Unit): Button = Button(activity).apply {
        text = "+ Add contact"
        setOnClickListener {
            if (ensureWritePermission()) actions.add(onDone = refresh)
        }
    }

    fun bindContactActions(view: View, contact: ContactResolver.Contact, refresh: () -> Unit) {
        view.setOnLongClickListener {
            actions.manage(contact, refresh)
            true
        }
    }

    fun unsavedNumberActions(phone: String, refresh: () -> Unit) {
        if (phone.isBlank()) return
        actions.manageUnsaved(phone, refresh)
    }

    fun blockButton(phone: String, refresh: () -> Unit): Button = Button(activity).apply {
        val blocked = manager.isBlocked(phone)
        text = if (blocked) "Unblock number" else "Block number"
        setOnClickListener {
            val result = if (blocked) manager.unblock(phone) else manager.block(phone)
            Toast.makeText(activity, if (result.success) "Done" else result.message, Toast.LENGTH_SHORT).show()
            if (result.success) refresh()
        }
    }

    private fun ensureWritePermission(): Boolean {
        if (activity.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) return true
        activity.requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 1006)
        return false
    }
}
