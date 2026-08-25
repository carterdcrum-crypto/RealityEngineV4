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
    @Volatile private var activeConnection: ServiceConnection? = null
    private val lock = Any()

    private val args: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(ComponentName("com.realityengine.v4", PrivilegedAudioService::class.java.name))
            .processNameSuffix("call_audio").daemon(false).tag("reality_call_audio").version(1)

    fun connect(timeoutMs: Long = 4000): Boolean {
        remote?.takeIf { it.isBinderAlive }?.let { return true }
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) return false
        synchronized(lock) {
            remote?.takeIf { it.isBinderAlive }?.let { return true }
            val connected = CountDownLatch(1)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) { remote=service; connected.countDown() }
                override fun onServiceDisconnected(name: ComponentName?) { remote=null }
            }
            activeConnection=connection
            return try {
                Shizuku.bindUserService(args,connection)
                val ok=connected.await(timeoutMs,TimeUnit.MILLISECONDS)&&remote?.isBinderAlive==true
                if(!ok) unbindLocked(connection)
                ok
            } catch (_:Throwable) { unbindLocked(connection); false }
        }
    }

    fun start():Int=transactInt(PrivilegedAudioService.TRANSACTION_START)

    fun read(maxBytes:Int):ByteArray? {
        val binder=remote?.takeIf{it.isBinderAlive}?:return null;val data=Parcel.obtain();val reply=Parcel.obtain()
        return try { data.writeInterfaceToken(PrivilegedAudioService.DESCRIPTOR);data.writeInt(maxBytes.coerceIn(320,PrivilegedAudioService.MAX_BINDER_CHUNK));if(!binder.transact(PrivilegedAudioService.TRANSACTION_READ,data,reply,0))return null;reply.readException();val count=reply.readInt();if(count>0)reply.createByteArray()?.let{if(it.size==count)it else it.copyOf(count.coerceAtMost(it.size))}else null } catch(_:Throwable){null} finally{reply.recycle();data.recycle()}
    }

    fun stop(){transactInt(PrivilegedAudioService.TRANSACTION_STOP)}

    fun disconnect(){
        synchronized(lock){
            stop();remote=null
            activeConnection?.let{unbindLocked(it)}
        }
    }

    private fun unbindLocked(connection:ServiceConnection){runCatching{Shizuku.unbindUserService(args,connection,true)};if(activeConnection===connection)activeConnection=null}

    private fun transactInt(code:Int):Int {
        val binder=remote?.takeIf{it.isBinderAlive}?:return PrivilegedAudioService.START_SOURCE_BLOCKED;val data=Parcel.obtain();val reply=Parcel.obtain()
        return try { data.writeInterfaceToken(PrivilegedAudioService.DESCRIPTOR);if(!binder.transact(code,data,reply,0))PrivilegedAudioService.START_SOURCE_BLOCKED else{reply.readException();if(code==PrivilegedAudioService.TRANSACTION_START)reply.readInt()else PrivilegedAudioService.START_OK} } catch(_:Throwable){PrivilegedAudioService.START_SOURCE_BLOCKED} finally{reply.recycle();data.recycle()}
    }
}
