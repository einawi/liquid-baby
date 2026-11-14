package com.liquid.liquidbaby

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File

/** Plays an encrypted recording: decrypts it into the app cache, then plays it. Tap to pause or resume. */
class VideoPlayerActivity : Activity() {

    private var player: MediaPlayer? = null
    private var clear: File? = null
    private var ready = false
    private var status: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val encPath = intent.getStringExtra("path")
        if (encPath == null) {
            finish()
            return
        }
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        val surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val label = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            text = Tr.pick("Preparing...", "جارٍ التجهيز...")
            setPadding(24, 24, 24, 24)
        }
        status = label
        root.addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        setContentView(root)

        surface.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (!ready) return@setOnClickListener
            if (p.isPlaying) {
                p.pause()
                status?.text = Tr.pick("Paused (tap to resume)", "متوقف مؤقتاً (اضغط للمتابعة)")
            } else {
                p.start()
                status?.text = ""
            }
        }

        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (player != null || clear != null) return
                Thread {
                    val out = File(File(cacheDir, "video-play").apply { mkdirs() }, "view.mp4")
                    val ok = VideoVault.decryptFile(File(encPath), out)
                    runOnUiThread {
                        if (!ok) {
                            status?.text = Tr.pick("This video could not be opened.", "تعذّر فتح هذا الفيديو.")
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
                                status?.text = ""
                                it.start()
                            }
                            mp.setOnCompletionListener { status?.text = Tr.pick("Finished", "انتهى") }
                            mp.setOnErrorListener { _, what, extra ->
                                Diag.i("Video playback error: what=" + what + ", extra=" + extra)
                                status?.text = Tr.pick("Playback error.", "خطأ في التشغيل.")
                                true
                            }
                            mp.prepareAsync()
                            player = mp
                        } catch (e: Exception) {
                            Diag.i("Video playback problem: " + e.message)
                            status?.text = Tr.pick("This video could not be played.", "تعذّر تشغيل هذا الفيديو.")
                        }
                    }
                }.start()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    override fun onPause() {
        super.onPause()
        try { player?.pause() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { player?.release() } catch (_: Exception) {}
        player = null
        // the decrypted copy never stays on the phone
        clear?.delete()
        clear = null
    }
}
