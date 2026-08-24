package com.realityengine.v4

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.VideoView

/** Full-screen bundled video splash. CENTER_CROP-style layout fills tall phone displays,
 * playback is muted, and the hard timeout guarantees startup even on media failure. */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var launched = false
    private var player: MediaPlayer? = null
    private val hardHandoff = Runnable { openApp() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = object : VideoView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = MeasureSpec.getSize(widthMeasureSpec)
                val h = MeasureSpec.getSize(heightMeasureSpec)
                val vw = player?.videoWidth ?: 0
                val vh = player?.videoHeight ?: 0
                if (vw > 0 && vh > 0 && w > 0 && h > 0) {
                    val videoRatio = vw.toFloat() / vh
                    val screenRatio = w.toFloat() / h
                    val measuredW: Int
                    val measuredH: Int
                    if (videoRatio > screenRatio) {
                        measuredH = h
                        measuredW = (h * videoRatio).toInt()
                    } else {
                        measuredW = w
                        measuredH = (w / videoRatio).toInt()
                    }
                    setMeasuredDimension(measuredW, measuredH)
                } else setMeasuredDimension(w, h)
            }
        }
        root.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        setContentView(root)

        try {
            video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.reality_engine_splash}"))
            video.setOnPreparedListener { mp ->
                player = mp
                mp.setVolume(0f, 0f)
                mp.isLooping = false
                video.requestLayout()
                val duration = mp.duration.toLong().coerceAtLeast(500L)
                handler.removeCallbacks(hardHandoff)
                handler.postDelayed(hardHandoff, (duration + 1200L).coerceAtMost(15_000L))
                video.start()
            }
            video.setOnCompletionListener { openApp() }
            video.setOnErrorListener { _, _, _ -> openApp(); true }
            handler.postDelayed(hardHandoff, 8_000L)
        } catch (_: Throwable) {
            openApp()
        }
    }

    private fun openApp() {
        if (launched || isFinishing) return
        launched = true
        handler.removeCallbacks(hardHandoff)
        player?.setVolume(0f, 0f)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        handler.removeCallbacks(hardHandoff)
        player = null
        super.onDestroy()
    }
}
