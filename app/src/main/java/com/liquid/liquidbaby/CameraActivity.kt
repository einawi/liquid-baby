package com.liquid.liquidbaby

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Parent unit: live camera. Normal view, and a separate full-screen layer with pinch zoom and icon controls. */
class CameraActivity : androidx.activity.ComponentActivity(), MicHost {

    private val mic = MicAsk(this)
    override fun askMic(onGranted: () -> Unit) = mic.askMic(onGranted)

    private var hostRoot: LinearLayout? = null

    private val ui = Handler(Looper.getMainLooper())
    private var picture: ImageView? = null
    private var fullPicture: ImageView? = null
    private var statusTv: TextView? = null
    private var soundBtn: Button? = null
    private var flashBtn: Button? = null
    private var switchBtn: Button? = null
    private var nightBtn: Button? = null
    private var nightIcon: ImageButton? = null
    private var fpsSpinner: Spinner? = null
    private var recorder: VideoRecorder? = null
    @Volatile
    private var picturesTotal = 0
    private var picturesAtRecordStart = 0
    private var recordIcon: ImageButton? = null
    private var fullRecord: ImageButton? = null
    private var frames = 0
    private var fps = 0
    private val qualityBtns = arrayOfNulls<Button>(4)
    private var overlay: FrameLayout? = null
    private var fullFlash: ImageButton? = null
    private var fullSound: ImageButton? = null
    private var fullTalk: ImageButton? = null
    private var fullNight: ImageButton? = null
    private var zoom = 1f
    private var fullTurn = 0
    private val pending = AtomicBoolean(false)
    private var lastNight = false
    private var ending = false

    @Volatile
    private var latest: Bitmap? = null

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)
    private val qualityLabels get() = arrayOf(t("Low", "منخفضة"), t("Normal", "عادية"), "960×540", "720p")

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyLookTo()
        camState.pressMode = Prefs.getString("talkMode", "hold") == "press"
        setContent { CameraPage() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        ParentEngine.frameListener = { bmp ->
            latest = bmp
            frames++
            picturesTotal++
            try { recorder?.onFrame(bmp) } catch (e: Throwable) { Diag.i("Recorder frame problem: " + e.message) }
            if (pending.compareAndSet(false, true)) {
                runOnUiThread {
                    pending.set(false)
                    val b = latest
                    picture?.setImageBitmap(b)
                    fullPicture?.setImageBitmap(b)
                }
            }
        }
        ParentEngine.onChange = { runOnUiThread { refresh() } }
        if (!ParentEngine.beginView(this)) {
            toast(ParentEngine.viewNote)
            ending = true
            finish()
            return
        }
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        stopRecording()
        exitFull()
        talkUp()
        ui.removeCallbacks(tick)
        ParentEngine.frameListener = null
        ParentEngine.onChange = null
        ParentEngine.endView()
    }

    override fun onBackPressed() {
        when {
            camState.fullOpen -> exitFull()
            recorder != null -> Dialogs.show(
                this,
                t("Stop the recording and leave?", "إيقاف التسجيل والخروج؟"),
                null,
                listOf(
                    t("Stop and leave", "إيقاف وخروج") to { stopRecording(); super.onBackPressed() },
                    t("Keep recording", "متابعة التسجيل") to {}
                )
            )
            else -> super.onBackPressed()
        }
    }

    // ---------- recording ----------

    private fun toggleRecording() {
        if (recorder != null) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (ParentEngine.viewQuality < 0) return
        // same size and shape as the live picture on screen (no stretching)
        val live = latest
        val (w, h) = if (live != null) Pair(live.width, live.height) else when (ParentEngine.viewQuality) {
            0 -> Pair(320, 240)
            1 -> Pair(640, 480)
            2 -> Pair(960, 540)
            else -> Pair(1280, 720)
        }
        val fps = ParentEngine.viewFps
        val bitrate = (w * h * fps / 10).coerceIn(300_000, 4_000_000)
        val tmp = File(File(cacheDir, "rec").apply { mkdirs() }, "rec-${System.currentTimeMillis()}.mp4")
        val rec = VideoRecorder(tmp)
        rec.onFinished = { ok -> runOnUiThread { finishRecording(rec, tmp, ok) } }
        if (!rec.start(w, h, fps, bitrate)) {
            toast(t("Recording could not start on this phone.", "تعذّر بدء التسجيل على هذا الجوال."))
            return
        }
        recorder = rec
        picturesAtRecordStart = picturesTotal
        Diag.i("Recorder started: " + w + "x" + h + " at " + fps + " fps, bitrate " + bitrate)
        ParentEngine.roomSink = { s, n -> try { rec.onSound(s, n) } catch (e: Throwable) { Diag.i("Recorder sound problem: " + e.message) } }
        ParentEngine.micSink = { s, n -> try { rec.onSound(s, n) } catch (e: Throwable) { Diag.i("Recorder mic problem: " + e.message) } }
        updateRecordIcons()
        toast(t("Recording started.", "بدأ التسجيل."))
    }

    private fun stopRecording() {
        if (recorder == null) return
        toast(t("Stopping. Saving in the background...", "جارٍ الإيقاف والحفظ في الخلفية..."))
        recorder?.stop()
    }

    /** Runs when the recording stops, by the button, by leaving, or by the 10-minute limit. */
    private fun finishRecording(rec: VideoRecorder, tmp: File, ok: Boolean) {
        if (recorder !== rec) return
        Diag.i("Pictures received during recording: " + (picturesTotal - picturesAtRecordStart))
        recorder = null
        ParentEngine.roomSink = null
        ParentEngine.micSink = null
        updateRecordIcons()
        if (!ok) {
            toast(t("The recording could not be saved.", "تعذّر حفظ التسجيل."))
            return
        }
        val out = File(File(filesDir, "videos").apply { mkdirs() }, "video-${System.currentTimeMillis()}.lbv")
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val t0 = System.nanoTime()
            val saved = VideoVault.encryptFile(tmp, out)
            if (saved) ParentEngine.clipsVersion++
            Diag.i("Video encrypted in " + (System.nanoTime() - t0) / 1_000_000 + " ms, saved=" + saved)
            runOnUiThread {
                toast(if (saved) t("Video saved (encrypted).", "تم حفظ الفيديو (مشفّر).")
                else t("The video could not be saved.", "تعذّر حفظ الفيديو."))
            }
        }.start()
    }

    private fun updateRecordIcons() {
        val recording = recorder != null
        camState.recording = recording
        val red = (0xAA shl 24) or 0xE53935
        val icon = if (recording) R.drawable.ic_stop_record else R.drawable.ic_record
        recordIcon?.setImageResource(icon)
        recordIcon?.background = Look.glass(this, 18, if (recording) red else Look.BUTTON)
    }

    // ---------- full-screen layer ----------

    private fun iconButton(icon: Int, desc: String, onTap: () -> Unit): ImageButton {
        val b = ImageButton(this)
        b.setImageResource(icon)
        b.setColorFilter(Look.TEXT)
        b.background = Look.glass(this, 25, Look.BUTTON)
        b.scaleType = ImageView.ScaleType.CENTER
        b.setPadding(dpInt(12), dpInt(12), dpInt(12), dpInt(12))
        b.contentDescription = desc
        b.setOnClickListener { onTap() }
        b.setOnLongClickListener {
            Toast.makeText(this, desc, Toast.LENGTH_SHORT).show()
            true
        }
        return b
    }

    private fun enterFull() {
        if (camState.fullOpen) return
        camState.fullOpen = true
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        refreshFull()
    }

    /** Two fingers zoom the picture, between 1x and 4x. */
    private val fullDetector: ScaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val p = fullPicture ?: return true
                zoom = (zoom * d.scaleFactor).coerceIn(1f, 4f)
                p.pivotX = d.focusX
                p.pivotY = d.focusY
                p.scaleX = zoom
                p.scaleY = zoom
                return true
            }
        })
    }

    private fun chooseFps() {
        Dialogs.show(
            this,
            t("Frame rate", "معدل الفريمات"),
            null,
            (10..30).map { n ->
                "$n fps" to {
                    ParentEngine.setFps(n)
                    checkHeavy(ParentEngine.viewQuality, n)
                    fpsSpinner?.setSelection(n - 10, false)
                    camState.fps = n
                    Unit
                }
            } + listOf(t("Cancel", "تراجع") to {})
        )
    }

    /** Off, then low light, then pitch dark, then off again. */
    private fun cycleNight() {
        val next = if (ParentEngine.viewNightLevel == 0) 1 else 0
        ParentEngine.setNightLevel(next)
        val msg = if (next == 1) t("Night mode: on", "الوضع الليلي: مشغّل") else t("Night mode: off", "الوضع الليلي: إيقاف")
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /** One tap: on at the last chosen strength, or off. */
    private fun tapFlash() {
        if (ParentEngine.viewFlashLevel != 0) {
            ParentEngine.setFlashLevel(0)
            Toast.makeText(this, t("Flash: off", "الفلاش: إيقاف"), Toast.LENGTH_SHORT).show()
        } else {
            val level = Prefs.getString("flashPref", "2").toIntOrNull() ?: 2
            ParentEngine.setFlashLevel(level)
            Toast.makeText(this, flashName(level), Toast.LENGTH_SHORT).show()
        }
    }

    /** Long press: choose the strength, light, medium or high. */
    private fun showFlashChoice() {
        Dialogs.show(
            this,
            t("Flash strength", "شدة الفلاش"),
            null,
            listOf(
                t("Light", "خفيف") to { chooseFlash(1) },
                t("Medium", "متوسط") to { chooseFlash(2) },
                t("High", "عالي") to { chooseFlash(3) },
                t("Off", "إيقاف") to { chooseFlash(0) }
            )
        )
    }

    private fun chooseFlash(level: Int) {
        if (level != 0) Prefs.putString("flashPref", level.toString())
        ParentEngine.setFlashLevel(level)
        Toast.makeText(this, if (level == 0) t("Flash: off", "الفلاش: إيقاف") else flashName(level), Toast.LENGTH_SHORT).show()
    }

    private fun flashName(level: Int): String = when (level) {
        1 -> t("Flash: light", "الفلاش: خفيف")
        2 -> t("Flash: medium", "الفلاش: متوسط")
        else -> t("Flash: high", "الفلاش: عالي")
    }

    /** Warns when 720p is combined with a high frame rate, which stutters on this phone. */
    private fun checkHeavy(quality: Int, fps: Int) {
        if (quality == 3 && fps >= 20) {
            Toast.makeText(
                this,
                t("720p with $fps fps may stutter on this phone. For a smooth picture, use 15 fps.",
                  "720p مع $fps فريم قد يتقطّع على هذا الجوال. للصورة السلسة استخدم 15 فريم."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun chooseQuality() {
        val labels = qualityLabels
        Dialogs.show(
            this,
            t("Quality", "الجودة"),
            null,
            labels.mapIndexed { i, label ->
                label to {
                    ParentEngine.setView(i, ParentEngine.viewFront, ParentEngine.viewFlash)
                    checkHeavy(i, ParentEngine.viewFps)
                    refresh()
                }
            } + listOf(t("Cancel", "تراجع") to {})
        )
    }

    /** Leaves full screen: stops any talk that is running and restores the normal screen. */
    private fun exitFull() {
        if (!camState.fullOpen) return
        talkUp()
        camState.fullOpen = false
        fullPicture = null
        zoom = 1f
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun refreshFull() {
        camState.fullFlash = ParentEngine.viewFlash
        camState.fullSound = ParentEngine.liveSound
        camState.fullTalk = ParentEngine.talking
        camState.talking = ParentEngine.talking
        camState.fullNight = ParentEngine.viewNight
    }

    private fun refresh() {
        if (!ending && !ParentEngine.connected) {
            ending = true
            toast(t("The connection to the child phone was lost.", "انقطع الاتصال بجوال الطفل."))
            finish()
            return
        }
        if (!ending && !ParentEngine.viewing && ParentEngine.viewNote.isNotEmpty()) {
            ending = true
            toast(ParentEngine.viewNote)
            finish()
            return
        }
        val sb = StringBuilder()
        sb.append(if (ParentEngine.viewing) t("Live", "مباشر") else t("Starting...", "جارٍ البدء..."))
        sb.append(" - ").append(t("watching now: ", "يشاهد الآن: ")).append(maxOf(ParentEngine.viewers, 1)).append("\n")
        if (ParentEngine.talkNote.isNotEmpty() && !ParentEngine.connected) sb.append(ParentEngine.talkNote).append("\n")
        frames = 0
        statusTv?.text = sb.toString()
        camState.status = sb.toString()
        nightIcon?.background = Look.glass(this, 18, if (ParentEngine.viewNight) Fx.tint(Themes.current().accent, 0x88) else Look.BUTTON)
        camState.night = ParentEngine.viewNight
        camState.quality = ParentEngine.viewQuality
        camState.fps = ParentEngine.viewFps

        val flashState = when (ParentEngine.viewFlashLevel) {
            1 -> t("low (tap for more)", "منخفض (اضغط للمزيد)")
            2 -> t("medium (tap for more)", "متوسط (اضغط للمزيد)")
            3 -> t("full (tap to turn off)", "كامل (اضغط للإطفاء)")
            else -> t("off (tap to turn on)", "مطفأ (اضغط للتشغيل)")
        }
        flashBtn?.text = t("Flash: ", "الفلاش: ") + flashState
        switchBtn?.text = t("Camera: " + (if (ParentEngine.viewFront) "front" else "back") + " (tap to switch)",
            "الكاميرا: " + (if (ParentEngine.viewFront) "أمامية" else "خلفية") + " (اضغط للتبديل)")
        nightBtn?.text = t("Night mode: " + (if (ParentEngine.viewNight) "on (tap to turn off)" else "off (tap to turn on)"),
            "وضع الليل: " + (if (ParentEngine.viewNight) "مشغّل (اضغط للإطفاء)" else "مطفأ (اضغط للتشغيل)"))
        soundBtn?.text = t("Room sound: " + (if (ParentEngine.liveSound) "on" else "muted"),
            "صوت الغرفة: " + (if (ParentEngine.liveSound) "مشغّل" else "مكتوم"))

        val labels = qualityLabels
        for (q in 0..3) {
            val b = qualityBtns[q] ?: continue
            val chosen = ParentEngine.viewQuality == q
            b.text = (if (chosen) "✓ " else "") + labels[q]
            b.background = Look.glass(this, 16, if (chosen) Fx.tint(Themes.current().accent, 0x66) else Look.BUTTON)
        }

        val night = ParentEngine.viewNight
        if (night != lastNight) {
            lastNight = night
            val filter = if (night) ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    1.35f, 0f, 0f, 0f, 22f,
                    0f, 1.35f, 0f, 0f, 22f,
                    0f, 0f, 1.35f, 0f, 22f,
                    0f, 0f, 0f, 1f, 0f
                ))
            ) else null
            picture?.colorFilter = filter
            fullPicture?.colorFilter = filter
        }
        refreshFull()
        camState.flashText = t("Flash: ", "الفلاش: ") + flashState
        camState.switchText = t("Camera: " + (if (ParentEngine.viewFront) "front" else "back") + " (tap to switch)",
            "الكاميرا: " + (if (ParentEngine.viewFront) "أمامية" else "خلفية") + " (اضغط للتبديل)")
        camState.soundText = t("Room sound: " + (if (ParentEngine.liveSound) "on" else "muted"),
            "صوت الغرفة: " + (if (ParentEngine.liveSound) "مشغّل" else "مكتوم"))
    }

    private val camState = CamTopState()

    @Composable
    private fun CameraPage() {
        val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CameraTop()
                CameraBottom()
            }
            if (camState.fullOpen) FullScreenLayer()
            }
        }
    }

    /** Top area in Compose: title, live picture, status, quality, night, record, frame rate and full screen. */
    @Composable
    private fun CameraTop() {
        val text = ComposeColor(Look.TEXT)
        val sub = ComposeColor(Look.SUB)
        val accent = ComposeColor(Themes.current().accent)
        val glass = RoundedCornerShape(18.dp)
        val chipBg = ComposeColor(Look.BUTTON)
        val glassColors = ButtonDefaults.buttonColors(containerColor = chipBg, contentColor = text)
        val labels = qualityLabels
        var fpsOpen by remember { mutableStateOf(false) }

        Text(t("Live camera", "الكاميرا المباشرة"), fontSize = 28.sp, color = text)

        AndroidView(
            factory = { c ->
                ImageView(c).apply {
                    background = Look.glass(c, 18, Color.BLACK)
                    clipToOutline = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }.also { picture = it }
            },
            modifier = Modifier.fillMaxWidth().height(340.dp)
        )

        Text(camState.status, fontSize = 14.sp, color = sub)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (q in 0..3) {
                val chosen = camState.quality == q
                ComposeButton(
                    onClick = {
                        ParentEngine.setView(q, ParentEngine.viewFront, ParentEngine.viewFlash)
                        checkHeavy(q, ParentEngine.viewFps)
                        refresh()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = glass,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    colors = if (chosen) ButtonDefaults.buttonColors(containerColor = accent, contentColor = ComposeColor.White)
                    else glassColors
                ) {
                    Text(
                        (if (chosen) "✓ " else "") + labels[q],
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = {
                    cycleNight()
                    refresh()
                },
                modifier = Modifier.size(52.dp).background(if (camState.night) accent else chipBg, RoundedCornerShape(18.dp))
            ) {
                Icon(painterResource(R.drawable.ic_night), t("Night mode", "وضع الليل"), tint = text)
            }
            IconButton(
                onClick = { toggleRecording() },
                modifier = Modifier.size(52.dp).background(
                    if (camState.recording) ComposeColor(0xAAE53935.toInt()) else chipBg,
                    RoundedCornerShape(18.dp)
                )
            ) {
                Icon(painterResource(R.drawable.ic_record), t("Record video", "تسجيل فيديو"), tint = text)
            }
            Box {
                ComposeButton(
                    onClick = { fpsOpen = true },
                    modifier = Modifier.width(112.dp).height(52.dp),
                    shape = glass,
                    colors = glassColors
                ) { Text("${camState.fps} fps", fontSize = 14.sp) }
                DropdownMenu(expanded = fpsOpen, onDismissRequest = { fpsOpen = false }) {
                    for (n in 10..30) {
                        DropdownMenuItem(
                            text = { Text("$n fps") },
                            onClick = {
                                fpsOpen = false
                                ParentEngine.setFps(n)
                                checkHeavy(ParentEngine.viewQuality, n)
                                camState.fps = n
                            }
                        )
                    }
                }
            }
            ComposeButton(
                onClick = { enterFull() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(22.dp),
                colors = glassColors
            ) { Text(t("Full screen", "ملء الشاشة"), fontSize = 14.sp) }
        }
    }


    /** Bottom bar in Compose. The talk control stays a classic view: it has its own press-and-hold logic. */
    @Composable
    private fun CameraBottom() {
        val text = ComposeColor(Look.TEXT)
        val sub = ComposeColor(Look.SUB)
        val glass = RoundedCornerShape(18.dp)
        val glassColors = ButtonDefaults.buttonColors(containerColor = ComposeColor(Look.BUTTON), contentColor = text)

        ChildTalkButton()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(ComposeColor(Look.BUTTON), glass)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapFlash() },
                        onLongPress = { showFlashChoice() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(camState.flashText, fontSize = 15.sp, color = text)
        }

        ComposeButton(
            onClick = { ParentEngine.setView(ParentEngine.viewQuality, !ParentEngine.viewFront, false) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = glass,
            colors = glassColors
        ) { Text(camState.switchText, fontSize = 15.sp) }

        ComposeButton(
            onClick = { ParentEngine.viewRotate = (ParentEngine.viewRotate + 1) and 3 },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = glass,
            colors = glassColors
        ) { Text(t("Rotate the picture", "تدوير الصورة"), fontSize = 15.sp) }

        ComposeButton(
            onClick = {
                ParentEngine.liveSound = !ParentEngine.liveSound
                refresh()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = glass,
            colors = glassColors
        ) { Text(camState.soundText, fontSize = 15.sp) }

        ComposeButton(
            onClick = { finish() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = glass,
            colors = glassColors
        ) { Text(t("Stop and close", "إيقاف وإغلاق"), fontSize = 15.sp) }

        Text(
            t("Night mode: the picture is slower and brighter, which helps in a dark room but can blur fast movement. While someone watches, the child phone does not record clips. The live view stops by itself after 15 minutes, and the flash after 2 minutes.",
                "وضع الليل: الصورة أبطأ وأسطع، يساعد في الغرفة المظلمة لكن قد يطمس الحركة السريعة. أثناء المشاهدة لا يسجل جوال الطفل مقاطع. يتوقف البث المباشر وحده بعد 15 دقيقة، والفلاش بعد دقيقتين."),
            fontSize = 13.sp,
            color = sub
        )
    }

    /** Full-screen view in Compose: the picture (pinch to zoom) with the controls on top and at the bottom. */
    @Composable
    private fun FullScreenLayer() {
        val glass = RoundedCornerShape(18.dp)
        val accent = ComposeColor(Themes.current().accent)
        val chip = ComposeColor(Look.BUTTON)
        val text = ComposeColor(Look.TEXT)
        val on = accent.copy(alpha = 0.55f)

        Box(modifier = Modifier.fillMaxSize().background(ComposeColor.Black)) {
            AndroidView(
                factory = { c ->
                    ImageView(c).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(Color.BLACK)
                        setImageBitmap(latest)
                        rotation = fullTurn * 90f
                        scaleX = zoom
                        scaleY = zoom
                        setOnTouchListener { _, ev ->
                            if (ev.pointerCount >= 2 || ev.actionMasked == MotionEvent.ACTION_POINTER_UP) fullDetector.onTouchEvent(ev)
                            true
                        }
                    }.also { fullPicture = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FullIcon(R.drawable.ic_back, t("Back", "رجوع"), chip, text, Modifier.size(52.dp)) { exitFull() }
                FullIcon(R.drawable.ic_rotate, t("Rotate the picture", "تدوير الصورة"), chip, text, Modifier.size(52.dp)) {
                    fullTurn = (fullTurn + 1) and 3
                    fullPicture?.rotation = fullTurn * 90f
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .background(if (camState.fullFlash) on else chip, glass)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { tapFlash() }, onLongPress = { showFlashChoice() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_flash), t("Flash", "الفلاش"), tint = text, modifier = Modifier.size(22.dp))
                }
                FullIcon(R.drawable.ic_quality, t("Quality", "الجودة"), chip, text, Modifier.weight(1f).height(50.dp)) { chooseQuality() }
                FullIcon(
                    if (camState.fullSound) R.drawable.ic_speaker else R.drawable.ic_speaker_off,
                    t("Room sound", "صوت الغرفة"),
                    if (camState.fullSound) chip else on, text, Modifier.weight(1f).height(50.dp)
                ) {
                    ParentEngine.liveSound = !ParentEngine.liveSound
                    refreshFull()
                }
                FullIcon(R.drawable.ic_mic, t("Talk to the child", "التحدث مع الطفل"),
                    if (camState.fullTalk) accent.copy(alpha = 0.7f) else chip, text,
                    Modifier.weight(1f).height(50.dp)) { talkTap() }
                FullIcon(R.drawable.ic_night, t("Night mode", "وضع الليل"), if (camState.fullNight) on else chip, text, Modifier.weight(1f).height(50.dp)) {
                    cycleNight()
                    refreshFull()
                }
                FullIcon(R.drawable.ic_fps, t("Frame rate", "معدل الفريمات"), chip, text, Modifier.weight(1f).height(50.dp)) { chooseFps() }
                FullIcon(R.drawable.ic_record, t("Record video", "تسجيل فيديو"),
                    if (camState.recording) ComposeColor(0xAAE53935.toInt()) else chip, text,
                    Modifier.weight(1f).height(50.dp)) { toggleRecording() }
            }
        }
    }

    @Composable
    private fun FullIcon(
        icon: Int,
        desc: String,
        background: ComposeColor,
        tint: ComposeColor,
        modifier: Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier = modifier
                .background(background, RoundedCornerShape(18.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), desc, tint = tint, modifier = Modifier.size(22.dp))
        }
    }

    /** Talk-to-child logic, the same rules as the classic control: hold or tap, 60 seconds in tap mode. */
    private val talkLimit = Runnable {
        talkUp()
        toast(t("Talk stopped after 60 seconds.", "توقف الحديث بعد 60 ثانية."))
    }

    private fun talkDown() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askMic { talkDown() }
            return
        }
        if (ParentEngine.beginTalk()) {
            camState.talking = true
            if (camState.pressMode) ui.postDelayed(talkLimit, 60_000L)
        } else {
            toast(ParentEngine.talkNote)
        }
    }

    private fun talkUp() {
        ui.removeCallbacks(talkLimit)
        ParentEngine.endTalk()
        camState.talking = false
    }

    private fun talkTap() {
        if (ParentEngine.talking) talkUp() else talkDown()
    }

    private fun applyTalkMode(press: Boolean) {
        Diag.i("Talk mode changed to " + (if (press) "tap" else "hold"))
        Prefs.putString("talkMode", if (press) "press" else "hold")
        if (ParentEngine.talking) talkUp()
        camState.pressMode = press
    }

    /** The talk-to-child button in Compose, with the Hold / Tap switch in its corner. */
    @Composable
    private fun ChildTalkButton() {
        val text = ComposeColor(Look.TEXT)
        val glass = RoundedCornerShape(26.dp)
        var pressed by remember { mutableStateOf(false) }
        val press = camState.pressMode
        val idle = if (press) t("Tap to talk", "اضغط للتحدث") else t("Hold to talk", "اضغط مع الاستمرار للتحدث")
        val talkingLabel = if (press) t("Talking… tap to stop", "تتحدث… اضغط للإيقاف")
        else t("Talking… release to stop", "تتحدث… ارفع إصبعك للإيقاف")
        val bg = if (camState.talking) ComposeColor(Themes.current().accent).copy(alpha = 0.6f) else ComposeColor(Look.BUTTON)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .scale(if (pressed) 0.97f else 1f)
                .background(bg, glass)
                .pointerInput(press) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                if (!press) {
                                    talkDown()
                                    tryAwaitRelease()
                                    talkUp()
                                } else {
                                    tryAwaitRelease()
                                }
                            } finally {
                                pressed = false
                            }
                        },
                        onTap = { if (press) talkTap() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(if (camState.talking) talkingLabel else idle, fontSize = 18.sp, color = text)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .background(ComposeColor(0x66000000), RoundedCornerShape(14.dp))
                    .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (press) t("Tap", "نقرة") else t("Hold", "ضغط"), fontSize = 13.sp, color = ComposeColor.White)
                Switch(checked = press, onCheckedChange = { applyTalkMode(it) })
            }
        }
    }
}

/** What the Compose top area of the live camera shows. The classic flows write into it. */
class CamTopState {
    var status by mutableStateOf("")
    var quality by mutableStateOf(0)
    var night by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var fps by mutableStateOf(10)
    var flashText by mutableStateOf("Flash")
    var switchText by mutableStateOf("Switch camera")
    var soundText by mutableStateOf("Room sound")
    var fullOpen by mutableStateOf(false)
    var fullFlash by mutableStateOf(false)
    var fullSound by mutableStateOf(true)
    var fullTalk by mutableStateOf(false)
    var fullNight by mutableStateOf(false)
    var talking by mutableStateOf(false)
    var pressMode by mutableStateOf(false)
}
