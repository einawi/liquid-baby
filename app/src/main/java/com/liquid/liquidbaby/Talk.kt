package com.liquid.liquidbaby

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Walkie-talkie, parent side: records the microphone and sends it to the child phone. */
class TalkSender(private val conn: Conn, private val onFail: (String) -> Unit) : Thread("LiquidBaby-talk") {

    @Volatile
    var stopFlag = false

    private fun create(source: Int, minBuf: Int): AudioRecord? {
        return try {
            val r = AudioRecord(
                source, Wav.RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, Wav.RATE)
            )
            if (r.state == AudioRecord.STATE_INITIALIZED) r else {
                r.release()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun run() {
        var rec: AudioRecord? = null
        var started = false
        try {
            val min = AudioRecord.getMinBufferSize(Wav.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            rec = create(MediaRecorder.AudioSource.VOICE_COMMUNICATION, min) ?: create(MediaRecorder.AudioSource.MIC, min)
            if (rec == null) {
                onFail("The microphone could not start")
                return
            }
            conn.send(T.TALK_START)
            started = true
            rec.startRecording()
            val frame = 640
            val buf = ShortArray(frame)
            while (!stopFlag) {
                var got = 0
                while (got < frame && !stopFlag) {
                    val n = rec.read(buf, got, frame - got)
                    if (n < 0) throw IOException("Microphone error $n")
                    got += n
                }
                if (got < frame) break
                val out = ByteArray(frame * 2)
                ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buf)
                conn.send(T.TALK_DATA, out)
            }
        } catch (e: Exception) {
            Diag.e("Talking failed", e)
            onFail("Talking stopped: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            if (started) {
                try {
                    conn.send(T.TALK_STOP)
                } catch (_: Exception) {
                }
            }
            try {
                rec?.stop()
            } catch (_: Exception) {
            }
            rec?.release()
        }
    }
}

/** Plays 16 kHz PCM sound through the speaker (parent's voice on the child phone, live sound on the parent phone). */
class TalkPlayer {
    private var track: AudioTrack? = null

    fun start(ctx: Context, volumeOutOfTen: Int, changeVolume: Boolean) {
        if (changeVolume) {
            try {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val v = Math.round(max * volumeOutOfTen.coerceIn(1, 10) / 10.0).toInt().coerceIn(1, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            } catch (e: Exception) {
                Diag.i("Could not set the speaker volume: " + e.message)
            }
        }
        val min = AudioTrack.getMinBufferSize(Wav.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Wav.RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, Wav.RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        track = t
    }

    fun write(data: ByteArray) {
        try {
            track?.write(data, 0, data.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (e: Exception) {
            Diag.i("Playback problem: " + e.message)
        }
    }

    fun stop() {
        val t = track ?: return
        track = null
        Thread {
            try {
                t.stop()
                Thread.sleep(700)
            } catch (_: Exception) {
            }
            try {
                t.release()
            } catch (_: Exception) {
            }
        }.start()
    }
}
