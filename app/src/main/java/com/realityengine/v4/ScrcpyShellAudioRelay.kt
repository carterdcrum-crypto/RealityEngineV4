package com.realityengine.v4

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Shell-process half of the pinned scrcpy 4.0 audio transport. */
class ScrcpyShellAudioRelay : Closeable {
    data class LaunchSpec(
        val serverJar: File,
        val audioSource: String = "voice_communication",
        val audioCodec: String = "raw"
    )

    sealed class StartResult {
        data class Started(val pipeReadEnd: ParcelFileDescriptor, val scid: String) : StartResult()
        data class Failed(val reason: String) : StartResult()
    }

    private val active = AtomicBoolean(false)
    @Volatile private var process: Process? = null
    @Volatile private var serverSocket: LocalServerSocket? = null
    @Volatile private var clientSocket: LocalSocket? = null
    @Volatile private var pipeWriteEnd: ParcelFileDescriptor? = null
    @Volatile private var relayThread: Thread? = null
    @Volatile private var logThread: Thread? = null

    fun start(spec: LaunchSpec): StartResult {
        if (!active.compareAndSet(false, true)) return StartResult.Failed("scrcpy relay already active")
        if (!spec.serverJar.isFile) return failStart("scrcpy-server artifact missing")
        if (!sha256(spec.serverJar).equals(SERVER_SHA256, ignoreCase = true)) {
            return failStart("scrcpy-server integrity check failed")
        }

        return try {
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            pipeWriteEnd = pipe[1]
            val scid = randomScid()
            serverSocket = LocalServerSocket(SOCKET_PREFIX + scid)
            relayThread = Thread({ relayLoop() }, "reality-scrcpy-shell-relay").apply { isDaemon = true; start() }

            val command = arrayListOf(
                "app_process", "/", SERVER_MAIN_CLASS, SERVER_VERSION,
                "log_level=info", "video=false", "audio=true", "control=false",
                "tunnel_forward=false", "send_dummy_byte=false", "scid=$scid",
                "audio_source=${spec.audioSource}", "audio_codec=${spec.audioCodec}",
                "send_device_meta=false", "send_frame_meta=true", "send_stream_meta=true"
            )
            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment()["CLASSPATH"] = spec.serverJar.absolutePath }
                .start()
            logThread = Thread({
                runCatching { process?.inputStream?.bufferedReader()?.use { r -> while (active.get() && r.readLine() != null) Unit } }
            }, "reality-scrcpy-shell-log").apply { isDaemon = true; start() }
            StartResult.Started(readEnd, scid)
        } catch (t: Throwable) {
            close()
            StartResult.Failed("scrcpy relay startup failed: ${t.javaClass.simpleName}")
        }
    }

    private fun failStart(reason: String): StartResult.Failed { active.set(false); return StartResult.Failed(reason) }

    private fun relayLoop() {
        try {
            val connection = serverSocket?.accept() ?: return
            clientSocket = connection
            val output = pipeWriteEnd?.let(ParcelFileDescriptor::AutoCloseOutputStream) ?: return
            connection.inputStream.use { input ->
                val buffer = ByteArray(RELAY_BUFFER_BYTES)
                while (active.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) { output.write(buffer, 0, count); output.flush() }
                }
            }
        } catch (_: Throwable) {
            // Closing descriptors intentionally interrupts blocking I/O during shutdown.
        } finally {
            runCatching { pipeWriteEnd?.close() }
            pipeWriteEnd = null
        }
    }

    override fun close() {
        if (!active.getAndSet(false)) return
        val child = process
        runCatching { child?.destroy() }
        runCatching { child?.waitFor(PROCESS_STOP_GRACE_SECONDS, TimeUnit.SECONDS) }
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { pipeWriteEnd?.close() }
        relayThread?.interrupt(); logThread?.interrupt()
        process = null; clientSocket = null; serverSocket = null; pipeWriteEnd = null
        relayThread = null; logThread = null
    }

    fun isActive(): Boolean = active.get()

    private fun randomScid(): String = SecureRandom().nextInt(Int.MAX_VALUE).toString(16).padStart(8, '0')
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; if (n > 0) digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SERVER_VERSION = "4.0"
        const val SERVER_SHA256 = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a"
        const val SERVER_MAIN_CLASS = "com.genymobile.scrcpy.Server"
        private const val SOCKET_PREFIX = "scrcpy_"
        private const val RELAY_BUFFER_BYTES = 32 * 1024
        private const val PROCESS_STOP_GRACE_SECONDS = 2L
    }
}
