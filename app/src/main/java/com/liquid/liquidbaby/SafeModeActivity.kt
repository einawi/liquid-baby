package com.liquid.liquidbaby

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Opens instead of the normal screen after two crashes right after start. */
class SafeModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = CrashGuard.lastCrash(this)
        val report = Diag.deviceInfo(this) + "\n" + crash
        screen {
            addTitle("Safe mode")
            addBody("Liquid Baby crashed twice right after starting, so it opened in this safe screen. Tap Copy or Share and send the text to Claude.")
            addButton("Copy crash text") { copyToClipboard(this@SafeModeActivity, "Liquid Baby crash", report) }
            addButton("Share crash text") { shareText(this@SafeModeActivity, "Liquid Baby crash", report) }
            addButton("Open diagnostics") { startActivity(Intent(this@SafeModeActivity, DiagnosticsActivity::class.java)) }
            addButton("Try normal start") {
                CrashGuard.reset(this@SafeModeActivity)
                startActivity(Intent(this@SafeModeActivity, MainActivity::class.java))
                finish()
            }
            addMono(if (crash.isEmpty()) "(no crash text saved)" else crash)
        }
    }
}
