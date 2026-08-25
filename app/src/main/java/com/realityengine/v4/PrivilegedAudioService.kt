package com.realityengine.v4

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Parcel
import android.os.SystemClock
import androidx.annotation.Keep
import kotlin.math.abs

/** Privileged Shizuku UserService for adaptive carrier-call capture.
 * Prefers simultaneous telephony RX/TX streams, then falls back to a single
 * audible downlink/mixed/uplink/voice-communication source. */
@Keep
class PrivilegedAudioService : Binder() {
    private var recorder: AudioRecord? = null
    private var downlinkRecorder: AudioRecord? = null
    private var uplinkRecorder: AudioRecord? = null
    @Volatile private var started=false
    @Volatile private var dualStream=false
    @Volatile private var activeSource=SOURCE_NONE
    @Volatile private var probeMask=0
    @Volatile private var bootstrapHealth=ShellAudioBootstrap.Health.FAILED
    @Volatile private var lastProbePeak=0
    @Volatile private var downlinkPeak=0
    @Volatile private var uplinkPeak=0

    override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean=when(code){
        TRANSACTION_START->{data.enforceInterface(DESCRIPTOR);val result=startCapture();reply?.writeNoException();reply?.writeInt(result);true}
        TRANSACTION_READ->{data.enforceInterface(DESCRIPTOR);val requested=data.readInt().coerceIn(320,MAX_BINDER_CHUNK);val buffer=ByteArray(requested);val count=read(buffer);reply?.writeNoException();reply?.writeInt(count);if(count>0)reply?.writeByteArray(buffer.copyOf(count));true}
        TRANSACTION_READ_DOWNLINK->{data.enforceInterface(DESCRIPTOR);val requested=data.readInt().coerceIn(320,MAX_BINDER_CHUNK);val buffer=ByteArray(requested);val count=readFrom(downlinkRecorder,buffer);reply?.writeNoException();reply?.writeInt(count);if(count>0)reply?.writeByteArray(buffer.copyOf(count));true}
        TRANSACTION_READ_UPLINK->{data.enforceInterface(DESCRIPTOR);val requested=data.readInt().coerceIn(320,MAX_BINDER_CHUNK);val buffer=ByteArray(requested);val count=readFrom(uplinkRecorder,buffer);reply?.writeNoException();reply?.writeInt(count);if(count>0)reply?.writeByteArray(buffer.copyOf(count));true}
        TRANSACTION_STOP->{data.enforceInterface(DESCRIPTOR);stopCapture();reply?.writeNoException();true}
        TRANSACTION_STATUS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(if(started)activeSource else SOURCE_NONE);true}
        TRANSACTION_PROBE_STATUS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(probeMask);true}
        TRANSACTION_BOOTSTRAP_STATUS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(bootstrapHealth.ordinal);true}
        TRANSACTION_AUDIBILITY_STATUS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(lastProbePeak);true}
        TRANSACTION_CAPTURE_MODE->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(if(dualStream)CAPTURE_MODE_DUAL else if(started)CAPTURE_MODE_SINGLE else CAPTURE_MODE_NONE);true}
        TRANSACTION_DUAL_PEAKS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(downlinkPeak);reply?.writeInt(uplinkPeak);true}
        DESTROY_TRANSACTION->{stopCapture();reply?.writeNoException();true}
        else->super.onTransact(code,data,reply,flags)
    }

    @Synchronized private fun startCapture():Int{
        if(started)return START_OK
        resetDiagnostics()
        val baseContext=runCatching{val at=Class.forName("android.app.ActivityThread");at.getMethod("currentApplication").invoke(null) as? android.content.Context}.getOrNull()?:return START_CONTEXT_UNAVAILABLE
        val bootstrap=ShellAudioBootstrap.install(baseContext);bootstrapHealth=bootstrap.health
        if(bootstrap.health==ShellAudioBootstrap.Health.FAILED)return START_BOOTSTRAP_FAILED
        val min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        if(min<=0)return START_FORMAT_UNAVAILABLE
        val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()

        // Preferred path: clean RX and TX streams. Both must actually contain
        // audible PCM; merely reaching RECORDSTATE_RECORDING is insufficient.
        val down=createRecorder(bootstrap.context,MediaRecorder.AudioSource.VOICE_DOWNLINK,format,min)
        val up=createRecorder(bootstrap.context,MediaRecorder.AudioSource.VOICE_UPLINK,format,min)
        probeMask=probeMask or PROBE_VOICE_DOWNLINK or PROBE_VOICE_UPLINK or PROBE_DUAL
        if(startRecorder(down)&&startRecorder(up)){
            downlinkPeak=measurePeak(down!!);uplinkPeak=measurePeak(up!!)
            lastProbePeak=maxOf(downlinkPeak,uplinkPeak)
            if(downlinkPeak>=AUDIBLE_PEAK_THRESHOLD&&uplinkPeak>=AUDIBLE_PEAK_THRESHOLD){
                downlinkRecorder=down;uplinkRecorder=up;dualStream=true;started=true;activeSource=SOURCE_DUAL
                probeMask=probeMask or PROBE_SUCCESS or PROBE_AUDIBLE or PROBE_DUAL_SUCCESS
                return START_OK
            }
        }
        releaseRecorder(down);releaseRecorder(up)

        val probes=arrayOf(
            MediaRecorder.AudioSource.VOICE_DOWNLINK to PROBE_VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_CALL to PROBE_VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_UPLINK to PROBE_VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to PROBE_VOICE_COMMUNICATION
        )
        var anyStarted=false
        for((source,bit) in probes){
            probeMask=probeMask or bit
            val candidate=createRecorder(bootstrap.context,source,format,min)?:continue
            if(!startRecorder(candidate)){releaseRecorder(candidate);continue}
            anyStarted=true
            val peak=measurePeak(candidate);lastProbePeak=maxOf(lastProbePeak,peak)
            if(peak>=AUDIBLE_PEAK_THRESHOLD){recorder=candidate;activeSource=source;started=true;probeMask=probeMask or PROBE_SUCCESS or PROBE_AUDIBLE;return START_OK}
            releaseRecorder(candidate)
        }
        activeSource=SOURCE_NONE
        return if(anyStarted)START_SILENT_SOURCE else START_SOURCE_BLOCKED
    }

    private fun createRecorder(context:android.content.Context,source:Int,format:AudioFormat,min:Int):AudioRecord?=try{
        AudioRecord.Builder().setContext(context).setAudioSource(source).setAudioFormat(format).setBufferSizeInBytes(maxOf(min*2,8192)).build().takeIf{it.state==AudioRecord.STATE_INITIALIZED}
    }catch(_:Throwable){null}
    private fun startRecorder(candidate:AudioRecord?):Boolean{if(candidate==null)return false;return try{candidate.startRecording();candidate.recordingState==AudioRecord.RECORDSTATE_RECORDING}catch(_:Throwable){false}}
    private fun releaseRecorder(candidate:AudioRecord?){runCatching{candidate?.stop()};runCatching{candidate?.release()}}

    private fun measurePeak(candidate:AudioRecord):Int{
        val samples=ShortArray(PROBE_SAMPLES);var peak=0;val deadline=SystemClock.elapsedRealtime()+PROBE_WINDOW_MS
        while(SystemClock.elapsedRealtime()<deadline){
            val count=try{candidate.read(samples,0,samples.size,AudioRecord.READ_NON_BLOCKING)}catch(_:Throwable){return 0}
            if(count>0){for(i in 0 until count)peak=maxOf(peak,abs(samples[i].toInt()));if(peak>=AUDIBLE_PEAK_THRESHOLD)return peak}else SystemClock.sleep(PROBE_IDLE_MS)
        }
        return peak
    }

    private fun read(buffer:ByteArray):Int=if(dualStream)readFrom(downlinkRecorder,buffer) else readFrom(recorder,buffer)
    private fun readFrom(active:AudioRecord?,buffer:ByteArray):Int{
        active?:return READ_NOT_RUNNING;if(!started)return READ_NOT_RUNNING
        val result=try{active.read(buffer,0,buffer.size,AudioRecord.READ_NON_BLOCKING)}catch(_:Throwable){READ_FAILED}
        if(result==AudioRecord.ERROR_DEAD_OBJECT){stopCapture();return READ_DEAD_OBJECT}
        if(result<0&&result!=AudioRecord.ERROR_INVALID_OPERATION&&result!=AudioRecord.ERROR_BAD_VALUE)return READ_FAILED
        return result.coerceAtLeast(0)
    }
    private fun resetDiagnostics(){probeMask=0;lastProbePeak=0;downlinkPeak=0;uplinkPeak=0;dualStream=false}
    @Synchronized private fun stopCapture(){started=false;dualStream=false;activeSource=SOURCE_NONE;val single=recorder;val down=downlinkRecorder;val up=uplinkRecorder;recorder=null;downlinkRecorder=null;uplinkRecorder=null;releaseRecorder(single);releaseRecorder(down);releaseRecorder(up)}
    @Keep fun destroy(){stopCapture();System.exit(0)}

    companion object{
        const val DESCRIPTOR="com.realityengine.v4.PrivilegedAudioService";const val SAMPLE_RATE=16_000;const val SOURCE_NONE=-1;const val SOURCE_DUAL=-2
        const val TRANSACTION_START=FIRST_CALL_TRANSACTION;const val TRANSACTION_READ=FIRST_CALL_TRANSACTION+1;const val TRANSACTION_STOP=FIRST_CALL_TRANSACTION+2;const val TRANSACTION_STATUS=FIRST_CALL_TRANSACTION+3;const val TRANSACTION_PROBE_STATUS=FIRST_CALL_TRANSACTION+4;const val TRANSACTION_BOOTSTRAP_STATUS=FIRST_CALL_TRANSACTION+5;const val TRANSACTION_AUDIBILITY_STATUS=FIRST_CALL_TRANSACTION+6;const val TRANSACTION_CAPTURE_MODE=FIRST_CALL_TRANSACTION+7;const val TRANSACTION_READ_DOWNLINK=FIRST_CALL_TRANSACTION+8;const val TRANSACTION_READ_UPLINK=FIRST_CALL_TRANSACTION+9;const val TRANSACTION_DUAL_PEAKS=FIRST_CALL_TRANSACTION+10;const val DESTROY_TRANSACTION=16777115
        const val START_OK=0;const val START_FORMAT_UNAVAILABLE=-1;const val START_SOURCE_BLOCKED=-2;const val READ_NOT_RUNNING=-3;const val READ_FAILED=-4;const val READ_DEAD_OBJECT=-5;const val START_CONTEXT_UNAVAILABLE=-6;const val START_BOOTSTRAP_FAILED=-7;const val START_SILENT_SOURCE=-8;const val MAX_BINDER_CHUNK=16_384
        const val CAPTURE_MODE_NONE=0;const val CAPTURE_MODE_SINGLE=1;const val CAPTURE_MODE_DUAL=2
        const val PROBE_VOICE_CALL=1;const val PROBE_VOICE_DOWNLINK=2;const val PROBE_VOICE_UPLINK=4;const val PROBE_SUCCESS=8;const val PROBE_VOICE_COMMUNICATION=16;const val PROBE_AUDIBLE=32;const val PROBE_DUAL=64;const val PROBE_DUAL_SUCCESS=128
        private const val PROBE_WINDOW_MS=700L;private const val PROBE_IDLE_MS=10L;private const val PROBE_SAMPLES=1024;private const val AUDIBLE_PEAK_THRESHOLD=256
    }
}
