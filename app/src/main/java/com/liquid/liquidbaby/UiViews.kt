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

/** Row of bars that follow the room sound level (no motion when the room is quiet). */
class SoundBars(ctx: Context) : View(ctx) {
    private val n = 24
    private var target = 0f
    private var shown = 0f
    private val weight = FloatArray(n) { i -> (0.45f + 0.55f * ((sin(i * 0.55) + 1) / 2)).toFloat() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var smooth: ValueAnimator? = null

    /** level 0..100 from the screen; quiet sounds are lifted so they are visible. */
    fun setLevel(v: Int) {
        val raw = v.coerceIn(0, 100) / 100f
        val to = (Math.sqrt(raw.toDouble()) * 1.5).toFloat().coerceIn(0f, 1f)
        if (to == target) return
        target = to
        smooth?.cancel()
        smooth = ValueAnimator.ofFloat(shown, to).apply {
            duration = 220L
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        paint.color = Themes.current().accent
        val barW = width / (n * 1.7f)
        val gap = barW * 0.7f
        val minH = 3f * resources.displayMetrics.density
        for (i in 0 until n) {
            val h = maxOf(minH, height * shown * weight[i])
            val x = i * (barW + gap)
            val top = (height - h) / 2f
            rect.set(x, top, x + barW, top + h)
            c.drawRoundRect(rect, barW / 2, barW / 2, paint)
        }
    }

    override fun onDetachedFromWindow() {
        smooth?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Styles a horizontal ProgressBar as a rounded level meter: a soft track with a bright accent fill.
 * Display only; the bar still shows the same numbers it always did.
 */
fun styleLevelMeter(pb: android.widget.ProgressBar?) {
    pb ?: return
    val density = pb.resources.displayMetrics.density
    val t = Themes.current()
    val radius = 8f * density
    val track = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor((t.sub and 0x00FFFFFF) or 0x33000000)
    }
    val fill = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(t.accent, (t.text and 0x00FFFFFF) or 0xCC000000.toInt())
    ).apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = radius
    }
    val clip = android.graphics.drawable.ClipDrawable(fill, android.view.Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
    val layers = android.graphics.drawable.LayerDrawable(arrayOf(track, clip))
    layers.setId(0, android.R.id.background)
    layers.setId(1, android.R.id.progress)
    pb.progressDrawable = layers
    pb.minimumHeight = (12f * density).toInt()
}

/**
 * Room-sound visual with three looks, chosen in Settings: "wave" (calm glowing wave), "rings" (ripples
 * spreading from the centre) and "orb" (a glowing orb). Flat and still when the room is silent.
 * Display only: it draws the level it is given.
 */
class SoundVisual(ctx: Context) : View(ctx) {
    private var style = "wave"
    private var target = 0f
    private var level = 0f
    private var phase = 0f
    private val cool = intArrayOf(94, 234, 212)
    private val warm = intArrayOf(255, 176, 32)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase += 0.04f
            invalidate()
        }
    }

    fun setStyle(s: String) {
        if (s != style) {
            style = s
            invalidate()
        }
    }

    /** level 0..100 from the screen; quiet sounds are lifted so they are visible. */
    fun setLevel(v: Int) {
        val raw = v.coerceIn(0, 100) / 100f
        target = (Math.sqrt(raw.toDouble()) * 1.4).toFloat().coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ticker.isRunning) ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    private fun mix(k: Float): Int {
        val r = (cool[0] + (warm[0] - cool[0]) * k).toInt()
        val g = (cool[1] + (warm[1] - cool[1]) * k).toInt()
        val b = (cool[2] + (warm[2] - cool[2]) * k).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    override fun onDraw(c: Canvas) {
        level += (target - level) * 0.12f
        if (kotlin.math.abs(target - level) > 0.002f) postInvalidateOnAnimation()
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f
        val dpi = resources.displayMetrics.density
        when (style) {
            "rings" -> {
                val cx = w / 2f
                for (r in 0 until 4) {
                    val k = ((phase * 0.15f + r * 0.25f) % 1f)
                    val rad = 6f * dpi + k * (h * 0.45f) * (0.4f + level)
                    p.strokeWidth = 2f * dpi
                    p.color = mix(k)
                    p.alpha = ((1f - k) * (0.25f + 0.75f * level) * 255).toInt()
                    c.drawCircle(cx, mid, rad, p)
                }
                p.alpha = 255
                fill.color = mix(level)
                fill.setShadowLayer(12f * dpi, 0f, 0f, fill.color)
                c.drawCircle(cx, mid, 4f * dpi, fill)
                fill.clearShadowLayer()
            }
            "orb" -> {
                val r = (16f + level * 22f + kotlin.math.sin(phase * 2f) * 1.5f * (0.2f + level)) * dpi
                val col = mix(level)
                fill.color = col
                fill.alpha = 217
                fill.setShadowLayer((14f + 20f * level) * dpi, 0f, 0f, col)
                c.drawCircle(w / 2f, mid, r, fill)
                fill.alpha = 255
                fill.clearShadowLayer()
            }
            else -> {
                p.strokeWidth = 2.4f * dpi
                val amp = level * h * 0.4f
                var px = 0f
                var py = mid
                var x = 4f
                while (x <= w) {
                    val ph = x / w * Math.PI.toFloat() * 3f + phase * 1.4f
                    val wob = kotlin.math.sin(ph) * 0.75f + kotlin.math.sin(ph * 0.5f + phase) * 0.25f
                    val y = mid - wob * amp
                    val k = minOf(1f, kotlin.math.abs(y - mid) / (h * 0.4f + 0.001f))
                    p.color = mix(k)
                    p.setShadowLayer((6f + 8f * k) * dpi, 0f, 0f, p.color)
                    c.drawLine(px, py, x, y, p)
                    px = x
                    py = y
                    x += 4f
                }
                p.clearShadowLayer()
            }
        }
    }
}

/**
 * The app background: the theme's three colours, drifting very slowly so the screen feels alive.
 * It only moves a little (about 12% of the height over 14 seconds) and costs almost nothing to draw.
 */
class ShiftingBackground(private val t: Style, alpha: Int) : android.graphics.drawable.Drawable() {
    private var phase = 0f
    private var a = alpha
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drift = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 14000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidateSelf()
        }
    }

    override fun draw(c: Canvas) {
        if (!drift.isRunning) drift.start()
        val b = bounds
        val h = b.height().toFloat()
        val shift = (phase - 0.5f) * h * 0.12f
        paint.shader = android.graphics.LinearGradient(
            0f, shift, 0f, h + shift,
            intArrayOf(t.top, t.mid, t.bottom), null,
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.alpha = a
        c.drawRect(b, paint)
    }

    override fun setAlpha(alpha: Int) {
        a = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
