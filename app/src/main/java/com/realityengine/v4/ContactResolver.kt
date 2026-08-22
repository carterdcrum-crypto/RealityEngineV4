package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

object ContactResolver {
    data class Contact(val name: String, val number: String)

    fun resolveName(context: Context, input: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val target = normalize(input)
        if (target.length < 7) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val stored = normalize(cursor.getString(numberIndex).orEmpty())
                    if (numbersMatch(target, stored)) {
                        return cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun allContacts(context: Context): List<Contact> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val result = ArrayList<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
                    val number = cursor.getString(numberIndex)?.takeIf { it.isNotBlank() } ?: continue
                    result.add(Contact(name, number))
                }
            }
        } catch (_: Throwable) {}
        return result
    }

    private fun normalize(value: String): String = value.filter(Char::isDigit)

    private fun numbersMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 10 && b.length >= 10) return a.takeLast(10) == b.takeLast(10)
        return a.length >= 7 && b.length >= 7 && a.takeLast(7) == b.takeLast(7)
    }
}
