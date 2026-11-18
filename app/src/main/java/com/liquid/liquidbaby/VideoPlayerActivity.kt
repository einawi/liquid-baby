package com.liquid.liquidbaby

import android.media.MediaPlayer
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.File

/**
 * Plays an encrypted recording. The decrypted copy lives in the app cache only while this screen is open.
 * Built with Compose: the picture fills the top area and the controls sit in a fixed bar underneath.
 * The picture keeps its proportions (MediaPlayer scales it to fit the surface).
 */
class VideoPlayerActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var clear: File? = null
    private var encFile: File? = null
    private var ready = false
    private var prepareStarted = false

    private var playing by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var status by mutableStateOf("")
    private var posMs by mutableStateOf(0)
    private var durMs by mutableStateOf(0)
    private var seekFraction by mutableStateOf(0f)
    private var userSeeking = false

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun ms(v: Int): String {
        val s = v / 1000
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path")
        if (path == null) {
            finish()
            return
        }
        encFile = File(path)
        status = t("Preparing the video...", "جارٍ تجهيز الفيديو...")
        setContent { VideoScreen() }
    }

    @Composable
    private fun VideoScreen() {
        val accent = Color(Themes.current().accent)
        LaunchedEffect(Unit) {
            while (true) {
                delay(250)
                tickPlayer()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { c ->
                        SurfaceView(c).also { sv ->
                            sv.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    if (player != null || clear != null || prepareStarted) return
                                    prepareStarted = true
                                    Thread { prepare(holder) }.start()
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {}
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                if (status.isNotEmpty()) {
                    Text(
                        status,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { togglePlay() }) {
                    Icon(
                        painter = painterResource(if (playing) R.drawable.ic_btn_pause else R.drawable.ic_btn_play),
                        contentDescription = t("Play or pause", "تشغيل أو إيقاف"),
                        tint = Color.White
                    )
                }
                Slider(
                    value = seekFraction,
                    onValueChange = {
                        userSeeking = true
                        seekFraction = it
                    },
                    onValueChangeFinished = {
                        seekTo(seekFraction)
                        userSeeking = false
                    },
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ms(posMs) + " / " + ms(durMs),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { exportVideo(share = true) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = t("Share", "مشاركة"),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { exportVideo(share = false) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = t("Save to gallery", "حفظ في المعرض"),
                        tint = Color.White
                    )
                }
            }
        }
    }

    /** Runs on the UI thread every 250 ms: keeps the time, the slider and the play icon in step. */
    private fun tickPlayer() {
        val p = player ?: return
        if (!ready) return
        val d = try { p.duration } catch (e: Exception) { 0 }
        val pos = try { p.currentPosition } catch (e: Exception) { 0 }
        durMs = d
        posMs = pos
        if (!userSeeking && d > 0) seekFraction = pos.toFloat() / d
        playing = try { p.isPlaying } catch (e: Exception) { false }
    }

    private fun seekTo(fraction: Float) {
        val p = player ?: return
        val d = try { p.duration } catch (e: Exception) { 0 }
        if (ready && d > 0) p.seekTo((d * fraction).toInt().coerceIn(0, d))
    }

    private fun prepare(holder: SurfaceHolder) {
        val out = File(File(cacheDir, "video-play").apply { mkdirs() }, "view.mp4")
        val ok = VideoVault.decryptFile(encFile ?: return, out)
        runOnUiThread {
            if (!ok) {
                loading = false
                status = t("This video could not be opened.", "تعذّر فتح هذا الفيديو.")
                return@runOnUiThread
            }
            clear = out
            try {
                val mp = MediaPlayer()
                mp.setDataSource(out.absolutePath)
                mp.setDisplay(holder)
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                mp.setOnPreparedListener {
                    Diag.i("Video playing: " + it.videoWidth + "x" + it.videoHeight + ", " + it.duration + " ms")
                    ready = true
                    loading = false
                    status = ""
                    startPlayback()
                }
                mp.setOnCompletionListener {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    playing = false
                    status = t("Finished", "انتهى")
                }
                mp.setOnErrorListener { _, what, extra ->
                    Diag.i("Video playback error: what=" + what + ", extra=" + extra)
                    loading = false
                    status = t("Playback error.", "خطأ في التشغيل.")
                    true
                }
                mp.prepareAsync()
                player = mp
            } catch (e: Exception) {
                Diag.i("Video playback problem: " + e.message)
                loading = false
                status = t("This video could not be played.", "تعذّر تشغيل هذا الفيديو.")
            }
        }
    }

    private fun startPlayback() {
        val p = player ?: return
        p.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playing = true
    }

    private fun togglePlay() {
        val p = player ?: return
        if (!ready) return
        if (p.isPlaying) {
            p.pause()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playing = false
            status = t("Paused", "متوقف مؤقتاً")
        } else {
            status = ""
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
        playing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try { player?.release() } catch (_: Exception) {}
        player = null
        clear?.delete()
        clear = null
    }
}
