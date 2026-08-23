package com.realityengine.v4

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.ContactsContract

/** Contact CRUD plus system blocked-number operations for the default phone app. */
class ContactManager(private val context: Context) {
    data class Result(val success: Boolean, val message: String = "")

    fun add(name: String, phone: String): Result = try {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build()
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim()).build()
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.trim())
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        Result(true)
    } catch (t: Throwable) { Result(false, t.message ?: "Could not add contact") }

    fun update(contactId: Long, name: String, phone: String): Result = try {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim()).build()
        ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE))
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.trim()).build()
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        Result(true)
    } catch (t: Throwable) { Result(false, t.message ?: "Could not update contact") }

    fun delete(contactId: Long): Result = try {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        context.contentResolver.delete(uri, null, null)
        Result(true)
    } catch (t: Throwable) { Result(false, t.message ?: "Could not delete contact") }

    fun block(phone: String): Result {
        return try {
            if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                Result(false, "Blocking unavailable")
            } else {
                val values = android.content.ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phone.trim())
                }
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
                Result(true)
            }
        } catch (t: Throwable) { Result(false, t.message ?: "Could not block number") }
    }

    fun unblock(phone: String): Result = try {
        val rows = context.contentResolver.delete(BlockedNumberContract.BlockedNumbers.CONTENT_URI, "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER}=?", arrayOf(phone.trim()))
        Result(rows > 0, if (rows > 0) "" else "Number was not blocked")
    } catch (t: Throwable) { Result(false, t.message ?: "Could not unblock number") }

    fun isBlocked(phone: String): Boolean = try { BlockedNumberContract.isBlocked(context, phone.trim()) } catch (_: Throwable) { false }
}
