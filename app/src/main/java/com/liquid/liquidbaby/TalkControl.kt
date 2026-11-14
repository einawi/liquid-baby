package com.liquid.liquidbaby

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The talk button with its Hold/Tap toggle in the corner. The main screen and the camera screen
 * both use this class, so they look and behave the same.
 */
class TalkControl(private val host: Activity) {

    private val ui = Handler(Looper.getMainLooper())
    private var glow: ObjectAnimator? = null
    private val hitRect = Rect()

    private val limit = Runnable {
        up()
        Toast.makeText(host, t("Talk stopped after 60 seconds.", "توقف الحديث بعد 60 ثانية."), Toast.LENGTH_SHORT).show()
    }

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)
    private fun mode(): String = Prefs.getString("talkMode", "hold")

    val view = FrameLayout(host)
    private val button = Button(host)
    private val label = TextView(host)
    private val toggle = ModeToggle(host)
    private val modeRow = LinearLayout(host)

    /** true while the parent is talking. */
    val talking: Boolean get() = ParentEngine.talking

    init {
        button.isAllCaps = false
        button.textSize = 18f
        button.setTextColor(Look.TEXT)
        button.background = Look.glass(host, 26, Look.BUTTON)
        button.isClickable = false
        button.isFocusable = false
        button.minHeight = host.dp(104)
        view.addView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, host.dp(104)))

        label.textSize = 12f
        label.setTextColor(Look.TEXT)
        label.setPadding(0, 0, host.dp(8), 0)
        toggle.tapMode = mode() == "press"
        toggle.onToggle = { press -> applyMode(press) }
        modeRow.orientation = LinearLayout.HORIZONTAL
        modeRow.gravity = Gravity.CENTER_VERTICAL
        modeRow.addView(label)
        modeRow.addView(toggle)
        view.addView(modeRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply {
            topMargin = host.dp(8)
            marginEnd = host.dp(12)
        })
        modeRow.bringToFront()

        // the toggle is a child, so it takes touches on its own area first; this handles the rest
        view.setOnTouchListener { _, ev ->
            modeRow.getHitRect(hitRect)
            if (!hitRect.contains(ev.x.toInt(), ev.y.toInt())) {
                if (mode() == "press") {
                    if (ev.actionMasked == MotionEvent.ACTION_UP) tap()
                } else {
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> down()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> up()
                    }
                }
            }
            true
        }
        refresh()
    }

    /** One tap: starts talking, or stops it if already talking (Tap mode, and the mic button). */
    fun tap() {
        if (ParentEngine.talking) up() else down()
    }

    fun down() {
        if (host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 30)
            Toast.makeText(host, t("Allow the microphone, then try again.", "اسمح بالميكروفون ثم حاول مرة أخرى."), Toast.LENGTH_SHORT).show()
            return
        }
        if (ParentEngine.beginTalk()) {
            button.text = t("Talking… ", "تتحدث… ") + (if (mode() == "press") t("tap to stop", "اضغط للإيقاف")
            else t("release to stop", "ارفع إصبعك للإيقاف"))
            glow?.cancel()
            glow = Fx.breathe(button)
            if (mode() == "press") ui.postDelayed(limit, 60_000L)
        } else {
            Toast.makeText(host, ParentEngine.talkNote, Toast.LENGTH_SHORT).show()
        }
    }

    fun up() {
        ui.removeCallbacks(limit)
        ParentEngine.endTalk()
        glow?.cancel()
        glow = null
        button.alpha = 1f
        refresh()
    }

    private fun applyMode(press: Boolean) {
        Diag.i("Talk mode changed to " + (if (press) "tap" else "hold"))
        Prefs.putString("talkMode", if (press) "press" else "hold")
        if (ParentEngine.talking) up()
        refresh()
    }

    /** Brings the labels up to date with the current mode and talk state. */
    fun refresh() {
        label.text = if (mode() == "press") t("Tap", "نقرة") else t("Hold", "ضغط")
        if (!ParentEngine.talking) {
            button.text = if (mode() == "press") t("Tap to talk", "اضغط للتحدث")
            else t("Hold to talk", "اضغط مع الاستمرار للتحدث")
        }
    }
}
