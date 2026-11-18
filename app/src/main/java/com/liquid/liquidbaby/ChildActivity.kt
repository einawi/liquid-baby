package com.liquid.liquidbaby

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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

/** Child screen: the status card, the monitoring switch and the parent phones it serves. Compose hosts the layout. */
class ChildActivity : ComponentActivity() {

    private val ui = Handler(Looper.getMainLooper())
    private var builtAr: Boolean? = null
    private var dialogShown = false
    private var dark: View? = null
    private var lastParentCount = -1
    private var entered = false

    private val state = ChildHomeState()
    private var meter: ProgressBar? = null
    private var soundVisual: SoundVisual? = null
    private var listBox: LinearLayout? = null

    /** Permission answers arrive here; ComponentActivity no longer delivers onRequestPermissionsResult. */
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionsAnswered()
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1000)
        }
    }

    /** Classic views shown inside the Compose home. Each factory stores its view so refresh() can update it. */
    private val views = ChildViews(
        meter = { ctx ->
            ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = View.GONE
            }.also { meter = it }
        },
        sound = { ctx -> SoundVisual(ctx).also { soundVisual = it } },
        parents = { ctx -> buildParentsBox(ctx) }
    )

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t0 = System.nanoTime()
        builtAr = Tr.isAr()
        setContent { ChildRoot() }
        entered = true
        Diag.limited("buildChild", "Child screen built in ${(System.nanoTime() - t0) / 1_000_000} ms", 60_000L)
    }

    @Composable
    private fun ChildRoot() {
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
                ChildHomeTop(
                    state = state,
                    views = views,
                    onMain = { toggle() },
                    onVpn = { openVpnSettings() },
                    onCam = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
                    onPair = { allowPairing() },
                    onDark = { showDark() },
                    onBattery = { openBatterySettings() }
                )
            }
        }
    }

    /** The paired parent phones list, built as classic views. */
    private fun buildParentsBox(ctx: Context): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        if (Tr.isAr()) box.layoutDirection = View.LAYOUT_DIRECTION_RTL
        listBox = box
        fillParents()
        return box
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            toast(t("Turn battery optimization off for Liquid Baby in phone settings.",
                "أوقف تحسين البطارية لتطبيق Liquid Baby من إعدادات الجوال."))
        }
    }

    override fun onResume() {
        super.onResume()
        overridePendingTransition(R.anim.screen_in, R.anim.screen_out)
        if (builtAr != Tr.isAr()) {
            recreate()
            return
        }
        ChildEngine.onChange = { runOnUiThread { refresh() } }
        if (Prefs.getBool("serviceWanted", false) && !ChildEngine.running &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            UnitService.start(this)
        }
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ChildEngine.onChange = null
        ui.removeCallbacks(tick)
    }

    private fun toggle() {
        if (ChildEngine.running) {
            UnitService.stop(this)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
            return
        }
        UnitService.start(this)
    }

    private fun onPermissionsAnswered() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!mic) {
            toast(t("The microphone permission is needed to listen to the child.",
                "صلاحية الميكروفون لازمة للاستماع للطفل."))
            return
        }
        if (ChildEngine.running) {
            // restart so the background service starts with camera access
            UnitService.stop(this)
            ui.postDelayed({ UnitService.start(this) }, 700)
        } else {
            UnitService.start(this)
        }
    }

    private fun ignoringBattery(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) {
            true
        }
    }

    /** Black screen over the app. Monitoring keeps running; a touch brings the screen back. */
    private fun showDark() {
        val v = View(this)
        v.setBackgroundColor(android.graphics.Color.BLACK)
        v.setOnTouchListener { _, _ ->
            hideDark()
            true
        }
        dark = v
        window.addContentView(
            v,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun hideDark() {
        val v = dark ?: return
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        dark = null
    }

    private fun allowPairing() {
        if (!ChildEngine.running) {
            toast(t("Tap Start monitoring first.", "اضغط ابدأ المراقبة أولاً."))
            return
        }
        ChildEngine.pairingUntil = System.currentTimeMillis() + 120_000
        toast(t("Now start pairing on the parent phone.", "الآن ابدأ الإقران من جوال الأبوين."))
        refresh()
    }

    private fun fillParents() {
        val box = listBox ?: return
        box.removeAllViews()
        val list = PairStore.parents()
        lastParentCount = list.size
        if (list.isEmpty()) {
            box.line(t("No parent phone yet", "لا يوجد جوال أبوين بعد"))
            return
        }
        for ((index, p) in list.withIndex()) {
            val c = box.card(14) {
                val row = addRow()
                row.addView(Fx.dot(context, 0xFF66BB6A.toInt()))
                val name = TextView(context)
                name.text = p.name.ifBlank { t("Parent phone", "جوال أبوين") }
                name.textSize = 16f
                name.setTextColor(Look.TEXT)
                row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val b = Button(context)
                b.text = t("Remove", "إزالة")
                b.isAllCaps = false
                b.setTextColor(0xFFFF8A8A.toInt())
                b.background = Look.glass(context, 18, 0x22FF5252)
                b.minHeight = dpInt(44)
                b.setOnClickListener {
                    Dialogs.show(
                        this@ChildActivity,
                        t("Remove this parent phone?", "إزالة جوال الأبوين هذا؟"),
                        null,
                        listOf(
                            t("Remove", "إزالة") to {
                                PairStore.removeParent(p.id)
                                fillParents()
                            },
                            t("Cancel", "تراجع") to {}
                        )
                    )
                }
                row.addView(b)
            }
            if (!entered) Fx.enter(c, index.coerceAtMost(6))
        }
        Fx.feedbackAll(box)
    }

    /** Writes the current values into the Compose state and the classic views. Runs every second. */
    private fun refresh() {
        val running = ChildEngine.running
        val connected = ChildEngine.connectedCount()
        val paired = lastParentCount.coerceAtLeast(0)

        val (label, color) = when {
            !running -> Pair(t("Stopped", "متوقف"), 0xFF6B7785.toInt())
            connected > 0 -> Pair(t("Connected", "متصل"), 0xFF2E9E6A.toInt())
            else -> Pair(t("Searching", "جارٍ البحث"), 0xFFB7791F.toInt())
        }
        state.statusText = label
        state.statusColor = color
        state.running = running

        state.hero = when {
            !running -> t("Not listening", "غير مستمع")
            ChildEngine.talking -> t("A parent is talking", "أحد الأبوين يتحدث")
            connected > 0 -> t("Listening, parent connected", "يستمع، والأب متصل")
            else -> t("Listening", "يستمع")
        }
        val last = if (ChildEngine.lastClipMs > 0)
            java.text.DateFormat.getTimeInstance().format(java.util.Date(ChildEngine.lastClipMs)) else "--"
        state.heroSub = if (running) t(
            "Parents connected: $connected of $paired  ·  last clip $last",
            "الأبوين المتصلين: $connected من $paired  ·  آخر مقطع $last"
        ) else t("Tap Start monitoring to begin.", "اضغط ابدأ المراقبة للبدء.")
        state.address = t("Wi-Fi address: ", "عنوان الواي فاي: ") + (Net.wifiIp(this) ?: "--")

        styleLevelMeter(meter)
        meter?.progress = ChildEngine.soundPercent()
        soundVisual?.setStyle(Prefs.getString("soundVisual", "wave"))
        soundVisual?.setLevel(ChildEngine.soundPercent())

        state.vpnWarn = Net.vpnBlocksLocal(this)
        state.camNeeded = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED

        val left = (ChildEngine.pairingUntil - System.currentTimeMillis()) / 1000
        state.pairNote = if (left > 0)
            t("Pairing is open for $left more seconds.", "الإقران مفتوح لمدة $left ثانية أخرى.")
        else t("Let a parent phone pair with this phone for 2 minutes.", "اسمح لجوال أبوين بالاقتران مع هذا الجوال لمدة دقيقتين.")

        state.batteryWarn = if (ignoringBattery())
            t("Keep this phone on the charger and on the same Wi-Fi as the parent phones.",
                "خلّ هذا الجوال على الشاحن وعلى نفس الواي فاي مع جوالات الأبوين.")
        else t("Battery is restricted: the phone may stop monitoring. Tap 'Keep running' below.",
            "البطارية مقيّدة: قد يتوقف الجوال عن المراقبة. اضغط 'البقاء في الخلفية' بالأسفل.")

        val req = ChildEngine.pendingPair
        if (req != null && !dialogShown) {
            dialogShown = true
            AlertDialog.Builder(this)
                .setTitle(t("Pairing request", "طلب إقران"))
                .setMessage(t("Parent phone \"${req.name}\" wants to pair.\n\nCode:  ${req.sas}\n\nAccept only if the parent phone shows the same number.",
                    "جوال الأبوين \"${req.name}\" يريد الاقتران.\n\nالرمز:  ${req.sas}\n\nاقبل فقط إذا أظهر جوال الأبوين نفس الرقم."))
                .setCancelable(false)
                .setPositiveButton(t("Same number: accept", "نفس الرقم: اقبل")) { _, _ ->
                    req.answer.offer(true)
                    dialogShown = false
                    ui.postDelayed({ fillParents() }, 1500)
                }
                .setNegativeButton(t("Reject", "ارفض")) { _, _ ->
                    req.answer.offer(false)
                    dialogShown = false
                }
                .show()
        }
    }
}
