package com.liquid.liquidbaby

import java.nio.ByteBuffer

/** Pure helpers for the live camera (no Android classes, so they can be unit tested). */
object Yuv {

    /**
     * Converts the three planes of a YUV_420_888 camera image to NV21
     * (all Y values, then V and U interleaved), which Android can turn into a JPEG.
     * Works for planar and semi-planar layouts and for padded rows.
     */
    fun toNv21(
        w: Int, h: Int,
        y: ByteBuffer, yRow: Int,
        u: ByteBuffer, uRow: Int, uPix: Int,
        v: ByteBuffer, vRow: Int, vPix: Int
    ): ByteArray {
        val out = ByteArray(w * h * 3 / 2)
        var pos = 0
        for (row in 0 until h) {
            y.position(row * yRow)
            y.get(out, pos, w)
            pos += w
        }
        for (row in 0 until h / 2) {
            for (col in 0 until w / 2) {
                out[pos++] = v.get(row * vRow + col * vPix)
                out[pos++] = u.get(row * uRow + col * uPix)
            }
        }
        return out
    }

    /**
     * Picks the picture size closest to 640x480 (quality 1) or 320x240 (quality 0),
     * preferring 4:3 and landscape sizes. Each size is [width, height].
     */
    fun pickSize(sizes: List<IntArray>, quality: Int): IntArray? {
        val tw = if (quality == 0) 320 else 640
        val th = if (quality == 0) 240 else 480
        val target = tw.toLong() * th
        var best: IntArray? = null
        var bestScore = Long.MAX_VALUE
        for (s in sizes) {
            if (s.size < 2 || s[0] < s[1]) continue
            val area = s[0].toLong() * s[1]
            var score = Math.abs(area - target)
            if (s[0] * 3 != s[1] * 4) score += target
            if (score < bestScore) {
                bestScore = score
                best = s
            }
        }
        return best
    }
}
