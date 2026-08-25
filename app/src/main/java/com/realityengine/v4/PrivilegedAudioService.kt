package com.realityengine.v4

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Parcel
import androidx.annotation.Keep

/**
 * Binder endpoint instantiated by Shizuku as a UserService. This deliberately
 * owns the protected AudioRecord in the privileged process rather than merely
 * checking Shizuku permission from the normal application process.
 *
 * PCM transport is exposed as bounded Binder reads so the app process controls
 * backpressure and never floods Binder with unsolicited audio callbacks.
 */
@Keep
class PrivilegedAudioService : Binder() {
    private var recorder: AudioRecord? = null
    private var started = false

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_START -> {
                data.enforceInterface(DESCRIPTOR)
                val result = startCapture()
                reply?.writeNoException(); reply?.writeInt(result)
                true
            }
            TRANSACTION_READ -> {
                data.enforceInterface(DESCRIPTOR)
                val requested = data.readInt().coerceIn(320, MAX_BINDER_CHUNK)
                val buffer = ByteArray(requested)
                val count = read(buffer)
                reply?.writeNoException(); reply?.writeInt(count)
                if (count > 0) reply?.writeByteArray(buffer.copyOf(count))
                true
            }
            TRANSACTION_STOP -> {
                data.enforceInterface(DESCRIPTOR); stopCapture()
                reply?.writeNoException(); true
            }
            TRANSACTION_STATUS -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException(); reply?.writeInt(if (started) 1 else 0)
                true
            }
            DESTROY_TRANSACTION -> {
                stopCapture(); reply?.writeNoException(); true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    @Synchronized private fun startCapture(): Int {
        if (started) return START_OK
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return START_FORMAT_UNAVAILABLE
        val candidate = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_CALL, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 8192))
        } catch (_: Throwable) { null } ?: return START_SOURCE_BLOCKED
        if (candidate.state != AudioRecord.STATE_INITIALIZED) { runCatching { candidate.release() }; return START_SOURCE_BLOCKED }
        return try {
            candidate.startRecording()
            if (candidate.recordingState != AudioRecord.RECORDSTATE_RECORDING) { candidate.release(); START_SOURCE_BLOCKED }
            else { recorder = candidate; started = true; START_OK }
        } catch (_: Throwable) { runCatching { candidate.release() }; START_SOURCE_BLOCKED }
    }

    private fun read(buffer: ByteArray): Int {
        val active = recorder ?: return READ_NOT_RUNNING
        return try { active.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) } catch (_: Throwable) { READ_FAILED }
    }

    @Synchronized private fun stopCapture() {
        started = false
        val active = recorder; recorder = null
        runCatching { active?.stop() }; runCatching { active?.release() }
    }

    @Keep fun destroy() { stopCapture(); System.exit(0) }

    companion object {
        const val DESCRIPTOR = "com.realityengine.v4.PrivilegedAudioService"
        const val SAMPLE_RATE = 16_000
        const val TRANSACTION_START = FIRST_CALL_TRANSACTION
        const val TRANSACTION_READ = FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_STOP = FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_STATUS = FIRST_CALL_TRANSACTION + 3
        const val DESTROY_TRANSACTION = 16777115
        const val START_OK = 0
        const val START_FORMAT_UNAVAILABLE = -1
        const val START_SOURCE_BLOCKED = -2
        const val READ_NOT_RUNNING = -3
        const val READ_FAILED = -4
        const val MAX_BINDER_CHUNK = 16_384
    }
}
