package com.liquid.liquidbaby

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Listens to the microphone and records a clip when the child's voice is heard. */
class AudioMonitor(
    private val dir: File,
    private val clipSeconds: () -> Int,
    private val onClip: (File, Long) -> Unit,
    private val onStatus: (String) -> Unit
) : Thread("LiquidBaby-monitor") {
    companion object {
        const val LISTEN_WINDOW_MS = 5000L
    }

    /** 0 = listen all the time. 1..15 = wake up every N seconds and listen for 5 seconds. */
    @Volatile
    var intervalSec = 0

    private var lpState = 0.0

    @Volatile
    var stopFlag = false

    @Volatile
    var lastRms = 0.0

    /** No new clips are started before this time (used while the parent is talking). */
    @Volatile
    var suppressUntil = 0L

    /** While someone watches live, every 100 ms block of sound (16-bit PCM) is handed to this function. */
    @Volatile
    var liveSink: ((ByteArray) -> Unit)? = null

    val detector = SoundDetector()

    private val rate = Wav.RATE
    private val block = 1600

    private fun readBlock(rec: AudioRecord, buf: ShortArray): Int {
        var got = 0
        while (got < buf.size && !stopFlag) {
            val n = rec.read(buf, got, buf.size - got)
            if (n < 0) return n
            got += n
        }
        return got
    }

    private fun rms(buf: ShortArray, n: Int): Double {
        if (n <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until n) {
            val v = buf[i].toDouble()
            // high-pass: removes low rumble (fans, motors, hum) before measuring
            lpState += 0.06 * (v - lpState)
            val hp = v - lpState
            sum += hp * hp
        }
        return Math.sqrt(sum / n)
    }

    private fun toBytes(s: ShortArray, n: Int, out: ByteArrayOutputStream) {
        val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) bb.putShort(s[i])
        out.write(bb.array(), 0, n * 2)
    }

    override fun run() {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var rec: AudioRecord? = null
        try {
            rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate * 2)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                onStatus("Microphone could not start")
                Diag.e("AudioRecord not initialised")
                return
            }
            rec.startRecording()
            var recording = true
            onStatus("Listening")
            val ring = ArrayDeque<ShortArray>()
            val buf = ShortArray(block)
            var errors = 0
            while (!stopFlag) {
                val iv = intervalSec
                val active = iv == 0 || (System.currentTimeMillis() % (iv * 1000L)) < LISTEN_WINDOW_MS
                if (!active) {
                    if (recording) {
                        rec.stop()
                        recording = false
                    }
                    sleep(100)
                    continue
                }
                if (!recording) {
                    rec.startRecording()
                    recording = true
                }
                val n = readBlock(rec, buf)
                if (n < 0) {
                    errors++
                    if (errors > 20) {
                        onStatus("Microphone stopped working")
                        Diag.e("AudioRecord read error $n")
                        return
                    }
                    sleep(50)
                    continue
                }
                errors = 0
                if (n < buf.size) continue
                val level = rms(buf, n)
                lastRms = level
                val sink = liveSink
                if (sink != null) {
                    val pcm = ByteArray(n * 2)
                    ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buf, 0, n)
                    sink(pcm)
                }
                if (System.currentTimeMillis() < suppressUntil) {
                    detector.reset()
                    ring.clear()
                    continue
                }
                ring.addLast(buf.copyOf(n))
                if (ring.size > 20) ring.removeFirst()
                if (detector.feed(level)) {
                    record(rec, ring)
                    detector.reset()
                    ring.clear()
                }
            }
        } catch (e: Exception) {
            Diag.e("Audio monitor failed", e)
            onStatus("Microphone error: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            try {
                rec?.stop()
            } catch (_: Exception) {
            }
            rec?.release()
        }
    }

    private fun record(rec: AudioRecord, ring: ArrayDeque<ShortArray>) {
        val total = clipSeconds().coerceIn(5, 60) * rate
        val pcm = ByteArrayOutputStream(total * 2 + 64)
        var samples = 0
        val startMs = System.currentTimeMillis() - ring.size * 100L
        for (b in ring) {
            toBytes(b, b.size, pcm)
            samples += b.size
        }
        onStatus("Recording a clip")
        val buf = ShortArray(block)
        while (samples < total && !stopFlag) {
            val n = readBlock(rec, buf)
            if (n <= 0) break
            val use = minOf(n, total - samples)
            toBytes(buf, use, pcm)
            samples += use
            lastRms = rms(buf, n)
        }
        val data = pcm.toByteArray()
        if (data.size < Wav.RATE) {
            onStatus("Listening")
            return
        }
        try {
            if (!dir.exists()) dir.mkdirs()
            val wav = Wav.header(data.size) + data
            val stored = ClipCrypt.seal(wav) ?: wav
            val tmp = File(dir, "$startMs.tmp")
            FileOutputStream(tmp).use { it.write(stored) }
            val done = File(dir, "$startMs.wav")
            if (tmp.renameTo(done)) onClip(done, startMs)
        } catch (e: Exception) {
            Diag.e("Could not save clip", e)
        }
        onStatus("Listening")
    }
}
