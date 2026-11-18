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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Parent screen: the home part is Compose; recordings and settings are the classic views shown inside it. */
class ParentActivity : ComponentActivity(), MicHost {

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
    )

    private val mic = MicAsk(this)
    override fun askMic(onGranted: () -> Unit) = mic.askMic(onGranted)

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        builtAr = Tr.isAr()
        state.pressMode = Prefs.getString("talkMode", "hold") == "press"
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
                    onPair = { startPairFlow() },
                    onChildHoldStart = { parentTalkDown() },
                    onChildHoldEnd = { parentTalkUp() },
                    onChildTap = { parentTalkTap() },
                    onPartnerStart = { partnerStart() },
                    onPartnerEnd = { ParentEngine.endPartnerTalk() },
                    onPartnerTap = { if (ParentEngine.partnerSending) ParentEngine.endPartnerTalk() else partnerStart() },
                    onTalkMode = { applyTalkMode(it) }
                )
                if (state.paired) {
                    ParentLowerSection()
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
            syncPartnerState()
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
        parentTalkUp()
        ParentEngine.endListen()
        ParentEngine.setLevelSub(false)
        stopPlayer()
    }

    /** The child talk button: hold or tap, 60 seconds in tap mode, the same rules as before. */
    private val talkLimit = Runnable {
        parentTalkUp()
        toast(t("Talk stopped after 60 seconds.", "توقف الحديث بعد 60 ثانية."))
    }

    private fun parentTalkDown() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMic { parentTalkDown() }
            return
        }
        if (ParentEngine.beginTalk()) {
            state.talking = true
            if (state.pressMode) ui.postDelayed(talkLimit, 60_000L)
        } else {
            toast(ParentEngine.talkNote)
        }
    }

    private fun parentTalkUp() {
        ui.removeCallbacks(talkLimit)
        ParentEngine.endTalk()
        state.talking = false
    }

    private fun parentTalkTap() {
        if (ParentEngine.talking) parentTalkUp() else parentTalkDown()
    }

    /** The partner button starts talking only once the microphone is allowed. */
    private fun partnerStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMic { partnerStart() }
            return
        }
        ParentEngine.beginPartnerTalk()
    }

    private fun applyTalkMode(press: Boolean) {
        Diag.i("Talk mode changed to " + (if (press) "tap" else "hold"))
        Prefs.putString("talkMode", if (press) "press" else "hold")
        if (ParentEngine.talking) parentTalkUp()
        state.pressMode = press
    }

    /** Copies the partner link into the Compose state. Called every second. */
    private fun syncPartnerState() {
        state.partnerConnected = ParentEngine.connected && ParentEngine.partnerConnected
        state.partnerTalking = ParentEngine.partnerTalking || ParentEngine.partnerSpeaking
        state.partnerSending = ParentEngine.partnerSending
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
        syncPartnerState()
        val levelIsFresh = ParentEngine.connected && System.currentTimeMillis() - ParentEngine.levelAt < 2500L
        Fx.animateMeter(meter, if (levelIsFresh) ParentEngine.level else 0)
        soundVisual?.setStyle(Prefs.getString("soundVisual", "wave"))
        soundVisual?.setLevel(if (levelIsFresh) ParentEngine.level else 0)
        batteryRing?.setPercent(ParentEngine.batteryPct)

        state.listening = ParentEngine.listening

        if (ParentEngine.clipsVersion != lastClipsVersion) {
            lastClipsVersion = ParentEngine.clipsVersion
            lowerTick++
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
                    lowerTick++
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
                lowerTick++
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

    private var lowerTick by mutableStateOf(0)

    private fun intervalText(v: Int): String =
        if (v == 0) t("Listening: continuously", "الاستماع: متواصل")
        else t("Checks every $v seconds (listens 5 s each time)", "يفحص كل $v ثوانٍ (يستمع 5 ثوانٍ في كل مرة)")

    @Composable
    private fun SectionLabel(label: String, color: Color) {
        Text(label, fontSize = 13.sp, color = color, modifier = Modifier.padding(top = 4.dp))
    }

    @Composable
    private fun Panel(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(Look.BUTTON), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }

    @Composable
    private fun IconAction(icon: Int, desc: String, tint: Color, onClick: () -> Unit) {
        IconButton(onClick = onClick, modifier = Modifier.size(46.dp)) {
            Icon(painter = painterResource(icon), contentDescription = desc, tint = tint, modifier = Modifier.size(22.dp))
        }
    }

    @Composable
    private fun SettingSlider(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        accent: Color,
        text: Color,
        onChange: (Float) -> Unit,
        onDone: () -> Unit
    ) {
        Text(label, color = text, fontSize = 14.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
    }

    @Composable
    private fun ParentLowerSection() {
        val ticket = lowerTick
        val text = Color(Look.TEXT)
        val sub = Color(Look.SUB)
        val accent = Color(Themes.current().accent)
        val glass = RoundedCornerShape(18.dp)
        val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
        val dangerColors = ButtonDefaults.buttonColors(containerColor = Color(0x22FF5252), contentColor = Color(0xFFFF8A8A))
        val clips = remember(ticket) { ParentEngine.clips(this@ParentActivity) }
        val videos = remember(ticket) { videoFiles() }
        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
        var settingsOpen by remember { mutableStateOf(false) }
        var len by remember { mutableStateOf(ParentEngine.childLength.toFloat()) }
        var sens by remember { mutableStateOf(ParentEngine.childSens.toFloat()) }
        var interval by remember { mutableStateOf(ParentEngine.childInterval.toFloat()) }
        var talkVol by remember { mutableStateOf(ParentEngine.childTalkVol.toFloat()) }
        val playing = playingTs

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t("Recordings", "التسجيلات"), sub)
            if (clips.isEmpty() && videos.isEmpty()) {
                Panel {
                    Text(t("No recordings yet.", "لا توجد تسجيلات بعد."), color = text, fontSize = 15.sp)
                    Text(
                        t("The child phone sends a clip when it hears sound, and only while this phone is connected.",
                            "جوال الطفل يرسل مقطعاً عند سماع صوت، وفقط عندما يكون هذا الجوال متصلاً."),
                        color = sub,
                        fontSize = 13.sp
                    )
                }
            } else {
                if (clips.isNotEmpty()) {
                    androidx.compose.material3.Button(
                        onClick = { chooseOlder() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = glass,
                        colors = glassColors
                    ) { Text(t("Delete older than...", "حذف الأقدم من..."), fontSize = 14.sp) }
                    androidx.compose.material3.Button(
                        onClick = { confirmDelete(t("Delete all recordings?", "حذف كل التسجيلات؟"), null) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = glass,
                        colors = glassColors
                    ) { Text(t("Delete all recordings", "حذف كل التسجيلات"), fontSize = 14.sp) }
                }
                var lastGroup = ""
                clips.forEach { c ->
                    val group = groupOf(c.ts)
                    if (group != lastGroup) {
                        SectionLabel(group, sub)
                        lastGroup = group
                    }
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                fmt.format(Date(c.ts)) + "  ·  " + (c.durationMs / 1000) + " s",
                                color = text,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconAction(
                                if (playing == c.ts) R.drawable.ic_btn_pause else R.drawable.ic_btn_play,
                                t("Play recording", "تشغيل التسجيل"),
                                text
                            ) {
                                if (playingTs == c.ts) stopPlayer() else play(c)
                                lowerTick++
                            }
                            IconAction(R.drawable.ic_btn_delete, t("Delete recording", "حذف التسجيل"), text) {
                                if (playingTs == c.ts) stopPlayer()
                                ParentEngine.deleteClip(this@ParentActivity, c.ts)
                                lowerTick++
                            }
                        }
                        if (playing == c.ts) {
                            AndroidView(
                                factory = { ctx ->
                                    WaveView(ctx).also {
                                        waveView = it
                                        it.setBars(waveBars)
                                        it.playing = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(34.dp)
                            )
                        }
                    }
                }
                if (videos.isNotEmpty()) {
                    SectionLabel(t("Videos (encrypted)", "الفيديوهات (مشفّرة)"), sub)
                    videos.forEach { f ->
                        val kb = f.length() / 1024.0
                        val size = if (kb < 1024) String.format(java.util.Locale.US, "%.0f KB", kb)
                        else String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    fmt.format(Date(f.lastModified())) + "  ·  " + size,
                                    color = text,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconAction(R.drawable.ic_btn_play, t("Play video", "تشغيل الفيديو"), text) {
                                    startActivity(Intent(this@ParentActivity, VideoPlayerActivity::class.java).putExtra("path", f.absolutePath))
                                }
                                IconAction(R.drawable.ic_share, t("Share or save video", "مشاركة أو حفظ الفيديو"), text) {
                                    chooseExport(f)
                                }
                                IconAction(R.drawable.ic_btn_delete, t("Delete video", "حذف الفيديو"), text) {
                                    Dialogs.show(
                                        this@ParentActivity,
                                        t("Delete this video?", "حذف هذا الفيديو؟"),
                                        null,
                                        listOf(
                                            t("Delete", "حذف") to {
                                                f.delete()
                                                lowerTick++
                                            },
                                            t("Cancel", "تراجع") to {}
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionLabel(t("Settings", "الإعدادات"), sub)
            androidx.compose.material3.Button(
                onClick = { settingsOpen = !settingsOpen },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = glass,
                colors = glassColors
            ) {
                Text(
                    (if (settingsOpen) "▾  " else "▸  ") + t("Clip length, sensitivity, volume", "طول المقطع والحساسية والصوت"),
                    fontSize = 15.sp
                )
            }
            if (settingsOpen) {
                Panel {
                    SettingSlider(
                        label = t("Clip length: ${len.roundToInt()} seconds", "طول المقطع: ${len.roundToInt()} ثوانٍ"),
                        value = len, range = 5f..60f, steps = 54, accent = accent, text = text,
                        onChange = { len = it },
                        onDone = {
                            val v = len.roundToInt()
                            Prefs.putInt("clipSeconds", v)
                            ParentEngine.childLength = v
                            ParentEngine.sendSettings(v, ParentEngine.childSens)
                        }
                    )
                    SettingSlider(
                        label = t("Sensitivity: ${sens.roundToInt()} of 5", "الحساسية: ${sens.roundToInt()} من 5"),
                        value = sens, range = 1f..5f, steps = 3, accent = accent, text = text,
                        onChange = { sens = it },
                        onDone = {
                            val v = sens.roundToInt()
                            ParentEngine.childSens = v
                            ParentEngine.sendSettings(ParentEngine.childLength, v)
                        }
                    )
                    SettingSlider(
                        label = intervalText(interval.roundToInt()),
                        value = interval, range = 0f..15f, steps = 14, accent = accent, text = text,
                        onChange = { interval = it },
                        onDone = { ParentEngine.setInterval(interval.roundToInt()) }
                    )
                    SettingSlider(
                        label = t("Talk volume on the child phone: ${talkVol.roundToInt()} of 10",
                            "صوت المحادثة على جوال الطفل: ${talkVol.roundToInt()} من 10"),
                        value = talkVol, range = 1f..10f, steps = 8, accent = accent, text = text,
                        onChange = { talkVol = it },
                        onDone = { ParentEngine.setTalkVolume(talkVol.roundToInt()) }
                    )
                }
            }

            androidx.compose.material3.Button(
                onClick = { confirmRemove() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = glass,
                colors = dangerColors
            ) { Text(t("Remove pairing", "إلغاء الاقتران"), fontSize = 15.sp) }
        }
    }
}
