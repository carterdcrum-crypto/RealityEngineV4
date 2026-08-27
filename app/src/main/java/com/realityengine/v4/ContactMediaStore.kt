package com.realityengine.v4

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract

/** Native Android contact media bridge: photos and per-contact custom ringtones. */
object ContactMediaStore {
    data class Match(val contactId: Long, val name: String, val number: String)
    data class RingtoneChoice(val title: String, val uri: Uri?)

    fun findByNumber(context: Context, input: String): Match? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val target = normalize(input)
        if (target.length < 7) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex).orEmpty()
                    if (numbersMatch(target, normalize(number))) {
                        return Match(
                            contactId = cursor.getLong(idIndex),
                            name = cursor.getString(nameIndex).orEmpty().ifBlank { number },
                            number = number,
                        )
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun loadPhoto(context: Context, contactId: Long): Bitmap? {
        if (contactId < 0L || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)
                ?.use(BitmapFactory::decodeStream)
        } catch (_: Throwable) {
            null
        }
    }

    fun customRingtoneUri(context: Context, contactId: Long): Uri? {
        if (contactId < 0L || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun customRingtoneTitle(context: Context, contactId: Long): String {
        val uri = customRingtoneUri(context, contactId) ?: return "Default ringtone"
        return try {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)?.takeIf { it.isNotBlank() }
                ?: "Custom ringtone"
        } catch (_: Throwable) {
            "Custom ringtone"
        }
    }

    fun setCustomRingtone(context: Context, contactId: Long, ringtoneUri: Uri?): Boolean {
        if (contactId < 0L || context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val values = ContentValues().apply {
                if (ringtoneUri == null) putNull(ContactsContract.Contacts.CUSTOM_RINGTONE)
                else put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
            }
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun ringtoneChoices(context: Context): List<RingtoneChoice> {
        val result = ArrayList<RingtoneChoice>()
        result += RingtoneChoice("Default ringtone", null)
        return try {
            val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_RINGTONE) }
            manager.cursor.use { cursor ->
                for (position in 0 until cursor.count) {
                    val uri = manager.getRingtoneUri(position) ?: continue
                    val title = try {
                        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                    } catch (_: Throwable) {
                        null
                    }?.takeIf { it.isNotBlank() } ?: "Ringtone ${position + 1}"
                    result += RingtoneChoice(title, uri)
                }
            }
            result.distinctBy { it.uri?.toString().orEmpty() }
        } catch (_: Throwable) {
            result
        }
    }

    private fun normalize(value: String): String = value.filter(Char::isDigit)

    private fun numbersMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 10 && b.length >= 10) return a.takeLast(10) == b.takeLast(10)
        return a.length >= 7 && b.length >= 7 && a.takeLast(7) == b.takeLast(7)
    }
}
