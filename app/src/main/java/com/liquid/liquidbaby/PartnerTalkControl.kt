package com.liquid.liquidbaby

import android.app.Activity
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

    init {
        view.setOnTouchListener { _, ev ->
            val press = Prefs.getString("talkMode", "hold") == "press"
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> if (!press) ParentEngine.beginPartnerTalk()
                android.view.MotionEvent.ACTION_UP -> {
                    if (press) {
                        if (ParentEngine.partnerSending) ParentEngine.endPartnerTalk() else ParentEngine.beginPartnerTalk()
                    } else {
                        ParentEngine.endPartnerTalk()
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> if (!press) ParentEngine.endPartnerTalk()
            }
            true
        }
    }

    /** Shows the state of the partner link; greyed out with the reason while the partner is not connected. */
    fun setState(connected: Boolean, talking: Boolean) {
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
