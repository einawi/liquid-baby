package com.liquid.liquidbaby

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Records the live picture (video) and the mixed sound (room + parent's microphone) into an MP4 file.
 * Video: H.264 from the frames shown on screen. Audio: AAC, 16 kHz mono, mixed by time.
 * The MP4 is written in cache first; VideoVault then encrypts it in chunks.
 */
class VideoRecorder(private val outFile: File, private val maxMs: Long = 600_000L) {

    private val sampleRate = 16_000
    private val audioChunk = 1_600          // 100 ms
    private val mixLen = sampleRate * 4     // ring of 4 seconds of mixed sound

    private var venc: MediaCodec? = null
    private var aenc: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var vFormat: MediaFormat? = null
    private var aFormat: MediaFormat? = null
    private var vTrack = -1
    private var aTrack = -1
    private var started = false
    private var stopping = false
    private var width = 0
    private var height = 0
    private var startNs = 0L
    private var audioEos = false
    private var videoEos = false

    private var nv12 = ByteArray(0)
    private var pixels = IntArray(0)
    private val mix = IntArray(mixLen)
    private var mixBase = 0L                // sample index of mix[0]
    private var audioSentSamples = 0L

    /** Called when the recording stops by itself (time limit) or fails. */
    var onFinished: ((Boolean) -> Unit)? = null

    @Synchronized
    fun start(w: Int, h: Int, fps: Int, bitrate: Int): Boolean {
        return try {
            width = w and 1.inv()
            height = h and 1.inv()
            nv12 = ByteArray(width * height * 3 / 2)
            pixels = IntArray(width * height)

            val vf = MediaFormat.createVideoFormat("video/avc", width, height)
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            vf.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            val v = MediaCodec.createEncoderByType("video/avc")
            v.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            v.start()
            venc = v

            val af = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 1)
            af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            af.setInteger(MediaFormat.KEY_BIT_RATE, 48_000)
            af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            val a = MediaCodec.createEncoderByType("audio/mp4a-latm")
            a.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            a.start()
            aenc = a

            outFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            startNs = System.nanoTime()
            true
        } catch (e: Exception) {
            Diag.i("Recorder could not start: " + e.message)
            releaseAll()
            false
        }
    }

    /** One picture from the screen. Scaled to the recording size, converted to NV12, and queued for the encoder. */
    @Synchronized
    fun onFrame(src: Bitmap) {
        val v = venc ?: return
        if (stopping) return
        if (elapsedMs() >= maxMs) {
            finish(true)
            return
        }
        val bmp = if (src.width == width && src.height == height) src
        else Bitmap.createScaledBitmap(src, width, height, true)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        toNv12(pixels, width, height, nv12)
        val idx = v.dequeueInputBuffer(0)
        if (idx >= 0) {
            val buf = v.getInputBuffer(idx) ?: return
            buf.clear()
            buf.put(nv12)
            v.queueInputBuffer(idx, 0, nv12.size, elapsedUs(), 0)
        }
        drainVideo(false)
        drainAudio()
    }

    /** Room sound or the parent's microphone, 16 kHz mono. Both are added into the same mix by time. */
    @Synchronized
    fun onSound(samples: ShortArray, count: Int) {
        if (stopping || aenc == null) return
        val now = sampleIndexNow()
        for (i in 0 until count) {
            val at = now + i
            if (at < mixBase || at >= mixBase + mixLen) continue
            val slot = (at % mixLen).toInt()
            mix[slot] += samples[i].toInt()
        }
        drainAudio()
    }

    /** Stops and saves. The caller then encrypts the result with VideoVault. */
    @Synchronized
    fun stop() {
        if (stopping) return
        finish(false)
    }

    private fun finish(byLimit: Boolean) {
        stopping = true
        try {
            drainAudio(true)
            signalEos()
            drainVideo(true)
            drainAudioOut(true)
            if (started) {
                muxer?.stop()
            }
            muxer?.release()
            muxer = null
            releaseCodecs()
            onFinished?.invoke(outFile.exists() && outFile.length() > 0)
        } catch (e: Exception) {
            Diag.i("Recorder stop problem: " + e.message)
            releaseAll()
            onFinished?.invoke(false)
        }
    }

    // ---------- video ----------

    private fun drainVideo(untilEos: Boolean) {
        val v = venc ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = v.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEos) return
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    vFormat = v.outputFormat
                    tryStartMuxer()
                }
                idx >= 0 -> {
                    val buf = v.getOutputBuffer(idx)
                    if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && started) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer?.writeSampleData(vTrack, buf, info)
                    }
                    v.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoEos = true
                        return
                    }
                }
                else -> return
            }
        }
    }

    private fun signalEos() {
        venc?.let { v ->
            val idx = v.dequeueInputBuffer(10_000)
            if (idx >= 0) v.queueInputBuffer(idx, 0, 0, elapsedUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        aenc?.let { a ->
            val idx = a.dequeueInputBuffer(10_000)
            if (idx >= 0) a.queueInputBuffer(idx, 0, 0, audioUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    // ---------- audio ----------

    /** Sends whole 100 ms chunks that are safely behind the clock into the AAC encoder. */
    private fun drainAudio(all: Boolean = false) {
        val a = aenc ?: return
        val now = sampleIndexNow()
        while (all || now - audioSentSamples > audioChunk + sampleRate / 2) {
            val idx = a.dequeueInputBuffer(0)
            if (idx < 0) return
            val buf = a.getInputBuffer(idx) ?: return
            buf.clear()
            val bytes = ByteArray(audioChunk * 2)
            for (i in 0 until audioChunk) {
                val slot = ((audioSentSamples + i) % mixLen).toInt()
                val s = mix[slot].coerceIn(-32768, 32767)
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                mix[slot] = 0
            }
            buf.put(bytes)
            a.queueInputBuffer(idx, 0, bytes.size, audioUs(), 0)
            audioSentSamples += audioChunk
            if (all && audioSentSamples >= now) break
        }
    }

    private fun drainAudioOut(untilEos: Boolean) {
        val a = aenc ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = a.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEos) return
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    aFormat = a.outputFormat
                    tryStartMuxer()
                }
                idx >= 0 -> {
                    val buf = a.getOutputBuffer(idx)
                    if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && started) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer?.writeSampleData(aTrack, buf, info)
                    }
                    a.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        audioEos = true
                        return
                    }
                }
                else -> return
            }
        }
    }

    // ---------- helpers ----------

    private fun tryStartMuxer() {
        if (started) return
        val m = muxer ?: return
        val vf = vFormat ?: return
        val af = aFormat ?: return
        vTrack = m.addTrack(vf)
        aTrack = m.addTrack(af)
        m.start()
        started = true
    }

    private fun elapsedMs(): Long = (System.nanoTime() - startNs) / 1_000_000L
    private fun elapsedUs(): Long = (System.nanoTime() - startNs) / 1_000L
    private fun audioUs(): Long = audioSentSamples * 1_000_000L / sampleRate
    private fun sampleIndexNow(): Long = (System.nanoTime() - startNs) / (1_000_000_000L / sampleRate)

    private fun releaseCodecs() {
        try { venc?.stop() } catch (_: Exception) {}
        try { aenc?.stop() } catch (_: Exception) {}
        venc?.release(); venc = null
        aenc?.release(); aenc = null
    }

    private fun releaseAll() {
        releaseCodecs()
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
    }

    /** ARGB pixels to NV12 (Y plane, then interleaved U and V at half resolution). */
    private fun toNv12(argb: IntArray, w: Int, h: Int, out: ByteArray) {
        var yi = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = argb[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[yi++] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
            }
        }
        var ui = w * h
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                var rs = 0; var gs = 0; var bs = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val c = argb[(y + dy) * w + (x + dx)]
                    rs += (c shr 16) and 0xFF
                    gs += (c shr 8) and 0xFF
                    bs += c and 0xFF
                }
                val r = rs / 4; val g = gs / 4; val b = bs / 4
                val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                out[ui++] = u.toByte()
                out[ui++] = v.toByte()
            }
        }
    }
}
