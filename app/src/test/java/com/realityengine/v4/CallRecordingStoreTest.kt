package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CallRecordingStoreTest {
    @Test
    fun stereoWavHeaderDescribesCallerAndUserPcm() {
        val header = CallRecordingStore.wavHeader(sampleRate = 16_000, channels = 2, dataBytes = 64_000)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, buffer.getShort(22).toInt())
        assertEquals(16_000, buffer.getInt(24))
        assertEquals(64_000, buffer.getInt(40))
    }

    @Test
    fun unknownCallGetsStablePrivateBucket() {
        assertEquals("unknown", CallRecordingStore.storageKey(""))
        assertEquals("Unknown", CallRecordingStore.storageKey("Unknown"))
    }

    @Test
    fun numberStorageKeyDropsUnsafeCharacters() {
        assertEquals("+17125551212", CallRecordingStore.storageKey("+1 (712) 555-1212"))
    }
}
