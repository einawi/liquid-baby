package com.liquid.liquidbaby

import android.app.Activity
import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
                lenLabel = addBody("")
                lenBar = addSlider(15, 60, ParentEngine.childLength, { v ->
                    draggingLen = true
                    lenLabel?.text = "Clip length: $v seconds"
                }, { v ->
                    draggingLen = false
                    Prefs.putInt("clipSeconds", v)
                    ParentEngine.childLength = v
                    ParentEngine.sendSettings(v, ParentEngine.childSens)
                })
                sensLabel = addBody("")
                sensBar = addSlider(1, 5, ParentEngine.childSens, { v ->
                    draggingSens = true
                    sensLabel?.text = "Sensitivity: $v of 5 (5 = hears quieter sounds)"
                }, { v ->
                    draggingSens = false
                    ParentEngine.childSens = v
                    ParentEngine.sendSettings(ParentEngine.childLength, v)
                })
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
        if (Prefs.getBool("serviceWanted", false) && !ParentEngine.running && PairStore.paired() != null) {
            UnitService.start(this)
        }
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ParentEngine.onChange = null
        ui.removeCallbacks(tick)
        stopPlayer()
    }

    private fun toggle() {
        if (ParentEngine.running) UnitService.stop(this) else UnitService.start(this)
    }

    private fun refresh() {
        statusTv?.text = "Status: " + ParentEngine.status + "\n" + Net.describe(this)
        toggleBtn?.text = if (ParentEngine.running) "Stop listening" else "Start listening"
        if (!draggingLen) {
            lenLabel?.text = "Clip length: " + ParentEngine.childLength + " seconds"
            lenBar?.progress = ParentEngine.childLength - 15
        }
        if (!draggingSens) {
            sensLabel?.text = "Sensitivity: " + ParentEngine.childSens + " of 5 (5 = hears quieter sounds)"
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
            val play = Button(this)
            play.isAllCaps = false
            play.text = if (playingTs == c.ts) "Stop" else "Play"
            play.setOnClickListener {
                if (playingTs == c.ts) stopPlayer() else play(c)
                fillList()
            }
            row.addView(play)
            val del = Button(this)
            del.isAllCaps = false
            del.text = "Delete"
            del.setOnClickListener {
                if (playingTs == c.ts) stopPlayer()
                c.file.delete()
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
            mp.start()
            player = mp
            playingTs = c.ts
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
