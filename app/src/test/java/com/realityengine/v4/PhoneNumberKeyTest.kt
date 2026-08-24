package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberKeyTest {
    @Test fun `blank number has no persistent caller key`() {
        assertNull(PhoneNumberKey.normalize(null))
        assertNull(PhoneNumberKey.normalize("   "))
    }

    @Test fun `formatted domestic numbers collapse to digits`() {
        assertEquals("7125551212", PhoneNumberKey.normalize("(712) 555-1212"))
        assertEquals("17125551212", PhoneNumberKey.normalize("1-712-555-1212"))
    }

    @Test fun `international plus prefix is preserved`() {
        assertEquals("+17125551212", PhoneNumberKey.normalize(" +1 (712) 555-1212 "))
        assertEquals("+442079460018", PhoneNumberKey.normalize("+44 20 7946 0018"))
    }

    @Test fun `non numeric telecom identifiers remain stable instead of becoming empty`() {
        assertEquals("Private", PhoneNumberKey.normalize(" Private "))
    }
}
