package com.realityengine.v4

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shell-process half of Reality Engine's scrcpy call-audio transport.
 *
 * This class deliberately owns every blocking primitive (Process, LocalServerSocket and relay
 * reads) on worker threads. The app process receives only the read end of a kernel pipe.
 *
 * The scrcpy-server artifact and its exact version-specific arguments are supplied by the caller;
 * this keeps the transport independent from a particular scrcpy protocol revision and prevents
 * silently launching a mismatched server/client pair.
 */
class ScrcpyShellAudioRelay : Closeable {
    data class LaunchSpec(
        val serverJar: File,
        val serverMainClass: String,
        val serverArgs: List<String>,
        val socketPrefix: String = "scrcpy_"
    )

    sealed class StartResult {
        data class Started(val pipeReadEnd: ParcelFileDescriptor, val socketName: String) : StartResult()
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
        if (!spec.serverJar.isFile) {
            active.set(false)
            return StartResult.Failed("scrcpy-server artifact missing")
        }

        return try {
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            pipeWriteEnd = pipe[1]
            val socketName = spec.socketPrefix + UUID.randomUUID().toString().replace("-", "").take(16)
            serverSocket = LocalServerSocket(socketName)

            relayThread = Thread({ relayLoop() }, "reality-scrcpy-shell-relay").apply {
                isDaemon = true
                start()
            }

            val command = ArrayList<String>(2 + spec.serverArgs.size).apply {
                add("app_process")
                add("/")
                add(spec.serverMainClass)
                addAll(spec.serverArgs)
            }
            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment()["CLASSPATH"] = spec.serverJar.absolutePath }
                .start()

            logThread = Thread({
                runCatching {
                    process?.inputStream?.bufferedReader()?.use { reader ->
                        while (active.get() && reader.readLine() != null) Unit
                    }
                }
            }, "reality-scrcpy-shell-log").apply {
                isDaemon = true
                start()
            }

            StartResult.Started(readEnd, socketName)
        } catch (t: Throwable) {
            close()
            StartResult.Failed("scrcpy relay startup failed: ${t.javaClass.simpleName}")
        }
    }

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
                    if (count > 0) {
                        output.write(buffer, 0, count)
                        output.flush()
                    }
                }
            }
        } catch (_: Throwable) {
            // Closing the socket/descriptor intentionally interrupts blocking I/O during shutdown.
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
        relayThread?.interrupt()
        logThread?.interrupt()
        process = null
        clientSocket = null
        serverSocket = null
        pipeWriteEnd = null
        relayThread = null
        logThread = null
    }

    fun isActive(): Boolean = active.get()

    companion object {
        private const val RELAY_BUFFER_BYTES = 32 * 1024
        private const val PROCESS_STOP_GRACE_SECONDS = 2L
    }
}
