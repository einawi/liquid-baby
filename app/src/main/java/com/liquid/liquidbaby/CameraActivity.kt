package com.liquid.liquidbaby

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

/** Parent unit: live camera, live sound, flash, night mode, and a hold-to-talk button. */
class CameraActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var picture: ImageView? = null
    private var statusTv: TextView? = null
    private var flashBtn: Button? = null
    private var switchBtn: Button? = null
    private var qualityBtn: Button? = null
    private var nightBtn: Button? = null
    private var soundBtn: Button? = null
    private var talkBtn: Button? = null
    private val pending = AtomicBoolean(false)
    private var lastNight = false

    @Volatile
    private var latest: Bitmap? = null
    private var ending = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screen {
            addTitle("Live camera")
            val iv = ImageView(context)
            iv.setBackgroundColor(Color.BLACK)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(iv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(340)))
            picture = iv
            statusTv = addBody("")
            val tb = addButton("Hold to talk to the child") { }
            tb.textSize = 20f
            tb.minHeight = dp(96)
            tb.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> onTalkDown()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTalkUp()
                }
                true
            }
            talkBtn = tb
            flashBtn = addButton("Flash") {
                ParentEngine.setView(ParentEngine.viewQuality, ParentEngine.viewFront, !ParentEngine.viewFlash)
            }
            switchBtn = addButton("Switch camera") {
                ParentEngine.setView(ParentEngine.viewQuality, !ParentEngine.viewFront, false)
            }
            nightBtn = addButton("Night mode") {
                ParentEngine.setView(ParentEngine.viewQuality, ParentEngine.viewFront, ParentEngine.viewFlash, !ParentEngine.viewNight)
            }
            qualityBtn = addButton("Quality") {
                ParentEngine.setView(1 - ParentEngine.viewQuality, ParentEngine.viewFront, ParentEngine.viewFlash)
            }
            addButton("Rotate the picture") { ParentEngine.viewRotate = (ParentEngine.viewRotate + 1) and 3 }
            soundBtn = addButton("Live sound") {
                ParentEngine.liveSound = !ParentEngine.liveSound
                refresh()
            }
            addButton("Stop and close") { finish() }
            addBody("Night mode: the picture is slower and brighter, which helps in a dark room but can blur fast movement. While someone watches, the child phone does not record clips. The live view stops by itself after 15 minutes, and the flash after 2 minutes.")
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        ParentEngine.frameListener = { bmp ->
            latest = bmp
            if (pending.compareAndSet(false, true)) {
                runOnUiThread {
                    pending.set(false)
                    picture?.setImageBitmap(latest)
                }
            }
        }
        ParentEngine.onChange = { runOnUiThread { refresh() } }
        if (!ParentEngine.beginView(this)) {
            toast(ParentEngine.viewNote)
            ending = true
            finish()
            return
        }
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
        ParentEngine.frameListener = null
        ParentEngine.onChange = null
        ParentEngine.endTalk()
        ParentEngine.endView()
    }

    private fun onTalkDown() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 30)
            toast("Allow the microphone, then hold the button again.")
            return
        }
        if (ParentEngine.beginTalk()) {
            talkBtn?.text = Tr.t("Talking...  release to stop (live sound is muted)")
        } else {
            toast(ParentEngine.talkNote)
        }
    }

    private fun onTalkUp() {
        ParentEngine.endTalk()
        talkBtn?.text = Tr.t("Hold to talk to the child")
    }

    private fun refresh() {
        if (!ending && !ParentEngine.connected) {
            ending = true
            toast("The connection to the child phone was lost.")
            finish()
            return
        }
        if (!ending && !ParentEngine.viewing && ParentEngine.viewNote.isNotEmpty()) {
            ending = true
            toast(ParentEngine.viewNote)
            finish()
            return
        }
        val sb = StringBuilder()
        sb.append(if (ParentEngine.viewing) "Live" else "Starting...")
        sb.append(" - watching now: ").append(maxOf(ParentEngine.viewers, 1)).append("\n")
        if (ParentEngine.talkNote.isNotEmpty()) sb.append(ParentEngine.talkNote).append("\n")
        statusTv?.text = Tr.t(sb.toString())
        val flashState = if (ParentEngine.viewFlash) "ON (tap to turn off)" else "off (tap to turn on)"
        val flashNote = if (ParentEngine.viewHasFlash) "" else " - this camera does not report a flash, so it may not light"
        flashBtn?.text = Tr.t("Flash: $flashState$flashNote")
        flashBtn?.isEnabled = true
        switchBtn?.text = Tr.t("Camera: " + (if (ParentEngine.viewFront) "front" else "back") + " (tap to switch)")
        nightBtn?.text = Tr.t("Night mode: " + (if (ParentEngine.viewNight) "on (tap to turn off)" else "off (tap to turn on)"))
        qualityBtn?.text = Tr.t("Quality: " + (if (ParentEngine.viewQuality == 1) "Normal" else "Low") + " (tap to change)")
        soundBtn?.text = Tr.t("Live sound: " + (if (ParentEngine.liveSound) "on" else "off") + " (tap to change)")
        val night = ParentEngine.viewNight
        if (night != lastNight) {
            lastNight = night
            picture?.colorFilter = if (night) ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        1.35f, 0f, 0f, 0f, 22f,
                        0f, 1.35f, 0f, 0f, 22f,
                        0f, 0f, 1.35f, 0f, 22f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            ) else null
        }
        if (!ParentEngine.talking && talkBtn?.text?.startsWith("Talking") == true) {
            talkBtn?.text = Tr.t("Hold to talk to the child")
        }
    }
}
