package com.liquid.liquidbaby

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Tiny helpers to build screens in code (no extra libraries needed). */

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

@Suppress("DEPRECATION")
fun Activity.screen(build: LinearLayout.() -> Unit) {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(16), dp(16), dp(16), dp(24))
    col.build()
    val sv = ScrollView(this)
    sv.setBackgroundColor(Color.WHITE)
    sv.addView(col)
    sv.setOnApplyWindowInsetsListener { v, insets ->
        var l = 0
        var t = 0
        var r = 0
        var b = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            l = bars.left
            t = bars.top
            r = bars.right
            b = bars.bottom
        } else {
            l = insets.systemWindowInsetLeft
            t = insets.systemWindowInsetTop
            r = insets.systemWindowInsetRight
            b = insets.systemWindowInsetBottom
        }
        v.setPadding(l, t, r, b)
        insets
    }
    setContentView(sv)
}

fun LinearLayout.addTitle(text: String): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 24f
    tv.setTypeface(tv.typeface, Typeface.BOLD)
    tv.setTextColor(Color.BLACK)
    tv.setPadding(0, 0, 0, context.dp(12))
    addView(tv)
    return tv
}

fun LinearLayout.addBody(text: String): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 16f
    tv.setTextColor(Color.DKGRAY)
    tv.setPadding(0, context.dp(4), 0, context.dp(8))
    addView(tv)
    return tv
}

fun LinearLayout.addMono(text: String): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 11f
    tv.typeface = Typeface.MONOSPACE
    tv.setTextColor(Color.BLACK)
    tv.setTextIsSelectable(true)
    addView(tv)
    return tv
}

fun LinearLayout.addButton(text: String, onClick: () -> Unit): Button {
    val b = Button(context)
    b.text = text
    b.isAllCaps = false
    b.setOnClickListener { onClick() }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.topMargin = context.dp(6)
    addView(b, lp)
    return b
}

fun LinearLayout.addCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox {
    val c = CheckBox(context)
    c.text = text
    c.isChecked = checked
    c.setOnCheckedChangeListener { _, v -> onChange(v) }
    addView(c)
    return c
}

fun LinearLayout.addBanner(text: String, onClick: () -> Unit): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 16f
    tv.setTextColor(Color.WHITE)
    tv.setBackgroundColor(Color.parseColor("#1E88E5"))
    tv.setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
    tv.setOnClickListener { onClick() }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.bottomMargin = context.dp(12)
    addView(tv, lp)
    return tv
}

fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

fun shareText(ctx: Context, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/plain"
    i.putExtra(Intent.EXTRA_SUBJECT, subject)
    i.putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, subject))
}
