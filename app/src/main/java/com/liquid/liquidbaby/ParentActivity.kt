package com.liquid.liquidbaby

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** Parent screen: status first, then monitoring, then recordings, with settings folded away. */
class ParentActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var builtAr: Boolean? = null
    private var pairedCache: PairStore.Paired? = null
    private var lastClipsVersion = -1
    private var entered = false
    private var pulse: ObjectAnimator? = null

    private var statusPill: TextView? = null
    private var runBtn: Button? = null
    private var vpnBtn: Button? = null
    private var childLine: TextView? = null
    private var detailLine: TextView? = null
    private var batteryDot: View? = null
    private var batteryTv: TextView? = null
    private var tempDot: View? = null
    private var tempTv: TextView? = null
    private var meter: ProgressBar? = null
    private var listenBtn: Button? = null
    private var talkBtn: Button? = null
    private var talkModeLabel: TextView? = null
    private var talkModeToggle: ModeToggle? = null
    private var modeRowView: View? = null
    private var talkPulse: ObjectAnimator? = null
    private val talkLimit = Runnable {
        onTalkUp()
        toast(t("Talk stopped after 60 seconds.", "توقف الحديث بعد 60 ثانية."))
    }
    private var settingsHead: TextView? = null
    private var settingsBox: LinearLayout? = null
    private var settingsOpen = false
    private var lenLabel: TextView? = null
    private var lenBar: SeekBar? = null
    private var sensLabel: TextView? = null
    private var sensBar: SeekBar? = null
    private var intervalLabel: TextView? = null
    private var intervalBar: SeekBar? = null
    private var walkieLabel: TextView? = null
    private var walkieBar: SeekBar? = null
    private var listBox: LinearLayout? = null
    private var lastClipCount = -1
    private var draggingLen = false
    private var draggingSens = false
    private var warnedVault = false

    private var player: MediaPlayer? = null
    private var playingTs = 0L
    private var waveView: WaveView? = null
    private var waveBars = FloatArray(0)
    private val waveTick = object : Runnable {
        override fun run() {
            val mp = player
            val wv = waveView
            if (mp != null && wv != null) {
                val d = try { mp.duration } catch (e: Exception) { 0 }
                val p = try { mp.currentPosition } catch (e: Exception) { 0 }
                wv.progress = if (d > 0) p.toFloat() / d else 0f
                wv.invalidate()
                ui.postDelayed(this, 80)
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1000)
        }
    }

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun build() {
        val t0 = System.nanoTime()
        builtAr = Tr.isAr()
        pulse?.cancel()
        pulse = null
        val paired = PairStore.paired()
        pairedCache = paired
        screen {
            addTitle(t("Parent", "وحدة الأبوين"))
            statusPill = addChip("")
            runBtn = addButton(t("Start monitoring", "ابدأ المراقبة")) { toggle() }
            Fx.press(runBtn!!)
            Fx.ripple(runBtn!!)
            vpnBtn = addButton(t("VPN is blocking the connection: open VPN settings", "الـ VPN يمنع الاتصال: افتح إعدادات الـ VPN")) {
                openVpnSettings()
            }
            vpnBtn?.visibility = View.GONE

            if (paired == null) {
                card {
                    addBody(t("No child phone yet", "لم يتم اقتران جوال طفل بعد"))
                    addButton(t("Pair with the child phone", "اقرن مع جوال الطفل")) { startPairFlow() }
                    line(t("On the child phone, tap 'Allow a new parent phone to pair'.",
                        "على جوال الطفل، اضغط 'السماح بإقران جوال أبوين جديد'."))
                }
            } else {
                if (paired.key.isEmpty()) {
                    card {
                        addBody(t("Pairing keys could not be read on this phone.", "تعذّر قراءة مفاتيح الاقتران على هذا الجوال."))
                        line(t("Remove the pairing below and pair again.", "ألغِ الاقتران من الأسفل واقرن من جديد."))
                    }
                }
                card {
                    val row = addRow()
                    batteryDot = Fx.dot(context, 0xFF8899AA.toInt())
                    row.addView(batteryDot)
                    childLine = TextView(context).apply {
                        textSize = 17f
                        setTextColor(Look.TEXT)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    childLine?.text = paired.childName.ifBlank { t("Child phone", "جوال الطفل") }
                    row.addView(childLine)
                    detailLine = line("")
                    batteryTv = line("", 15f)
                    val tRow = addRow()
                    tempDot = Fx.dot(context, 0xFF8899AA.toInt())
                    tRow.addView(tempDot)
                    tempTv = TextView(context).apply { textSize = 15f; setTextColor(Look.SUB) }
                    tRow.addView(tempTv)
                    meter = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = ParentEngine.level
                    }
                    addView(meter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply {
                        topMargin = dp(10)
                    })
                    line(t("Sound in the room", "صوت الغرفة"), 13f)
                }

                sectionLabel(t("Monitor", "المراقبة"))
                val grid = LinearLayout(context)
                grid.orientation = LinearLayout.HORIZONTAL
                addView(grid)
                tile(grid, t("Live camera", "الكاميرا المباشرة"), t("See the room", "شاهد الغرفة")) {
                    if (ParentEngine.connected) {
                        startActivity(Intent(this@ParentActivity, CameraActivity::class.java))
                    } else {
                        toast(t("Not connected to the child phone yet.", "غير متصل بجوال الطفل بعد."))
                    }
                }
                val listenTile = tile(grid, t("Listen", "استماع"), t("Sound only", "صوت فقط")) { toggleListen() }
                listenBtn = listenTile

                val talkFrame = android.widget.FrameLayout(context)
                talkBtn = Button(context).apply {
                    text = idleTalkText()
                    isAllCaps = false
                    textSize = 18f
                    setTextColor(Look.TEXT)
                    background = Look.glass(context, 26, Look.BUTTON)
                    minHeight = dp(88)
                    setOnTouchListener { _, ev ->
                        // the Hold/Tap toggle sits on top of this button: touches inside its bounds belong to it
                        val hit = android.graphics.Rect()
                        modeRowView?.getHitRect(hit)
                        if (hit.contains(ev.x.toInt(), ev.y.toInt())) return@setOnTouchListener false
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
                }
                talkFrame.addView(talkBtn, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(104)))
                Fx.ripple(talkBtn!!)
                val modeRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                talkModeLabel = TextView(context).apply {
                    textSize = 12f
                    setTextColor(Look.TEXT)
                    text = modePillText()
                    setPadding(0, 0, dp(8), 0)
                }
                modeRow.addView(talkModeLabel)
                talkModeToggle = ModeToggle(context).apply {
                    tapMode = talkMode() == "press"
                    onToggle = { press -> applyTalkMode(press) }
                    contentDescription = t("Talk mode: hold or tap", "وضع التحدث: ضغط أو نقرة")
                }
                modeRow.addView(talkModeToggle)
                talkFrame.addView(modeRow, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(8)
                    marginEnd = dp(12)
                })
                modeRow.bringToFront()
                modeRowView = modeRow
                addView(talkFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12)
                })

                sectionLabel(t("Recordings", "التسجيلات"))
                val box = LinearLayout(context)
                box.orientation = LinearLayout.VERTICAL
                addView(box)
                listBox = box

                sectionLabel(t("Settings", "الإعدادات"))
                settingsHead = TextView(context).apply {
                    textSize = 16f
                    setTextColor(Look.TEXT)
                    setPadding(dp(4), dp(10), dp(4), dp(10))
                    setOnClickListener { toggleSettings() }
                }
                addView(settingsHead)
                val sBox = LinearLayout(context)
                sBox.orientation = LinearLayout.VERTICAL
                sBox.visibility = if (settingsOpen) View.VISIBLE else View.GONE
                addView(sBox)
                settingsBox = sBox
                sBox.card(14) {
                    lenLabel = line("")
                    lenBar = addSlider(5, 60, ParentEngine.childLength, { v ->
                        draggingLen = true
                        lenLabel?.text = t("Clip length: $v seconds", "طول المقطع: $v ثوانٍ")
                    }, { v ->
                        draggingLen = false
                        Prefs.putInt("clipSeconds", v)
                        ParentEngine.childLength = v
                        ParentEngine.sendSettings(v, ParentEngine.childSens)
                    })
                    sensLabel = line("")
                    sensBar = addSlider(1, 5, ParentEngine.childSens, { v ->
                        draggingSens = true
                        sensLabel?.text = t("Sensitivity: $v of 5", "الحساسية: $v من 5")
                    }, { v ->
                        draggingSens = false
                        ParentEngine.childSens = v
                        ParentEngine.sendSettings(ParentEngine.childLength, v)
                    })
                    intervalLabel = line("")
                    intervalBar = addSlider(0, 15, ParentEngine.childInterval, { v ->
                        intervalLabel?.text = intervalText(v)
                    }, { v ->
                        ParentEngine.setInterval(v)
                    })
                    walkieLabel = line("")
                    walkieBar = addSlider(1, 10, ParentEngine.childTalkVol, { v ->
                        walkieLabel?.text = t("Talk volume on the child phone: $v of 10", "صوت المحادثة على جوال الطفل: $v من 10")
                    }, { v ->
                        ParentEngine.setTalkVolume(v)
                    })
                }

                Button(context).apply {
                    text = t("Remove pairing", "إلغاء الاقتران")
                    isAllCaps = false
                    setTextColor(0xFFFF8A8A.toInt())
                    background = Look.glass(context, 22, 0x22FF5252)
                    setOnClickListener { confirmRemove() }
                    Fx.press(this)
                }.let { b ->
                    addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(28)
                    })
                }
            }
        }
        refresh()
        fillList()
        lastClipsVersion = ParentEngine.clipsVersion
        Diag.limited("buildParent", "Parent screen built in ${(System.nanoTime() - t0) / 1_000_000} ms", 60_000L)
    }

    /** A square tile in a two-column grid. Returns the button so it can be styled later. */
    private fun LinearLayout.tile(grid: LinearLayout, title: String, sub: String, onClick: () -> Unit): Button {
        val b = Button(context)
        b.isAllCaps = false
        b.setTextColor(Look.TEXT)
        b.background = Look.glass(context, 22, Look.BUTTON)
        b.text = "$title\n$sub"
        b.textSize = 16f
        b.minHeight = dp(104)
        b.setOnClickListener { onClick() }
        Fx.press(b)
        Fx.ripple(b)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = dp(6)
        lp.marginStart = dp(6)
        grid.addView(b, lp)
        return b
    }

    private fun intervalText(v: Int): String =
        if (v == 0) t("Listening: continuously", "الاستماع: متواصل")
        else t("Checks every $v seconds (listens 5 s each time)", "يفحص كل $v ثوانٍ (يستمع 5 ثوانٍ في كل مرة)")

    private fun toggleSettings() {
        settingsOpen = !settingsOpen
        val box = settingsBox ?: return
        if (settingsOpen) {
            box.alpha = 0f
            box.visibility = View.VISIBLE
            box.animate().alpha(1f).setDuration(250).start()
        } else {
            box.visibility = View.GONE
        }
        refresh()
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle(t("Remove pairing with the child phone?", "هل تريد إلغاء الاقتران مع جوال الطفل؟"))
            .setPositiveButton(t("Remove", "إلغاء الاقتران")) { _, _ ->
                UnitService.stop(this)
                PairStore.clearPaired()
                build()
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (builtAr != Tr.isAr()) build()
        ParentEngine.onChange = { runOnUiThread { refresh() } }
        ParentEngine.setLevelSub(true)
        if (Vault.failed && !warnedVault) {
            warnedVault = true
            toast("تحذير: تشفير المفاتيح غير متاح على هذا الجوال")
        }
        if (!entered) {
            entered = true
            overridePendingTransition(R.anim.screen_in, R.anim.screen_out)
        }
        if (Prefs.getBool("serviceWanted", false) && !ParentEngine.running && pairedCache != null) {
            UnitService.start(this)
        }
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ParentEngine.onChange = null
        ui.removeCallbacks(tick)
        onTalkUp()
        ParentEngine.endListen()
        ParentEngine.setLevelSub(false)
        stopPlayer()
    }

    private fun talkMode(): String = Prefs.getString("talkMode", "hold")

    /** Short label on the pill inside the talk button. */
    private fun modePillText(): String =
        if (talkMode() == "press") t("Tap", "نقرة") else t("Hold", "ضغط")

    /** Applies hold or tap mode from the toggle. Stops any talk that is running. */
    private fun applyTalkMode(press: Boolean) {
        Diag.i("Talk mode changed to " + (if (press) "tap" else "hold"))
        Prefs.putString("talkMode", if (press) "press" else "hold")
        if (ParentEngine.talking) onTalkUp()
        talkModeLabel?.text = modePillText()
        talkBtn?.text = idleTalkText()
    }

    private fun idleTalkText(): String =
        if (talkMode() == "press") t("Tap to talk", "اضغط للتحدث")
        else t("Hold to talk", "اضغط مع الاستمرار للتحدث")

    private fun talkingText(): String =
        if (talkMode() == "press") t("Talking… tap to stop", "تتحدث… اضغط للإيقاف")
        else t("Talking… release to stop", "تتحدث… ارفع إصبعك للإيقاف")

    private fun togglePressTalk() {
        if (ParentEngine.talking) {
            onTalkUp()
        } else {
            onTalkDown()
            if (ParentEngine.talking) ui.postDelayed(talkLimit, 60_000L)
        }
    }

    private fun onTalkDown() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 30)
            toast(t("Allow the microphone, then hold the button again.", "اسمح بالميكروفون ثم اضغط الزر مرة أخرى."))
            return
        }
        if (ParentEngine.beginTalk()) {
            talkBtn?.text = talkingText()
            talkBtn?.background = Look.glass(this, 26, Fx.tint(Themes.current().accent, 0x66))
            talkPulse?.cancel()
            talkBtn?.let { talkPulse = Fx.breathe(it) }
            talkBtn?.animate()?.scaleX(1.02f)?.scaleY(1.02f)?.setDuration(200)?.start()
        } else {
            toast(ParentEngine.talkNote)
        }
    }

    private fun onTalkUp() {
        ui.removeCallbacks(talkLimit)
        talkPulse?.cancel()
        talkPulse = null
        talkBtn?.alpha = 1f
        ParentEngine.endTalk()
        talkBtn?.text = idleTalkText()
        talkBtn?.background = Look.glass(this, 26, Look.BUTTON)
        talkBtn?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
    }

    private fun toggle() {
        if (ParentEngine.running) UnitService.stop(this) else UnitService.start(this)
    }

    /** One label for the whole connection state. Engine details stay in Diagnostics. */
    private fun stateLabel(): Pair<String, Int> = when {
        ParentEngine.connected -> Pair(t("Connected", "متصل"), 0xFF2E9E6A.toInt())
        ParentEngine.running -> Pair(t("Searching", "جارٍ البحث"), 0xFFB7791F.toInt())
        else -> Pair(t("Stopped", "متوقف"), 0xFF6B7785.toInt())
    }

    private fun refresh() {
        val (label, color) = stateLabel()
        Fx.animateChip(statusPill, label, color)
        runBtn?.text = t(if (ParentEngine.running) "Stop monitoring" else "Start monitoring",
            if (ParentEngine.running) "أوقف المراقبة" else "ابدأ المراقبة")
        runBtn?.background = Look.glass(this, 22,
            if (ParentEngine.running) Look.BUTTON else Fx.tint(Themes.current().accent, 0x66))
        vpnBtn?.visibility = if (Net.vpnBlocksLocal(this)) View.VISIBLE else View.GONE

        if (ParentEngine.connected) {
            if (pulse == null) pulse = Fx.breathe(statusPill ?: return)
        } else {
            pulse?.cancel()
            pulse = null
            statusPill?.alpha = 1f
        }

        val childIp = pairedCache?.lastIp?.ifBlank { null } ?: "--"
        val ago = (System.currentTimeMillis() - ParentEngine.lastContact) / 1000
        detailLine?.text = t(
            "Child address: $childIp" + if (ParentEngine.connected) "  ·  last contact ${ago}s ago" else "",
            "عنوان الطفل: $childIp" + if (ParentEngine.connected) "  ·  آخر اتصال منذ ${ago} ثانية" else ""
        )
        val pct = ParentEngine.batteryPct
        val tc = ParentEngine.tempC
        batteryTv?.text = if (pct < 0) t("Battery: --", "البطارية: --")
        else t("Battery ", "البطارية ") + pct + "%" +
            (if (ParentEngine.charging) t(" (charging)", " (تشحن)") else "")
        tempTv?.text = if (pct < 0) t("Temperature: --", "الحرارة: --")
        else t("Temperature ", "الحرارة ") + String.format(java.util.Locale.US, "%.1f", tc) + " °C"
        val fresh = ParentEngine.connected && System.currentTimeMillis() - ParentEngine.lastStatusMs < 45_000L
        Fx.animateDot(batteryDot, when {
            !fresh || pct < 0 -> 0xFF8899AA.toInt()
            !ParentEngine.charging && pct < 20 -> 0xFFE53935.toInt()
            !ParentEngine.charging && pct < 40 -> 0xFFFFA726.toInt()
            else -> 0xFF66BB6A.toInt()
        })
        Fx.animateDot(tempDot, when {
            !fresh || pct < 0 -> 0xFF8899AA.toInt()
            tc >= 42.0 -> 0xFFE53935.toInt()
            tc >= 38.0 -> 0xFFFFA726.toInt()
            else -> 0xFF66BB6A.toInt()
        })
        Fx.animateMeter(meter, ParentEngine.level)

        listenBtn?.text = t(if (ParentEngine.listening) "Stop" else "Listen",
            if (ParentEngine.listening) "أوقف" else "استماع") + "\n" +
            t(if (ParentEngine.listening) "Listening to the room" else "Sound only",
                if (ParentEngine.listening) "يستمع للغرفة الآن" else "صوت فقط")
        listenBtn?.background = Look.glass(this, 22,
            if (ParentEngine.listening) Fx.tint(Themes.current().accent, 0x40) else Look.BUTTON)

        settingsHead?.text = (if (settingsOpen) "▾  " else "▸  ") + t("Clip length, sensitivity, volume", "طول المقطع والحساسية والصوت")
        if (!draggingLen) {
            lenLabel?.text = t("Clip length: ${ParentEngine.childLength} seconds", "طول المقطع: ${ParentEngine.childLength} ثوانٍ")
            lenBar?.progress = ParentEngine.childLength - 5
        }
        if (!draggingSens) {
            sensLabel?.text = t("Sensitivity: ${ParentEngine.childSens} of 5", "الحساسية: ${ParentEngine.childSens} من 5")
            sensBar?.progress = ParentEngine.childSens - 1
        }
        intervalLabel?.text = intervalText(ParentEngine.childInterval)
        walkieLabel?.text = t("Talk volume on the child phone: ${ParentEngine.childTalkVol} of 10",
            "صوت المحادثة على جوال الطفل: ${ParentEngine.childTalkVol} من 10")

        if (listBox != null && ParentEngine.clipsVersion != lastClipsVersion) {
            lastClipsVersion = ParentEngine.clipsVersion
            fillList()
        }
    }

    private fun groupOf(ts: Long): String {
        val dayMs = 24L * 60 * 60 * 1000
        val startToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            ts >= startToday -> t("Today", "اليوم")
            ts >= startToday - dayMs -> t("Yesterday", "أمس")
            ts >= startToday - 6 * dayMs -> t("This week", "هذا الأسبوع")
            else -> t("Earlier", "أقدم")
        }
    }

    private fun confirmDelete(title: String, olderThanMs: Long?) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setPositiveButton(t("Delete", "حذف")) { _, _ ->
                stopPlayer()
                val cutoff = if (olderThanMs == null) Long.MAX_VALUE else System.currentTimeMillis() - olderThanMs
                for (c in ParentEngine.clips(this)) {
                    if (c.ts < cutoff) ParentEngine.deleteClip(this, c.ts)
                }
                fillList()
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun chooseOlder() {
        val days = arrayOf(1L, 7L, 30L)
        val labels = arrayOf(
            t("Older than 1 day", "أقدم من يوم"),
            t("Older than 7 days", "أقدم من 7 أيام"),
            t("Older than 30 days", "أقدم من 30 يوماً")
        )
        AlertDialog.Builder(this)
            .setTitle(t("Delete recordings", "حذف التسجيلات"))
            .setItems(labels) { _, which ->
                confirmDelete(labels[which] + "?", days[which] * 24L * 60 * 60 * 1000)
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun fillList() {
        val box = listBox ?: return
        box.removeAllViews()
        val clips = ParentEngine.clips(this)
        lastClipCount = clips.size
        if (clips.isEmpty()) {
            box.card(14) {
                addBody(t("No recordings yet.", "لا توجد تسجيلات بعد."))
                line(t("The child phone sends a clip when it hears sound, and only while this phone is connected.",
                    "جوال الطفل يرسل مقطعاً عند سماع صوت، وفقط عندما يكون هذا الجوال متصلاً."))
            }
            return
        }
        box.addButton(t("Delete older than...", "حذف الأقدم من...")) { chooseOlder() }
        box.addButton(t("Delete all recordings", "حذف كل التسجيلات")) { confirmDelete(t("Delete all recordings?", "حذف كل التسجيلات؟"), null) }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        var lastGroup = ""
        for ((index, c) in clips.withIndex()) {
            val group = groupOf(c.ts)
            if (group != lastGroup) {
                box.sectionLabel(group)
                lastGroup = group
            }
            val card = box.card(12) {
                val row = addRow()
                val info = TextView(context)
                info.text = fmt.format(Date(c.ts)) + "  ·  " + (c.durationMs / 1000) + " s"
                info.textSize = 15f
                info.setTextColor(Look.TEXT)
                row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val playBtn = ImageButton(context)
                playBtn.setImageResource(if (playingTs == c.ts) R.drawable.ic_btn_pause else R.drawable.ic_btn_play)
                playBtn.background = Look.glass(context, 18, Look.BUTTON)
                playBtn.setColorFilter(Look.TEXT)
                playBtn.setPadding(dp(10), dp(10), dp(10), dp(10))
                playBtn.contentDescription = t("Play recording", "تشغيل التسجيل")
                playBtn.setOnClickListener {
                    if (playingTs == c.ts) stopPlayer() else play(c)
                    fillList()
                }
                row.addView(playBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
                val del = ImageButton(context)
                del.setImageResource(R.drawable.ic_btn_delete)
                del.background = Look.glass(context, 18, Look.BUTTON)
                del.setColorFilter(Look.TEXT)
                del.setPadding(dp(10), dp(10), dp(10), dp(10))
                del.contentDescription = t("Delete recording", "حذف التسجيل")
                del.setOnClickListener {
                    if (playingTs == c.ts) stopPlayer()
                    ParentEngine.deleteClip(this@ParentActivity, c.ts)
                    fillList()
                }
                row.addView(del, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
                if (playingTs == c.ts) {
                    val wv = WaveView(context)
                    wv.setBars(waveBars)
                    wv.playing = true
                    waveView = wv
                    addView(wv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                        topMargin = dp(8)
                    })
                }
            }
            if (!entered) Fx.enter(card, index.coerceAtMost(6))
        }
    }

    private fun play(c: ParentEngine.ClipItem) {
        stopPlayer()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(ClipCrypt.playablePath(this, c.file))
            mp.prepare()
            mp.setOnCompletionListener {
                stopPlayer()
                fillList()
            }
            waveBars = Wave.envelope(c.file)
            mp.start()
            player = mp
            playingTs = c.ts
            ui.removeCallbacks(waveTick)
            ui.post(waveTick)
        } catch (e: Exception) {
            Diag.e("Could not play clip", e)
            toast(t("Could not play this recording.", "تعذّر تشغيل هذا التسجيل."))
        }
    }

    private fun stopPlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingTs = 0L
        waveView = null
        ui.removeCallbacks(waveTick)
    }

    private fun toggleListen() {
        if (ParentEngine.listening) {
            ParentEngine.endListen()
        } else if (!ParentEngine.beginListen(this)) {
            toast(ParentEngine.listenNote)
        }
    }

    private fun startPairFlow() {
        toast(t("Searching for the child phone...", "جارٍ البحث عن جوال الطفل..."))
        Thread {
            val found = Discovery.scan(applicationContext)
            Diag.i("Scan from ${Net.wifiIp(this) ?: "no Wi-Fi"}: found ${found.size} child phone(s)")
            runOnUiThread { if (found.isEmpty()) showNotFound() else showPicker(found) }
        }.start()
    }

    private fun showNotFound() {
        val own = Net.wifiIp(this) ?: "--"
        AlertDialog.Builder(this)
            .setTitle(t("Child phone not found", "لم نجد جوال الطفل"))
            .setMessage(t(
                "This phone: $own\n\nCheck:\n- The child phone is monitoring and on the same Wi-Fi.\n- The router is not in Guest mode.\n- 'AP isolation' or 'Client isolation' is OFF in the router. If it is ON, phones on the same Wi-Fi cannot see each other.\n- The VPN is off on both phones.",
                "هذا الجوال: $own\n\nتأكد من:\n- جوال الطفل شغّال على نفس الواي فاي.\n- الراوتر ليس في وضع الضيوف (Guest).\n- خاصية 'AP isolation' أو 'Client isolation' مطفية في الراوتر. إذا كانت شغّالة، الجوالات على نفس الواي فاي ما تشوف بعض.\n- الـ VPN مطفي على الجوالين."
            ))
            .setPositiveButton(t("Type the address", "اكتب العنوان")) { _, _ -> askIp() }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun showPicker(found: List<Discovery.Found>) {
        val items = ArrayList<String>()
        for (f in found) items.add(f.name + " (" + f.ip + ")")
        items.add(t("Type an IP address...", "اكتب عنوان IP..."))
        AlertDialog.Builder(this)
            .setTitle(if (found.isEmpty()) t("Child phone not found automatically", "لم نجد جوال الطفل تلقائياً")
            else t("Choose the child phone", "اختر جوال الطفل"))
            .setItems(items.toTypedArray()) { _, which ->
                if (which < found.size) pair(found[which].ip) else askIp()
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun askIp() {
        val own = Net.wifiIp(this) ?: ""
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = t("For example 192.168.1.101", "مثال: 192.168.1.101")
        if (own.contains('.')) {
            input.setText(own.substringBeforeLast('.') + ".")
            input.setSelection(input.text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(t("IP address shown on the child phone", "عنوان IP الظاهر على جوال الطفل"))
            .setView(input)
            .setPositiveButton(t("Pair", "اقرن")) { _, _ ->
                checkAndPair(input.text.toString().trim(), own)
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun checkAndPair(ip: String, own: String) {
        if (!Ip.isValid(ip)) {
            toast(t("That is not a valid address. It looks like 192.168.1.101.", "هذا العنوان غير صحيح. مثال: 192.168.1.101"))
            askIp()
            return
        }
        if (own.isNotEmpty() && !Ip.sameSubnet24(ip, own)) {
            AlertDialog.Builder(this)
                .setTitle(t("Different network?", "شبكة مختلفة؟"))
                .setMessage(t("This phone is on $own but you typed $ip. Both phones must start with the same first three numbers.",
                    "هذا الجوال على $own وكتبت $ip. لازم الجوالين يبدأون بنفس الأرقام الثلاثة الأولى."))
                .setPositiveButton(t("Try anyway", "جرّب على أي حال")) { _, _ -> pair(ip) }
                .setNegativeButton(t("Fix it", "صحّحه")) { _, _ -> askIp() }
                .show()
            return
        }
        pair(ip)
    }

    private fun pair(ip: String) {
        toast(t("Connecting...", "جارٍ الاتصال..."))
        ParentEngine.pair(this, ip, object : ParentEngine.PairListener {
            override fun onSas(sas: String, childName: String, decide: (Boolean) -> Unit) {
                runOnUiThread {
                    AlertDialog.Builder(this@ParentActivity)
                        .setTitle(t("Check the code", "تحقق من الرمز"))
                        .setMessage(t("The child phone ($childName) must show the same number:\n\n$sas\n\nIs it the same?",
                            "جوال الطفل ($childName) لازم يُظهر نفس الرقم:\n\n$sas\n\nهل هو نفسه؟"))
                        .setCancelable(false)
                        .setPositiveButton(t("Yes, same", "نعم، نفسه")) { _, _ -> decide(true) }
                        .setNegativeButton(t("No", "لا")) { _, _ -> decide(false) }
                        .show()
                }
            }

            override fun onResult(ok: Boolean, message: String) {
                runOnUiThread {
                    toast(message)
                    if (ok) {
                        build()
                        UnitService.start(this@ParentActivity)
                    }
                }
            }
        })
    }
}
