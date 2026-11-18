package com.liquid.liquidbaby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Small toggle switch used as the Hold / Tap trigger in the corner of the talk button.
 * Off = Hold, on = Tap. The knob slides, and the track takes the accent colour when on.
 */
class ModeToggle(ctx: Context) : View(ctx) {

    var onToggle: ((Boolean) -> Unit)? = null

    /** true = Tap mode. Setting it slides the knob. */
    var tapMode: Boolean = false
        set(value) {
            field = value
            slide(if (value) 1f else 0f)
        }

    private var knob = 0f
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val offTrack = 0x88FFFFFF.toInt()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            tapMode = !tapMode
            onToggle?.invoke(tapMode)
        }
    }

    private fun slide(to: Float) {
        ValueAnimator.ofFloat(knob, to).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                knob = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(46), context.dp(26))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = context.dp(3).toFloat()
        val r = h / 2 - pad
        trackPaint.color = blend(offTrack, Fx.tint(Themes.current().accent, 0xCC), knob)
        canvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, trackPaint)
        val cx = pad + r + knob * (w - 2 * (pad + r))
        canvas.drawCircle(cx, h / 2, r, knobPaint)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ra = (a shr 16) and 255; val ga = (a shr 8) and 255; val ba = a and 255; val aa = (a shr 24) and 255
        val rb = (b shr 16) and 255; val gb = (b shr 8) and 255; val bb = b and 255; val ab = (b shr 24) and 255
        val r = (ra + (rb - ra) * t).toInt()
        val g = (ga + (gb - ga) * t).toInt()
        val bl = (ba + (bb - ba) * t).toInt()
        val al = (aa + (ab - aa) * t).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
