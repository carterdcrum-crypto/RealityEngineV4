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
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class CallActivity : Activity() {
    private var call: Call? = null
    private lateinit var caller: TextView
    private lateinit var state: TextView
    private lateinit var timer: TextView
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button
    private lateinit var keypadContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var activeStartedAt: Long? = null
    private val registryListener: () -> Unit = { runOnUiThread { refresh() } }
    private val timerTick = object : Runnable { override fun run() { updateTimer(); handler.postDelayed(this, 1000L) } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi(); refresh() }
    override fun onResume() { super.onResume(); CallSessionRegistry.addListener(registryListener); refresh(); handler.removeCallbacks(timerTick); handler.post(timerTick) }
    override fun onPause() { handler.removeCallbacks(timerTick); CallSessionRegistry.removeListener(registryListener); super.onPause() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(24,32,24,24); setBackgroundColor(Color.rgb(10,10,14)) }
        caller=TextView(this).apply{textSize=26f;setTextColor(Color.WHITE);gravity=Gravity.CENTER};root.addView(caller,LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT))
        state=TextView(this).apply{textSize=16f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(0,12,0,4)};root.addView(state)
        timer=TextView(this).apply{text="00:00";textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,0,0,18)};root.addView(timer)
        root.addView(Button(this).apply{text="ANSWER";setOnClickListener{call?.takeIf{it.state==Call.STATE_RINGING}?.answer(0)}})
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        muteButton=Button(this).apply{setOnClickListener{toggleMute()}};speakerButton=Button(this).apply{setOnClickListener{toggleSpeaker()}}
        controls.addView(muteButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});controls.addView(speakerButton,LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(4,4,4,4)});root.addView(controls)
        root.addView(Button(this).apply{text="KEYPAD";setOnClickListener{keypadContainer.visibility=if(keypadContainer.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}})
        keypadContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE}
        val digits=arrayOf("1","2","3","4","5","6","7","8","9","*","0","#");val grid=GridLayout(this).apply{columnCount=3;useDefaultMargins=true}
        digits.forEach{digit->grid.addView(Button(this).apply{text=digit;textSize=20f;minWidth=0;minHeight=0;setOnClickListener{sendDtmf(digit[0])}},GridLayout.LayoutParams().apply{width=0;height=56.dp();columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)})};keypadContainer.addView(grid);root.addView(keypadContainer)
        root.addView(Button(this).apply{text="END CALL";setOnClickListener{call?.disconnect()}});setContentView(root)
    }

    private fun toggleMute(){val service=RealityInCallService.instance?:return;val muted=!service.isMutedNow();service.setMuted(muted);muteButton.text=if(muted)"UNMUTE" else "MUTE"}
    private fun toggleSpeaker(){val service=RealityInCallService.instance?:return;val audio=service.callAudioState?:return;val speaker=CallAudioState.ROUTE_SPEAKER;val earpiece=CallAudioState.ROUTE_EARPIECE;val bluetooth=CallAudioState.ROUTE_BLUETOOTH;val target=when{audio.route==speaker->earpiece;audio.supportedRouteMask and speaker!=0->speaker;audio.supportedRouteMask and bluetooth!=0->bluetooth;else->earpiece};service.setAudioRoute(target);speakerButton.text=if(target==speaker)"EARPIECE" else "SPEAKER"}
    private fun sendDtmf(digit:Char){call?.let{it.playDtmfTone(digit);it.stopDtmfTone()}}

    private fun refresh(){
        call=CallSessionRegistry.primary();val current=call
        if(current==null){finish();return}
        val number=current.details?.handle?.schemeSpecificPart?:"UNKNOWN CALLER"
        caller.text=resolveCallerLabel(number)
        state.text=when(current.state){Call.STATE_RINGING->"INCOMING CALL";Call.STATE_DIALING->"DIALING";Call.STATE_CONNECTING->"CONNECTING";Call.STATE_ACTIVE->"ACTIVE CALL";Call.STATE_HOLDING->"ON HOLD";Call.STATE_DISCONNECTED->"CALL ENDED";else->"CALL"}
        if(current.state==Call.STATE_ACTIVE&&activeStartedAt==null)activeStartedAt=SystemClock.elapsedRealtime()
        if(current.state==Call.STATE_DISCONNECTED)activeStartedAt=null
        updateTimer()
        val service=RealityInCallService.instance;muteButton.text=if(service?.isMutedNow()==true)"UNMUTE" else "MUTE";speakerButton.text=if(service?.callAudioState?.route==CallAudioState.ROUTE_SPEAKER)"EARPIECE" else "SPEAKER"
    }

    private fun resolveCallerLabel(number:String):String {
        if(number=="UNKNOWN CALLER"||checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return number
        return try {
            val lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number))
            contentResolver.query(lookup,arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),null,null,null)?.use { c ->
                if(c.moveToFirst()) c.getString(0)?.takeIf{it.isNotBlank()}?.let{return "$it\n$number"}
            }
            number
        } catch (_:Throwable) { number }
    }

    private fun updateTimer(){val started=activeStartedAt;if(started==null){timer.text="00:00";return};val total=(SystemClock.elapsedRealtime()-started)/1000L;val hours=total/3600L;val minutes=(total%3600L)/60L;val seconds=total%60L;timer.text=if(hours>0)String.format("%d:%02d:%02d",hours,minutes,seconds) else String.format("%02d:%02d",minutes,seconds)}
    private fun Int.dp():Int=(this*resources.displayMetrics.density).toInt()
}
