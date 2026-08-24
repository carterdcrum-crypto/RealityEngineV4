package com.realityengine.v4

/** Pure normalization used anywhere a phone number becomes a persistent caller key. */
object PhoneNumberKey {
    fun normalize(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val plus = raw.startsWith("+")
        val digits = raw.filter(Char::isDigit)
        if (digits.isBlank()) return raw
        return if (plus) "+$digits" else digits
    }
}
