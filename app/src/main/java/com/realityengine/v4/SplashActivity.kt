package com.realityengine.v4

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView

/** Dependency-free RE Core Ignition splash with a hard handoff timeout. */
class SplashActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper());private var launched=false
    private val openMain=Runnable{openApp()}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=Color.rgb(3,7,12);window.navigationBarColor=Color.rgb(3,7,12);setContentView(buildSplash());handler.postDelayed(openMain,1450L)}
    private fun buildSplash():View{
        val root=FrameLayout(this).apply{setBackgroundColor(Color.rgb(3,7,12))}
        val core=TextView(this).apply{text="RE";textSize=42f;gravity=Gravity.CENTER;setTextColor(Color.rgb(40,224,255));typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);alpha=0f;scaleX=.45f;scaleY=.45f};root.addView(core,FrameLayout.LayoutParams(132.dp(),132.dp(),Gravity.CENTER))
        val outer=TextView(this).apply{text="◯";textSize=116f;gravity=Gravity.CENTER;setTextColor(Color.rgb(255,55,190));alpha=0f;scaleX=.35f;scaleY=.35f};root.addView(outer,FrameLayout.LayoutParams(190.dp(),190.dp(),Gravity.CENTER))
        val inner=TextView(this).apply{text="◎";textSize=82f;gravity=Gravity.CENTER;setTextColor(Color.rgb(40,224,255));alpha=0f;rotation=-45f};root.addView(inner,FrameLayout.LayoutParams(150.dp(),150.dp(),Gravity.CENTER))
        val title=TextView(this).apply{text="REALITY ENGINE";textSize=17f;letterSpacing=.22f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);alpha=0f;translationY=18.dp().toFloat()};root.addView(title,FrameLayout.LayoutParams(-1,56.dp(),Gravity.CENTER).apply{topMargin=210.dp()})
        val status=TextView(this).apply{text="CORE // INITIALIZING";textSize=9f;letterSpacing=.14f;gravity=Gravity.CENTER;setTextColor(Color.rgb(75,255,165));typeface=Typeface.MONOSPACE;alpha=0f};root.addView(status,FrameLayout.LayoutParams(-1,40.dp(),Gravity.CENTER).apply{topMargin=292.dp()})
        try{AnimatorSet().apply{interpolator=AccelerateDecelerateInterpolator();playTogether(ObjectAnimator.ofFloat(core,View.ALPHA,0f,1f).setDuration(360),ObjectAnimator.ofFloat(core,View.SCALE_X,.45f,1f).setDuration(620),ObjectAnimator.ofFloat(core,View.SCALE_Y,.45f,1f).setDuration(620),ObjectAnimator.ofFloat(outer,View.ALPHA,0f,.72f).setDuration(500),ObjectAnimator.ofFloat(outer,View.SCALE_X,.35f,1f).setDuration(760),ObjectAnimator.ofFloat(outer,View.SCALE_Y,.35f,1f).setDuration(760),ObjectAnimator.ofFloat(outer,View.ROTATION,0f,110f).setDuration(1200),ObjectAnimator.ofFloat(inner,View.ALPHA,0f,.85f).setDuration(520),ObjectAnimator.ofFloat(inner,View.ROTATION,-45f,85f).setDuration(1200),ObjectAnimator.ofFloat(title,View.ALPHA,0f,1f).setDuration(900),ObjectAnimator.ofFloat(title,View.TRANSLATION_Y,18.dp().toFloat(),0f).setDuration(900),ObjectAnimator.ofFloat(status,View.ALPHA,0f,1f).setDuration(1050));start()}}catch(_:Throwable){core.alpha=1f;title.alpha=1f;status.alpha=1f}
        return root
    }
    private fun openApp(){if(launched||isFinishing)return;launched=true;handler.removeCallbacks(openMain);startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out)}
    override fun onDestroy(){handler.removeCallbacks(openMain);super.onDestroy()}
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
}
