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
    sealed class ReadResult { data class Pcm(val bytes:ByteArray):ReadResult();data object Empty:ReadResult();data class Failed(val code:Int):ReadResult() }
    @Volatile private var remote:IBinder?=null;@Volatile private var activeConnection:ServiceConnection?=null;private val lock=Any()
    private val args:Shizuku.UserServiceArgs get()=Shizuku.UserServiceArgs(ComponentName("com.realityengine.v4",PrivilegedAudioService::class.java.name)).processNameSuffix("call_audio").daemon(true).tag("reality_call_audio").version(2)
    fun connect(timeoutMs:Long=6000):Boolean{
        remote?.takeIf{it.isBinderAlive}?.let{return true}
        if(!ShizukuAudioStatus.binderAvailable()||!ShizukuAudioStatus.permissionGranted())return false
        synchronized(lock){
            remote?.takeIf{it.isBinderAlive}?.let{return true}
            activeConnection?.let{unbindLocked(it)}
            repeat(2){attempt->
                val connected=CountDownLatch(1)
                val connection=object:ServiceConnection{
                    override fun onServiceConnected(name:ComponentName?,service:IBinder?){remote=service;connected.countDown()}
                    override fun onServiceDisconnected(name:ComponentName?){if(activeConnection===this){remote=null;activeConnection=null}}
                    override fun onBindingDied(name:ComponentName?){if(activeConnection===this){remote=null;activeConnection=null};connected.countDown()}
                    override fun onNullBinding(name:ComponentName?){if(activeConnection===this){remote=null;activeConnection=null};connected.countDown()}
                }
                activeConnection=connection
                val ok=try{Shizuku.bindUserService(args,connection);connected.await(timeoutMs,TimeUnit.MILLISECONDS)&&remote?.isBinderAlive==true}catch(_:Throwable){false}
                if(ok)return true
                remote=null;unbindLocked(connection)
                if(attempt==0)try{Thread.sleep(250)}catch(_:InterruptedException){Thread.currentThread().interrupt();return false}
            }
            return false
        }
    }
    fun start():Int=transactInt(PrivilegedAudioService.TRANSACTION_START)
    fun activeSource():Int=transactInt(PrivilegedAudioService.TRANSACTION_STATUS)
    fun captureMode():Int=transactInt(PrivilegedAudioService.TRANSACTION_CAPTURE_MODE)
    fun probeMask():Int=transactInt(PrivilegedAudioService.TRANSACTION_PROBE_STATUS)
    fun activeSourceLabel():String=when(activeSource()){PrivilegedAudioService.SOURCE_DUAL->"DUAL_RX_TX";android.media.MediaRecorder.AudioSource.VOICE_CALL->"VOICE_CALL";android.media.MediaRecorder.AudioSource.VOICE_DOWNLINK->"VOICE_DOWNLINK";android.media.MediaRecorder.AudioSource.VOICE_UPLINK->"VOICE_UPLINK";android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION->"VOICE_COMMUNICATION";else->"UNKNOWN"}
    fun probeSummary():String{val mask=probeMask();val attempted=buildList{if(mask and PrivilegedAudioService.PROBE_VOICE_CALL!=0)add("VOICE_CALL");if(mask and PrivilegedAudioService.PROBE_VOICE_DOWNLINK!=0)add("VOICE_DOWNLINK");if(mask and PrivilegedAudioService.PROBE_VOICE_UPLINK!=0)add("VOICE_UPLINK");if(mask and PrivilegedAudioService.PROBE_VOICE_COMMUNICATION!=0)add("VOICE_COMMUNICATION")};return if(mask and PrivilegedAudioService.PROBE_DUAL_SUCCESS!=0)"Protected dual RX/TX active" else if(mask and PrivilegedAudioService.PROBE_SUCCESS!=0)"Protected source active: ${activeSourceLabel()}" else if(attempted.isEmpty())"No protected source probe completed" else "Protected sources blocked: ${attempted.joinToString(" / ")}"}
    fun readResult(maxBytes:Int):ReadResult=readTransaction(PrivilegedAudioService.TRANSACTION_READ,maxBytes)
    fun readDownlinkResult(maxBytes:Int):ReadResult=readTransaction(PrivilegedAudioService.TRANSACTION_READ_DOWNLINK,maxBytes)
    fun readUplinkResult(maxBytes:Int):ReadResult=readTransaction(PrivilegedAudioService.TRANSACTION_READ_UPLINK,maxBytes)
    fun read(maxBytes:Int):ByteArray?=(readResult(maxBytes) as? ReadResult.Pcm)?.bytes
    fun stop(){transactInt(PrivilegedAudioService.TRANSACTION_STOP)}
    fun disconnect(){synchronized(lock){stop();remote=null;activeConnection?.let{unbindLocked(it)}}}
    private fun readTransaction(code:Int,maxBytes:Int):ReadResult{val binder=remote?.takeIf{it.isBinderAlive}?:return ReadResult.Failed(PrivilegedAudioService.READ_DEAD_OBJECT);val data=Parcel.obtain();val reply=Parcel.obtain();return try{data.writeInterfaceToken(PrivilegedAudioService.DESCRIPTOR);data.writeInt(maxBytes.coerceIn(320,PrivilegedAudioService.MAX_BINDER_CHUNK));if(!binder.transact(code,data,reply,0))return ReadResult.Failed(PrivilegedAudioService.READ_FAILED);reply.readException();val count=reply.readInt();when{count>0->{val bytes=reply.createByteArray()?:ByteArray(0);ReadResult.Pcm(if(bytes.size==count)bytes else bytes.copyOf(count.coerceAtMost(bytes.size)))};count==0->ReadResult.Empty;else->ReadResult.Failed(count)}}catch(_:Throwable){ReadResult.Failed(PrivilegedAudioService.READ_FAILED)}finally{reply.recycle();data.recycle()}}
    private fun unbindLocked(connection:ServiceConnection){runCatching{Shizuku.unbindUserService(args,connection,false)};if(activeConnection===connection)activeConnection=null}
    private fun transactInt(code:Int):Int{val binder=remote?.takeIf{it.isBinderAlive}?:return PrivilegedAudioService.START_SOURCE_BLOCKED;val data=Parcel.obtain();val reply=Parcel.obtain();return try{data.writeInterfaceToken(PrivilegedAudioService.DESCRIPTOR);if(!binder.transact(code,data,reply,0))PrivilegedAudioService.START_SOURCE_BLOCKED else{reply.readException();when(code){PrivilegedAudioService.TRANSACTION_START,PrivilegedAudioService.TRANSACTION_STATUS,PrivilegedAudioService.TRANSACTION_PROBE_STATUS,PrivilegedAudioService.TRANSACTION_CAPTURE_MODE->reply.readInt();else->PrivilegedAudioService.START_OK}}}catch(_:Throwable){PrivilegedAudioService.START_SOURCE_BLOCKED}finally{reply.recycle();data.recycle()}}
}
