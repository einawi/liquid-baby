package com.liquid.liquidbaby

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen {
            addTitle("Settings")
            addButton("App updates") { startActivity(Intent(this@SettingsActivity, UpdatesActivity::class.java)) }
            addButton("Diagnostics") { startActivity(Intent(this@SettingsActivity, DiagnosticsActivity::class.java)) }
            addButton("Change phone role") { askRole() }
            addBody("Backup / Export of your settings")
            addButton("Export settings (share)") {
                shareText(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
            }
            addButton("Copy settings to clipboard") {
                copyToClipboard(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
            }
            addButton("Restore settings from clipboard") { restore() }
            addBody("Recordings will be added to the backup when recording arrives.")
            addButton("Write a test line to Diagnostics") {
                Diag.e("Test error line created from Settings")
                Toast.makeText(this@SettingsActivity, "Done. Open Diagnostics to see it.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun askRole() {
        val items = arrayOf("Child unit", "Parent unit")
        AlertDialog.Builder(this)
            .setTitle("This phone is the")
            .setItems(items) { _, which ->
                Prefs.putString("role", if (which == 0) "child" else "parent")
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun restore() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        val n = Prefs.importJson(text)
        val msg = if (n < 0) "The clipboard does not contain exported settings." else "Restored $n settings."
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
