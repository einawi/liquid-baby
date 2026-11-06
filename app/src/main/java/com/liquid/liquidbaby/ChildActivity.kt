package com.liquid.liquidbaby

import android.Manifest
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
import android.widget.TextView

class ChildActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private var statusTv: TextView? = null
    private var toggleBtn: Button? = null
    private var vpnBtn: Button? = null
    private var listBox: LinearLayout? = null
    private var dialogShown = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen {
            addTitle("Child unit")
            statusTv = addBody("")
            val vb = addButton("A VPN is blocking local connections: open VPN settings") { openVpnSettings() }
            vb.visibility = View.GONE
            vpnBtn = vb
            toggleBtn = addButton("Start monitoring") { toggle() }
            addButton("Allow a new parent phone to pair (2 minutes)") { allowPairing() }
            addBody("Paired parent phones")
            val box = LinearLayout(context)
            box.orientation = LinearLayout.VERTICAL
            addView(box)
            listBox = box
            addButton("Keep running: battery settings") {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    toast("Open the phone settings and turn battery optimization off for Liquid Baby.")
                }
            }
            addBody("Keep this phone on the charger, on the same Wi-Fi as the parent phones. If the phone restarts, open this screen and tap Start monitoring.")
        }
        fillParents()
    }

    override fun onResume() {
        super.onResume()
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
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            return
        }
        UnitService.start(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 20) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                UnitService.start(this)
            } else {
                toast("The microphone permission is needed to listen to the child.")
            }
        }
    }

    private fun allowPairing() {
        if (!ChildEngine.running) {
            toast("Tap Start monitoring first.")
            return
        }
        ChildEngine.pairingUntil = System.currentTimeMillis() + 120_000
        toast("Now start pairing on the parent phone.")
        refresh()
    }

    private fun fillParents() {
        val box = listBox ?: return
        box.removeAllViews()
        val list = PairStore.parents()
        if (list.isEmpty()) box.addBody("(none yet)")
        for (p in list) {
            val row = box.addRow()
            val tv = TextView(this)
            tv.text = p.name.ifBlank { "Parent phone" }
            tv.textSize = 16f
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val b = Button(this)
            b.text = "Remove"
            b.isAllCaps = false
            b.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove this parent phone?")
                    .setPositiveButton("Remove") { _, _ ->
                        PairStore.removeParent(p.id)
                        fillParents()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            row.addView(b)
        }
    }

    private fun refresh() {
        val sb = StringBuilder()
        sb.append("Status: ").append(ChildEngine.status).append("\n")
        if (ChildEngine.running) {
            sb.append("Parent phones connected: ").append(ChildEngine.connectedCount())
                .append(" of ").append(PairStore.parents().size).append("\n")
            sb.append(ChildEngine.level()).append("\n")
            sb.append("Clip length: ").append(ChildEngine.clipSeconds()).append(" s, sensitivity ")
                .append(ChildEngine.sensitivity()).append(" of 5 (set from the parent phone)\n")
            if (ChildEngine.lastClipMs > 0) {
                sb.append("Last clip: ").append(java.text.DateFormat.getTimeInstance().format(java.util.Date(ChildEngine.lastClipMs))).append("\n")
            }
            val left = (ChildEngine.pairingUntil - System.currentTimeMillis()) / 1000
            if (left > 0) sb.append("Pairing is open for ").append(left).append(" more seconds\n")
        }
        sb.append(Net.describe(this))
        statusTv?.text = sb.toString()
        vpnBtn?.visibility = if (Net.vpnBlocksLocal(this)) View.VISIBLE else View.GONE
        toggleBtn?.text = if (ChildEngine.running) "Stop monitoring" else "Start monitoring"
        fillParentsIfChanged()
        val req = ChildEngine.pendingPair
        if (req != null && !dialogShown) {
            dialogShown = true
            AlertDialog.Builder(this)
                .setTitle("Pairing request")
                .setMessage("Parent phone \"" + req.name + "\" wants to pair.\n\nCode:  " + req.sas + "\n\nAccept only if the parent phone shows the same number.")
                .setCancelable(false)
                .setPositiveButton("Same number: accept") { _, _ ->
                    req.answer.offer(true)
                    dialogShown = false
                    ui.postDelayed({ fillParents() }, 1500)
                }
                .setNegativeButton("Reject") { _, _ ->
                    req.answer.offer(false)
                    dialogShown = false
                }
                .show()
        }
    }

    private var lastParentCount = -1

    private fun fillParentsIfChanged() {
        val n = PairStore.parents().size
        if (n != lastParentCount) {
            lastParentCount = n
            fillParents()
        }
    }
}
