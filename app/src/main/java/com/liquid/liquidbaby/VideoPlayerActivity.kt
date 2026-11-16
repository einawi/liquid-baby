package com.liquid.liquidbaby

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Plays an encrypted recording. The decrypted copy lives in the app cache only while this screen is open.
 * Controls: play/pause, a seek bar with time, share and save. The picture keeps its proportions.
 */
class VideoPlayerActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var clear: File? = null
    private var encFile: File? = null
    private var ready = false
    private var surface: SurfaceView? = null
    private var root: FrameLayout? = null
    private var loading: ProgressBar? = null
    private var status: TextView? = null
    private var playBtn: ImageButton? = null
    private var seek: SeekBar? = null
    private var timeTv: TextView? = null
    private var userSeeking = false

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun ms(v: Int): String {
        val s = v / 1000
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && ready) {
                val d = try { p.duration } catch (e: Exception) { 0 }
                val pos = try { p.currentPosition } catch (e: Exception) { 0 }
                if (!userSeeking && d > 0) seek?.progress = (pos.toLong() * 1000 / d).toInt()
                timeTv?.text = ms(pos) + " / " + ms(d)
                playBtn?.setImageResource(if (p.isPlaying) R.drawable.ic_btn_pause else R.drawable.ic_btn_play)
            }
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path")
        if (path == null) {
            finish()
            return
        }
        encFile = File(path)
        val r = FrameLayout(this)
        r.setBackgroundColor(0xFF000000.toInt())
        root = r

        val sv = SurfaceView(this)
        surface = sv
        r.addView(sv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        val pb = ProgressBar(this)
        loading = pb
        r.addView(pb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val st = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            text = t("Preparing the video...", "جارٍ تجهيز الفيديو...")
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        status = st
        r.addView(st, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))

        // bottom bar: play/pause, seek bar with time, share, save
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = Look.glass(this, 22, 0xB3000000.toInt())
        bar.setPadding(dp(10), dp(8), dp(10), dp(8))

        val play = ImageButton(this).apply {
            setImageResource(R.drawable.ic_btn_play)
            setColorFilter(0xFFFFFFFF.toInt())
            background = null
            contentDescription = t("Play or pause", "تشغيل أو إيقاف")
            setOnClickListener { togglePlay() }
        }
        playBtn = play
        bar.addView(play, LinearLayout.LayoutParams(dp(52), dp(52)))

        val sb = SeekBar(this).apply {
            max = 1000
            val accent = android.content.res.ColorStateList.valueOf(Themes.current().accent)
            progressTintList = accent
            thumbTintList = accent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(s: SeekBar?) {
                    userSeeking = false
                    val p = player ?: return
                    val d = try { p.duration } catch (e: Exception) { 0 }
                    if (ready && d > 0) p.seekTo((d.toLong() * (s?.progress ?: 0) / 1000).toInt())
                }
            })
        }
        seek = sb
        bar.addView(sb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val time = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            text = "0:00 / 0:00"
            setPadding(dp(8), 0, dp(8), 0)
        }
        timeTv = time
        bar.addView(time)

        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            setColorFilter(0xFFFFFFFF.toInt())
            background = null
            contentDescription = t("Share", "مشاركة")
            setOnClickListener { exportVideo(share = true) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) })

        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_save)
            setColorFilter(0xFFFFFFFF.toInt())
            background = null
            contentDescription = t("Save to gallery", "حفظ في المعرض")
            setOnClickListener { exportVideo(share = false) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) })

        r.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(r)
        Fx.feedbackAll(r)

        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (player != null || clear != null) return
                Thread { prepare(holder) }.start()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
        ui.post(tick)
    }

    private fun prepare(holder: SurfaceHolder) {
        val out = File(File(cacheDir, "video-play").apply { mkdirs() }, "view.mp4")
        val ok = VideoVault.decryptFile(encFile ?: return, out)
        runOnUiThread {
            if (!ok) {
                loading?.visibility = View.GONE
                status?.text = t("This video could not be opened.", "تعذّر فتح هذا الفيديو.")
                return@runOnUiThread
            }
            clear = out
            try {
                val mp = MediaPlayer()
                mp.setDataSource(out.absolutePath)
                mp.setDisplay(holder)
                mp.setOnPreparedListener {
                    Diag.i("Video playing: " + it.videoWidth + "x" + it.videoHeight + ", " + it.duration + " ms")
                    ready = true
                    loading?.visibility = View.GONE
                    status?.text = ""
                    fitPicture(it.videoWidth, it.videoHeight)
                    startPlayback()
                }
                mp.setOnCompletionListener {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    playBtn?.setImageResource(R.drawable.ic_btn_play)
                    status?.text = t("Finished", "انتهى")
                }
                mp.setOnErrorListener { _, what, extra ->
                    Diag.i("Video playback error: what=" + what + ", extra=" + extra)
                    loading?.visibility = View.GONE
                    status?.text = t("Playback error.", "خطأ في التشغيل.")
                    true
                }
                mp.prepareAsync()
                player = mp
            } catch (e: Exception) {
                Diag.i("Video playback problem: " + e.message)
                loading?.visibility = View.GONE
                status?.text = t("This video could not be played.", "تعذّر تشغيل هذا الفيديو.")
            }
        }
    }

    /** Keeps the whole picture visible with its own proportions (black edges where needed). */
    private fun fitPicture(vw: Int, vh: Int) {
        val r = root ?: return
        val sv = surface ?: return
        if (vw <= 0 || vh <= 0) return
        r.post {
            val aw = r.width.toFloat()
            val ah = (r.height - dp(72)).toFloat().coerceAtLeast(1f)
            val scale = minOf(aw / vw, ah / vh)
            val w = (vw * scale).toInt()
            val h = (vh * scale).toInt()
            sv.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER).apply { bottomMargin = dp(72) }
        }
    }

    private fun startPlayback() {
        val p = player ?: return
        p.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playBtn?.setImageResource(R.drawable.ic_btn_pause)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (!ready) return
        if (p.isPlaying) {
            p.pause()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            status?.text = t("Paused", "متوقف مؤقتاً")
        } else {
            status?.text = ""
            startPlayback()
        }
    }

    /** Share or save an unencrypted copy (Movies/LiquidBaby). This copy is made only when you ask for it. */
    private fun exportVideo(share: Boolean) {
        VideoExport.run(this, encFile ?: return, share)
    }

    override fun onPause() {
        super.onPause()
        try { player?.pause() } catch (_: Exception) {}
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(tick)
        try { player?.release() } catch (_: Exception) {}
        player = null
        clear?.delete()
        clear = null
    }
}
