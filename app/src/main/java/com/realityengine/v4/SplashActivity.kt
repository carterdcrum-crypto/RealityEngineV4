package com.realityengine.v4

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/** Lucid Prism core ignition. A hard timeout always hands control to MainActivity. */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var launched = false
    private val openMain = Runnable { openApp() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = RealityVisuals.Colors.Background
        window.navigationBarColor = RealityVisuals.Colors.Background
        setContentView(CoreView(this))
        handler.postDelayed(openMain, 1650L)
    }

    private fun openApp() {
        if (launched || isFinishing) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            handler.removeCallbacks(openMain)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
            return
        }
        launchMain()
    }

    private fun launchMain() {
        if (launched || isFinishing) return
        launched = true
        handler.removeCallbacks(openMain)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) launchMain()
    }

    override fun onDestroy() {
        handler.removeCallbacks(openMain)
        super.onDestroy()
    }

    private class CoreView(context: Context) : View(context) {
        private val ice = RealityVisuals.Colors.Cyan
        private val lilac = RealityVisuals.Colors.Lilac
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private var p = 0f
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1550L
            interpolator = LinearInterpolator()
            addUpdateListener {
                p = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            c.drawColor(Color.argb(164, 1, 4, 12))
            val cx = width / 2f
            val cy = height * .43f
            val base = min(width, height) * .16f
            val ignite = (p / .22f).coerceIn(0f, 1f)
            val build = ((p - .12f) / .42f).coerceIn(0f, 1f)
            val identity = ((p - .48f) / .22f).coerceIn(0f, 1f)
            val titleA = ((p - .65f) / .18f).coerceIn(0f, 1f)
            val online = ((p - .78f) / .15f).coerceIn(0f, 1f)

            paint.style = Paint.Style.FILL
            for (i in 7 downTo 1) {
                paint.color = Color.argb((8 * ignite).toInt(), Color.red(ice), Color.green(ice), Color.blue(ice))
                c.drawCircle(cx, cy, base * .055f * i, paint)
            }
            paint.color = Color.argb((245 * ignite).toInt(), 238, 247, 255)
            c.drawCircle(cx, cy, base * .052f * ignite, paint)

            paint.style = Paint.Style.STROKE
            for (r in 0..6) {
                val radius = base * (.52f + r * .20f) * build
                paint.strokeWidth = if (r < 2) 4f else 2.4f
                val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val rot = p * 120f * (if (r % 2 == 0) 1 else -1)
                for (s in 0..3) {
                    val source = if ((r + s) % 2 == 0) ice else lilac
                    paint.color = Color.argb(
                        ((if ((r + s) % 2 == 0) 182 else 166) * build).toInt(),
                        Color.red(source), Color.green(source), Color.blue(source),
                    )
                    c.drawArc(rect, rot + s * 90f, 34f + (r % 3) * 8f, false, paint)
                }
            }
            for (r in 7..10) {
                val radius = base * (.52f + r * .20f) * build
                paint.strokeWidth = 1.2f
                paint.color = Color.argb((34 * build).toInt(), Color.red(ice), Color.green(ice), Color.blue(ice))
                c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), p * -75f + r * 21f, 48f, false, paint)
            }

            val logoSize = base * .72f
            text.textSize = logoSize
            text.typeface = Typeface.create("sans-serif-medium", Typeface.ITALIC)
            text.color = Color.argb((255 * identity).toInt(), Color.red(ice), Color.green(ice), Color.blue(ice))
            text.setShadowLayer(16f, 0f, 0f, ice)
            c.drawText("R", cx - logoSize * .25f, cy + logoSize * .25f, text)
            text.color = Color.argb((255 * identity).toInt(), Color.red(lilac), Color.green(lilac), Color.blue(lilac))
            text.setShadowLayer(16f, 0f, 0f, lilac)
            c.drawText("E", cx + logoSize * .25f, cy + logoSize * .25f, text)
            text.clearShadowLayer()

            text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            text.textSize = base * .18f
            text.letterSpacing = .16f
            text.color = Color.argb((255 * titleA).toInt(), 246, 248, 255)
            c.drawText("REALITY ENGINE", cx, cy + base * 2.15f, text)

            text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            text.textSize = base * .082f
            text.letterSpacing = .075f
            text.color = Color.argb((235 * online).toInt(), 168, 196, 238)
            c.drawText("CONVERSATION  |  CONTEXT  |  CLARITY", cx, cy + base * 2.55f, text)
            text.color = Color.argb((255 * online).toInt(), 99, 244, 142)
            c.drawText("SYSTEM ONLINE", cx, cy + base * 3.05f, text)
        }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 2401
    }
}
