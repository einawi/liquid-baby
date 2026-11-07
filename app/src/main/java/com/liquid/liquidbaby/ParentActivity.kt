package com.liquid.liquidbaby

import android.app.Activity
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class ParentActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var statusTv: TextView? = null
    private var toggleBtn: Button? = null
    private var vpnBtn: Button? = null
    private var talkBtn: Button? = null
    private var listBox: LinearLayout? = null
    private var lenLabel: TextView? = null
    private var sensLabel: TextView? = null
    private var lenBar: android.widget.SeekBar? = null
    private var sensBar: android.widget.SeekBar? = null
    private var player: MediaPlayer? = null
    private var playingTs = 0L
    private var lastClipCount = -1
    private var draggingLen = false
    private var draggingSens = false
    private var intervalLabel: TextView? = null
    private var intervalBar: android.widget.SeekBar? = null
    private var walkieLabel: TextView? = null
    private var statusLine: TextView? = null
    private var meter: android.widget.ProgressBar? = null
    private var statusDot: View? = null
    private var tempDot: View? = null
    private fun intervalText(v: Int): String =
        if (v == 0) "Listening: continuously" else "Checking every $v seconds (listens 5 s each time)"
    private var listenBtn: Button? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun build() {
        screen {
            addTitle("Parent unit")
            statusTv = addBody("")
            val vb = addButton("A VPN is blocking local connections: open VPN settings") { openVpnSettings() }
            vb.visibility = View.GONE
            vpnBtn = vb
            val paired = PairStore.paired()
            if (paired == null) {
                addButton("Pair with the child phone") { startPairFlow() }
                addBody("First, on the child phone tap 'Allow a new parent phone to pair'.")
            } else {
                addBody("Paired with: " + paired.childName.ifBlank { "child phone" })
                toggleBtn = addButton("Start listening") { toggle() }
                addButton("Remove pairing") {
                    AlertDialog.Builder(this@ParentActivity)
                        .setTitle("Remove pairing with the child phone?")
                        .setPositiveButton("Remove") { _, _ ->
                            UnitService.stop(this@ParentActivity)
                            PairStore.clearPaired()
                            build()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                listenBtn = addButton("Listen to the room (sound only)") { toggleListen() }
                addButton("Open the live camera") {
                    if (ParentEngine.connected) {
                        startActivity(Intent(this@ParentActivity, CameraActivity::class.java))
                    } else {
                        toast("Not connected to the child phone yet.")
                    }
                }
                val tb = addButton("Hold to talk to the child") { }
                tb.textSize = 20f
                tb.minHeight = dp(96)
                tb.setOnTouchListener { _, ev ->
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> onTalkDown()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTalkUp()
                    }
                    true
                }
                talkBtn = tb
                lenLabel = addBody("")
                lenBar = addSlider(5, 60, ParentEngine.childLength, { v ->
                    draggingLen = true
                    lenLabel?.text = Tr.t("Clip length: $v seconds")
                }, { v ->
                    draggingLen = false
                    Prefs.putInt("clipSeconds", v)
                    ParentEngine.childLength = v
                    ParentEngine.sendSettings(v, ParentEngine.childSens)
                })
                sensLabel = addBody("")
                sensBar = addSlider(1, 5, ParentEngine.childSens, { v ->
                    draggingSens = true
                    sensLabel?.text = Tr.t("Sensitivity: $v of 5 (5 = hears quieter sounds)")
                }, { v ->
                    draggingSens = false
                    ParentEngine.childSens = v
                    ParentEngine.sendSettings(ParentEngine.childLength, v)
                })
                intervalLabel = addBody("")
                intervalBar = addSlider(0, 15, ParentEngine.childInterval, { v ->
                    intervalLabel?.text = Tr.t(intervalText(v))
                }, { v ->
                    ParentEngine.setInterval(v)
                })
                walkieLabel = addBody("")
                addSlider(1, 10, ParentEngine.childTalkVol, { v ->
                    walkieLabel?.text = Tr.t("Walkie-talkie volume on the child phone: $v of 10")
                }, { v ->
                    ParentEngine.setTalkVolume(v)
                })
                val statusRow = addRow()
                statusDot = View(context)
                statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                    leftMargin = dp(8)
                    rightMargin = dp(8)
                })
                tempDot = View(context)
                statusRow.addView(tempDot, LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                    rightMargin = dp(8)
                })
                statusLine = TextView(context)
                statusLine?.textSize = 16f
                statusLine?.setTextColor(Look.SUB)
                statusRow.addView(statusLine, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                meter = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
                meter?.max = 100
                addView(meter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)))
                addBody("Recordings")
                val box = LinearLayout(context)
                box.orientation = LinearLayout.VERTICAL
                addView(box)
                listBox = box
            }
        }
        refresh()
        fillList()
    }

    override fun onResume() {
        super.onResume()
        ParentEngine.onChange = { runOnUiThread { refresh() } }
        ParentEngine.setLevelSub(true)
        if (Prefs.getBool("serviceWanted", false) && !ParentEngine.running && PairStore.paired() != null) {
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

    private fun onTalkDown() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 30)
            toast("Allow the microphone, then hold the button again.")
            return
        }
        if (ParentEngine.beginTalk()) {
            talkBtn?.text = Tr.t("Talking...  release to stop")
        } else {
            toast(ParentEngine.talkNote)
        }
    }

    private fun onTalkUp() {
        ParentEngine.endTalk()
        talkBtn?.text = Tr.t("Hold to talk to the child")
    }

    private fun toggle() {
        if (ParentEngine.running) UnitService.stop(this) else UnitService.start(this)
    }

    private fun refreshExtra() {
        intervalLabel?.text = Tr.t(intervalText(ParentEngine.childInterval))
        walkieLabel?.text = Tr.t("Walkie-talkie volume on the child phone: " + ParentEngine.childTalkVol + " of 10")
        val parts = ArrayList<String>()
        if (ParentEngine.batteryPct >= 0) {
            parts.add("Child unit battery: " + ParentEngine.batteryPct + "%" + (if (ParentEngine.charging) " (charging)" else ""))
        }
        if (ParentEngine.batteryPct >= 0) {
            parts.add("Temperature: " + String.format(java.util.Locale.US, "%.1f", ParentEngine.tempC) + " °C")
        }
        statusLine?.text = Tr.t(parts.joinToString("   "))
        val pct = ParentEngine.batteryPct
        val dotColor = when {
            pct < 0 -> 0xFF8899AA.toInt()
            !ParentEngine.charging && pct < 20 -> 0xFFE53935.toInt()
            !ParentEngine.charging && pct < 40 -> 0xFFFFA726.toInt()
            else -> 0xFF66BB6A.toInt()
        }
        statusDot?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(dotColor)
        }
        val tc = ParentEngine.tempC
        val tempColor = when {
            ParentEngine.batteryPct < 0 -> 0xFF8899AA.toInt()
            tc >= 42.0 -> 0xFFE53935.toInt()
            tc >= 38.0 -> 0xFFFFA726.toInt()
            else -> 0xFF66BB6A.toInt()
        }
        tempDot?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(tempColor)
        }
        meter?.progress = ParentEngine.level
    }

    private fun refresh() {
        refreshExtra()
        val note = if (ParentEngine.talkNote.isEmpty()) "" else ParentEngine.talkNote + "\n"
        statusTv?.text = Tr.t("Status: " + ParentEngine.status + "\n" + note + Net.describe(this))
        if (!ParentEngine.talking && talkBtn?.text?.startsWith("Talking") == true) talkBtn?.text = "Hold to talk to the child"
        vpnBtn?.visibility = if (Net.vpnBlocksLocal(this)) View.VISIBLE else View.GONE
        toggleBtn?.text = Tr.t(if (ParentEngine.running) "Stop listening" else "Start listening")
        listenBtn?.text = Tr.t(if (ParentEngine.listening) "Stop listening to the room" else "Listen to the room (sound only)")
        if (!draggingLen) {
            lenLabel?.text = Tr.t("Clip length: " + ParentEngine.childLength + " seconds")
            lenBar?.progress = ParentEngine.childLength - 5
        }
        if (!draggingSens) {
            sensLabel?.text = Tr.t("Sensitivity: " + ParentEngine.childSens + " of 5 (5 = hears quieter sounds)")
            sensBar?.progress = ParentEngine.childSens - 1
        }
        if (listBox != null) {
            val count = ParentEngine.clips(this).size
            if (count != lastClipCount) fillList()
        }
    }

    private fun fillList() {
        val box = listBox ?: return
        box.removeAllViews()
        val clips = ParentEngine.clips(this)
        lastClipCount = clips.size
        if (clips.isEmpty()) box.addBody("(no recordings yet)")
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        for (c in clips) {
            val row = box.addRow()
            val tv = TextView(this)
            tv.text = fmt.format(Date(c.ts)) + "  (" + (c.durationMs / 1000) + " s)"
            tv.textSize = 15f
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (playingTs == c.ts) {
                val wv = WaveView(this)
                wv.setBars(waveBars)
                wv.playing = true
                waveView = wv
                row.addView(wv, LinearLayout.LayoutParams(0, dp(34), 1.2f))
            }
            val play = Button(this)
            play.isAllCaps = false
            play.setTextColor(Look.TEXT)
            play.background = Look.glass(this, 18, Look.BUTTON)
            play.text = if (playingTs == c.ts) "Stop" else "Play"
            play.setOnClickListener {
                if (playingTs == c.ts) stopPlayer() else play(c)
                fillList()
            }
            row.addView(play)
            val del = Button(this)
            del.isAllCaps = false
            del.setTextColor(Look.TEXT)
            del.background = Look.glass(this, 18, Look.BUTTON)
            del.text = "Delete"
            del.setOnClickListener {
                if (playingTs == c.ts) stopPlayer()
                ParentEngine.deleteClip(this, c.ts)
                fillList()
            }
            row.addView(del)
        }
    }

    private fun play(c: ParentEngine.ClipItem) {
        stopPlayer()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(c.file.absolutePath)
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
            toast("Could not play this recording.")
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
        toast("Searching for the child phone...")
        Thread {
            val found = Discovery.scan(applicationContext)
            runOnUiThread { showPicker(found) }
        }.start()
    }

    private fun showPicker(found: List<Discovery.Found>) {
        val items = ArrayList<String>()
        for (f in found) items.add(f.name + " (" + f.ip + ")")
        items.add("Type an IP address...")
        AlertDialog.Builder(this)
            .setTitle(if (found.isEmpty()) "Child phone not found automatically" else "Choose the child phone")
            .setItems(items.toTypedArray()) { _, which ->
                if (which < found.size) pair(found[which].ip) else askIp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askIp() {
        val own = Net.wifiIp(this) ?: ""
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "For example 192.168.1.101"
        if (own.contains('.')) {
            input.setText(own.substringBeforeLast('.') + ".")
            input.setSelection(input.text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("IP address shown on the child phone")
            .setView(input)
            .setPositiveButton("Pair") { _, _ ->
                val ip = input.text.toString().trim()
                checkAndPair(ip, own)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndPair(ip: String, own: String) {
        if (!Ip.isValid(ip)) {
            toast("That is not a valid address. It looks like 192.168.1.101 (four numbers with dots).")
            askIp()
            return
        }
        if (own.isNotEmpty() && !Ip.sameSubnet24(ip, own)) {
            AlertDialog.Builder(this)
                .setTitle("Different network?")
                .setMessage("This phone is $own but you typed $ip. Both phones must start with the same first three numbers. Check the digits.")
                .setPositiveButton("Try anyway") { _, _ -> pair(ip) }
                .setNegativeButton("Fix it") { _, _ -> askIp() }
                .show()
            return
        }
        pair(ip)
    }

    private fun pair(ip: String) {
        toast("Connecting...")
        ParentEngine.pair(this, ip, object : ParentEngine.PairListener {
            override fun onSas(sas: String, childName: String, decide: (Boolean) -> Unit) {
                runOnUiThread {
                    AlertDialog.Builder(this@ParentActivity)
                        .setTitle("Check the code")
                        .setMessage("The child phone ($childName) must show the same number:\n\n$sas\n\nIs it the same number?")
                        .setCancelable(false)
                        .setPositiveButton("Yes, same") { _, _ -> decide(true) }
                        .setNegativeButton("No") { _, _ -> decide(false) }
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
