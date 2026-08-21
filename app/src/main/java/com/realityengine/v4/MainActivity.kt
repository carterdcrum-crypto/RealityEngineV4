package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status:TextView;private lateinit var shizukuStatus:TextView;private lateinit var audioStatus:TextView;private lateinit var number:EditText;private lateinit var error:TextView;private lateinit var content:LinearLayout
    private val bg=Color.rgb(8,10,14);private val panel=Color.rgb(22,25,31);private val soft=Color.rgb(35,39,47);private val accent=Color.rgb(73,215,142);private val muted=Color.rgb(145,151,162)
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildPhoneUi()}
    override fun onResume(){super.onResume();if(::status.isInitialized){updateRoleStatus();updateShizukuStatus();updateAudioStatus()}}
    private fun rounded(color:Int,r:Float=24f)=GradientDrawable().apply{setColor(color);cornerRadius=r.dpF()}
    private fun textButton(label:String,click:()->Unit)=Button(this).apply{text=label;textSize=13f;setTextColor(Color.WHITE);background=rounded(soft,18f);setOnClickListener{click()};stateListAnimator=null}
    private fun buildPhoneUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20.dp(),18.dp(),20.dp(),12.dp());setBackgroundColor(bg)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        top.addView(TextView(this).apply{text="Reality Engine";textSize=24f;setTextColor(Color.WHITE);typeface=android.graphics.Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,52.dp(),1f))
        top.addView(textButton("SETTINGS") { showSettings() }.apply{textSize=11f},LinearLayout.LayoutParams(96.dp(),48.dp()))
        root.addView(top)
        status=TextView(this).apply{textSize=12f;setTextColor(accent);setPadding(2.dp(),0,0,10.dp())};root.addView(status)
        shizukuStatus=TextView(this).apply{visibility=View.GONE};audioStatus=TextView(this).apply{visibility=View.GONE};root.addView(shizukuStatus);root.addView(audioStatus)
        val scroll=ScrollView(this).apply{isFillViewport=true};content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.TOP};scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(0,8.dp(),0,0)}
        nav.addView(textButton("PHONE"){showPhone()},LinearLayout.LayoutParams(0,54.dp(),1f).apply{setMargins(3.dp(),0,3.dp(),0)})
        nav.addView(textButton("RECENTS"){showRecents()},LinearLayout.LayoutParams(0,54.dp(),1f).apply{setMargins(3.dp(),0,3.dp(),0)})
        nav.addView(textButton("CONTACTS"){showContacts()},LinearLayout.LayoutParams(0,54.dp(),1f).apply{setMargins(3.dp(),0,3.dp(),0)})
        root.addView(nav);setContentView(root);updateRoleStatus();updateShizukuStatus();updateAudioStatus();showPhone()
    }
    private fun showPhone(){content.removeAllViews();content.gravity=Gravity.TOP
        number=EditText(this).apply{hint="Enter number";setHintTextColor(muted);setTextColor(Color.WHITE);textSize=30f;gravity=Gravity.CENTER;background=null;inputType=android.text.InputType.TYPE_CLASS_PHONE;isSingleLine=true;setPadding(0,22.dp(),0,18.dp())};content.addView(number,LinearLayout.LayoutParams(-1,78.dp()))
        val grid=GridLayout(this).apply{columnCount=3;rowCount=4;alignmentMode=GridLayout.ALIGN_BOUNDS;useDefaultMargins=false};val keys=arrayOf("1","2","3","4","5","6","7","8","9","*","0","#")
        keys.forEach{key->val b=Button(this).apply{text=key;textSize=25f;setTextColor(Color.WHITE);background=rounded(panel,40f);stateListAnimator=null;setOnClickListener{number.append(key)}};grid.addView(b,GridLayout.LayoutParams().apply{width=0;height=72.dp();columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(7.dp(),7.dp(),7.dp(),7.dp())})};content.addView(grid)
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(0,12.dp(),0,0)}
        actions.addView(textButton("⌫"){val t=number.text;if(t.isNotEmpty())t.delete(t.length-1,t.length)}.apply{setOnLongClickListener{number.text.clear();true}},LinearLayout.LayoutParams(68.dp(),58.dp()).apply{setMargins(4.dp(),0,8.dp(),0)})
        actions.addView(Button(this).apply{text="CALL";textSize=17f;setTextColor(Color.rgb(5,20,12));background=rounded(accent,28f);stateListAnimator=null;setOnClickListener{placeCall(number.text.toString().trim())}},LinearLayout.LayoutParams(0,58.dp(),1f).apply{setMargins(8.dp(),0,4.dp(),0)});content.addView(actions)
        error=TextView(this).apply{textSize=13f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(0,12.dp(),0,0)};content.addView(error)
    }
    private fun showRecents(){content.removeAllViews();sectionTitle("Recents");if(checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){content.addView(textButton("Allow call history"){requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG),REQ_CALL_LOG)});return};val c:Cursor?=contentResolver.query(CallLog.Calls.CONTENT_URI,arrayOf(CallLog.Calls.NUMBER,CallLog.Calls.TYPE,CallLog.Calls.DURATION),null,null,"${CallLog.Calls.DATE} DESC");c?.use{var count=0;while(it.moveToNext()&&count<30){val n=it.getString(0)?:"Unknown";val type=when(it.getInt(1)){CallLog.Calls.INCOMING_TYPE->"Incoming";CallLog.Calls.OUTGOING_TYPE->"Outgoing";CallLog.Calls.MISSED_TYPE->"Missed";else->"Call"};val dur=it.getLong(2);content.addView(listButton("$n\n$type · ${dur}s"){openRecent(n)}.apply{setOnLongClickListener{placeCall(n);true}});count++}}}
    private fun showContacts(query:String=""){content.removeAllViews();sectionTitle("Contacts");if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){content.addView(textButton("Allow contacts"){requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),REQ_CONTACTS)});return};val search=EditText(this).apply{hint="Search name or number";setHintTextColor(muted);setTextColor(Color.WHITE);setText(query);isSingleLine=true;background=rounded(panel,20f);setPadding(18.dp(),0,18.dp(),0)};content.addView(search,LinearLayout.LayoutParams(-1,52.dp()).apply{setMargins(0,0,0,8.dp())});content.addView(textButton("SEARCH"){showContacts(search.text.toString().trim())});val sel=if(query.isBlank())null else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?";val args=if(query.isBlank())null else arrayOf("%$query%","%$query%");contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),sel,args,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")?.use{var count=0;while(it.moveToNext()&&count<100){val name=it.getString(0)?:"Unknown";val phone=it.getString(1)?:"";content.addView(listButton("$name\n$phone"){numberFromContact(phone)}.apply{setOnLongClickListener{placeCall(phone);true}});count++}}}
    private fun showSettings(){content.removeAllViews();sectionTitle("Settings");content.addView(settingCard("Default phone app",status.text.toString()){requestDefaultPhoneRole()});content.addView(settingCard("Shizuku audio",shizukuStatus.text.toString()){requestShizuku()});content.addView(settingCard("Call audio",audioStatus.text.toString()){checkCallAudio()});content.addView(settingCard("Android phone settings","Manage default calling apps"){startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))})}
    private fun sectionTitle(t:String){content.addView(TextView(this).apply{text=t;textSize=27f;setTextColor(Color.WHITE);typeface=android.graphics.Typeface.DEFAULT_BOLD;setPadding(2.dp(),18.dp(),0,18.dp())})}
    private fun listButton(label:String,click:()->Unit)=Button(this).apply{text=label;textSize=16f;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setTextColor(Color.WHITE);background=rounded(panel,18f);stateListAnimator=null;setPadding(18.dp(),0,18.dp(),0);setOnClickListener{click()}}.also{it.layoutParams=LinearLayout.LayoutParams(-1,68.dp()).apply{setMargins(0,4.dp(),0,4.dp())}}
    private fun settingCard(title:String,sub:String,click:()->Unit)=Button(this).apply{text="$title\n$sub";textSize=15f;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setTextColor(Color.WHITE);background=rounded(panel,18f);stateListAnimator=null;setPadding(18.dp(),0,18.dp(),0);setOnClickListener{click()}}.also{it.layoutParams=LinearLayout.LayoutParams(-1,76.dp()).apply{setMargins(0,5.dp(),0,5.dp())}}
    private fun updateShizukuStatus(){shizukuStatus.text=when{!ShizukuAudioStatus.binderAvailable()->"Not running";!ShizukuAudioStatus.permissionGranted()->"Permission required";else->"Connected"}}
    private fun updateAudioStatus(){audioStatus.text=when(CallAudioBridge.state(this)){CallAudioBridge.State.UNAVAILABLE->"Waiting for Shizuku";CallAudioBridge.State.SHIZUKU_READY->"Shizuku ready";CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED->"Microphone permission required";CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE->"VOICE_CALL available";CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED->"VOICE_CALL blocked"}}
    private fun checkCallAudio(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),REQ_AUDIO);return};updateAudioStatus();showSettings()}
    private fun requestShizuku(){if(ShizukuAudioStatus.binderAvailable()&&!ShizukuAudioStatus.permissionGranted())ShizukuAudioStatus.requestPermission();updateShizukuStatus();updateAudioStatus();showSettings()}
    private fun openRecent(phone:String){showPhone();number.setText(phone);number.setSelection(number.length())}
    private fun numberFromContact(phone:String){showPhone();number.setText(phone);number.setSelection(number.length())}
    private fun updateRoleStatus(){val t=getSystemService(Context.TELECOM_SERVICE) as TelecomManager;status.text=if(t.defaultDialerPackage==packageName)"● Ready" else "● Set as default phone app"}
    private fun requestDefaultPhoneRole(){val r=getSystemService(RoleManager::class.java);if(r!=null&&r.isRoleAvailable(RoleManager.ROLE_DIALER)&&!r.isRoleHeld(RoleManager.ROLE_DIALER))startActivityForResult(r.createRequestRoleIntent(RoleManager.ROLE_DIALER),REQ_ROLE)else updateRoleStatus()}
    private fun placeCall(v:String){if(v.isEmpty()){if(::error.isInitialized)error.text="Enter a phone number";return};val t=getSystemService(Context.TELECOM_SERVICE) as TelecomManager;if(t.defaultDialerPackage!=packageName){if(::error.isInitialized)error.text="Set Reality Engine as your default phone app first";return};if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CALL_PHONE),REQ_CALL);return};try{t.placeCall(Uri.fromParts("tel",v,null),null)}catch(e:Exception){if(::error.isInitialized)error.text="Unable to place call"}}
    override fun onRequestPermissionsResult(rc:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(rc,p,g);when(rc){REQ_CALL->if(g.firstOrNull()==PackageManager.PERMISSION_GRANTED&&::number.isInitialized)placeCall(number.text.toString().trim());REQ_CALL_LOG->showRecents();REQ_CONTACTS->showContacts();REQ_AUDIO->{updateAudioStatus();showSettings()}}}
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt();private fun Float.dpF()=this*resources.displayMetrics.density
    companion object{private const val REQ_ROLE=1001;private const val REQ_CALL=1002;private const val REQ_CALL_LOG=1003;private const val REQ_CONTACTS=1004;private const val REQ_AUDIO=1005}
}
