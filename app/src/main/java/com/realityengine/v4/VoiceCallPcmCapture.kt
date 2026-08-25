package com.realityengine.v4

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** 16 kHz mono PCM capture backed by the privileged Shizuku UserService. */
class VoiceCallPcmCapture(context:Context){
 data class Format(val sampleRate:Int=SAMPLE_RATE,val channels:Int=1,val bitsPerSample:Int=16)
 enum class Direction{MIXED,DOWNLINK,UPLINK}
 enum class Mode{SINGLE,DUAL}
 sealed class StartResult{data class Started(val format:Format,val source:String,val mode:Mode):StartResult();data class Unavailable(val reason:String):StartResult()}
 @Suppress("unused") private val appContext=context.applicationContext;private val running=AtomicBoolean(false);@Volatile private var worker:Thread?=null;@Volatile var activeSource:String?=null;private set
 fun start(onPcm:(ByteArray,Int)->Unit,onStopped:(String?)->Unit={}):StartResult=start(onFrame={bytes,length,_->onPcm(bytes,length)},onStarted={},onStopped=onStopped)
 fun start(onFrame:(ByteArray,Int,Direction)->Unit,onStarted:(Mode)->Unit={},onStopped:(String?)->Unit={}):StartResult{
  if(!running.compareAndSet(false,true))return StartResult.Unavailable("Capture already running")
  val shizukuState=ShizukuAudioStatus.state()
  if(shizukuState!=ShizukuAudioStatus.State.READY){running.set(false);return StartResult.Unavailable(ShizukuAudioStatus.diagnostic())}
  var connected=false
  repeat(CONNECT_ATTEMPTS){attempt->if(ShizukuAudioClient.connect()){connected=true;return@repeat};if(attempt<CONNECT_ATTEMPTS-1)try{Thread.sleep(CONNECT_RETRY_MS)}catch(_:InterruptedException){Thread.currentThread().interrupt()}}
  if(!connected){running.set(false);return StartResult.Unavailable("${ShizukuAudioStatus.diagnostic()}; privileged UserService failed to bind after $CONNECT_ATTEMPTS attempts")}
  val startCode=ShizukuAudioClient.start();if(startCode!=PrivilegedAudioService.START_OK){val probe=ShizukuAudioClient.probeSummary();running.set(false);ShizukuAudioClient.disconnect();return StartResult.Unavailable("$probe (code $startCode)")}
  val source=ShizukuAudioClient.activeSourceLabel();val dual=ShizukuAudioClient.captureMode()==PrivilegedAudioService.CAPTURE_MODE_DUAL;val mode=if(dual)Mode.DUAL else Mode.SINGLE;activeSource="SHIZUKU_$source";onStarted(mode)
  worker=Thread({var failure:String?=null;var lastAudio=SystemClock.elapsedRealtime();var receivedAny=false
   fun accept(result:ShizukuAudioClient.ReadResult,direction:Direction):Boolean=when(result){is ShizukuAudioClient.ReadResult.Pcm->{val pcm=result.bytes;if(pcm.isNotEmpty()){receivedAny=true;lastAudio=SystemClock.elapsedRealtime();onFrame(pcm,pcm.size,direction)};true};ShizukuAudioClient.ReadResult.Empty->true;is ShizukuAudioClient.ReadResult.Failed->{failure=when(result.code){PrivilegedAudioService.READ_DEAD_OBJECT->"Android audio source died (ERROR_DEAD_OBJECT)";PrivilegedAudioService.READ_NOT_RUNNING->"Privileged recorder stopped unexpectedly";PrivilegedAudioService.READ_FAILED->"Privileged Binder/audio read failed";else->"Privileged audio read failed (code ${result.code})"};false}}
   try{while(running.get()){
    val ok=if(dual){val downOk=accept(ShizukuAudioClient.readDownlinkResult(READ_CHUNK_BYTES),Direction.DOWNLINK);val upOk=if(downOk)accept(ShizukuAudioClient.readUplinkResult(READ_CHUNK_BYTES),Direction.UPLINK)else false;downOk&&upOk}else accept(ShizukuAudioClient.readResult(READ_CHUNK_BYTES),Direction.MIXED)
    if(!ok)break
    if(SystemClock.elapsedRealtime()-lastAudio>STALL_TIMEOUT_MS){failure=if(receivedAny)"Privileged $source audio stream stalled (no PCM for 5s)" else "$source opened but Samsung returned no PCM for 5s";break}
    try{Thread.sleep(READ_IDLE_MS)}catch(_:InterruptedException){if(!running.get())break}
   }}catch(t:Throwable){if(running.get())failure="Privileged call audio exception: ${t.javaClass.simpleName}"}finally{running.set(false);activeSource=null;ShizukuAudioClient.disconnect();worker=null;onStopped(failure)}
  },"reality-shizuku-call-pcm").apply{isDaemon=true;start()};return StartResult.Started(Format(channels=if(dual)2 else 1),"SHIZUKU_$source",mode)
 }
 fun stop(){if(!running.getAndSet(false))return;activeSource=null;ShizukuAudioClient.stop();worker?.interrupt()}
 fun isRunning():Boolean=running.get()
 companion object{const val SAMPLE_RATE=16_000;private const val READ_CHUNK_BYTES=8_192;private const val READ_IDLE_MS=10L;private const val STALL_TIMEOUT_MS=5_000L;private const val CONNECT_ATTEMPTS=3;private const val CONNECT_RETRY_MS=350L}
}
