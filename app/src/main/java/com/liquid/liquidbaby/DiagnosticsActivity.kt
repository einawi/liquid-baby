package com.liquid.liquidbaby

import android.app.Activity
import android.os.Bundle

class DiagnosticsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun report(): String = Diag.deviceInfo(this) + "\n" + Diag.readTail(60_000)

    private fun build() {
        val log = Diag.readTail(30_000)
        screen {
            addTitle("Diagnostics")
            addBody("Last errors and crashes. Tap Copy or Share and paste the text to Claude.")
            addButton("Copy") { copyToClipboard(this@DiagnosticsActivity, "Liquid Baby diagnostics", report()) }
            addButton("Share") { shareText(this@DiagnosticsActivity, "Liquid Baby diagnostics", report()) }
            addButton("Refresh") { build() }
            addButton("Clear") {
                Diag.clear()
                build()
            }
            addMono(Diag.deviceInfo(this@DiagnosticsActivity) + "\n" + (if (log.isEmpty()) "(no entries yet)" else log))
        }
    }
}
