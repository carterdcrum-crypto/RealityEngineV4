package com.realityengine.v4

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class RealityInCallService:InCallService(){
 companion object{@Volatile var instance:RealityInCallService?=null}
 private lateinit var transcription:LiveTranscriptionPipeline;private lateinit var audioRouter:AudioCaptureRouter;private lateinit var summaryBuilder:CallSummaryBuilder
 private val finalizedCalls=java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call,Boolean>());@Volatile private var failedCall:Call?=null
 override fun onCreate(){super.onCreate();instance=this;LiveSignalState.initialize(applicationContext);transcription=LiveTranscriptionPipeline(applicationContext);audioRouter=AudioCaptureRouter(applicationContext);summaryBuilder=CallSummaryBuilder(applicationContext);ShizukuAudioStatus.requestPermission()}
 override fun onDestroy(){transcription.stop();clearLiveSession();if(instance===this)instance=null;super.onDestroy()}
 override fun onCallAdded(call:Call){super.onCallAdded(call);finalizedCalls.remove(call);failedCall=null;if(CallSessionRegistry.primary()==null)clearLiveSession();CallSessionRegistry.add(call);call.registerCallback(callback);syncTranscription();launchCallUi()}
 override fun onCallRemoved(call:Call){val endedNumber=CallSessionRegistry.numberFor(call).orEmpty();call.unregisterCallback(callback);CallSessionRegistry.remove(call);if(failedCall===call)failedCall=null;finalizeOnce(call,endedNumber);if(CallSessionRegistry.primary()!=null){syncTranscription();launchCallUi()}else{transcription.stop();clearLiveSession()};super.onCallRemoved(call)}
 override fun onCallAudioStateChanged(audioState:CallAudioState?){super.onCallAudioStateChanged(audioState);if(CallSessionRegistry.primary()!=null){syncTranscription();launchCallUi()}}
 fun isMutedNow():Boolean=callAudioState?.isMuted==true
 @Synchronized private fun finalizeOnce(call:Call,phoneNumber:String){if(phoneNumber.isBlank()||!finalizedCalls.add(call))return;summaryBuilder.finalize(phoneNumber)}
 private fun clearLiveSession(){LiveTranscriptState.clear();LiveSignalState.clear();AudioRouteState.clear();ResponseCoachState.clearCall()}
 private fun publishFailure(call:Call,reason:String?){failedCall=call;val detail=reason?.takeIf{it.isNotBlank()}?:"Audio stream ended";AudioRouteState.publish(AudioCaptureRouter.Decision(AudioCaptureRouter.Route.UNAVAILABLE,"Live transcription stopped: ${detail.take(120)}",false));launchCallUi()}
 private fun startNative(call:Call){when(val result=transcription.start(onStopped={reason->runOnMain{if(CallSessionRegistry.primary()===call&&call.state==Call.STATE_ACTIVE)publishFailure(call,reason)}})){LiveTranscriptionPipeline.StartResult.Started->Unit;is LiveTranscriptionPipeline.StartResult.Unavailable->publishFailure(call,result.reason)}}
 private fun startTwilio(call:Call){when(val result=transcription.startTwilio(onStopped={reason->runOnMain{if(CallSessionRegistry.primary()===call&&call.state==Call.STATE_ACTIVE)publishFailure(call,reason)}})){LiveTranscriptionPipeline.StartResult.Started->Unit;is LiveTranscriptionPipeline.StartResult.Unavailable->publishFailure(call,result.reason)}}
 private fun runOnMain(block:()->Unit){android.os.Handler(mainLooper).post(block)}
 private fun syncTranscription(){val call=CallSessionRegistry.primary()?:run{transcription.stop();failedCall=null;clearLiveSession();return};if(call.state!=Call.STATE_ACTIVE){if(transcription.isRunning())transcription.stop();if(call.state==Call.STATE_DISCONNECTED)failedCall=null;LiveTranscriptState.clear();AudioRouteState.clear();launchCallUi();return};if(failedCall===call&&!transcription.isRunning())return;val decision=audioRouter.decide(twilioCallActive=TwilioFallbackState.isActive());AudioRouteState.publish(decision);AudioRouteState.diagnose(applicationContext);when(decision.route){AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL->if(!transcription.isRunning())startNative(call);AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM->if(!transcription.isRunning())startTwilio(call);else->if(transcription.isRunning())transcription.stop()}}
 private fun launchCallUi(){startActivity(Intent(this,CallActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)})}
 private val callback=object:Call.Callback(){override fun onStateChanged(call:Call,state:Int){if(state==Call.STATE_DISCONNECTED){val endedNumber=CallSessionRegistry.numberFor(call).orEmpty();CallSessionRegistry.removeIfDisconnected(call);if(failedCall===call)failedCall=null;finalizeOnce(call,endedNumber);if(CallSessionRegistry.primary()==null){transcription.stop();clearLiveSession()}else{syncTranscription();launchCallUi()}}else{CallSessionRegistry.add(call);syncTranscription();launchCallUi()}}}
}
