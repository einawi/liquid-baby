package com.liquid.liquidbaby

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView

class UpdatesActivity : Activity() {

    private var statusView: TextView? = null
    private var message = ""
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun setStatus(text: String) {
        message = text
        statusView?.text = text
    }

    private fun build() {
        val local = AppInfo.versionCode(this)
        val info = Updater.cached()
        screen {
            addTitle("App updates")
            addBody("Installed: " + AppInfo.versionName(this@UpdatesActivity) + " (build " + local + ")")
            statusView = addBody(message)
            if (info != null && info.code > local) {
                val req = if (info.required) " - REQUIRED" else ""
                addBody("New version: " + info.name + " (build " + info.code + ")" + req + "\nSize: " + VersionUtil.formatSize(info.apkSize))
                addBody("Release notes:\n" + (if (info.notes.isBlank()) "(none)" else info.notes))
                if (Updater.apkFile(this@UpdatesActivity, info).exists()) {
                    addButton("Install update") { install(info) }
                } else {
                    addButton("Download update") { download(info) }
                }
                if (!info.required) {
                    addButton("Skip this version") {
                        Prefs.putInt("skippedCode", info.code)
                        setStatus("Version " + info.name + " skipped.")
                        build()
                    }
                }
            } else if (info != null) {
                addBody("You have the latest version.")
            }
            addButton("Check now") { check() }
            addCheck("Pre-download updates on Wi-Fi", Prefs.getBool("wifiPredownload", true)) {
                Prefs.putBool("wifiPredownload", it)
            }
        }
    }

    private fun check() {
        if (busy) return
        busy = true
        setStatus("Checking...")
        Thread {
            var result: String
            try {
                val info = Updater.backgroundCheck(applicationContext, true)
                result = when {
                    info == null -> "No release found yet."
                    info.code > AppInfo.versionCode(this) -> "Update found: " + info.name
                    else -> "You have the latest version."
                }
            } catch (e: Exception) {
                Diag.e("Update check failed", e)
                result = "Check failed: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                busy = false
                message = result
                build()
            }
        }.start()
    }

    private fun download(info: Updater.Info) {
        if (busy) return
        busy = true
        setStatus("Downloading...")
        Thread {
            var result = "Download finished."
            try {
                Updater.download(applicationContext, info) { pct ->
                    runOnUiThread { statusView?.text = "Downloading... $pct%" }
                }
            } catch (e: Exception) {
                Diag.e("Download failed", e)
                result = "Download failed: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                busy = false
                message = result
                build()
            }
        }.start()
    }

    private fun install(info: Updater.Info) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            setStatus("Allow Liquid Baby to install apps on the next screen, then come back and tap Install again.")
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                Diag.e("Could not open install permission screen", e)
            }
            return
        }
        try {
            Updater.install(this, Updater.apkFile(this, info))
            setStatus("Opening the installer...")
        } catch (e: Exception) {
            Diag.e("Install failed", e)
            setStatus("Install failed: " + (e.message ?: e.javaClass.simpleName))
        }
    }
}
