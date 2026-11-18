package com.liquid.liquidbaby

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Plain screens that do not use the glass styling, so they still open when a normal screen fails. */
object Safe {
    fun showText(a: Activity, title: String, body: String, actions: List<Pair<String, () -> Unit>> = emptyList()) {
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(0xFF0B1D2A.toInt())
        col.setPadding(40, 60, 40, 60)

        val head = TextView(a)
        head.text = title
        head.textSize = 22f
        head.setTextColor(0xFFFFFFFF.toInt())
        col.addView(head)

        val copy = Button(a)
        copy.text = "Copy text"
        copy.setOnClickListener {
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Liquid Baby", body))
            Toast.makeText(a, "Copied", Toast.LENGTH_SHORT).show()
        }
        col.addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        for ((label, action) in actions) {
            val b = Button(a)
            b.text = label
            b.setOnClickListener { action() }
            col.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val tv = TextView(a)
        tv.text = body
        tv.textSize = 12f
        tv.setTextColor(0xFFFFFFFF.toInt())
        tv.setTextIsSelectable(true)
        tv.typeface = android.graphics.Typeface.MONOSPACE
        col.addView(tv)

        val sv = ScrollView(a)
        sv.addView(col)
        a.setContentView(sv)
    }

    fun showCrash(a: Activity, t: Throwable, where: String) {
        try {
            Diag.e("Screen failed: $where", t)
        } catch (_: Throwable) {
        }
        val body = "Liquid Baby could not open this screen ($where).\n" +
            "Send this text to Claude.\n\n" +
            Diag.deviceInfo(a) + "\n" + Diag.stackTrace(t)
        showText(a, "Liquid Baby error", body)
    }
}
