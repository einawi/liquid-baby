package com.liquid.liquidbaby

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure helpers (no Android classes) so they can be unit tested. */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) {
            sb.append(DIGITS[(x.toInt() shr 4) and 15])
            sb.append(DIGITS[x.toInt() and 15])
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        require(s.length % 2 == 0) { "Odd hex length" }
        return ByteArray(s.length / 2) { i ->
            val hi = Character.digit(s[2 * i], 16)
            val lo = Character.digit(s[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "Bad hex" }
            ((hi shl 4) or lo).toByte()
        }
    }
}

/** Length-prefixed fields inside one message. */
object Wire {
    fun pack(vararg parts: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        for (p in parts) {
            o.write(p.size shr 8)
            o.write(p.size and 255)
            o.write(p, 0, p.size)
        }
        return o.toByteArray()
    }

    fun unpack(data: ByteArray): List<ByteArray> {
        val r = ArrayList<ByteArray>()
        var i = 0
        while (i + 2 <= data.size) {
            val n = ((data[i].toInt() and 255) shl 8) or (data[i + 1].toInt() and 255)
            i += 2
            if (i + n > data.size) throw IllegalArgumentException("Bad field length")
            r.add(data.copyOfRange(i, i + n))
            i += n
        }
        return r
    }

    fun longBytes(v: Long): ByteArray = ByteBuffer.allocate(8).putLong(v).array()

    fun bytesLong(b: ByteArray): Long {
        require(b.size >= 8) { "Short value" }
        return ByteBuffer.wrap(b).long
    }
}

/** 16 kHz, 16-bit, mono WAV files. */
object Wav {
    const val RATE = 16000

    fun header(dataLen: Int): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
        b.putInt(36 + dataLen)
        b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray())
        b.putInt(16)
        b.putShort(1)
        b.putShort(1)
        b.putInt(RATE)
        b.putInt(RATE * 2)
        b.putShort(2)
        b.putShort(16)
        b.put("data".toByteArray())
        b.putInt(dataLen)
        return b.array()
    }

    fun durationMs(fileSize: Long): Long =
        if (fileSize <= 44) 0L else (fileSize - 44) * 1000L / (RATE * 2L)
}

/**
 * Decides when the child's voice starts. Feed it the loudness (RMS) of every
 * 100 ms block. It returns true after 3 loud blocks in a row.
 */
class SoundDetector {
    @Volatile
    var sensitivity = 3

    @Volatile
    var threshold = 0.0
        private set

    private var floor = 100.0
    private var above = 0

    fun feed(rms: Double): Boolean {
        val s = sensitivity.coerceIn(1, 5)
        threshold = maxOf(ABS_MIN[s], floor * MULT[s])
        if (rms > threshold) {
            above++
            if (above >= 3) {
                above = 0
                return true
            }
        } else {
            above = 0
            floor = (floor * 0.98 + rms * 0.02).coerceIn(20.0, 2000.0)
        }
        return false
    }

    fun reset() {
        above = 0
    }

    companion object {
        private val ABS_MIN = doubleArrayOf(0.0, 2500.0, 1800.0, 1200.0, 800.0, 500.0)
        private val MULT = doubleArrayOf(0.0, 8.0, 6.0, 4.0, 3.0, 2.5)
    }
}

/** IPv4 helpers for the manual address entry. */
object Ip {
    fun parse(s: String): IntArray? {
        val m = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(s.trim()) ?: return null
        val v = IntArray(4) { m.groupValues[it + 1].toInt() }
        return if (v.all { it in 0..255 }) v else null
    }

    fun isValid(s: String): Boolean = parse(s) != null

    fun sameSubnet24(a: String, b: String): Boolean {
        val x = parse(a) ?: return false
        val y = parse(b) ?: return false
        return x[0] == y[0] && x[1] == y[1] && x[2] == y[2]
    }
}
