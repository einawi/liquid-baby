package com.liquid.liquidbaby

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/** Visual effects shared by the parent and child screens. */
object Fx {
    /** Slight shrink while a control is pressed; back to normal on release. Clicks still work. */
    fun press(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            false
        }
    }

    /** Fades and slides a view in, staggered by its index so a screen appears in steps. */
    fun enter(v: View, index: Int) {
        v.alpha = 0f
        v.translationY = 28f
        v.animate().alpha(1f).translationY(0f).setStartDelay(70L * index)
            .setDuration(420).setInterpolator(DecelerateInterpolator()).start()
    }

    /** Soft endless pulse for "live" indicators. Cancel the returned animator to stop it. */
    fun breathe(v: View): ObjectAnimator {
        val a = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.35f, 1f)
        a.duration = 1800
        a.repeatCount = ObjectAnimator.INFINITE
        a.start()
        return a
    }

    /** A small filled circle, used as a status light. */
    fun dot(ctx: Context, color: Int, sizeDp: Int = 12): View {
        val v = View(ctx)
        v.background = oval(color)
        v.layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp)).apply {
            marginEnd = ctx.dp(10)
            marginStart = ctx.dp(2)
        }
        return v
    }

    fun paintDot(v: View?, color: Int) {
        v?.background = oval(color)
    }

    private val dotColour = java.util.WeakHashMap<View, Int>()

    /** Colour changes glide over 350 ms instead of jumping. */
    fun animateDot(v: View?, to: Int) {
        v ?: return
        val from = dotColour[v] ?: to
        dotColour[v] = to
        if (from == to) return
        android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).apply {
            duration = 350
            addUpdateListener { v.background = oval(it.animatedValue as Int) }
            start()
        }
    }

    /** Progress bar moves smoothly to the new value. */
    fun animateMeter(pb: android.widget.ProgressBar?, to: Int) {
        pb ?: return
        android.animation.ObjectAnimator.ofInt(pb, "progress", pb.progress, to).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private val chipColour = java.util.WeakHashMap<View, Int>()

    /** Status pill: text changes at once, its colour cross-fades over 400 ms. */
    fun animateChip(tv: android.widget.TextView?, text: String, color: Int) {
        tv ?: return
        tv.text = Tr.t(text)
        val from = chipColour[tv] ?: color
        chipColour[tv] = color
        if (from == color) {
            tv.background = Look.pill(color)
            return
        }
        android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, color).apply {
            duration = 400
            addUpdateListener { tv.background = Look.pill(it.animatedValue as Int) }
            start()
        }
    }

    /** Standard Android touch ripple drawn over the glass background. */
    fun ripple(v: View) {
        val tv = android.util.TypedValue()
        v.context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        v.foreground = v.context.getDrawable(tv.resourceId)
    }

    private fun oval(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    /** Accent colour at low opacity, for "on" states. */
    fun tint(color: Int, alpha: Int = 0x40): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}

/** A rounded glass card that stacks its children vertically. */
fun LinearLayout.card(pad: Int = 16, build: LinearLayout.() -> Unit): LinearLayout {
    val c = LinearLayout(context)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(context.dp(pad), context.dp(pad), context.dp(pad), context.dp(pad))
    c.background = Look.glass(context, 22, Look.GLASS)
    c.build()
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = context.dp(12)
    addView(c, lp)
    return c
}

/** Small upper-case label that starts a section. */
fun LinearLayout.sectionLabel(text: String): TextView {
    val tv = TextView(context)
    tv.text = text.uppercase()
    tv.textSize = 12f
    tv.letterSpacing = 0.12f
    tv.setTextColor(Look.SUB)
    tv.setPadding(context.dp(4), context.dp(24), 0, context.dp(8))
    addView(tv)
    return tv
}

/** A line of text inside a card, with no translation (the caller already picked the language). */
fun LinearLayout.line(text: String, size: Float = 15f, muted: Boolean = true): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = size
    tv.setTextColor(if (muted) Look.SUB else Look.TEXT)
    tv.setPadding(0, context.dp(3), 0, context.dp(3))
    addView(tv)
    return tv
}
