package com.liquid.liquidbaby

import android.app.AlertDialog
import java.io.File
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
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** Parent unit: live camera. Normal view, and a separate full-screen layer with pinch zoom and icon controls. */
class CameraActivity : android.app.Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var picture: ImageView? = null
    private var fullPicture: ImageView? = null
    private var statusTv: TextView? = null
    private var talk: TalkControl? = null
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
        screen {
            addTitle("Live camera")
            val iv = ImageView(context)
            iv.background = Look.glass(context, 18, Color.BLACK)
            iv.clipToOutline = true
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(iv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(340)))
            picture = iv
            statusTv = addBody("")

            // quality: four levels side by side; the chosen one has a tick
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            for (q in 0..3) {
                val b = Button(context)
                b.isAllCaps = false
                b.textSize = 13f
                b.minHeight = dp(44)
                b.setTextColor(Look.TEXT)
                b.background = Look.glass(context, 14, Look.BUTTON)
                b.setOnClickListener {
                    ParentEngine.setView(q, ParentEngine.viewFront, ParentEngine.viewFlash)
                    checkHeavy(q, ParentEngine.viewFps)
                    refresh()
                }
                qualityBtns[q] = b
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                })
            }
            addView(row)

            // the same talk control as the main screen
            val tc = TalkControl(this@CameraActivity)
            talk = tc
            addView(tc.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })

            flashBtn = addButton(t("Flash", "الفلاش")) {
                tapFlash()
            }
            flashBtn?.setOnLongClickListener {
                showFlashChoice()
                true
            }
            switchBtn = addButton(t("Switch camera", "تبديل الكاميرا")) {
                ParentEngine.setView(ParentEngine.viewQuality, !ParentEngine.viewFront, false)
            }
            val controls = LinearLayout(context)
            controls.orientation = LinearLayout.HORIZONTAL
            controls.gravity = Gravity.CENTER
            nightIcon = ImageButton(context).apply {
                setImageResource(R.drawable.ic_night)
                setColorFilter(Look.TEXT)
                background = Look.glass(context, 18, Look.BUTTON)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                contentDescription = t("Night mode", "وضع الليل")
                setOnClickListener {
                    cycleNight()
                    refresh()
                }
            }
            controls.addView(nightIcon, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(16) })
            recordIcon = ImageButton(context).apply {
                setImageResource(R.drawable.ic_record)
                background = Look.glass(context, 18, Look.BUTTON)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                contentDescription = t("Record video", "تسجيل فيديو")
                setOnClickListener { toggleRecording() }
            }
            controls.addView(recordIcon, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(16) })
            fpsSpinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, (10..30).map { "$it fps" })
                setSelection(ParentEngine.viewFps - 10, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        val fps = pos + 10
                        if (fps != ParentEngine.viewFps) {
                            ParentEngine.setFps(fps)
                            checkHeavy(ParentEngine.viewQuality, fps)
                        }
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            }
            controls.addView(fpsSpinner, LinearLayout.LayoutParams(dp(112), dp(52)).apply { marginEnd = dp(16) })
            controls.addView(Button(context).apply {
                isAllCaps = false
                text = t("Full screen", "ملء الشاشة")
                setTextColor(Look.TEXT)
                background = Look.glass(context, 22, Look.BUTTON)
                setOnClickListener { enterFull() }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
                bottomMargin = dp(6)
            })
            addButton(t("Rotate the picture", "تدوير الصورة")) { ParentEngine.viewRotate = (ParentEngine.viewRotate + 1) and 3 }
            soundBtn = addButton(t("Room sound", "صوت الغرفة")) {
                ParentEngine.liveSound = !ParentEngine.liveSound
                refresh()
            }
            addButton(t("Stop and close", "إيقاف وإغلاق")) { finish() }
            addBody(t("Night mode: the picture is slower and brighter, which helps in a dark room but can blur fast movement. While someone watches, the child phone does not record clips. The live view stops by itself after 15 minutes, and the flash after 2 minutes.",
                "وضع الليل: الصورة أبطأ وأسطع، يساعد في الغرفة المظلمة لكن قد يطمس الحركة السريعة. أثناء المشاهدة لا يسجل جوال الطفل مقاطع. يتوقف البث المباشر وحده بعد 15 دقيقة، والفلاش بعد دقيقتين."))
        }
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
        talk?.up()
        ui.removeCallbacks(tick)
        ParentEngine.frameListener = null
        ParentEngine.onChange = null
        ParentEngine.endView()
    }

    override fun onBackPressed() {
        when {
            overlay != null -> exitFull()
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
        val red = (0xAA shl 24) or 0xE53935
        val icon = if (recording) R.drawable.ic_stop_record else R.drawable.ic_record
        recordIcon?.setImageResource(icon)
        recordIcon?.background = Look.glass(this, 18, if (recording) red else Look.BUTTON)
        fullRecord?.setImageResource(icon)
        fullRecord?.background = Look.glass(this, 24, if (recording) red else Look.BUTTON)
    }

    // ---------- full-screen layer ----------

    private fun iconButton(icon: Int, desc: String, onTap: () -> Unit): ImageButton {
        val b = ImageButton(this)
        b.setImageResource(icon)
        b.setColorFilter(Look.TEXT)
        b.background = Look.glass(this, 25, Look.BUTTON)
        b.scaleType = ImageView.ScaleType.CENTER
        b.setPadding(dp(12), dp(12), dp(12), dp(12))
        b.contentDescription = desc
        b.setOnClickListener { onTap() }
        b.setOnLongClickListener {
            Toast.makeText(this, desc, Toast.LENGTH_SHORT).show()
            true
        }
        return b
    }

    private fun enterFull() {
        if (overlay != null) return
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        // the picture, zoomed by pinch only
        val pic = ImageView(this)
        pic.scaleType = ImageView.ScaleType.FIT_CENTER
        pic.setBackgroundColor(Color.BLACK)
        pic.setImageBitmap(latest)
        pic.rotation = fullTurn * 90f
        pic.scaleX = zoom
        pic.scaleY = zoom
        root.addView(pic, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fullPicture = pic

        // the layer takes every touch: two fingers zoom, anything else does nothing
        val detector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
        root.setOnTouchListener { _, ev ->
            if (ev.pointerCount >= 2 || ev.actionMasked == MotionEvent.ACTION_POINTER_UP) detector.onTouchEvent(ev)
            true
        }

        // top: back and rotate
        val back = iconButton(R.drawable.ic_back, t("Back", "رجوع")) { exitFull() }
        root.addView(back, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(14); marginStart = dp(14)
        })
        val rotate = iconButton(R.drawable.ic_rotate, t("Rotate the picture", "تدوير الصورة")) {
            fullTurn = (fullTurn + 1) and 3
            fullPicture?.rotation = fullTurn * 90f
        }
        root.addView(rotate, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(14); marginEnd = dp(14)
        })

        // bottom: icons in one row
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        fullFlash = iconButton(R.drawable.ic_flash, t("Flash", "الفلاش")) {
            tapFlash()
        }
        fullFlash?.setOnLongClickListener {
            showFlashChoice()
            true
        }
        bar.addView(fullFlash, barParams())
        bar.addView(iconButton(R.drawable.ic_quality, t("Quality", "الجودة")) { chooseQuality() }, barParams())
        fullSound = iconButton(R.drawable.ic_speaker, t("Room sound", "صوت الغرفة")) {
            ParentEngine.liveSound = !ParentEngine.liveSound
            refreshFull()
        }
        bar.addView(fullSound, barParams())
        fullTalk = iconButton(R.drawable.ic_mic, t("Talk to the child", "التحدث مع الطفل")) { talk?.tap() }
        bar.addView(fullTalk, barParams())
        fullNight = iconButton(R.drawable.ic_night, t("Night mode", "وضع الليل")) {
            cycleNight()
            refreshFull()
        }
        bar.addView(fullNight, barParams())
        bar.addView(iconButton(R.drawable.ic_fps, t("Frame rate", "معدل الفريمات")) { chooseFps() }, barParams())
        fullRecord = iconButton(R.drawable.ic_record, t("Record video", "تسجيل فيديو")) { toggleRecording() }
        bar.addView(fullRecord, barParams())
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply {
            bottomMargin = dp(20)
            marginStart = dp(10)
            marginEnd = dp(10)
        })

        addContentView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        Fx.feedbackAll(root)
        overlay = root
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        refreshFull()
    }

    /** Equal shares of the bar's width, so all seven icons fit on any screen. */
    private fun barParams() = LinearLayout.LayoutParams(0, dp(50), 1f).apply {
        marginStart = dp(6)
        marginEnd = dp(6)
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
        val root = overlay ?: return
        talk?.up()
        (root.parent as? ViewGroup)?.removeView(root)
        overlay = null
        fullPicture = null
        fullFlash = null
        fullSound = null
        fullTalk = null
        fullNight = null
        zoom = 1f
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun refreshFull() {
        val accent = Themes.current().accent
        fullFlash?.background = Look.glass(this, 18, if (ParentEngine.viewFlash) Fx.tint(accent, 0x88) else Look.BUTTON)
        fullSound?.setImageResource(if (ParentEngine.liveSound) R.drawable.ic_speaker else R.drawable.ic_speaker_off)
        fullSound?.background = Look.glass(this, 18, if (ParentEngine.liveSound) Look.BUTTON else Fx.tint(accent, 0x88))
        fullTalk?.background = Look.glass(this, 18, if (ParentEngine.talking) Fx.tint(accent, 0xAA) else Look.BUTTON)
        fullNight?.background = Look.glass(this, 18, if (ParentEngine.viewNight) Fx.tint(accent, 0x88) else Look.BUTTON)
    }

    // ---------- refresh ----------

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
        if (ParentEngine.talkNote.isNotEmpty()) sb.append(ParentEngine.talkNote).append("\n")
        frames = 0
        statusTv?.text = sb.toString()
        nightIcon?.background = Look.glass(this, 18, if (ParentEngine.viewNight) Fx.tint(Themes.current().accent, 0x88) else Look.BUTTON)

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
    }
}
