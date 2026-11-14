package com.liquid.liquidbaby

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

/** Parent unit: live camera with quality levels, full screen with a microphone, and the talk controls. */
class CameraActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var picture: ImageView? = null
    private var fullPicture: ImageView? = null
    private var statusTv: TextView? = null
    private var flashBtn: Button? = null
    private var switchBtn: Button? = null
    private var nightBtn: Button? = null
    private var soundBtn: Button? = null
    private var talkBtn: Button? = null
    private var fullMic: ImageView? = null
    private var overlay: FrameLayout? = null
    private val qualityBtns = arrayOfNulls<Button>(4)
    private val pending = AtomicBoolean(false)
    private var lastNight = false
    private var ending = false

    @Volatile
    private var latest: Bitmap? = null

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun talkMode(): String = Prefs.getString("talkMode", "hold")

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
            iv.setBackgroundColor(Color.BLACK)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(iv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(340)))
            picture = iv
            statusTv = addBody("")

            // quality: four levels side by side; the chosen one has a tick
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            val labels = arrayOf(
                t("Low", "منخفضة"), t("Normal", "عادية"), "960×540", "720p"
            )
            for (q in 0..3) {
                val b = Button(context)
                b.isAllCaps = false
                b.textSize = 13f
                b.text = labels[q]
                b.setOnClickListener {
                    ParentEngine.setView(q, ParentEngine.viewFront, ParentEngine.viewFlash)
                    refresh()
                }
                qualityBtns[q] = b
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                })
            }
            addView(row)

            val tb = addButton(t("Hold to talk to the child", "اضغط مع الاستمرار للتحدث")) { }
            tb.textSize = 20f
            tb.minHeight = dp(96)
            tb.setOnTouchListener { _, ev ->
                if (talkMode() == "press") {
                    if (ev.actionMasked == MotionEvent.ACTION_UP) togglePressTalk()
                } else {
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> onTalkDown()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTalkUp()
                    }
                }
                true
            }
            talkBtn = tb
            flashBtn = addButton(t("Flash", "الفلاش")) {
                ParentEngine.setView(ParentEngine.viewQuality, ParentEngine.viewFront, !ParentEngine.viewFlash)
            }
            switchBtn = addButton(t("Switch camera", "تبديل الكاميرا")) {
                ParentEngine.setView(ParentEngine.viewQuality, !ParentEngine.viewFront, false)
            }
            nightBtn = addButton(t("Night mode", "وضع الليل")) {
                ParentEngine.setView(ParentEngine.viewQuality, ParentEngine.viewFront, ParentEngine.viewFlash, !ParentEngine.viewNight)
            }
            addButton(t("Full screen, with microphone", "ملء الشاشة مع الميكروفون")) { enterFull() }
            addButton(t("Rotate the picture", "تدوير الصورة")) { ParentEngine.viewRotate = (ParentEngine.viewRotate + 1) and 3 }
            soundBtn = addButton(t("Live sound", "الصوت المباشر")) {
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
        exitFull()
        ui.removeCallbacks(tick)
        ParentEngine.frameListener = null
        ParentEngine.onChange = null
        ParentEngine.endTalk()
        ParentEngine.endView()
    }

    override fun onBackPressed() {
        if (overlay != null) exitFull() else super.onBackPressed()
    }

    // ---------- full screen ----------

    private fun enterFull() {
        if (overlay != null) return
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val pic = ImageView(this)
        pic.scaleType = ImageView.ScaleType.FIT_CENTER
        pic.setBackgroundColor(Color.BLACK)
        root.addView(pic, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fullPicture = pic
        fullPicture?.setImageBitmap(latest)

        val back = Button(this)
        back.isAllCaps = false
        back.text = t("Back", "رجوع")
        back.setTextColor(Look.TEXT)
        back.background = Look.glass(this, 20, Look.BUTTON)
        back.setOnClickListener { exitFull() }
        root.addView(back, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply { topMargin = dp(16); marginStart = dp(16) })

        val rotate = Button(this)
        rotate.isAllCaps = false
        rotate.text = t("Rotate", "تدوير")
        rotate.setTextColor(Look.TEXT)
        rotate.background = Look.glass(this, 20, Look.BUTTON)
        rotate.setOnClickListener { rotateScreen() }
        root.addView(rotate, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply { topMargin = dp(16); marginEnd = dp(16) })

        val mic = ImageView(this)
        mic.setImageResource(R.drawable.ic_mic)
        mic.setColorFilter(Look.TEXT)
        mic.background = Look.glass(this, 40, Look.BUTTON)
        mic.setPadding(dp(18), dp(18), dp(18), dp(18))
        mic.contentDescription = t("Talk to the child", "التحدث مع الطفل")
        mic.setOnTouchListener { _, ev ->
            if (talkMode() == "press") {
                if (ev.actionMasked == MotionEvent.ACTION_UP) togglePressTalk()
            } else {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onTalkDown()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTalkUp()
                }
            }
            true
        }
        fullMic = mic
        root.addView(mic, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = dp(24)
            marginEnd = dp(24)
        })

        addContentView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = root
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    /** Leaves full screen: stops any talk that is running and restores the normal screen. */
    private fun exitFull() {
        val root = overlay ?: return
        if (ParentEngine.talking) onTalkUp()
        (root.parent as? ViewGroup)?.removeView(root)
        overlay = null
        fullPicture = null
        fullMic = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /** Manual rotation: landscape and portrait, only when the button is pressed. */
    private fun rotateScreen() {
        requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    // ---------- talk ----------

    private fun togglePressTalk() {
        if (ParentEngine.talking) onTalkUp() else onTalkDown()
    }

    private fun onTalkDown() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 30)
            toast(t("Allow the microphone, then try again.", "اسمح بالميكروفون ثم حاول مرة أخرى."))
            return
        }
        if (ParentEngine.beginTalk()) {
            talkBtn?.text = t("Talking... (live sound is muted)", "تتحدث... (الصوت المباشر مكتوم)")
            fullMic?.background = Look.glass(this, 40, Fx.tint(Themes.current().accent, 0xAA))
        } else {
            toast(ParentEngine.talkNote)
        }
    }

    private fun onTalkUp() {
        ParentEngine.endTalk()
        talkBtn?.text = t("Hold to talk to the child", "اضغط مع الاستمرار للتحدث")
        fullMic?.background = Look.glass(this, 40, Look.BUTTON)
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
        statusTv?.text = sb.toString()

        val flashState = if (ParentEngine.viewFlash) t("ON (tap to turn off)", "مشغّل (اضغط للإطفاء)")
        else t("off (tap to turn on)", "مطفأ (اضغط للتشغيل)")
        val flashNote = if (ParentEngine.viewHasFlash) "" else t(" - this camera does not report a flash", " - هذه الكاميرا لا تُبلغ عن فلاش")
        flashBtn?.text = t("Flash: ", "الفلاش: ") + flashState + flashNote
        switchBtn?.text = t("Camera: " + (if (ParentEngine.viewFront) "front" else "back") + " (tap to switch)",
            "الكاميرا: " + (if (ParentEngine.viewFront) "أمامية" else "خلفية") + " (اضغط للتبديل)")
        nightBtn?.text = t("Night mode: " + (if (ParentEngine.viewNight) "on (tap to turn off)" else "off (tap to turn on)"),
            "وضع الليل: " + (if (ParentEngine.viewNight) "مشغّل (اضغط للإطفاء)" else "مطفأ (اضغط للتشغيل)"))
        soundBtn?.text = t("Live sound: " + (if (ParentEngine.liveSound) "on" else "off") + " (tap to change)",
            "الصوت المباشر: " + (if (ParentEngine.liveSound) "مشغّل" else "مطفأ") + " (اضغط للتغيير)")

        val labels = arrayOf(t("Low", "منخفضة"), t("Normal", "عادية"), "960×540", "720p")
        for (q in 0..3) {
            val b = qualityBtns[q] ?: continue
            val chosen = ParentEngine.viewQuality == q
            b.text = (if (chosen) "✓ " else "") + labels[q]
            b.background = Look.glass(this, 16, if (chosen) Fx.tint(Themes.current().accent, 0x66) else Look.BUTTON)
        }

        val night = ParentEngine.viewNight
        if (night != lastNight) {
            lastNight = night
            picture?.colorFilter = if (night) ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        1.35f, 0f, 0f, 0f, 22f,
                        0f, 1.35f, 0f, 0f, 22f,
                        0f, 0f, 1.35f, 0f, 22f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            ) else null
            fullPicture?.colorFilter = picture?.colorFilter
        }
        if (!ParentEngine.talking && talkBtn?.text?.toString()?.startsWith("Talk") == true && talkBtn?.text?.toString()?.contains("Talking") == true) {
            talkBtn?.text = t("Hold to talk to the child", "اضغط مع الاستمرار للتحدث")
        }
    }
}
