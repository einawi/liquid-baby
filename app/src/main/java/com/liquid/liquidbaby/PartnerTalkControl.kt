package com.liquid.liquidbaby

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Talk to partner": the same look and the same Hold/Tap switch as the talk-to-child button, placed below it.
 * Shown greyed out with its reason until the other parent is connected. The audio relay is added later;
 * this control only shows the states.
 */
class PartnerTalkControl(private val host: Activity) {

    val view = FrameLayout(host)
    private val button = Button(host)
    private val label = TextView(host)
    private val toggle = ModeToggle(host)
    private val modeRow = LinearLayout(host)

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    init {
        button.isAllCaps = false
        button.textSize = 18f
        button.setTextColor(Look.TEXT)
        button.background = Look.glass(host, 26, Look.BUTTON)
        button.isClickable = false
        button.isFocusable = false
        button.minHeight = host.dp(104)
        button.text = t("Talk to partner", "التحدث مع الشريك")
        view.addView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, host.dp(104)))

        label.textSize = 13f
        label.setTextColor(0xFFFFFFFF.toInt())
        label.setPadding(0, 0, host.dp(8), 0)
        label.text = t("Hold", "ضغط")
        toggle.tapMode = Prefs.getString("talkMode", "hold") == "press"
        toggle.onToggle = { press ->
            Prefs.putString("talkMode", if (press) "press" else "hold")
            label.text = if (press) t("Tap", "نقرة") else t("Hold", "ضغط")
        }
        modeRow.orientation = LinearLayout.HORIZONTAL
        modeRow.gravity = Gravity.CENTER_VERTICAL
        modeRow.background = Look.glass(host, 14, 0x66000000)
        modeRow.setPadding(host.dp(8), host.dp(4), host.dp(6), host.dp(4))
        modeRow.addView(label)
        modeRow.addView(toggle)
        view.addView(modeRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply {
            topMargin = host.dp(8)
            marginEnd = host.dp(12)
        })
        modeRow.bringToFront()
        modeRow.elevation = host.dp(8).toFloat()
        view.clipChildren = false
        view.clipToPadding = false

        setState(connected = false, talking = false)
    }

    private var sending = false

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

    init {
        view.setOnTouchListener { _, ev ->
            val press = Prefs.getString("talkMode", "hold") == "press"
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pressIn()
                    if (!press) startTalking()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    pressOut()
                    if (press) {
                        if (ParentEngine.partnerSending) ParentEngine.endPartnerTalk()
                        else startTalking()
                    } else {
                        ParentEngine.endPartnerTalk()
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    pressOut()
                    if (!press) ParentEngine.endPartnerTalk()
                }
            }
            true
        }
    }

    /** Shows that this parent is talking to the partner (green, with the talking label). */
    fun setSending(on: Boolean) {
        if (on == sending) return
        sending = on
        if (on) {
            button.text = t("Talking to partner", "تتحدث مع الشريك")
            button.background = Look.glass(host, 26, 0xFF1F5E4A.toInt())
            button.setTextColor(0xFFBFF5E0.toInt())
        } else {
            button.background = Look.glass(host, 26, Look.BUTTON)
            button.setTextColor(Look.TEXT)
            setState(connected = button.isEnabled, talking = false)
        }
    }

    /** Shows the state of the partner link; greyed out with the reason while the partner is not connected. */
    fun setState(connected: Boolean, talking: Boolean) {
        if (sending) return
        when {
            !connected -> {
                button.text = t("Talk to partner", "التحدث مع الشريك") + "\n" + t("Partner not connected", "الشريك غير متصل")
                button.alpha = 0.45f
            }
            talking -> {
                button.text = t("Partner is talking…", "الشريك يتحدث…")
                button.alpha = 1f
            }
            else -> {
                button.text = t("Talk to partner", "التحدث مع الشريك")
                button.alpha = 1f
            }
        }
        button.isEnabled = connected && !talking
    }
}
