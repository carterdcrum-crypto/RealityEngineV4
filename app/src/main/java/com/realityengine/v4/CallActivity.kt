package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.*

class CallActivity : Activity() {
    private var call:Call?=null
    private lateinit var caller:TextView;private lateinit var state:TextView;private lateinit var timer:TextView
    private lateinit var answerButton:Button;private lateinit var rejectButton:Button;private lateinit var muteButton:Button;private lateinit var speakerButton:Button;private lateinit var bluetoothButton:Button;private lateinit var holdButton:Button;private lateinit var keypadButton:Button;private lateinit var endButton:Button;private lateinit var keypadContainer:LinearLayout
    private val handler=Handler(Looper.getMainLooper());private var connectedStartedAt:Long?=null;private var finishScheduled=false;private var lastNumber:String?=null;private var restoreKeypadOpen=false
    private val bg=Color.rgb(3,7,12);private val panel=Color.rgb(9,18,27);private val cyan=Color.rgb(40,224,255);private val magenta=Color.rgb(255,55,190);private val green=Color.rgb(75,255,165);private val muted=Color.rgb(118,147,163)
    private val registryListener:()->Unit={runOnUiThread{refresh()}};private val timerTick=object:Runnable{override fun run(){updateTimer();handler.postDelayed(this,1000L)}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);connectedStartedAt=savedInstanceState?.getLong(KEY_CONNECTED_AT)?.takeIf{it>0};restoreKeypadOpen=savedInstanceState?.getBoolean(KEY_KEYPAD_OPEN,false)==true;buildUi();refresh()}
    override fun onSaveInstanceState(outState:Bundle){connectedStartedAt?.let{outState.putLong(KEY_CONNECTED_AT,it)};outState.putBoolean(KEY_KEYPAD_OPEN,::keypadContainer.isInitialized&&keypadContainer.visibility==View.VISIBLE);super.onSaveInstanceState(outState)}
    override fun onResume(){super.onResume();CallSessionRegistry.addListener(registryListener);refresh();handler.removeCallbacks(timerTick);handler.post(timerTick)}
    override fun onPause(){call?.stopDtmfTone();handler.removeCallbacks(timerTick);CallSessionRegistry.removeListener(registryListener);super.onPause()}
    private fun shapeRadius()=when(getPreferences(MODE_PRIVATE).getInt("buttonShape",1)){0->3f;2->30f;else->14f}
    private fun neon(fill:Int=panel,stroke:Int=cyan,r:Float=shapeRadius())=GradientDrawable().apply{setColor(fill);setStroke(1.dp(),stroke);cornerRadius=r*resources.displayMetrics.density}
    private fun control(label:String,stroke:Int=Color.rgb(20,88,108),action:()->Unit)=Button(this).apply{text=label;textSize=11f;letterSpacing=.08f;setTextColor(cyan);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);background=neon(panel,stroke);stateListAnimator=null;setOnClickListener{action()}}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(18.dp(),24.dp(),18.dp(),18.dp());setBackgroundColor(bg)}
        root.addView(TextView(this).apply{text="// LIVE CHANNEL";textSize=11f;letterSpacing=.18f;setTextColor(magenta);typeface=Typeface.MONOSPACE;gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,32.dp()))
        root.addView(View(this).apply{setBackgroundColor(Color.rgb(18,75,91))},LinearLayout.LayoutParams(-1,1.dp()))
        caller=TextView(this).apply{textSize=27f;setTextColor(cyan);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);gravity=Gravity.CENTER;setPadding(0,34.dp(),0,8.dp())};root.addView(caller,LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT))
        state=TextView(this).apply{textSize=12f;letterSpacing=.14f;setTextColor(green);typeface=Typeface.MONOSPACE;gravity=Gravity.CENTER;setPadding(0,8.dp(),0,4.dp())};root.addView(state)
        timer=TextView(this).apply{text="00:00";textSize=22f;letterSpacing=.12f;setTextColor(Color.WHITE);typeface=Typeface.MONOSPACE;gravity=Gravity.CENTER;setPadding(0,0,0,24.dp())};root.addView(timer)
        val incoming=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER};answerButton=control("ACCEPT",green){call?.takeIf{it.state==Call.STATE_RINGING}?.answer(0)};rejectButton=control("DECLINE",magenta){call?.takeIf{it.state==Call.STATE_RINGING}?.reject(false,null)};incoming.addView(answerButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4.dp(),4.dp(),4.dp(),4.dp())});incoming.addView(rejectButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4.dp(),4.dp(),4.dp(),4.dp())});root.addView(incoming)
        val spacer=Space(this);root.addView(spacer,LinearLayout.LayoutParams(1,0,1f))
        val controls=GridLayout(this).apply{columnCount=2;alignmentMode=GridLayout.ALIGN_BOUNDS;useDefaultMargins=false};muteButton=control("MUTE"){toggleMute()};speakerButton=control("SPEAKER"){toggleSpeaker()};bluetoothButton=control("BLUETOOTH"){toggleBluetooth()};holdButton=control("HOLD"){toggleHold()};arrayOf(muteButton,speakerButton,bluetoothButton,holdButton).forEach{controls.addView(it,GridLayout.LayoutParams().apply{width=0;height=58.dp();columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(5.dp(),5.dp(),5.dp(),5.dp())})};root.addView(controls,LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT))
        keypadButton=control("// KEYPAD"){keypadContainer.visibility=if(keypadContainer.visibility==View.VISIBLE)View.GONE else View.VISIBLE};root.addView(keypadButton,LinearLayout.LayoutParams(-1,52.dp()).apply{setMargins(5.dp(),6.dp(),5.dp(),4.dp())})
        keypadContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=if(restoreKeypadOpen)View.VISIBLE else View.GONE};val digits=arrayOf("1","2","3","4","5","6","7","8","9","*","0","#");val grid=GridLayout(this).apply{columnCount=3;useDefaultMargins=false};digits.forEach{digit->grid.addView(Button(this).apply{text=digit;textSize=19f;setTextColor(cyan);typeface=Typeface.MONOSPACE;background=neon(panel,Color.rgb(20,88,108));stateListAnimator=null;minWidth=0;minHeight=0;setOnTouchListener{_,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->{call?.playDtmfTone(digit[0]);true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{call?.stopDtmfTone();performClick();true};else->false}}},GridLayout.LayoutParams().apply{width=0;height=52.dp();columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(4.dp(),4.dp(),4.dp(),4.dp())})};keypadContainer.addView(grid);root.addView(keypadContainer)
        endButton=control("TERMINATE",magenta){call?.disconnect()};endButton.setTextColor(magenta);root.addView(endButton,LinearLayout.LayoutParams(-1,58.dp()).apply{setMargins(5.dp(),8.dp(),5.dp(),0)});setContentView(root)
    }
    private fun toggleMute(){val service=RealityInCallService.instance?:return;val muted=!service.isMutedNow();service.setMuted(muted);muteButton.text=if(muted)"UNMUTE" else "MUTE"}
    private fun toggleSpeaker(){val service=RealityInCallService.instance?:return;val audio=service.callAudioState?:return;val speaker=CallAudioState.ROUTE_SPEAKER;val earpiece=CallAudioState.ROUTE_EARPIECE;val target=if(audio.route==speaker)earpiece else speaker;if(audio.supportedRouteMask and target!=0)service.setAudioRoute(target);refreshAudioButtons()}
    private fun toggleBluetooth(){val service=RealityInCallService.instance?:return;val audio=service.callAudioState?:return;val bluetooth=CallAudioState.ROUTE_BLUETOOTH;val fallback=if(audio.supportedRouteMask and CallAudioState.ROUTE_EARPIECE!=0)CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER;val target=if(audio.route==bluetooth)fallback else bluetooth;if(audio.supportedRouteMask and target!=0)service.setAudioRoute(target);refreshAudioButtons()}
    private fun toggleHold(){val current=call?:return;when(current.state){Call.STATE_ACTIVE->current.hold();Call.STATE_HOLDING->current.unhold()}}
    private fun refresh(){call=CallSessionRegistry.primary();val current=call;if(current==null){scheduleFinish();return};val number=current.details?.handle?.schemeSpecificPart?:"UNKNOWN CALLER";if(number!=lastNumber){lastNumber=number;caller.text=resolveCallerLabel(number)};state.text=when(current.state){Call.STATE_RINGING->"● INCOMING SIGNAL";Call.STATE_DIALING->"● DIALING";Call.STATE_CONNECTING->"● CONNECTING";Call.STATE_ACTIVE->"● CHANNEL ACTIVE";Call.STATE_HOLDING->"● CHANNEL HOLD";Call.STATE_DISCONNECTED->"● CHANNEL CLOSED";else->"● CALL"};if(current.state==Call.STATE_DISCONNECTED){connectedStartedAt=null;scheduleFinish()}else finishScheduled=false;val ringing=current.state==Call.STATE_RINGING;answerButton.visibility=if(ringing)View.VISIBLE else View.GONE;rejectButton.visibility=if(ringing)View.VISIBLE else View.GONE;val interactive=current.state==Call.STATE_ACTIVE||current.state==Call.STATE_HOLDING;muteButton.isEnabled=interactive;speakerButton.isEnabled=interactive;keypadButton.isEnabled=interactive;holdButton.isEnabled=interactive;if(!interactive)keypadContainer.visibility=View.GONE;holdButton.text=if(current.state==Call.STATE_HOLDING)"RESUME" else "HOLD";endButton.isEnabled=current.state!=Call.STATE_DISCONNECTED;if((current.state==Call.STATE_ACTIVE||current.state==Call.STATE_HOLDING)&&connectedStartedAt==null)connectedStartedAt=SystemClock.elapsedRealtime();updateTimer();val service=RealityInCallService.instance;muteButton.text=if(service?.isMutedNow()==true)"UNMUTE" else "MUTE";refreshAudioButtons()}
    private fun refreshAudioButtons(){val audio=RealityInCallService.instance?.callAudioState;speakerButton.text=if(audio?.route==CallAudioState.ROUTE_SPEAKER)"EARPIECE" else "SPEAKER";bluetoothButton.text=if(audio?.route==CallAudioState.ROUTE_BLUETOOTH)"BT OFF" else "BLUETOOTH";val interactive=call?.state==Call.STATE_ACTIVE||call?.state==Call.STATE_HOLDING;bluetoothButton.isEnabled=interactive&&((audio?.supportedRouteMask?:0) and CallAudioState.ROUTE_BLUETOOTH!=0)}
    private fun scheduleFinish(){if(finishScheduled)return;finishScheduled=true;handler.postDelayed({if(!isFinishing)finish()},700L)}
    private fun resolveCallerLabel(number:String):String{if(number=="UNKNOWN CALLER"||checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return number;return try{val lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));contentResolver.query(lookup,arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)?.takeIf{it.isNotBlank()}?.let{return "$it\n$number"}};number}catch(_:Throwable){number}}
    private fun updateTimer(){val started=connectedStartedAt;if(started==null){timer.text="00:00";return};val total=(SystemClock.elapsedRealtime()-started)/1000L;val hours=total/3600L;val minutes=(total%3600L)/60L;val seconds=total%60L;timer.text=if(hours>0)String.format("%d:%02d:%02d",hours,minutes,seconds)else String.format("%02d:%02d",minutes,seconds)}
    private fun Int.dp():Int=(this*resources.displayMetrics.density).toInt()
    companion object{private const val KEY_CONNECTED_AT="connected_started_at";private const val KEY_KEYPAD_OPEN="keypad_open"}
}
