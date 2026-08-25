package com.realityengine.v4

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** 16 kHz mono PCM capture backed by the privileged Shizuku UserService. */
class VoiceCallPcmCapture(context:Context){
 data class Format(val sampleRate:Int=SAMPLE_RATE,val channels:Int=1,val bitsPerSample:Int=16)
 sealed class StartResult{data class Started(val format:Format,val source:String):StartResult();data class Unavailable(val reason:String):StartResult()}
 @Suppress("unused") private val appContext=context.applicationContext;private val running=AtomicBoolean(false);@Volatile private var worker:Thread?=null;@Volatile var activeSource:String?=null;private set
 fun start(onPcm:(ByteArray,Int)->Unit,onStopped:(String?)->Unit={}):StartResult{
  if(!running.compareAndSet(false,true))return StartResult.Unavailable("Capture already running")
  if(!ShizukuAudioStatus.binderAvailable()||!ShizukuAudioStatus.permissionGranted()){running.set(false);return StartResult.Unavailable("Shizuku authorization required")}
  if(!ShizukuAudioClient.connect()){running.set(false);return StartResult.Unavailable("Privileged audio service unavailable")}
  val startCode=ShizukuAudioClient.start();if(startCode!=PrivilegedAudioService.START_OK){running.set(false);ShizukuAudioClient.disconnect();return StartResult.Unavailable("VOICE_CALL unavailable in privileged service")}
  activeSource="SHIZUKU_VOICE_CALL";worker=Thread({var failure:String?=null;var lastAudio=SystemClock.elapsedRealtime();var receivedAny=false
   try{while(running.get()){val pcm=ShizukuAudioClient.read(READ_CHUNK_BYTES);if(!running.get())break;if(pcm==null||pcm.isEmpty()){if(SystemClock.elapsedRealtime()-lastAudio>STALL_TIMEOUT_MS){failure=if(receivedAny)"Privileged call audio stream stalled" else "Privileged call audio opened but produced no PCM";break};try{Thread.sleep(READ_IDLE_MS)}catch(_:InterruptedException){if(!running.get())break};continue};receivedAny=true;lastAudio=SystemClock.elapsedRealtime();onPcm(pcm,pcm.size)}}catch(_:Throwable){if(running.get())failure="Privileged call audio stream stopped unexpectedly"}finally{running.set(false);activeSource=null;ShizukuAudioClient.disconnect();worker=null;onStopped(failure)}
  },"reality-shizuku-call-pcm").apply{isDaemon=true;start()};return StartResult.Started(Format(),"SHIZUKU_VOICE_CALL")
 }
 fun stop(){if(!running.getAndSet(false))return;activeSource=null;ShizukuAudioClient.stop();worker?.interrupt()}
 fun isRunning():Boolean=running.get()
 companion object{const val SAMPLE_RATE=16_000;private const val READ_CHUNK_BYTES=8_192;private const val READ_IDLE_MS=10L;private const val STALL_TIMEOUT_MS=5_000L}
}
