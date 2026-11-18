package com.liquid.liquidbaby

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Talk to partner": the same card, size, colours and Hold/Tap switch as the talk-to-child button.
 * Only the name differs. While the partner is not connected it is dimmed and says why; a notice
 * appears below it while the partner is talking to this phone.
 */
class PartnerTalkControl(private val host: Activity) {

    /** The whole control: the button card on top, a notice line below it. */
    val view = LinearLayout(host)
    private val frame = FrameLayout(host)
    private val button = Button(host)
    private val label = TextView(host)
    private val toggle = ModeToggle(host)
    private val modeRow = LinearLayout(host)
    private val notice = TextView(host)

    private var connectedNow = false
    private var partnerTalking = false
    private var sending = false
    private var glow: ObjectAnimator? = null
    private var lastText = ""

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun pressMode(): Boolean = Prefs.getString("talkMode", "hold") == "press"

    private fun hasMic(): Boolean =
        host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun pressIn() {
        button.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
    }

    private fun pressOut() {
        button.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    /** Starts talking to the partner; asks for the microphone the first time, as the child talk does. */
    private fun startTalking(): Boolean {
        if (!hasMic()) {
            host.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 77)
            return false
        }
        return ParentEngine.beginPartnerTalk()
    }

    /** The label follows the same pattern as the child button: "Tap to talk" / "Hold to talk", and while talking. */
    private fun refreshLook() {
        val press = pressMode()
        val text = when {
            sending -> if (press) t("Talking… tap to stop", "أتحدث… اضغط للإيقاف") else t("Talking… release to stop", "أتحدث… ارفع إصبعك للإيقاف")
            press -> t("Tap to talk to partner", "اضغط للتحدث مع الشريك")
            else -> t("Hold to talk to partner", "اضغط مع الاستمرار للتحدث مع الشريك")
        }
        val shown = if (!connectedNow && !sending) text + "\n" + t("Partner not connected", "الشريك غير متصل") else text
        if (shown != lastText) {
            button.text = shown
            lastText = shown
        }
        button.alpha = if (connectedNow || sending) 1f else 0.45f
        button.isEnabled = true
        label.text = if (press) t("Tap", "نقرة") else t("Hold", "ضغط")
        notice.text = when {
            partnerTalking -> t("Partner is talking to you", "الشريك يتحدث إليك")
            else -> ""
        }
    }

    init {
        button.isAllCaps = false
        button.textSize = 18f
        button.setTextColor(Look.TEXT)
        button.background = Look.glass(host, 26, Look.BUTTON)
        button.isClickable = false
        button.isFocusable = false
        button.minHeight = host.dp(104)
        frame.addView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, host.dp(104)))

        label.textSize = 13f
        label.setTextColor(0xFFFFFFFF.toInt())
        label.setPadding(0, 0, host.dp(8), 0)
        toggle.tapMode = pressMode()
        toggle.onToggle = { press ->
            Prefs.putString("talkMode", if (press) "press" else "hold")
            refreshLook()
        }
        modeRow.orientation = LinearLayout.HORIZONTAL
        modeRow.gravity = Gravity.CENTER_VERTICAL
        modeRow.background = Look.glass(host, 14, 0x66000000)
        modeRow.setPadding(host.dp(8), host.dp(4), host.dp(6), host.dp(4))
        modeRow.addView(label)
        modeRow.addView(toggle)
        frame.addView(modeRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply {
            topMargin = host.dp(8)
            marginEnd = host.dp(12)
        })
        modeRow.bringToFront()
        modeRow.elevation = host.dp(8).toFloat()
        frame.clipChildren = false
        frame.clipToPadding = false

        notice.textSize = 12f
        notice.setTextColor(0xFFB4C0DC.toInt())
        notice.gravity = Gravity.CENTER_HORIZONTAL
        notice.setPadding(0, host.dp(6), 0, 0)

        view.orientation = LinearLayout.VERTICAL
        view.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        view.addView(notice, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        frame.setOnTouchListener { _, ev ->
            val press = pressMode()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!connectedNow) return@setOnTouchListener true
                    pressIn()
                    if (!press) startTalking()
                }
                MotionEvent.ACTION_UP -> {
                    pressOut()
                    if (!connectedNow) return@setOnTouchListener true
                    if (press) {
                        if (ParentEngine.partnerSending) ParentEngine.endPartnerTalk()
                        else startTalking()
                    } else {
                        ParentEngine.endPartnerTalk()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressOut()
                    if (!press) ParentEngine.endPartnerTalk()
                }
            }
            true
        }
        refreshLook()
    }

    /** Shows that this parent is talking to the partner: the same card, with the talking label. */
    fun setSending(on: Boolean) {
        if (on == sending) return
        sending = on
        glow?.cancel()
        glow = if (on) Fx.breathe(button) else null
        refreshLook()
    }

    /** Shows the partner link: dimmed with the reason while not connected, and the notice while the partner talks. */
    fun setState(connected: Boolean, talking: Boolean) {
        connectedNow = connected
        partnerTalking = talking
        refreshLook()
    }
}
