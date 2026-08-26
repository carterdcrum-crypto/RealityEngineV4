package com.realityengine.v4

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import androidx.annotation.Keep
import java.io.File

/** Privileged Shizuku UserService for carrier-call audio capture. */
@Keep
class PrivilegedAudioService : Binder {
    @Keep constructor() : super()
    @Keep constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context?) : super()

    private var recorder: AudioRecord? = null
    private var downlinkRecorder: AudioRecord? = null
    private var uplinkRecorder: AudioRecord? = null
    private var scrcpyRelay: ScrcpyShellAudioRelay? = null
    private var scrcpyDownlinkRelay: ScrcpyShellAudioRelay? = null
    private var scrcpyUplinkRelay: ScrcpyShellAudioRelay? = null
    @Volatile private var started = false
    @Volatile private var dualStream = false
    @Volatile private var activeSource = SOURCE_NONE
    @Volatile private var probeMask = 0
    @Volatile private var bootstrapHealth = ShellAudioBootstrap.Health.FAILED

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
        TRANSACTION_START -> {
            data.enforceInterface(DESCRIPTOR)
            val result = startCapture()
            reply?.writeNoException(); reply?.writeInt(result); true
        }
        TRANSACTION_START_SCRCPY -> {
            data.enforceInterface(DESCRIPTOR)
            val serverPath = data.readString().orEmpty()
            val pfd = startScrcpyCapture(serverPath)
            reply?.writeNoException()
            if (pfd == null) {
                reply?.writeInt(0)
            } else {
                reply?.writeInt(1)
                pfd.writeToParcel(reply!!, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }
            true
        }
        TRANSACTION_START_SCRCPY_DUAL -> {
            data.enforceInterface(DESCRIPTOR)
            val serverPath = data.readString().orEmpty()
            val pipes = startScrcpyDualCapture(serverPath)
            reply?.writeNoException()
            if (pipes == null) {
                reply?.writeInt(0)
            } else {
                reply?.writeInt(1)
                pipes.first.writeToParcel(reply!!, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                pipes.second.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }
            true
        }
        TRANSACTION_READ -> {
            data.enforceInterface(DESCRIPTOR)
            val requested = data.readInt().coerceIn(320, MAX_BINDER_CHUNK)
            val buffer = ByteArray(requested)
            val count = read(buffer)
            reply?.writeNoException(); reply?.writeInt(count)
            if (count > 0) reply?.writeByteArray(buffer.copyOf(count)); true
        }
        TRANSACTION_READ_DOWNLINK -> {
            data.enforceInterface(DESCRIPTOR)
            val requested = data.readInt().coerceIn(320, MAX_BINDER_CHUNK)
            val buffer = ByteArray(requested)
            val count = readFrom(downlinkRecorder, buffer)
            reply?.writeNoException(); reply?.writeInt(count)
            if (count > 0) reply?.writeByteArray(buffer.copyOf(count)); true
        }
        TRANSACTION_READ_UPLINK -> {
            data.enforceInterface(DESCRIPTOR)
            val requested = data.readInt().coerceIn(320, MAX_BINDER_CHUNK)
            val buffer = ByteArray(requested)
            val count = readFrom(uplinkRecorder, buffer)
            reply?.writeNoException(); reply?.writeInt(count)
            if (count > 0) reply?.writeByteArray(buffer.copyOf(count)); true
        }
        TRANSACTION_STOP -> {
            data.enforceInterface(DESCRIPTOR)
            stopAllCapture(); reply?.writeNoException(); true
        }
        TRANSACTION_STATUS -> {
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException(); reply?.writeInt(currentSource()); true
        }
        TRANSACTION_PROBE_STATUS -> {
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException(); reply?.writeInt(probeMask); true
        }
        TRANSACTION_BOOTSTRAP_STATUS -> {
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException(); reply?.writeInt(bootstrapHealth.ordinal); true
        }
        TRANSACTION_AUDIBILITY_STATUS -> {
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException(); reply?.writeInt(0); true
        }
        TRANSACTION_CAPTURE_MODE -> {
            data.enforceInterface(DESCRIPTOR)
            val mode = when {
                scrcpyDownlinkRelay?.isActive() == true && scrcpyUplinkRelay?.isActive() == true -> CAPTURE_MODE_DUAL
                scrcpyRelay?.isActive() == true -> CAPTURE_MODE_SINGLE
                dualStream -> CAPTURE_MODE_DUAL
                started -> CAPTURE_MODE_SINGLE
                else -> CAPTURE_MODE_NONE
            }
            reply?.writeNoException(); reply?.writeInt(mode); true
        }
        TRANSACTION_DUAL_PEAKS -> {
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException(); reply?.writeInt(0); reply?.writeInt(0); true
        }
        DESTROY_TRANSACTION -> {
            stopAllCapture(); reply?.writeNoException(); true
        }
        else -> super.onTransact(code, data, reply, flags)
    }

    @Synchronized
    private fun startScrcpyCapture(serverPath: String): ParcelFileDescriptor? {
        if (serverPath.isBlank()) return null
        stopAllCapture()
        val relay = ScrcpyShellAudioRelay()
        return when (val result = relay.start(
            ScrcpyShellAudioRelay.LaunchSpec(
                serverJar = File(serverPath),
                audioSource = "voice-call",
                audioCodec = "raw"
            )
        )) {
            is ScrcpyShellAudioRelay.StartResult.Started -> {
                scrcpyRelay = relay
                activeSource = SOURCE_SCRCPY
                result.pipeReadEnd
            }
            is ScrcpyShellAudioRelay.StartResult.Failed -> {
                relay.close()
                scrcpyRelay = null
                activeSource = SOURCE_NONE
                null
            }
        }
    }

    /**
     * Starts two independent scrcpy-server audio sessions so the call directions never get mixed.
     * Pipe order is DOWNLINK (remote caller) first, UPLINK (local microphone) second.
     */
    @Synchronized
    private fun startScrcpyDualCapture(serverPath: String): Pair<ParcelFileDescriptor, ParcelFileDescriptor>? {
        if (serverPath.isBlank()) return null
        stopAllCapture()
        val server = File(serverPath)
        val downRelay = ScrcpyShellAudioRelay()
        val down = downRelay.start(
            ScrcpyShellAudioRelay.LaunchSpec(
                serverJar = server,
                audioSource = "voice-call-downlink",
                audioCodec = "raw"
            )
        )
        if (down !is ScrcpyShellAudioRelay.StartResult.Started) {
            downRelay.close()
            return null
        }

        val upRelay = ScrcpyShellAudioRelay()
        val up = upRelay.start(
            ScrcpyShellAudioRelay.LaunchSpec(
                serverJar = server,
                audioSource = "voice-call-uplink",
                audioCodec = "raw"
            )
        )
        if (up !is ScrcpyShellAudioRelay.StartResult.Started) {
            runCatching { down.pipeReadEnd.close() }
            downRelay.close()
            upRelay.close()
            return null
        }

        scrcpyDownlinkRelay = downRelay
        scrcpyUplinkRelay = upRelay
        activeSource = SOURCE_SCRCPY_DUAL
        return down.pipeReadEnd to up.pipeReadEnd
    }

    @Synchronized
    private fun startCapture(): Int {
        if (started) return START_OK
        resetDiagnostics()
        val baseContext = runCatching {
            val at = Class.forName("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.content.Context
        }.getOrNull() ?: return START_CONTEXT_UNAVAILABLE
        val bootstrap = ShellAudioBootstrap.install(baseContext)
        bootstrapHealth = bootstrap.health
        if (bootstrap.health == ShellAudioBootstrap.Health.FAILED) return START_BOOTSTRAP_FAILED
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return START_FORMAT_UNAVAILABLE
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()

        val down = createRecorder(bootstrap.context, MediaRecorder.AudioSource.VOICE_DOWNLINK, format, min)
        val up = createRecorder(bootstrap.context, MediaRecorder.AudioSource.VOICE_UPLINK, format, min)
        probeMask = probeMask or PROBE_VOICE_DOWNLINK or PROBE_VOICE_UPLINK or PROBE_DUAL
        val downStarted = startRecorder(down)
        val upStarted = startRecorder(up)
        if (downStarted && upStarted) {
            downlinkRecorder = down; uplinkRecorder = up; dualStream = true; started = true; activeSource = SOURCE_DUAL
            probeMask = probeMask or PROBE_SUCCESS or PROBE_DUAL_SUCCESS
            return START_OK
        }
        releaseRecorder(down); releaseRecorder(up)

        val probes = arrayOf(
            MediaRecorder.AudioSource.VOICE_DOWNLINK to PROBE_VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_CALL to PROBE_VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_UPLINK to PROBE_VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to PROBE_VOICE_COMMUNICATION
        )
        for ((source, bit) in probes) {
            probeMask = probeMask or bit
            val candidate = createRecorder(bootstrap.context, source, format, min) ?: continue
            if (!startRecorder(candidate)) { releaseRecorder(candidate); continue }
            recorder = candidate; activeSource = source; started = true; probeMask = probeMask or PROBE_SUCCESS
            return START_OK
        }
        activeSource = SOURCE_NONE
        return START_SOURCE_BLOCKED
    }

    private fun currentSource(): Int = when {
        scrcpyDownlinkRelay?.isActive() == true && scrcpyUplinkRelay?.isActive() == true -> SOURCE_SCRCPY_DUAL
        scrcpyRelay?.isActive() == true -> SOURCE_SCRCPY
        started -> activeSource
        else -> SOURCE_NONE
    }

    private fun createRecorder(context: android.content.Context, source: Int, format: AudioFormat, min: Int): AudioRecord? = try {
        AudioRecord.Builder().setContext(context).setAudioSource(source).setAudioFormat(format).setBufferSizeInBytes(maxOf(min * 2, 8192)).build().takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    } catch (_: Throwable) { null }

    private fun startRecorder(candidate: AudioRecord?): Boolean {
        if (candidate == null) return false
        return try { candidate.startRecording(); candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING } catch (_: Throwable) { false }
    }

    private fun releaseRecorder(candidate: AudioRecord?) { runCatching { candidate?.stop() }; runCatching { candidate?.release() } }
    private fun read(buffer: ByteArray): Int = if (dualStream) readFrom(downlinkRecorder, buffer) else readFrom(recorder, buffer)
    private fun readFrom(active: AudioRecord?, buffer: ByteArray): Int {
        active ?: return READ_NOT_RUNNING
        if (!started) return READ_NOT_RUNNING
        val result = try { active.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING) } catch (_: Throwable) { READ_FAILED }
        if (result == AudioRecord.ERROR_DEAD_OBJECT) { stopCapture(); return READ_DEAD_OBJECT }
        if (result < 0 && result != AudioRecord.ERROR_INVALID_OPERATION && result != AudioRecord.ERROR_BAD_VALUE) return READ_FAILED
        return result.coerceAtLeast(0)
    }

    private fun resetDiagnostics() { probeMask = 0; dualStream = false }

    @Synchronized
    private fun stopCapture() {
        started = false; dualStream = false
        if (activeSource != SOURCE_SCRCPY && activeSource != SOURCE_SCRCPY_DUAL) activeSource = SOURCE_NONE
        val single = recorder; val down = downlinkRecorder; val up = uplinkRecorder
        recorder = null; downlinkRecorder = null; uplinkRecorder = null
        releaseRecorder(single); releaseRecorder(down); releaseRecorder(up)
    }

    @Synchronized
    private fun stopAllCapture() {
        stopCapture()
        scrcpyRelay?.close()
        scrcpyRelay = null
        scrcpyDownlinkRelay?.close()
        scrcpyDownlinkRelay = null
        scrcpyUplinkRelay?.close()
        scrcpyUplinkRelay = null
        activeSource = SOURCE_NONE
    }

    @Keep fun destroy() { stopAllCapture(); System.exit(0) }

    companion object {
        const val DESCRIPTOR = "com.realityengine.v4.PrivilegedAudioService"
        const val SAMPLE_RATE = 16_000
        const val SOURCE_NONE = -1
        const val SOURCE_DUAL = -2
        const val SOURCE_SCRCPY = -3
        const val SOURCE_SCRCPY_DUAL = -4
        const val TRANSACTION_START = FIRST_CALL_TRANSACTION
        const val TRANSACTION_READ = FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_STOP = FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_STATUS = FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_PROBE_STATUS = FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_BOOTSTRAP_STATUS = FIRST_CALL_TRANSACTION + 5
        const val TRANSACTION_AUDIBILITY_STATUS = FIRST_CALL_TRANSACTION + 6
        const val TRANSACTION_CAPTURE_MODE = FIRST_CALL_TRANSACTION + 7
        const val TRANSACTION_READ_DOWNLINK = FIRST_CALL_TRANSACTION + 8
        const val TRANSACTION_READ_UPLINK = FIRST_CALL_TRANSACTION + 9
        const val TRANSACTION_DUAL_PEAKS = FIRST_CALL_TRANSACTION + 10
        const val TRANSACTION_START_SCRCPY = FIRST_CALL_TRANSACTION + 11
        const val TRANSACTION_START_SCRCPY_DUAL = FIRST_CALL_TRANSACTION + 12
        const val DESTROY_TRANSACTION = 16777115
        const val START_OK = 0
        const val START_FORMAT_UNAVAILABLE = -1
        const val START_SOURCE_BLOCKED = -2
        const val READ_NOT_RUNNING = -3
        const val READ_FAILED = -4
        const val READ_DEAD_OBJECT = -5
        const val START_CONTEXT_UNAVAILABLE = -6
        const val START_BOOTSTRAP_FAILED = -7
        const val START_SILENT_SOURCE = -8
        const val MAX_BINDER_CHUNK = 16_384
        const val CAPTURE_MODE_NONE = 0
        const val CAPTURE_MODE_SINGLE = 1
        const val CAPTURE_MODE_DUAL = 2
        const val PROBE_VOICE_CALL = 1
        const val PROBE_VOICE_DOWNLINK = 2
        const val PROBE_VOICE_UPLINK = 4
        const val PROBE_SUCCESS = 8
        const val PROBE_VOICE_COMMUNICATION = 16
        const val PROBE_AUDIBLE = 32
        const val PROBE_DUAL = 64
        const val PROBE_DUAL_SUCCESS = 128
    }
}
