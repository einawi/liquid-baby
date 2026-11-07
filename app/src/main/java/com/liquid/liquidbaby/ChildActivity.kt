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
    private var camBtn: Button? = null
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
            val cb = addButton("Allow the camera (needed for the live camera)") {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 21)
            }
            cb.visibility = View.GONE
            camBtn = cb
            addButton("Allow a new parent phone to pair (2 minutes)") { allowPairing() }
            addBody("Walkie-talkie volume is set from the parent phone.")
            addButton("Dark screen (touch the screen to wake it)") { showDark() }
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
            toast("The microphone permission is needed to listen to the child.")
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

    private var dark: android.view.View? = null

    /** Black screen over the app. The monitoring keeps running; touching the screen wakes it. */
    private fun showDark() {
        val v = android.view.View(this)
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
            b.text = Tr.t("Remove")
            b.isAllCaps = false
            b.setTextColor(Look.TEXT)
            b.background = Look.glass(this, 18, Look.BUTTON)
            b.minHeight = dp(44)
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
            if (ChildEngine.talking) sb.append("A parent is talking through this phone\n")
            if (ChildEngine.viewLine().isNotEmpty()) sb.append(ChildEngine.viewLine()).append("\n")
            sb.append("Clip length: ").append(ChildEngine.clipSeconds()).append(" s, sensitivity ")
                .append(ChildEngine.sensitivity()).append(" of 5 (set from the parent phone)\n")
            if (ChildEngine.lastClipMs > 0) {
                sb.append("Last clip: ").append(java.text.DateFormat.getTimeInstance().format(java.util.Date(ChildEngine.lastClipMs))).append("\n")
            }
            val left = (ChildEngine.pairingUntil - System.currentTimeMillis()) / 1000
            if (left > 0) sb.append("Pairing is open for ").append(left).append(" more seconds\n")
        }
        sb.append(Net.describe(this)).append("\n")
        vpnBtn?.visibility = if (Net.vpnBlocksLocal(this)) View.VISIBLE else View.GONE
        val camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        camBtn?.visibility = if (camOk) View.GONE else View.VISIBLE
        sb.append("Camera permission: ").append(if (camOk) "allowed" else "NOT allowed (live camera will not work)").append("\n")
        statusTv?.text = Tr.t(sb.toString())
        toggleBtn?.text = Tr.t(if (ChildEngine.running) "Stop monitoring" else "Start monitoring")
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
