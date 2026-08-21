package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class CallActivity : Activity() {
    private var call:Call?=null
    private lateinit var caller:TextView;private lateinit var state:TextView;private lateinit var timer:TextView
    private lateinit var answerButton:Button;private lateinit var rejectButton:Button;private lateinit var muteButton:Button;private lateinit var speakerButton:Button;private lateinit var bluetoothButton:Button;private lateinit var holdButton:Button;private lateinit var keypadButton:Button;private lateinit var endButton:Button;private lateinit var keypadContainer:LinearLayout
    private val handler=Handler(Looper.getMainLooper());private var connectedStartedAt:Long?=null;private var finishScheduled=false;private var lastNumber:String?=null
    private val registryListener:()->Unit={runOnUiThread{refresh()}};private val timerTick=object:Runnable{override fun run(){updateTimer();handler.postDelayed(this,1000L)}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);connectedStartedAt=savedInstanceState?.getLong(KEY_CONNECTED_AT)?.takeIf{it>0};buildUi();refresh()}
    override fun onSaveInstanceState(outState:Bundle){connectedStartedAt?.let{outState.putLong(KEY_CONNECTED_AT,it)};super.onSaveInstanceState(outState)}
    override fun onResume(){super.onResume();CallSessionRegistry.addListener(registryListener);refresh();handler.removeCallbacks(timerTick);handler.post(timerTick)}
    override fun onPause(){call?.stopDtmfTone();handler.removeCallbacks(timerTick);CallSessionRegistry.removeListener(registryListener);super.onPause()}

    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(24,32,24,24);setBackgroundColor(Color.rgb(10,10,14))}
        caller=TextView(this).apply{textSize=26f;setTextColor(Color.WHITE);gravity=Gravity.CENTER};root.addView(caller,LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT))
        state=TextView(this).apply{textSize=16f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(0,12,0,4)};root.addView(state)
        timer=TextView(this).apply{text="00:00";textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,0,0,18)};root.addView(timer)
        val incoming=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        answerButton=Button(this).apply{text="ANSWER";setOnClickListener{call?.takeIf{it.state==Call.STATE_RINGING}?.answer(0)}}
        rejectButton=Button(this).apply{text="DECLINE";setOnClickListener{call?.takeIf{it.state==Call.STATE_RINGING}?.reject(false,null)}}
        incoming.addView(answerButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});incoming.addView(rejectButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});root.addView(incoming)
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        muteButton=Button(this).apply{setOnClickListener{toggleMute()}};speakerButton=Button(this).apply{setOnClickListener{toggleSpeaker()}};bluetoothButton=Button(this).apply{setOnClickListener{toggleBluetooth()}};holdButton=Button(this).apply{setOnClickListener{toggleHold()}}
        controls.addView(muteButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});controls.addView(speakerButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});controls.addView(bluetoothButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});controls.addView(holdButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});root.addView(controls)
        keypadButton=Button(this).apply{text="KEYPAD";setOnClickListener{keypadContainer.visibility=if(keypadContainer.visibility==View.VISIBLE)View.GONE else View.VISIBLE}};root.addView(keypadButton)
        keypadContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};val digits=arrayOf("1","2","3","4","5","6","7","8","9","*","0","#");val grid=GridLayout(this).apply{columnCount=3;useDefaultMargins=true}
        digits.forEach{digit->grid.addView(Button(this).apply{text=digit;textSize=20f;minWidth=0;minHeight=0;setOnTouchListener{_,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->{call?.playDtmfTone(digit[0]);true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{call?.stopDtmfTone();performClick();true};else->false}}},GridLayout.LayoutParams().apply{width=0;height=56.dp();columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)})};keypadContainer.addView(grid);root.addView(keypadContainer)
        endButton=Button(this).apply{text="END CALL";setOnClickListener{call?.disconnect()}};root.addView(endButton);setContentView(root)
    }

    private fun toggleMute(){val service=RealityInCallService.instance?:return;val muted=!service.isMutedNow();service.setMuted(muted);muteButton.text=if(muted)"UNMUTE" else "MUTE"}
    private fun toggleSpeaker(){val service=RealityInCallService.instance?:return;val audio=service.callAudioState?:return;val speaker=CallAudioState.ROUTE_SPEAKER;val earpiece=CallAudioState.ROUTE_EARPIECE;val target=if(audio.route==speaker)earpiece else speaker;if(audio.supportedRouteMask and target!=0)service.setAudioRoute(target);refreshAudioButtons()}
    private fun toggleBluetooth(){val service=RealityInCallService.instance?:return;val audio=service.callAudioState?:return;val bluetooth=CallAudioState.ROUTE_BLUETOOTH;val fallback=if(audio.supportedRouteMask and CallAudioState.ROUTE_EARPIECE!=0)CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER;val target=if(audio.route==bluetooth)fallback else bluetooth;if(audio.supportedRouteMask and target!=0)service.setAudioRoute(target);refreshAudioButtons()}
    private fun toggleHold(){val current=call?:return;when(current.state){Call.STATE_ACTIVE->current.hold();Call.STATE_HOLDING->current.unhold()}}

    private fun refresh(){
        call=CallSessionRegistry.primary();val current=call;if(current==null){scheduleFinish();return}
        val number=current.details?.handle?.schemeSpecificPart?:"UNKNOWN CALLER";if(number!=lastNumber){lastNumber=number;caller.text=resolveCallerLabel(number)}
        state.text=when(current.state){Call.STATE_RINGING->"INCOMING CALL";Call.STATE_DIALING->"DIALING";Call.STATE_CONNECTING->"CONNECTING";Call.STATE_ACTIVE->"ACTIVE CALL";Call.STATE_HOLDING->"ON HOLD";Call.STATE_DISCONNECTED->"CALL ENDED";else->"CALL"}
        if(current.state==Call.STATE_DISCONNECTED){connectedStartedAt=null;scheduleFinish()}else finishScheduled=false
        val ringing=current.state==Call.STATE_RINGING;answerButton.visibility=if(ringing)View.VISIBLE else View.GONE;rejectButton.visibility=if(ringing)View.VISIBLE else View.GONE
        val interactive=current.state==Call.STATE_ACTIVE||current.state==Call.STATE_HOLDING
        muteButton.isEnabled=interactive;speakerButton.isEnabled=interactive;keypadButton.isEnabled=interactive;holdButton.isEnabled=interactive
        if(!interactive)keypadContainer.visibility=View.GONE
        holdButton.text=if(current.state==Call.STATE_HOLDING)"RESUME" else "HOLD"
        endButton.isEnabled=current.state!=Call.STATE_DISCONNECTED
        if((current.state==Call.STATE_ACTIVE||current.state==Call.STATE_HOLDING)&&connectedStartedAt==null)connectedStartedAt=SystemClock.elapsedRealtime();updateTimer();val service=RealityInCallService.instance;muteButton.text=if(service?.isMutedNow()==true)"UNMUTE" else "MUTE";refreshAudioButtons()
    }

    private fun refreshAudioButtons(){val audio=RealityInCallService.instance?.callAudioState;speakerButton.text=if(audio?.route==CallAudioState.ROUTE_SPEAKER)"EARPIECE" else "SPEAKER";bluetoothButton.text=if(audio?.route==CallAudioState.ROUTE_BLUETOOTH)"BT OFF" else "BLUETOOTH";val interactive=call?.state==Call.STATE_ACTIVE||call?.state==Call.STATE_HOLDING;bluetoothButton.isEnabled=interactive&&((audio?.supportedRouteMask?:0) and CallAudioState.ROUTE_BLUETOOTH!=0)}
    private fun scheduleFinish(){if(finishScheduled)return;finishScheduled=true;handler.postDelayed({if(!isFinishing)finish()},700L)}
    private fun resolveCallerLabel(number:String):String{if(number=="UNKNOWN CALLER"||checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return number;return try{val lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));contentResolver.query(lookup,arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)?.takeIf{it.isNotBlank()}?.let{return "$it\n$number"}};number}catch(_:Throwable){number}}
    private fun updateTimer(){val started=connectedStartedAt;if(started==null){timer.text="00:00";return};val total=(SystemClock.elapsedRealtime()-started)/1000L;val hours=total/3600L;val minutes=(total%3600L)/60L;val seconds=total%60L;timer.text=if(hours>0)String.format("%d:%02d:%02d",hours,minutes,seconds)else String.format("%02d:%02d",minutes,seconds)}
    private fun Int.dp():Int=(this*resources.displayMetrics.density).toInt()
    companion object{private const val KEY_CONNECTED_AT="connected_started_at"}
}
