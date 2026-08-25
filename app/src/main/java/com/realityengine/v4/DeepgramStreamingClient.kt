package com.realityengine.v4

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Android WebSocket transport for Deepgram live transcription. */
class DeepgramStreamingClient(private val settings: SettingsStore) {
    data class Transcript(val text:String,val isFinal:Boolean,val speechFinal:Boolean,val speaker:Int?=null,val channel:Int?=null)
    sealed class SpeechEvent {
        data class Started(val channel:Int?,val timestampSeconds:Double):SpeechEvent()
        data class Ended(val channel:Int?,val lastWordEndSeconds:Double):SpeechEvent()
    }
    enum class State { IDLE, CONNECTING, CONNECTED, CLOSING, CLOSED, FAILED }

    private val connected=AtomicBoolean(false)
    private val closing=AtomicBoolean(false)
    private val client=OkHttpClient.Builder().pingInterval(15,TimeUnit.SECONDS).build()
    private val keepAliveExecutor=Executors.newSingleThreadScheduledExecutor { r->Thread(r,"reality-deepgram-keepalive").apply{isDaemon=true} }
    @Volatile private var keepAliveTask:ScheduledFuture<*>?=null
    @Volatile private var closeFallbackTask:ScheduledFuture<*>?=null
    @Volatile private var socket:WebSocket?=null
    @Volatile private var transcriptCallback:((Transcript)->Unit)?=null
    @Volatile private var speechEventCallback:((SpeechEvent)->Unit)?=null
    @Volatile private var closedCallback:((String?)->Unit)?=null
    @Volatile private var state:State=State.IDLE
    @Volatile private var lastFailure:String?=null
    @Volatile private var lastAudioSentAt=0L

    fun connect(sampleRate:Int=16_000,channels:Int=1,multichannel:Boolean=false,onTranscript:(Transcript)->Unit,onSpeechEvent:(SpeechEvent)->Unit={},onClosed:(String?)->Unit={}):Boolean{
        if(!settings.deepgramConfigured()||connected.get()||state==State.CONNECTING||state==State.CLOSING)return false
        transcriptCallback=onTranscript;speechEventCallback=onSpeechEvent;closedCallback=onClosed;closing.set(false);state=State.CONNECTING;lastFailure=null;lastAudioSentAt=System.currentTimeMillis()
        val request=Request.Builder().url(endpoint(sampleRate,channels,multichannel)).header("Authorization","Token ${settings.deepgramApiKey}").build()
        socket=client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){connected.set(true);state=State.CONNECTED;lastAudioSentAt=System.currentTimeMillis();startKeepAlive()}
            override fun onMessage(webSocket:WebSocket,text:String){acceptMessage(text)}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String){webSocket.close(code,reason)}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){state=State.CLOSED;finish(reason.takeIf{it.isNotBlank()})}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){lastFailure=t.message?:"Deepgram WebSocket failed";state=State.FAILED;finish(lastFailure)}
        });return true
    }

    fun sendPcm(bytes:ByteArray,length:Int=bytes.size):Boolean{
        if(!connected.get()||closing.get()||length<=0)return false
        val sent=socket?.send(bytes.toByteString(0,length.coerceAtMost(bytes.size)))==true
        if(sent)lastAudioSentAt=System.currentTimeMillis()
        return sent
    }

    /** Stop accepting audio, flush Deepgram's buffered audio, then ask Deepgram to close after
     * returning remaining transcript results. The local WebSocket is only force-closed if the
     * server does not finish promptly. */
    fun close(){
        if(!connected.get()){if(state!=State.FAILED)state=State.CLOSED;finish(null);return}
        if(!closing.compareAndSet(false,true))return
        stopKeepAlive();state=State.CLOSING
        val ws=socket
        val finalized=ws?.send("{\"type\":\"Finalize\"}")==true
        val closeSent=ws?.send("{\"type\":\"CloseStream\"}")==true
        if(!finalized||!closeSent){ws?.close(1000,"call ended")}
        else closeFallbackTask=keepAliveExecutor.schedule({socket?.close(1000,"close timeout")},CLOSE_FALLBACK_MS,TimeUnit.MILLISECONDS)
    }
    fun isConnected():Boolean=connected.get();fun connectionState():State=state;fun failureReason():String?=lastFailure

    internal fun acceptMessage(raw:String){
        try{
            val root=JSONObject(raw)
            when(root.optString("type")){
                "SpeechStarted"->{
                    val channelInfo=root.optJSONArray("channel")
                    val channel=channelInfo?.takeIf{it.length()>0}?.optInt(0)
                    speechEventCallback?.invoke(SpeechEvent.Started(channel,root.optDouble("timestamp",-1.0)))
                }
                "UtteranceEnd"->{
                    val lastWordEnd=root.optDouble("last_word_end",-1.0)
                    if(lastWordEnd<0.0)return
                    val channelInfo=root.optJSONArray("channel")
                    val channel=channelInfo?.takeIf{it.length()>0}?.optInt(0)
                    speechEventCallback?.invoke(SpeechEvent.Ended(channel,lastWordEnd))
                }
                "Results"->{
                    val alternatives=root.optJSONObject("channel")?.optJSONArray("alternatives")?:return
                    if(alternatives.length()==0)return
                    val alternative=alternatives.optJSONObject(0)?:return
                    val text=alternative.optString("transcript").trim();if(text.isBlank())return
                    val words=alternative.optJSONArray("words")
                    val speaker=if(words!=null&&words.length()>0)words.optJSONObject(0)?.takeIf{it.has("speaker")}?.optInt("speaker")else null
                    val channelIndex=root.optJSONArray("channel_index")
                    val channel=channelIndex?.takeIf{it.length()>0}?.optInt(0)
                    transcriptCallback?.invoke(Transcript(text,root.optBoolean("is_final"),root.optBoolean("speech_final"),speaker,channel))
                }
            }
        }catch(_:Throwable){}
    }

    internal fun endpoint(sampleRate:Int,channels:Int=1,multichannel:Boolean=false):String{
        val builder=Uri.Builder().scheme("wss").authority("api.deepgram.com").appendPath("v1").appendPath("listen").appendQueryParameter("model",settings.deepgramModel).appendQueryParameter("encoding","linear16").appendQueryParameter("sample_rate",sampleRate.toString()).appendQueryParameter("channels",channels.toString()).appendQueryParameter("interim_results","true").appendQueryParameter("smart_format","true").appendQueryParameter("endpointing","300").appendQueryParameter("utterance_end_ms","1000").appendQueryParameter("vad_events","true")
        if(multichannel&&channels>1)builder.appendQueryParameter("multichannel","true") else builder.appendQueryParameter("diarize","true")
        return builder.build().toString()
    }

    @Synchronized private fun startKeepAlive(){stopKeepAlive();keepAliveTask=keepAliveExecutor.scheduleAtFixedRate({if(connected.get()&&!closing.get()&&System.currentTimeMillis()-lastAudioSentAt>=KEEPALIVE_IDLE_MS)socket?.send("{\"type\":\"KeepAlive\"}")},KEEPALIVE_INTERVAL_MS,KEEPALIVE_INTERVAL_MS,TimeUnit.MILLISECONDS)}
    @Synchronized private fun stopKeepAlive(){keepAliveTask?.cancel(false);keepAliveTask=null}
    @Synchronized private fun finish(reason:String?){stopKeepAlive();closeFallbackTask?.cancel(false);closeFallbackTask=null;closing.set(false);val wasActive=connected.getAndSet(false);socket=null;if(wasActive||reason!=null)closedCallback?.invoke(reason);closedCallback=null;transcriptCallback=null;speechEventCallback=null}

    companion object{private const val KEEPALIVE_INTERVAL_MS=3_000L;private const val KEEPALIVE_IDLE_MS=3_000L;private const val CLOSE_FALLBACK_MS=2_000L}
}
