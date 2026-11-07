package com.liquid.liquidbaby

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen {
            addTitle("Settings")
            addButton("App updates") { startActivity(Intent(this@SettingsActivity, UpdatesActivity::class.java)) }
            addButton("Diagnostics") { startActivity(Intent(this@SettingsActivity, DiagnosticsActivity::class.java)) }
            addButton("Change phone role") { askRole() }
            addButton(if (Tr.isAr()) "اللغة: العربية (اضغط للتغيير)" else "Language: English (tap to change)") {
                Tr.toggle()
                recreate()
            }
            addButton("Notification sound") { pickTone() }
            addButton(if (Lock.pinSet()) "Change PIN" else "Set PIN (lock the parent phone)") { setPin() }
            if (Lock.pinSet()) addButton("Remove PIN") { removePin() }
            addBody(if (Tr.isAr()) "المظهر" else "Theme")
            val current = Themes.current().id
            for (t in Themes.all) {
                val row = addRow()
                val swatch = android.view.View(context)
                swatch.background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(t.top, t.mid, t.bottom)
                ).apply {
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(2), t.accent)
                }
                row.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) })
                val label = if (Tr.isAr()) t.nameAr else t.nameEn
                val b = Button(context)
                b.text = (if (t.id == current) "✓ " else "") + label
                b.isAllCaps = false
                b.setTextColor(Look.TEXT)
                b.background = Look.glass(context, 22, Look.BUTTON)
                b.setOnClickListener {
                    Themes.select(t.id)
                    recreate()
                }
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            addBody("Backup / Export of your settings")
            addButton("Export settings (share)") {
                shareText(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
            }
            addButton("Copy settings to clipboard") {
                copyToClipboard(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
            }
            addButton("Restore settings from clipboard") { restore() }
            addButton("Write a test line to Diagnostics") {
                Diag.e("Test error line created from Settings")
                toast("Done. Open Diagnostics to see it.")
            }
        }
    }

    private fun askRole() {
        val items = arrayOf("Child unit", "Parent unit")
        AlertDialog.Builder(this)
            .setTitle("This phone is the")
            .setItems(items) { _, which ->
                Prefs.putString("role", if (which == 0) "child" else "parent")
                toast("Saved")
            }
            .show()
    }

    private fun pickTone() {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Notifier.toneUri(this))
        startActivityForResult(i, 77)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Prefs.putString("alertTone", uri?.toString() ?: "")
            toast("Notification sound saved")
        }
    }

    private fun setPin() {
        val input = EditText(this)
        input.hint = "4 to 8 digits"
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("PIN")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..8 && pin.all { it.isDigit() }) {
                    Lock.setPin(pin)
                    toast("PIN saved")
                } else {
                    toast("Use 4 to 8 digits")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removePin() {
        val input = EditText(this)
        input.hint = "Current PIN"
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("Remove PIN")
            .setView(input)
            .setPositiveButton("Remove") { _, _ ->
                if (Lock.checkPin(input.text.toString())) {
                    Lock.clearPin()
                    toast("PIN removed")
                } else {
                    toast("Wrong PIN")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restore() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        val n = Prefs.importJson(text)
        toast(if (n < 0) "The clipboard does not contain exported settings." else "Restored $n settings.")
    }
}
