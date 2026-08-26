package com.realityengine.v4

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-side transport boundary for the scrcpy/Shizuku call-audio path.
 *
 * The privileged shell service owns scrcpy-server and the local socket. It returns the read end
 * of a kernel pipe to this process. This class drains that pipe on its own worker thread so no
 * binder/socket/file I/O can block the dialer UI thread.
 *
 * Bytes delivered here are the scrcpy audio transport stream, not assumed to be PCM. Framing /
 * codec parsing is intentionally the next layer; keeping that boundary explicit prevents encoded
 * packets from accidentally being sent to Deepgram as linear16 audio.
 */
class ScrcpyAudioTransport : Closeable {
    sealed class State {
        data object Idle : State()
        data object Starting : State()
        data object Streaming : State()
        data object Closed : State()
        data class Failed(val reason: String) : State()
    }

    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var descriptor: ParcelFileDescriptor? = null
    @Volatile var state: State = State.Idle
        private set

    fun attach(
        pipeReadEnd: ParcelFileDescriptor,
        onBytes: (ByteArray, Int) -> Unit,
        onClosed: (String?) -> Unit = {}
    ): Boolean {
        if (!running.compareAndSet(false, true)) {
            pipeReadEnd.closeQuietly()
            return false
        }
        descriptor = pipeReadEnd
        state = State.Starting
        worker = Thread({
            var failure: String? = null
            try {
                FileInputStream(pipeReadEnd.fileDescriptor).use { input ->
                    state = State.Streaming
                    val buffer = ByteArray(RELAY_BUFFER_BYTES)
                    while (running.get()) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) onBytes(buffer.copyOf(count), count)
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) failure = "scrcpy audio pipe failed: ${t.javaClass.simpleName}"
            } finally {
                running.set(false)
                descriptor?.closeQuietly()
                descriptor = null
                worker = null
                state = failure?.let(State::Failed) ?: State.Closed
                onClosed(failure)
            }
        }, "reality-scrcpy-audio-pipe").apply {
            isDaemon = true
            start()
        }
        return true
    }

    override fun close() {
        running.set(false)
        descriptor?.closeQuietly()
        descriptor = null
        worker?.interrupt()
    }

    fun isStreaming(): Boolean = running.get() && state is State.Streaming

    private fun ParcelFileDescriptor.closeQuietly() {
        runCatching { close() }
    }

    companion object {
        private const val RELAY_BUFFER_BYTES = 32 * 1024
    }
}
