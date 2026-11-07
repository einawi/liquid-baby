package com.liquid.liquidbaby

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Opens instead of the normal screen after two crashes right after start. */
class SafeModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = CrashGuard.lastCrash(this)
        val body = "Liquid Baby crashed twice right after starting, so it opened in this safe screen.\n" +
            "Send this text to Claude.\n\n" +
            Diag.deviceInfo(this) + "\n" + (if (crash.isEmpty()) "(no crash text saved)" else crash)
        Safe.showText(this, "Safe mode", body)
    }
}
