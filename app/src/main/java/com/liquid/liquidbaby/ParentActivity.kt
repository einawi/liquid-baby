package com.liquid.liquidbaby

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Parent screen: the home part is Compose; recordings and settings are the classic views shown inside it. */
class ParentActivity : ComponentActivity() {

    private val ui = Handler(Looper.getMainLooper())
    private var builtAr: Boolean? = null
    private var pairedCache: PairStore.Paired? = null
    private var lastClipsVersion = -1
    private var entered = false
    private var pulse: ObjectAnimator? = null

    private val state = ComposeParentHomeState()

    private var meter: ProgressBar? = null
    private var soundVisual: SoundVisual? = null
    private var batteryRing: BatteryRing? = null
    private var listenBtn: Button? = null
    private var talkBtn: Button? = null
    private var talkModeLabel: TextView? = null
    private var talkControl: TalkControl? = null
    private var partnerTalk: PartnerTalkControl? = null
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

    /** Classic views shown inside the Compose home. Each factory stores its view so refresh() can update it. */
    private val views = ParentViews(
        ring = { ctx -> BatteryRing(ctx).also { batteryRing = it } },
        sound = { ctx -> SoundVisual(ctx).also { soundVisual = it } },
        meter = { ctx ->
            ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = ParentEngine.level
            }.also { meter = it }
        },
        talk = { ctx -> TalkControl(this@ParentActivity).also { talkControl = it }.view },
        partner = { ctx -> PartnerTalkControl(this@ParentActivity).also { partnerTalk = it }.view }
    )

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        builtAr = Tr.isAr()
        applyWindowLook()
        syncPaired()
        setContent { ParentRoot() }
        lastClipsVersion = ParentEngine.clipsVersion
    }

    /** Background and blur, the same look the classic screen had. */
    private fun applyWindowLook() {
        val nightBits = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        Themes.systemDark = nightBits == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val blur = Build.VERSION.SDK_INT >= 31 && Themes.current().blur > 0
        window.setBackgroundDrawable(Look.background(if (blur) 210 else 255))
        if (blur) {
            try {
                window.setBackgroundBlurRadius(Themes.current().blur)
            } catch (e: Throwable) {
                Diag.i("Window blur not available: " + e.message)
            }
        }
    }

    /** Reads the pairing from storage and updates the Compose state. Called at start and after pairing changes. */
    private fun syncPaired() {
        val p = PairStore.paired()
        pairedCache = p
        state.paired = p != null
        state.keysBroken = p != null && p.key.isEmpty()
        state.childName = p?.childName?.ifBlank { null } ?: t("Child phone", "جوال الطفل")
        refresh()
    }

    @Composable
    private fun ParentRoot() {
        val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParentHomeTop(
                    state = state,
                    views = views,
                    onToggle = { toggle() },
                    onVpn = { openVpnSettings() },
                    onLive = { openLive() },
                    onListen = { toggleListen() },
                    onPair = { startPairFlow() }
                )
                if (state.paired) {
                    AndroidView(
                        factory = { ctx -> buildLower(ctx) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    private fun openLive() {
        if (ParentEngine.connected) {
            startActivity(Intent(this, CameraActivity::class.java))
        } else {
            toast(t("Not connected to the child phone yet.", "غير متصل بجوال الطفل بعد."))
        }
    }

    /** Recordings, settings and the remove button. These are classic views, hosted inside Compose. */
    private fun buildLower(ctx: Context): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (Tr.isAr()) col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(0, dp(4), 0, dp(4))

        col.sectionLabel(t("Recordings", "التسجيلات"))
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        col.addView(box)
        listBox = box

        col.sectionLabel(t("Settings", "الإعدادات"))
        settingsHead = TextView(ctx).apply {
            textSize = 16f
            setTextColor(Look.TEXT)
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setOnClickListener { toggleSettings() }
        }
        col.addView(settingsHead)
        val sBox = LinearLayout(ctx)
        sBox.orientation = LinearLayout.VERTICAL
        sBox.visibility = if (settingsOpen) View.VISIBLE else View.GONE
        col.addView(sBox)
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

        val remove = Button(ctx).apply {
            text = t("Remove pairing", "إلغاء الاقتران")
            isAllCaps = false
            setTextColor(0xFFFF8A8A.toInt())
            background = Look.glass(ctx, 22, 0x22FF5252)
            setOnClickListener { confirmRemove() }
            Fx.press(this)
        }
        col.addView(remove, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(28)
        })

        fillList()
        refresh()
        return col
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
        Dialogs.show(
            this,
            t("Remove pairing with the child phone?", "هل تريد إلغاء الاقتران مع جوال الطفل؟"),
            null,
            listOf(
                t("Remove", "إلغاء الاقتران") to {
                    UnitService.stop(this)
                    PairStore.clearPaired()
                    syncPaired()
                },
                t("Cancel", "تراجع") to {}
            )
        )
    }

    private val partnerHandler = Handler(Looper.getMainLooper())
    private val partnerTick = object : Runnable {
        override fun run() {
            partnerTalk?.setSending(ParentEngine.partnerSending)
            partnerTalk?.setState(connected = ParentEngine.connected && ParentEngine.partnerConnected, talking = ParentEngine.partnerTalking || ParentEngine.partnerSpeaking)
            partnerHandler.postDelayed(this, 1000L)
        }
    }

    override fun onResume() {
        super.onResume()
        partnerHandler.removeCallbacks(partnerTick)
        partnerHandler.post(partnerTick)
        if (builtAr != Tr.isAr()) {
            recreate()
            return
        }
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
        partnerHandler.removeCallbacks(partnerTick)
        super.onPause()
        ParentEngine.onChange = null
        ui.removeCallbacks(tick)
        talkControl?.up()
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
    private var lastConnectedMs = 0L

    /** Four states: Stopped, Connecting (first attempt), Connected, Disconnected (lost, retrying). */
    private fun stateLabel(): Pair<String, Int> {
        val now = System.currentTimeMillis()
        if (!ParentEngine.running) {
            lastConnectedMs = 0L
            return Pair(t("Stopped", "متوقف"), 0xFF6B7785.toInt())
        }
        if (ParentEngine.connected) {
            lastConnectedMs = now
            return Pair(t("Connected", "متصل"), 0xFF2E9E6A.toInt())
        }
        if (lastConnectedMs > 0L) {
            val secs = (now - lastConnectedMs) / 1000
            return Pair(t("Disconnected, retrying (${secs}s)", "انقطع الاتصال، نحاول مرة أخرى (${secs} ثانية)"), 0xFFC0392B.toInt())
        }
        return Pair(t("Connecting...", "جارٍ الاتصال..."), 0xFFB7791F.toInt())
    }

    /** Writes the current values into the Compose state and the classic views. Runs every second. */
    private fun refresh() {
        val (label, color) = stateLabel()
        state.status = label
        state.statusColor = color
        state.running = ParentEngine.running
        state.vpnWarning = Net.vpnBlocksLocal(this)

        val childIp = pairedCache?.lastIp?.ifBlank { null } ?: "--"
        val ago = (System.currentTimeMillis() - ParentEngine.lastContact) / 1000
        state.address = t(
            "Child address: $childIp" + if (ParentEngine.connected) "  ·  last contact ${ago}s ago" else "",
            "عنوان الطفل: $childIp" + if (ParentEngine.connected) "  ·  آخر اتصال منذ ${ago} ثانية" else ""
        )
        val pct = ParentEngine.batteryPct
        val tc = ParentEngine.tempC
        state.battery = if (pct < 0) t("Battery: --", "البطارية: --")
        else t("Battery ", "البطارية ") + pct + "%" +
            (if (ParentEngine.charging) t(" (charging)", " (تشحن)") else "")
        state.temp = if (pct < 0) t("Temperature: --", "الحرارة: --")
        else t("Temperature ", "الحرارة ") + String.format(java.util.Locale.US, "%.1f", tc) + " °C"

        val fresh = ParentEngine.connected && System.currentTimeMillis() - ParentEngine.lastStatusMs < 45_000L
        state.batteryDot = when {
            !fresh || pct < 0 -> 0xFF8899AA.toInt()
            !ParentEngine.charging && pct < 20 -> 0xFFE53935.toInt()
            !ParentEngine.charging && pct < 40 -> 0xFFFFA726.toInt()
            else -> 0xFF66BB6A.toInt()
        }
        state.tempDot = when {
            !fresh || pct < 0 -> 0xFF8899AA.toInt()
            tc >= 42.0 -> 0xFFE53935.toInt()
            tc >= 38.0 -> 0xFFFFA726.toInt()
            else -> 0xFF66BB6A.toInt()
        }

        styleLevelMeter(meter)
        partnerTalk?.setState(connected = ParentEngine.connected && ParentEngine.partnerConnected, talking = ParentEngine.partnerTalking || ParentEngine.partnerSpeaking)
        val levelIsFresh = ParentEngine.connected && System.currentTimeMillis() - ParentEngine.levelAt < 2500L
        Fx.animateMeter(meter, if (levelIsFresh) ParentEngine.level else 0)
        soundVisual?.setStyle(Prefs.getString("soundVisual", "wave"))
        soundVisual?.setLevel(if (levelIsFresh) ParentEngine.level else 0)
        batteryRing?.setPercent(ParentEngine.batteryPct)

        state.listening = ParentEngine.listening

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
        Dialogs.show(
            this,
            title,
            null,
            listOf(
                t("Delete", "حذف") to {
                    stopPlayer()
                    val cutoff = if (olderThanMs == null) Long.MAX_VALUE else System.currentTimeMillis() - olderThanMs
                    for (c in ParentEngine.clips(this)) {
                        if (c.ts < cutoff) ParentEngine.deleteClip(this, c.ts)
                    }
                    fillList()
                },
                t("Cancel", "تراجع") to {}
            )
        )
    }

    private fun chooseOlder() {
        val days = arrayOf(1L, 7L, 30L)
        val labels = arrayOf(
            t("Older than 1 day", "أقدم من يوم"),
            t("Older than 7 days", "أقدم من 7 أيام"),
            t("Older than 30 days", "أقدم من 30 يوماً")
        )
        Dialogs.show(
            this,
            t("Delete recordings", "حذف التسجيلات"),
            null,
            labels.mapIndexed { idx, label ->
                label to { confirmDelete(label + "?", days[idx] * 24L * 60 * 60 * 1000) }
            } + listOf(t("Cancel", "تراجع") to {})
        )
    }

    private fun fillList() {
        val box = listBox ?: return
        box.removeAllViews()
        val clips = ParentEngine.clips(this)
        lastClipCount = clips.size
        val videos = videoFiles()
        if (clips.isEmpty() && videos.isEmpty()) {
            box.card(14) {
                addBody(t("No recordings yet.", "لا توجد تسجيلات بعد."))
                line(t("The child phone sends a clip when it hears sound, and only while this phone is connected.",
                    "جوال الطفل يرسل مقطعاً عند سماع صوت، وفقط عندما يكون هذا الجوال متصلاً."))
            }
            return
        }
        box.addButton(t("Delete older than...", "حذف الأقدم من...")) { chooseOlder() }.apply {
            textSize = 14f
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        box.addButton(t("Delete all recordings", "حذف كل التسجيلات")) { confirmDelete(t("Delete all recordings?", "حذف كل التسجيلات؟"), null) }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        var lastGroup = ""
        for ((index, c) in clips.withIndex()) {
            val group = groupOf(c.ts)
            if (group != lastGroup) {
                box.sectionLabel(group)
                lastGroup = group
            }
            val card = box.card(20) {
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
                row.addView(playBtn, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(12) })
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
                row.addView(del, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(12) })
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
        Fx.feedbackAll(box)
        if (videos.isNotEmpty()) {
            box.sectionLabel(t("Videos (encrypted)", "الفيديوهات (مشفّرة)"))
            for (f in videos) {
                val card = box.card(20) {
                    val row = addRow()
                    val info = TextView(context)
                    val kb = f.length() / 1024.0
                    val size = if (kb < 1024) String.format(java.util.Locale.US, "%.0f KB", kb) else String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
                    info.text = fmt.format(Date(f.lastModified())) + "  ·  " + size
                    info.textSize = 15f
                    info.setTextColor(Look.TEXT)
                    row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    val playBtn = ImageButton(context)
                    playBtn.setImageResource(R.drawable.ic_btn_play)
                    playBtn.background = Look.glass(context, 18, Look.BUTTON)
                    playBtn.setColorFilter(Look.TEXT)
                    playBtn.setPadding(dp(10), dp(10), dp(10), dp(10))
                    playBtn.contentDescription = t("Play video", "تشغيل الفيديو")
                    playBtn.setOnClickListener {
                        startActivity(Intent(this@ParentActivity, VideoPlayerActivity::class.java).putExtra("path", f.absolutePath))
                    }
                    row.addView(playBtn, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(12) })
                    val del = ImageButton(context)
                    del.setImageResource(R.drawable.ic_btn_delete)
                    del.background = Look.glass(context, 18, Look.BUTTON)
                    del.setColorFilter(Look.TEXT)
                    del.setPadding(dp(10), dp(10), dp(10), dp(10))
                    del.contentDescription = t("Delete video", "حذف الفيديو")
                    del.setOnClickListener {
                        Dialogs.show(
                            this@ParentActivity,
                            t("Delete this video?", "حذف هذا الفيديو؟"),
                            null,
                            listOf(
                                t("Delete", "حذف") to {
                                    f.delete()
                                    fillList()
                                },
                                t("Cancel", "تراجع") to {}
                            )
                        )
                    }
                    row.addView(del, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(12) })
                    val share = ImageButton(context)
                    share.setImageResource(R.drawable.ic_share)
                    share.background = Look.glass(context, 18, Look.BUTTON)
                    share.setColorFilter(Look.TEXT)
                    share.setPadding(dp(10), dp(10), dp(10), dp(10))
                    share.contentDescription = t("Share or save video", "مشاركة أو حفظ الفيديو")
                    share.setOnClickListener { chooseExport(f) }
                    row.addView(share, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(12) })
                }
                Fx.enter(card, 0)
            }
        }
    }

    private fun chooseExport(f: File) {
        Dialogs.show(
            this,
            t("Video", "الفيديو"),
            null,
            listOf(
                t("Share", "مشاركة") to { exportVideo(f, share = true) },
                t("Save to the gallery (Movies/LiquidBaby)", "حفظ في المعرض (Movies/LiquidBaby)") to { exportVideo(f, share = false) }
            )
        )
    }

    /** Decrypts the video and writes a normal MP4 to Movies/LiquidBaby (Android 10 and newer), then optionally shares it. */
    private fun exportVideo(f: File, share: Boolean) {
        VideoExport.run(this, f, share)
    }

    /** Encrypted videos saved by the recorder, newest first. */
    private fun videoFiles(): List<File> =
        File(filesDir, "videos").listFiles { f -> f.name.endsWith(".lbv") }?.sortedByDescending { it.lastModified() } ?: emptyList()

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
            val broadcast = Discovery.scan(applicationContext)
            Diag.i("Scan from ${Net.wifiIp(this) ?: "no Wi-Fi"}: found ${broadcast.size} child phone(s)")
            val announced = Nsd.findAll(applicationContext)
            Diag.i("Network announcement: found ${announced.size} child phone(s)" + announced.joinToString { " at " + it.second })
            val found = ArrayList(broadcast)
            for ((name, ip) in announced) {
                if (found.none { it.ip == ip }) found.add(Discovery.Found(name.removePrefix("lb"), "Child phone", ip))
            }
            runOnUiThread { if (found.isEmpty()) showNotFound() else showPicker(found) }
        }.start()
    }

    private fun showNotFound() {
        val own = Net.wifiIp(this) ?: "--"
        Dialogs.show(
            this,
            t("Child phone not found", "لم نجد جوال الطفل"),
            t(
                "This phone: $own\n\nCheck:\n- The child phone is monitoring and on the same Wi-Fi.\n- The router is not in Guest mode.\n- 'AP isolation' or 'Client isolation' is OFF in the router. If it is ON, phones on the same Wi-Fi cannot see each other.\n- The VPN is off on both phones.",
                "هذا الجوال: $own\n\nتأكد من:\n- جوال الطفل شغّال على نفس الواي فاي.\n- الراوتر ليس في وضع الضيوف (Guest).\n- خاصية 'AP isolation' أو 'Client isolation' مطفية في الراوتر. إذا كانت شغّالة، الجوالات على نفس الواي فاي ما تشوف بعض.\n- الـ VPN مطفي على الجوالين."
            ),
            listOf(
                t("Type the address", "اكتب العنوان") to { askIp() },
                t("Cancel", "تراجع") to {}
            )
        )
    }

    private fun showPicker(found: List<Discovery.Found>) {
        val items = ArrayList<String>()
        for (f in found) items.add(f.name + " (" + f.ip + ")")
        items.add(t("Type an IP address...", "اكتب عنوان IP..."))
        AlertDialog.Builder(this)
            .setTitle(if (found.isEmpty()) t("Child phone not found automatically", "لم نجد جوال الطفل تلقائياً")
            else t("Choose the child phone", "اختر جوال الطفل"))
            .setAdapter(object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent) as android.widget.TextView
                    v.minHeight = dp(60)
                    v.setPadding(dp(20), dp(14), dp(20), dp(14))
                    v.textSize = 16f
                    return v
                }
            }) { _, which ->
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
            Dialogs.show(
                this,
                t("Different network?", "شبكة مختلفة؟"),
                t("This phone is on $own but you typed $ip. Both phones must start with the same first three numbers.",
                  "هذا الجوال على $own وكتبت $ip. لازم الجوالين يبدأون بنفس الأرقام الثلاثة الأولى."),
                listOf(
                    t("Try anyway", "جرّب على أي حال") to { pair(ip) },
                    t("Fix it", "صحّحه") to { askIp() }
                )
            )
            return
        }
        pair(ip)
    }

    private fun pair(ip: String) {
        toast(t("Connecting...", "جارٍ الاتصال..."))
        ParentEngine.pair(this, ip, object : ParentEngine.PairListener {
            override fun onSas(sas: String, childName: String, decide: (Boolean) -> Unit) {
                runOnUiThread {
                    Dialogs.show(
                        this@ParentActivity,
                        t("Check the code", "تحقق من الرمز"),
                        t("The child phone ($childName) must show the same number:\n\n$sas\n\nIs it the same?",
                          "جوال الطفل ($childName) لازم يُظهر نفس الرقم:\n\n$sas\n\nهل هو نفسه؟"),
                        listOf(
                            t("Yes, same", "نعم، نفسه") to { decide(true) },
                            t("No", "لا") to { decide(false) }
                        ),
                        cancelable = false
                    )
                }
            }

            override fun onResult(ok: Boolean, message: String) {
                runOnUiThread {
                    toast(message)
                    if (ok) {
                        syncPaired()
                        UnitService.start(this@ParentActivity)
                    }
                }
            }
        })
    }
}
