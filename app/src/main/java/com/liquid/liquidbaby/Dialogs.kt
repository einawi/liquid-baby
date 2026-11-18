package com.liquid.liquidbaby

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Help and choice windows in the app's own look (the current theme's glass and button colours),
 * instead of the system's default dialog. Display only.
 */
object Dialogs {

    fun show(
        activity: Activity,
        title: String,
        message: String?,
        buttons: List<Pair<String, () -> Unit>>,
        cancelable: Boolean = true
    ) {
        val d = Dialog(activity)
        d.setCancelable(cancelable)
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }

        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.background = Look.glass(activity, 22, Look.GLASS)
        root.setPadding(dpInt(22), dpInt(20), dpInt(22), dpInt(16))

        val head = TextView(activity)
        head.text = title
        head.textSize = 19f
        head.setTextColor(Look.TEXT)
        head.gravity = Gravity.START
        root.addView(head)

        if (!message.isNullOrBlank()) {
            val body = TextView(activity)
            body.text = message
            body.textSize = 14f
            body.setTextColor(Look.TEXT)
            body.alpha = 0.85f
            body.setPadding(0, dpInt(12), 0, dpInt(4))
            root.addView(body)
        }

        for ((label, action) in buttons) {
            val b = Button(activity)
            b.text = label
            b.isAllCaps = false
            b.textSize = 15f
            b.setTextColor(Look.TEXT)
            b.background = Look.glass(activity, 16, Look.BUTTON)
            b.minHeight = dpInt(52)
            b.setOnClickListener {
                d.dismiss()
                action()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dpInt(10)
            root.addView(b, lp)
        }

        val scroll = ScrollView(activity)
        scroll.addView(root)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.show()
    }
}
