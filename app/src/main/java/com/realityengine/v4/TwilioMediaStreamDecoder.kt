package com.realityengine.v4

import android.util.Base64
import org.json.JSONObject

/**
 * Decodes inbound Twilio Media Streams messages into linear PCM for transcription.
 * Twilio media payloads are 8 kHz mono mu-law; this converts them to signed PCM16.
 */
object TwilioMediaStreamDecoder {
    data class AudioFrame(val pcm16: ByteArray, val sampleRate: Int = 8_000, val track: String)

    fun decode(message: String): AudioFrame? {
        val root = try { JSONObject(message) } catch (_: Throwable) { return null }
        if (root.optString("event") != "media") return null
        val media = root.optJSONObject("media") ?: return null
        val payload = media.optString("payload")
        if (payload.isBlank()) return null
        val mulaw = try { Base64.decode(payload, Base64.NO_WRAP) } catch (_: Throwable) { return null }
        if (mulaw.isEmpty()) return null
        val pcm = ByteArray(mulaw.size * 2)
        var out = 0
        for (encoded in mulaw) {
            val sample = decodeMuLaw(encoded.toInt() and 0xff)
            pcm[out++] = (sample.toInt() and 0xff).toByte()
            pcm[out++] = ((sample.toInt() ushr 8) and 0xff).toByte()
        }
        return AudioFrame(pcm, track = media.optString("track", "inbound"))
    }

    private fun decodeMuLaw(value: Int): Short {
        val u = value.inv() and 0xff
        val sign = u and 0x80
        val exponent = (u ushr 4) and 0x07
        val mantissa = u and 0x0f
        var sample = ((mantissa shl 3) + 0x84) shl exponent
        sample -= 0x84
        if (sign != 0) sample = -sample
        return sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
