package com.liquid.liquidbaby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

/**
 * Display-only views for the redesigned parent screen. They draw values the screen already has;
 * they never read or change the connection, recording or pairing logic.
 */

/** Circular battery ring that breathes softly and fills to the child's battery level. */
class BatteryRing(ctx: Context) : View(ctx) {
    private var target = 0f
    private var shown = 0f
    private var breath = 1f
    private val stroke = 6f * resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(track)
    private val rect = RectF()
    private var fill: ValueAnimator? = null
    private val breathing = ValueAnimator.ofFloat(0.97f, 1.03f).apply {
        duration = 2400L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { breath = it.animatedValue as Float; invalidate() }
    }

    /** pct below 0 means unknown: the ring stays empty. */
    fun setPercent(pct: Int) {
        val to = if (pct < 0) 0f else pct.coerceAtMost(100) / 100f
        if (to == target) return
        target = to
        fill?.cancel()
        fill = ValueAnimator.ofFloat(shown, to).apply {
            duration = 600L
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!breathing.isRunning) breathing.start()
    }

    override fun onDetachedFromWindow() {
        breathing.cancel()
        fill?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val accent = Themes.current().accent
        track.color = (Themes.current().sub and 0x00FFFFFF) or 0x33000000
        arc.color = accent
        val pad = stroke
        rect.set(pad, pad, width - pad, height - pad)
        c.save()
        c.scale(breath, breath, width / 2f, height / 2f)
        c.drawArc(rect, 0f, 360f, false, track)
        if (shown > 0f) c.drawArc(rect, -90f, 360f * shown, false, arc)
        c.restore()
    }
}

/** Row of bars that move with the room sound level. */
class SoundBars(ctx: Context) : View(ctx) {
    private var level = 0
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moving = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    private val rect = RectF()

    fun setLevel(v: Int) {
        level = v.coerceIn(0, 100)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!moving.isRunning) moving.start()
    }

    override fun onDetachedFromWindow() {
        moving.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        paint.color = Themes.current().accent
        val n = 14
        val barW = width / (n * 1.8f)
        val gap = barW * 0.8f
        val minH = 3f * resources.displayMetrics.density
        for (i in 0 until n) {
            val wave = ((sin((phase + i * 0.07f) * 2 * Math.PI) + 1) / 2).toFloat()
            val amp = (level / 100f) * (0.35f + 0.65f * wave)
            val h = maxOf(minH, height * amp)
            val x = i * (barW + gap)
            val top = (height - h) / 2f
            rect.set(x, top, x + barW, top + h)
            c.drawRoundRect(rect, barW / 2, barW / 2, paint)
        }
    }
}
