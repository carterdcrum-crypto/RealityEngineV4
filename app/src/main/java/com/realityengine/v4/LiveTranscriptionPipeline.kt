package com.realityengine.v4

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Live transcription pipeline supporting native Shizuku PCM and Twilio media fallback PCM. */
class LiveTranscriptionPipeline(context: Context) {
    sealed class StartResult { data object Started : StartResult(); data class Unavailable(val reason: String) : StartResult() }
    data class Status(val running: Boolean, val transport: DeepgramStreamingClient.State, val failure: String?)

    private val appContext=context.applicationContext;private val settings=SettingsStore(appContext);private val capture=VoiceCallPcmCapture(appContext);private val deepgram=DeepgramStreamingClient(settings);private val conversation=LiveConversationSession(appContext);private val evidence=LiveEvidenceEngine(appContext);private val acoustic=AcousticSignalAnalyzer();private val factual=FactualSignalAnalyzer(appContext);private val memory=CallerMemoryExtractor(appContext);private val running=AtomicBoolean(false);private val stopping=AtomicBoolean(false)
    @Volatile private var interimCallback:((String)->Unit)?=null;@Volatile private var stoppedCallback:((String?)->Unit)?=null;@Volatile private var acousticScore=0;@Volatile private var callerSpeaker:Int?=null;@Volatile private var multichannel=false;@Volatile private var callerSpeaking=false;@Volatile private var userSpeaking=false;@Volatile private var callerSpeechStartedAt=-1.0;@Volatile private var userSpeechStartedAt=-1.0
    private val pendingLock=Any();private val pendingPcm=ByteArrayOutputStream(MAX_PENDING_PCM);private val stereoLock=Any();private var pendingRx=ByteArray(0);private var pendingTx=ByteArray(0);private val turnLock=Any();private val callerTurn=StringBuilder();private val userTurn=StringBuilder()

    fun start(onInterim:(String)->Unit={},onStopped:(String?)->Unit={}):StartResult{
        if(!settings.deepgramConfigured()||!running.compareAndSet(false,true))return StartResult.Unavailable(startFailureReason())
        prepareSession(onInterim,onStopped)
        var transportStarted=false
        val result=capture.start(onFrame={bytes,length,direction->if(running.get()&&!stopping.get()&&transportStarted){when(direction){VoiceCallPcmCapture.Direction.DOWNLINK->acceptDirectional(bytes,length,true);VoiceCallPcmCapture.Direction.UPLINK->acceptDirectional(bytes,length,false);VoiceCallPcmCapture.Direction.MIXED->{acousticScore=acoustic.analyze(bytes,length).score;sendOrBuffer(bytes,length)}}}},onStarted={mode->val dual=mode==VoiceCallPcmCapture.Mode.DUAL;transportStarted=connectDeepgram(VoiceCallPcmCapture.SAMPLE_RATE,if(dual)2 else 1,dual)},onStopped={reason->beginShutdown(reason,false)})
        return when(result){
            is VoiceCallPcmCapture.StartResult.Started->{if(transportStarted)StartResult.Started else{running.set(false);capture.stop();clearPending();deepgram.close();StartResult.Unavailable(startFailureReason())}}
            is VoiceCallPcmCapture.StartResult.Unavailable->{running.set(false);clearPending();deepgram.close();StartResult.Unavailable(result.reason)}
        }
    }

    fun startTwilio(onInterim:(String)->Unit={},onStopped:(String?)->Unit={}):StartResult{if(!settings.deepgramConfigured()||!running.compareAndSet(false,true))return StartResult.Unavailable(startFailureReason());prepareSession(onInterim,onStopped);return if(connectDeepgram(8_000,1,false))StartResult.Started else{running.set(false);StartResult.Unavailable(startFailureReason())}}
    fun acceptTwilioMessage(message:String):Boolean{if(!running.get()||stopping.get())return false;val frame=TwilioMediaStreamDecoder.decode(message)?:return false;acousticScore=acoustic.analyze(frame.pcm16).score;return deepgram.sendPcm(frame.pcm16)}

    private fun prepareSession(onInterim:(String)->Unit,onStopped:(String?)->Unit){stopping.set(false);multichannel=false;resetSpeechState();clearTurns();acoustic.reset();callerSpeaker=null;clearPending();LiveTranscriptState.clear();interimCallback=onInterim;stoppedCallback=onStopped;conversation.bindActiveCaller()}
    private fun connectDeepgram(sampleRate:Int,channels:Int,multi:Boolean):Boolean{multichannel=multi;return deepgram.connect(sampleRate=sampleRate,channels=channels,multichannel=multi,onTranscript={result->val isCaller=if(multichannel)result.channel?.let{it==0}else{val speaker=result.speaker;if(callerSpeaker==null&&speaker!=null)callerSpeaker=speaker;speaker?.let{it==callerSpeaker}};LiveTranscriptState.publish(result.text,result.isFinal,isCaller);interimCallback?.invoke(result.text);if(result.isFinal&&result.text.isNotBlank())appendFinalTurn(result.text,isCaller!=false);if(result.speechFinal){flushTurn(isCaller!=false);if(isCaller==true)callerSpeaking=false else if(isCaller==false)userSpeaking=false}},onSpeechEvent={event->handleSpeechEvent(event)},onClosed={reason->finishShutdown(reason)}).also{started->if(started)primeDeepgram(sampleRate,channels)}}

    private fun appendFinalTurn(text:String,isCaller:Boolean){synchronized(turnLock){val target=if(isCaller)callerTurn else userTurn;if(target.isNotEmpty())target.append(' ');target.append(text.trim())}}
    private fun flushTurn(isCaller:Boolean){val text=synchronized(turnLock){val target=if(isCaller)callerTurn else userTurn;val value=target.toString().trim();target.setLength(0);value};if(text.isBlank())return;if(isCaller){conversation.onCallerTurn(text);val phone=CallSessionRegistry.primaryNumber().orEmpty();memory.observe(phone,text);val linguistic=LinguisticSignalAnalyzer.analyze(text);val factualResult=factual.analyze(phone,text);val context=buildString{append(text.take(150));if(factualResult.reason.isNotBlank())append(" [consistency: ").append(factualResult.reason).append(']')};evidence.update(phone,acousticScore,linguistic.score,factualResult.score,context.take(220))}else conversation.onUserTurn(text)}
    private fun flushTurns(){flushTurn(true);flushTurn(false)}
    private fun clearTurns(){synchronized(turnLock){callerTurn.setLength(0);userTurn.setLength(0)}}

    private fun handleSpeechEvent(event:DeepgramStreamingClient.SpeechEvent){if(!multichannel)return;when(event){is DeepgramStreamingClient.SpeechEvent.Started->when(event.channel){0->{callerSpeaking=true;callerSpeechStartedAt=event.timestampSeconds};1->{userSpeaking=true;userSpeechStartedAt=event.timestampSeconds}};is DeepgramStreamingClient.SpeechEvent.Ended->when(event.channel){0->if(callerSpeechStartedAt<0.0||event.lastWordEndSeconds>=callerSpeechStartedAt){callerSpeaking=false;flushTurn(true)};1->if(userSpeechStartedAt<0.0||event.lastWordEndSeconds>=userSpeechStartedAt){userSpeaking=false;flushTurn(false)}}}}
    private fun resetSpeechState(){callerSpeaking=false;userSpeaking=false;callerSpeechStartedAt=-1.0;userSpeechStartedAt=-1.0}

    private fun primeDeepgram(sampleRate:Int,channels:Int){Thread({val deadline=System.currentTimeMillis()+FIRST_AUDIO_DEADLINE_MS;while(running.get()&&!stopping.get()&&!deepgram.isConnected()&&System.currentTimeMillis()<deadline){try{Thread.sleep(25)}catch(_:InterruptedException){return@Thread}};if(!running.get()||stopping.get()||!deepgram.isConnected())return@Thread;deepgram.sendPcm(ByteArray((sampleRate*channels*2)/10))},"reality-deepgram-prime").apply{isDaemon=true;start()}}

    private fun acceptDirectional(bytes:ByteArray,length:Int,rx:Boolean){val n=length.coerceIn(0,bytes.size) and -2;if(n<=0)return;if(rx)acousticScore=acoustic.analyze(bytes,n).score;synchronized(stereoLock){if(rx)pendingRx=append(pendingRx,bytes,n) else pendingTx=append(pendingTx,bytes,n);while(pendingRx.size>=STEREO_FRAME_MONO_BYTES||pendingTx.size>=STEREO_FRAME_MONO_BYTES){val rxCount=minOf(pendingRx.size,STEREO_FRAME_MONO_BYTES) and -2;val txCount=minOf(pendingTx.size,STEREO_FRAME_MONO_BYTES) and -2;val monoBytes=maxOf(rxCount,txCount);if(monoBytes<=0)break;val stereo=ByteArray(monoBytes*2);var s=0;var d=0;while(s<monoBytes){if(s+1<rxCount){stereo[d]=pendingRx[s];stereo[d+1]=pendingRx[s+1]};if(s+1<txCount){stereo[d+2]=pendingTx[s];stereo[d+3]=pendingTx[s+1]};s+=2;d+=4};if(rxCount>0)pendingRx=pendingRx.copyOfRange(rxCount,pendingRx.size);if(txCount>0)pendingTx=pendingTx.copyOfRange(txCount,pendingTx.size);sendOrBuffer(stereo,stereo.size)}}}
    private fun append(existing:ByteArray,bytes:ByteArray,length:Int):ByteArray{val out=ByteArray(existing.size+length);existing.copyInto(out);bytes.copyInto(out,existing.size,0,length);return out}
    private fun sendOrBuffer(bytes:ByteArray,length:Int){val n=length.coerceIn(0,bytes.size);if(n==0||stopping.get())return;synchronized(pendingLock){if(deepgram.isConnected()){if(pendingPcm.size()>0){val queued=pendingPcm.toByteArray();pendingPcm.reset();deepgram.sendPcm(queued)};deepgram.sendPcm(bytes,n)}else{val remaining=MAX_PENDING_PCM-pendingPcm.size();if(remaining>0)pendingPcm.write(bytes,0,n.coerceAtMost(remaining))}}}
    private fun clearPending(){synchronized(pendingLock){pendingPcm.reset()};synchronized(stereoLock){pendingRx=ByteArray(0);pendingTx=ByteArray(0)}}

    private fun beginShutdown(reason:String?,stopCapture:Boolean){if(!running.get()||!stopping.compareAndSet(false,true))return;if(stopCapture)capture.stop();clearPending();deepgram.close();if(!deepgram.isConnected())finishShutdown(reason)}
    private fun finishShutdown(reason:String?){if(!running.getAndSet(false))return;flushTurns();clearPending();resetSpeechState();conversation.clear();acoustic.reset();acousticScore=0;callerSpeaker=null;multichannel=false;stopping.set(false);val callback=stoppedCallback;interimCallback=null;stoppedCallback=null;LiveTranscriptState.clear();callback?.invoke(reason)}
    private fun startFailureReason():String=when{!settings.deepgramConfigured()->"Deepgram is not configured";deepgram.failureReason()!=null->deepgram.failureReason()!!.take(160);deepgram.connectionState()==DeepgramStreamingClient.State.CONNECTING->"Deepgram is already connecting";else->"Deepgram stream could not start"}
    fun status():Status=Status(running.get(),deepgram.connectionState(),deepgram.failureReason())
    fun stop(){beginShutdown(null,true)}
    fun isRunning():Boolean=running.get()
    companion object{private const val MAX_PENDING_PCM=128_000;private const val STEREO_FRAME_MONO_BYTES=3_200;private const val FIRST_AUDIO_DEADLINE_MS=5_000L}
}
