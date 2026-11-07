package com.liquid.liquidbaby

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Colours and shapes of the glass look used on every screen. */
object Look {
    val TEXT = 0xFFFFFFFF.toInt()
    val SUB = 0xE6FFFFFF.toInt()
    val GLASS = 0x33FFFFFF
    val GLASS_EDGE = 0x66FFFFFF
    val BUTTON = 0x4DFFFFFF
    val BANNER = 0xCC1E88E5.toInt()

    fun background(alpha: Int): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xF00B1D2A.toInt(), 0xF0163B4E.toInt(), 0xF01F5A6B.toInt())
        )
        g.setAlpha(alpha)
        return g
    }

    fun glass(ctx: Context, radiusDp: Int, fill: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = ctx.dp(radiusDp).toFloat()
        g.setColor(fill)
        g.setStroke(ctx.dp(1), GLASS_EDGE)
        return g
    }
}

/** Tiny helpers to build screens in code (no extra libraries needed). */

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

@Suppress("DEPRECATION")
fun Activity.screen(build: LinearLayout.() -> Unit) {
    val blur = Build.VERSION.SDK_INT >= 31
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(18), dp(18), dp(18), dp(28))
    col.build()
    val sv = ScrollView(this)
    // Android 12+: the window blurs what is behind the app (frosted glass). Older phones get a solid gradient.
    sv.background = Look.background(if (blur) 210 else 255)
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
    // Blur only AFTER the window exists (setting it earlier caused a null-window crash on Android 12+).
    if (blur) {
        try {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setBackgroundBlurRadius(60)
        } catch (t: Throwable) {
            Diag.i("Window blur not available: " + t.message)
        }
    }
}

fun LinearLayout.addTitle(text: String): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 28f
    tv.setTypeface(tv.typeface, Typeface.BOLD)
    tv.setTextColor(Look.TEXT)
    tv.setPadding(0, 0, 0, context.dp(14))
    addView(tv)
    return tv
}

fun LinearLayout.addBody(text: String): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 16f
    tv.setTextColor(Look.SUB)
    tv.setPadding(0, context.dp(4), 0, context.dp(8))
    addView(tv)
    return tv
}

fun LinearLayout.addMono(text: String): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 11f
    tv.typeface = Typeface.MONOSPACE
    tv.setTextColor(Look.TEXT)
    tv.setTextIsSelectable(true)
    tv.background = Look.glass(context, 16, Look.GLASS)
    tv.setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
    addView(tv)
    return tv
}

fun LinearLayout.addButton(text: String, onClick: () -> Unit): Button {
    val b = Button(context)
    b.text = text
    b.isAllCaps = false
    b.setTextColor(Look.TEXT)
    b.background = Look.glass(context, 22, Look.BUTTON)
    b.minHeight = context.dp(52)
    b.setOnClickListener { onClick() }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.topMargin = context.dp(8)
    addView(b, lp)
    return b
}

fun LinearLayout.addCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox {
    val c = CheckBox(context)
    c.text = text
    c.setTextColor(Look.TEXT)
    c.isChecked = checked
    c.setOnCheckedChangeListener { _, v -> onChange(v) }
    addView(c)
    return c
}

fun LinearLayout.addBanner(text: String, onClick: () -> Unit): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = 16f
    tv.setTextColor(Look.TEXT)
    tv.background = Look.glass(context, 18, Look.BANNER)
    tv.setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
    tv.setOnClickListener { onClick() }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.bottomMargin = context.dp(14)
    addView(tv, lp)
    return tv
}

fun LinearLayout.addSlider(min: Int, max: Int, value: Int, onChange: (Int) -> Unit, onRelease: (Int) -> Unit): android.widget.SeekBar {
    val sb = android.widget.SeekBar(context)
    sb.max = max - min
    sb.progress = (value - min).coerceIn(0, max - min)
    sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress + min)
        }

        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}

        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {
            onRelease(seekBar.progress + min)
        }
    })
    addView(sb)
    return sb
}

fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

fun LinearLayout.addRow(): LinearLayout {
    val r = LinearLayout(context)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = android.view.Gravity.CENTER_VERTICAL
    r.setPadding(0, context.dp(4), 0, context.dp(4))
    addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    return r
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

fun Context.openVpnSettings() {
    try {
        val action = if (Build.VERSION.SDK_INT >= 24) android.provider.Settings.ACTION_VPN_SETTINGS
        else android.provider.Settings.ACTION_WIRELESS_SETTINGS
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast("Open the phone settings and turn the VPN off.")
    }
}
