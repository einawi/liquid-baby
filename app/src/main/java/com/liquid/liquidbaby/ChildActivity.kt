package com.liquid.liquidbaby

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
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

/** Child screen: one status card, the monitoring switch, and the parent phones it serves. */
class ChildActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var builtAr: Boolean? = null
    private var pulse: ObjectAnimator? = null
    private var dialogShown = false
    private var dark: View? = null
    private var lastParentCount = -1

    private var statusPill: TextView? = null
    private var mainBtn: Button? = null
    private var vpnBtn: Button? = null
    private var camBtn: Button? = null
    private var hero: TextView? = null
    private var heroSub: TextView? = null
    private var meter: ProgressBar? = null
    private var pairBtn: Button? = null
    private var pairNote: TextView? = null
    private var infoLine: TextView? = null
    private var batteryWarn: TextView? = null
    private var listBox: LinearLayout? = null

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
        builtAr = Tr.isAr()
        pulse?.cancel()
        pulse = null
        screen {
            addTitle(t("Child", "وحدة الطفل"))
            statusPill = addChip("")
            vpnBtn = addButton(t("VPN is blocking the connection: open VPN settings", "الـ VPN يمنع الاتصال: افتح إعدادات الـ VPN")) {
                openVpnSettings()
            }
            vpnBtn?.visibility = View.GONE

            card {
                hero = TextView(context).apply {
                    textSize = 22f
                    setTextColor(Look.TEXT)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                addView(hero)
                heroSub = line("")
                meter = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                }
                addView(meter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply {
                    topMargin = dp(12)
                })
                line(t("Sound in the room", "صوت الغرفة"), 13f)
            }

            mainBtn = addButton(t("Start monitoring", "ابدأ المراقبة")) { toggle() }
            Fx.press(mainBtn!!)

            camBtn = addButton(t("Allow the camera (needed for the live camera)", "اسمح بالكاميرا (لازمة للكاميرا المباشرة)")) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 21)
            }
            camBtn?.visibility = View.GONE

            card {
                pairNote = line(t("Let a parent phone pair with this phone for 2 minutes.",
                    "اسمح لجوال أبوين بالاقتران مع هذا الجوال لمدة دقيقتين."))
                pairBtn = addButton(t("Allow a new parent phone to pair", "السماح بإقران جوال أبوين جديد")) { allowPairing() }
            }

            sectionLabel(t("Paired parent phones", "جوالات الأبوين المقترنة"))
            val box = LinearLayout(context)
            box.orientation = LinearLayout.VERTICAL
            addView(box)
            listBox = box

            sectionLabel(t("Options", "خيارات"))
            addButton(t("Dark screen (touch to wake)", "شاشة مطفأة (المس للعودة)")) { showDark() }
            addButton(t("Keep running: battery settings", "البقاء في الخلفية: إعدادات البطارية")) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    toast(t("Turn battery optimization off for Liquid Baby in phone settings.",
                        "أوقف تحسين البطارية لتطبيق Liquid Baby من إعدادات الجوال."))
                }
            }
            batteryWarn = line(t("Keep this phone on the charger and on the same Wi-Fi as the parent phones.",
                "خلّ هذا الجوال على الشاحن وعلى نفس الواي فاي مع جوالات الأبوين."))
        }
        fillParents()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (builtAr != Tr.isAr()) build()
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
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 20)
            return
        }
        UnitService.start(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 20 && requestCode != 21) return
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
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
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
                b.minHeight = dp(44)
                b.setOnClickListener {
                    AlertDialog.Builder(this@ChildActivity)
                        .setTitle(t("Remove this parent phone?", "إزالة جوال الأبوين هذا؟"))
                        .setPositiveButton(t("Remove", "إزالة")) { _, _ ->
                            PairStore.removeParent(p.id)
                            fillParents()
                        }
                        .setNegativeButton(t("Cancel", "تراجع"), null)
                        .show()
                }
                row.addView(b)
            }
            Fx.enter(c, index.coerceAtMost(6))
        }
    }

    private fun refresh() {
        val running = ChildEngine.running
        val connected = ChildEngine.connectedCount()
        val paired = PairStore.parents().size

        when {
            !running -> setChip(statusPill, t("Stopped", "متوقف"), 0xFF6B7785.toInt())
            connected > 0 -> setChip(statusPill, t("Monitoring", "المراقبة تعمل"), 0xFF2E9E6A.toInt())
            else -> setChip(statusPill, t("Waiting for parent", "بانتظار جوال الأبوين"), 0xFFB7791F.toInt())
        }
        if (running) {
            if (pulse == null) statusPill?.let { pulse = Fx.breathe(it) }
        } else {
            pulse?.cancel()
            pulse = null
            statusPill?.alpha = 1f
        }

        hero?.text = when {
            !running -> t("Not listening", "غير مستمع")
            ChildEngine.talking -> t("A parent is talking", "أحد الأبوين يتحدث")
            running && connected > 0 -> t("Listening, parent connected", "يستمع، والأب متصل")
            else -> t("Listening", "يستمع")
        }
        val last = if (ChildEngine.lastClipMs > 0)
            java.text.DateFormat.getTimeInstance().format(java.util.Date(ChildEngine.lastClipMs)) else "--"
        heroSub?.text = if (running) t(
            "Parents connected: $connected of $paired  ·  last clip $last",
            "الأبوين المتصلين: $connected من $paired  ·  آخر مقطع $last"
        ) else t("Tap Start monitoring to begin.", "اضغط ابدأ المراقبة للبدء.")
        meter?.progress = ChildEngine.soundPercent()

        mainBtn?.text = if (running) t("Stop monitoring", "أوقف المراقبة") else t("Start monitoring", "ابدأ المراقبة")
        mainBtn?.background = Look.glass(this, 22,
            if (running) Fx.tint(Themes.current().accent, 0x40) else Look.BUTTON)

        vpnBtn?.visibility = if (Net.vpnBlocksLocal(this)) View.VISIBLE else View.GONE
        val camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        camBtn?.visibility = if (camOk) View.GONE else View.VISIBLE

        val left = (ChildEngine.pairingUntil - System.currentTimeMillis()) / 1000
        pairNote?.text = if (left > 0)
            t("Pairing is open for $left more seconds.", "الإقران مفتوح لمدة $left ثانية أخرى.")
        else t("Let a parent phone pair with this phone for 2 minutes.", "اسمح لجوال أبوين بالاقتران مع هذا الجوال لمدة دقيقتين.")

        batteryWarn?.text = if (ignoringBattery())
            t("Keep this phone on the charger and on the same Wi-Fi as the parent phones.",
                "خلّ هذا الجوال على الشاحن وعلى نفس الواي فاي مع جوالات الأبوين.")
        else t("Battery is restricted: the phone may stop monitoring. Tap 'Keep running' below.",
            "البطارية مقيّدة: قد يتوقف الجوال عن المراقبة. اضغط 'البقاء في الخلفية' بالأسفل.")

        if (paired != lastParentCount) fillParents()

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
