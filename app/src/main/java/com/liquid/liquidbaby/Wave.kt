package com.liquid.liquidbaby

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

object Wave {
    /** Loudness of [bars] slices of a WAV clip, scaled to 0..1. Read once when a clip starts playing. */
    fun envelope(file: File, bars: Int = 24): FloatArray {
        val out = FloatArray(bars)
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return out
        }
        if (bytes.size <= 44) return out
        val total = (bytes.size - 44) / 2
        val per = max(1, total / bars)
        var top = 0f
        for (b in 0 until bars) {
            var peak = 0
            val start = 44 + b * per * 2
            for (i in 0 until per) {
                val idx = start + i * 2
                if (idx + 1 >= bytes.size) break
                val s = ((bytes[idx + 1].toInt() shl 8) or (bytes[idx].toInt() and 255)).toShort().toInt()
                peak = max(peak, abs(s))
            }
            out[b] = peak.toFloat()
            top = max(top, out[b])
        }
        if (top > 0f) {
            for (b in 0 until bars) out[b] = out[b] / top
        }
        return out
    }
}

/** Animated bars: bars left of the progress point are lit, and the bars pulse while the clip plays. */
class WaveView(ctx: Context) : View(ctx) {
    private var bars = FloatArray(0)

    @Volatile
    var progress = 0f

    var playing = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBars(b: FloatArray) {
        bars = b
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val n = bars.size
        if (n == 0 || width == 0) return
        val gap = width / n.toFloat()
        val bw = gap * 0.6f
        val mid = height / 2f
        val t = System.currentTimeMillis() / 110.0
        for (i in 0 until n) {
            val lit = (i + 0.5f) / n <= progress
            val pulse = if (playing) 0.82f + 0.18f * sin(t + i * 0.7).toFloat() else 1f
            val h = max(4f, bars[i] * height * 0.95f * pulse)
            paint.color = if (lit) Themes.current().accent else 0x66FFFFFF
            val left = i * gap + (gap - bw) / 2
            c.drawRoundRect(left, mid - h / 2, left + bw, mid + h / 2, bw / 2, bw / 2, paint)
        }
        if (playing) postInvalidateOnAnimation()
    }
}
