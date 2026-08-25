package com.realityengine.v4

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** App-process client for the privileged call-audio UserService. */
object ShizukuAudioClient {
    @Volatile private var remote: IBinder? = null
    @Volatile private var binding = false
    private val lock = Any()

    private val args: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(
            ComponentName("com.realityengine.v4", PrivilegedAudioService::class.java.name)
        ).processNameSuffix("call_audio").daemon(false).tag("reality_call_audio").version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) { remote = service; binding = false; lock.notifyAll() }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) { remote = null; binding = false; lock.notifyAll() }
        }
    }

    fun connect(timeoutMs: Long = 4000): Boolean {
        remote?.takeIf { it.isBinderAlive }?.let { return true }
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) return false
        synchronized(lock) {
            if (!binding) {
                binding = true
                try { Shizuku.bindUserService(args, connection) }
                catch (_: Throwable) { binding = false; return false }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (remote?.isBinderAlive != true && binding) {
                val left = deadline - System.currentTimeMillis(); if (left <= 0) break
                try { lock.wait(left) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }
            return remote?.isBinderAlive == true
        }
    }

    fun start(): Int = transactInt(PrivilegedAudioService.TRANSACTION_START) { }

    fun read(maxBytes: Int): ByteArray? {
        val binder = remote?.takeIf { it.isBinderAlive } ?: return null
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedAudioService.DESCRIPTOR)
            data.writeInt(maxBytes.coerceIn(320, PrivilegedAudioService.MAX_BINDER_CHUNK))
            if (!binder.transact(PrivilegedAudioService.TRANSACTION_READ, data, reply, 0)) return null
            reply.readException(); val count = reply.readInt()
            if (count > 0) reply.createByteArray()?.let { if (it.size == count) it else it.copyOf(count.coerceAtMost(it.size)) } else null
        } catch (_: Throwable) { null } finally { reply.recycle(); data.recycle() }
    }

    fun stop() { transactInt(PrivilegedAudioService.TRANSACTION_STOP) { } }

    fun disconnect() {
        stop()
        try { Shizuku.unbindUserService(args, connection, true) } catch (_: Throwable) { }
        synchronized(lock) { remote = null; binding = false }
    }

    private inline fun transactInt(code: Int, write: (Parcel) -> Unit): Int {
        val binder = remote?.takeIf { it.isBinderAlive } ?: return PrivilegedAudioService.START_SOURCE_BLOCKED
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedAudioService.DESCRIPTOR); write(data)
            if (!binder.transact(code, data, reply, 0)) PrivilegedAudioService.START_SOURCE_BLOCKED
            else { reply.readException(); if (code == PrivilegedAudioService.TRANSACTION_START) reply.readInt() else PrivilegedAudioService.START_OK }
        } catch (_: Throwable) { PrivilegedAudioService.START_SOURCE_BLOCKED }
        finally { reply.recycle(); data.recycle() }
    }
}
