package com.realityengine.v4

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/** RE Core Ignition rendered directly on Canvas: no image/font/network dependency.
 * A hard timeout always hands control to MainActivity even if animation fails. */
class SplashActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper());private var launched=false
    private val openMain=Runnable{openApp()}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;setContentView(CoreView(this));handler.postDelayed(openMain,1650L)}
    private fun openApp(){if(launched||isFinishing)return;launched=true;handler.removeCallbacks(openMain);startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();@Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out)}
    override fun onDestroy(){handler.removeCallbacks(openMain);super.onDestroy()}

    private class CoreView(context:Context):View(context){
        private val cyan=Color.rgb(0,220,255);private val magenta=Color.rgb(218,42,255)
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
        private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD);textAlign=Paint.Align.CENTER}
        private var p=0f
        private val animator=ValueAnimator.ofFloat(0f,1f).apply{duration=1550L;interpolator=LinearInterpolator();addUpdateListener{p=it.animatedValue as Float;invalidate()};start()}
        override fun onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow()}
        override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.BLACK);val cx=width/2f;val cy=height*.43f;val base=min(width,height)*.16f
            val ignite=(p/.22f).coerceIn(0f,1f);val build=((p-.12f)/.42f).coerceIn(0f,1f);val identity=((p-.48f)/.22f).coerceIn(0f,1f);val titleA=((p-.65f)/.18f).coerceIn(0f,1f);val online=((p-.78f)/.15f).coerceIn(0f,1f)
            paint.style=Paint.Style.FILL;for(i in 7 downTo 1){paint.color=Color.argb((12*ignite).toInt(),0,210,255);c.drawCircle(cx,cy,base*.055f*i,paint)};paint.color=Color.argb((255*ignite).toInt(),220,245,255);c.drawCircle(cx,cy,base*.055f*ignite,paint)
            paint.style=Paint.Style.STROKE
            for(r in 0..6){val radius=base*(.52f+r*.20f)*build;paint.strokeWidth=if(r<2)5f else 3f;val rect=RectF(cx-radius,cy-radius,cx+radius,cy+radius);val rot=p*150f*(if(r%2==0)1 else -1);for(s in 0..3){paint.color=if((r+s)%2==0)Color.argb((225*build).toInt(),0,220,255) else Color.argb((210*build).toInt(),218,42,255);c.drawArc(rect,rot+s*90f,38f+(r%3)*9f,false,paint)}}
            for(r in 7..10){val radius=base*(.52f+r*.20f)*build;paint.strokeWidth=1.5f;paint.color=Color.argb((55*build).toInt(),0,190,255);c.drawArc(RectF(cx-radius,cy-radius,cx+radius,cy+radius),p*-90f+r*21f,52f,false,paint)}
            val logoSize=base*.72f;text.textSize=logoSize;text.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD_ITALIC);text.color=Color.argb((255*identity).toInt(),0,220,255);text.setShadowLayer(20f,0f,0f,cyan);c.drawText("R",cx-logoSize*.25f,cy+logoSize*.25f,text);text.color=Color.argb((255*identity).toInt(),218,42,255);text.setShadowLayer(20f,0f,0f,magenta);c.drawText("E",cx+logoSize*.25f,cy+logoSize*.25f,text);text.clearShadowLayer()
            text.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL);text.textSize=base*.18f;text.letterSpacing=.18f;text.color=Color.argb((255*titleA).toInt(),245,248,255);c.drawText("REALITY ENGINE",cx,cy+base*2.15f,text)
            text.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);text.textSize=base*.085f;text.letterSpacing=.08f;text.color=Color.argb((255*online).toInt(),0,235,255);c.drawText("CONVERSATION  |  CONTEXT  |  CLARITY",cx,cy+base*2.55f,text);text.color=Color.argb((255*online).toInt(),0,255,190);c.drawText("SYSTEM ONLINE",cx,cy+base*3.05f,text)
        }
    }
}
