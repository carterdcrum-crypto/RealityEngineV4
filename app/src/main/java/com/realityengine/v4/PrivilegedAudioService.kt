package com.realityengine.v4

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Parcel
import androidx.annotation.Keep

/** Privileged Shizuku UserService that owns call capture.
 * Probes protected telephony sources first, then a privileged voice-communication fallback. */
@Keep
class PrivilegedAudioService : Binder() {
    private var recorder: AudioRecord? = null
    @Volatile private var started = false
    @Volatile private var activeSource = SOURCE_NONE
    @Volatile private var probeMask = 0

    override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean=when(code){
        TRANSACTION_START->{data.enforceInterface(DESCRIPTOR);val result=startCapture();reply?.writeNoException();reply?.writeInt(result);true}
        TRANSACTION_READ->{data.enforceInterface(DESCRIPTOR);val requested=data.readInt().coerceIn(320,MAX_BINDER_CHUNK);val buffer=ByteArray(requested);val count=read(buffer);reply?.writeNoException();reply?.writeInt(count);if(count>0)reply?.writeByteArray(buffer.copyOf(count));true}
        TRANSACTION_STOP->{data.enforceInterface(DESCRIPTOR);stopCapture();reply?.writeNoException();true}
        TRANSACTION_STATUS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(if(started)activeSource else SOURCE_NONE);true}
        TRANSACTION_PROBE_STATUS->{data.enforceInterface(DESCRIPTOR);reply?.writeNoException();reply?.writeInt(probeMask);true}
        DESTROY_TRANSACTION->{stopCapture();reply?.writeNoException();true}
        else->super.onTransact(code,data,reply,flags)
    }

    @Synchronized private fun startCapture():Int{
        if(started)return START_OK
        probeMask=0
        val min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        if(min<=0)return START_FORMAT_UNAVAILABLE
        val probes=arrayOf(
            MediaRecorder.AudioSource.VOICE_CALL to PROBE_VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_DOWNLINK to PROBE_VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_UPLINK to PROBE_VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to PROBE_VOICE_COMMUNICATION
        )
        for((source,bit) in probes){
            probeMask=probeMask or bit
            val candidate=try{AudioRecord(source,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min*2,8192))}catch(_:Throwable){null}?:continue
            if(candidate.state!=AudioRecord.STATE_INITIALIZED){runCatching{candidate.release()};continue}
            val ok=try{candidate.startRecording();candidate.recordingState==AudioRecord.RECORDSTATE_RECORDING}catch(_:Throwable){false}
            if(ok){recorder=candidate;activeSource=source;started=true;probeMask=probeMask or PROBE_SUCCESS;return START_OK}
            runCatching{candidate.release()}
        }
        activeSource=SOURCE_NONE;return START_SOURCE_BLOCKED
    }

    private fun read(buffer:ByteArray):Int{val active=recorder?:return READ_NOT_RUNNING;if(!started)return READ_NOT_RUNNING;val result=try{active.read(buffer,0,buffer.size,AudioRecord.READ_NON_BLOCKING)}catch(_:Throwable){READ_FAILED};if(result==AudioRecord.ERROR_DEAD_OBJECT){stopCapture();return READ_DEAD_OBJECT};if(result<0&&result!=AudioRecord.ERROR_INVALID_OPERATION&&result!=AudioRecord.ERROR_BAD_VALUE)return READ_FAILED;return result.coerceAtLeast(0)}
    @Synchronized private fun stopCapture(){started=false;activeSource=SOURCE_NONE;val active=recorder;recorder=null;runCatching{active?.stop()};runCatching{active?.release()}}
    @Keep fun destroy(){stopCapture();System.exit(0)}

    companion object{
        const val DESCRIPTOR="com.realityengine.v4.PrivilegedAudioService";const val SAMPLE_RATE=16_000;const val SOURCE_NONE=-1
        const val TRANSACTION_START=FIRST_CALL_TRANSACTION;const val TRANSACTION_READ=FIRST_CALL_TRANSACTION+1;const val TRANSACTION_STOP=FIRST_CALL_TRANSACTION+2;const val TRANSACTION_STATUS=FIRST_CALL_TRANSACTION+3;const val TRANSACTION_PROBE_STATUS=FIRST_CALL_TRANSACTION+4;const val DESTROY_TRANSACTION=16777115
        const val START_OK=0;const val START_FORMAT_UNAVAILABLE=-1;const val START_SOURCE_BLOCKED=-2;const val READ_NOT_RUNNING=-3;const val READ_FAILED=-4;const val READ_DEAD_OBJECT=-5;const val MAX_BINDER_CHUNK=16_384
        const val PROBE_VOICE_CALL=1;const val PROBE_VOICE_DOWNLINK=2;const val PROBE_VOICE_UPLINK=4;const val PROBE_SUCCESS=8;const val PROBE_VOICE_COMMUNICATION=16
    }
}
