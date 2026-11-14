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
import android.widget.EditText

/** Settings in three groups: appearance and language, alerts, and advanced. */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun build() {
        screen {
            addTitle(t("Settings", "الإعدادات"))

            sectionLabel(t("Appearance and language", "المظهر واللغة"))
            card(14) {
                addButton(if (Tr.isAr()) "اللغة: العربية (اضغط للتغيير)" else "Language: English (tap to change)") {
                    Tr.toggle()
                    recreate()
                }
                addBody(t("Mode", "الوضع"))
                for (m in listOf("system", "dark", "light")) {
                    val label = when (m) {
                        "dark" -> t("Dark", "داكن")
                        "light" -> t("Light", "فاتح")
                        else -> t("Follow the phone", "حسب الجوال")
                    }
                    addButton((if (Themes.mode() == m) "✓ " else "") + label) {
                        Themes.setMode(m)
                        recreate()
                    }
                }
                addBody(t("Style", "النمط"))
                for (st in listOf("classic", "minimal")) {
                    val label = if (st == "minimal") t("Flat", "مسطّح") else t("Glass", "زجاجي")
                    addButton((if (Themes.style() == st) "✓ " else "") + label) {
                        Themes.setStyle(st)
                        recreate()
                    }
                }
            }

            sectionLabel(t("Alerts", "التنبيهات"))
            card(14) {
                addButton(t("Notification sound", "صوت الإشعار")) { pickTone() }
                addBody(t("Disconnect alert after", "تنبيه الانقطاع بعد"))
                val cur = Prefs.getInt("offlineSec", 100)
                for (sec in listOf(60, 100, 180)) {
                    addButton((if (cur == sec) "✓ " else "") + "$sec s") {
                        Prefs.putInt("offlineSec", sec)
                        recreate()
                    }
                }
                line(t("Each kind of alert has its own channel. You can mute one in Android settings.",
                    "لكل نوع تنبيه قناة خاصة، ويمكنك كتم واحدة منها من إعدادات أندرويد."))
            }

            sectionLabel(t("Advanced", "متقدم"))
            card(14) {
                addButton(t("App updates", "تحديثات التطبيق")) {
                    startActivity(Intent(this@SettingsActivity, UpdatesActivity::class.java))
                }
                addButton(t("Diagnostics", "التشخيص")) {
                    startActivity(Intent(this@SettingsActivity, DiagnosticsActivity::class.java))
                }
                addButton(t("Change phone role", "تغيير دور الجوال")) { askRole() }
                addButton(if (Lock.pinSet()) t("Change PIN", "تغيير الرمز") else t("Set PIN (lock the parent phone)", "ضع رمزاً (لقفل جوال الأب)")) { setPin() }
                if (Lock.pinSet()) addButton(t("Remove PIN", "إزالة الرمز")) { removePin() }
                addButton(t("Export settings (share)", "تصدير الإعدادات (مشاركة)")) {
                    shareText(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
                }
                addButton(t("Copy settings to clipboard", "نسخ الإعدادات إلى الحافظة")) {
                    copyToClipboard(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
                }
                addButton(t("Restore settings from clipboard", "استعادة الإعدادات من الحافظة")) { restore() }
                addButton(t("Write a test line to Diagnostics", "اكتب سطر اختبار في التشخيص")) {
                    Diag.e("Test error line created from Settings")
                    toast(t("Done. Open Diagnostics to see it.", "تم. افتح التشخيص لرؤيته."))
                }
            }
        }
    }

    private fun askRole() {
        val items = arrayOf(t("Child unit", "وحدة الطفل"), t("Parent unit", "وحدة الأبوين"))
        AlertDialog.Builder(this)
            .setTitle(t("This phone is the", "هذا الجوال هو"))
            .setItems(items) { _, which ->
                Prefs.putString("role", if (which == 0) "child" else "parent")
                toast(t("Saved", "تم الحفظ"))
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
            toast(t("Notification sound saved", "تم حفظ صوت الإشعار"))
        }
    }

    private fun setPin() {
        val input = EditText(this)
        input.hint = t("4 to 8 digits", "من 4 إلى 8 أرقام")
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("PIN")
            .setView(input)
            .setPositiveButton(t("Save", "حفظ")) { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..8 && pin.all { it.isDigit() }) {
                    Lock.setPin(pin)
                    toast(t("PIN saved", "تم حفظ الرمز"))
                } else {
                    toast(t("Use 4 to 8 digits", "استخدم من 4 إلى 8 أرقام"))
                }
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun removePin() {
        val input = EditText(this)
        input.hint = t("Current PIN", "الرمز الحالي")
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle(t("Remove PIN", "إزالة الرمز"))
            .setView(input)
            .setPositiveButton(t("Remove", "إزالة")) { _, _ ->
                if (Lock.checkPin(input.text.toString())) {
                    Lock.clearPin()
                    toast(t("PIN removed", "تمت إزالة الرمز"))
                } else {
                    toast(t("Wrong PIN", "الرمز غير صحيح"))
                }
            }
            .setNegativeButton(t("Cancel", "تراجع"), null)
            .show()
    }

    private fun restore() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        val n = Prefs.importJson(text)
        toast(if (n < 0) t("The clipboard does not contain exported settings.", "الحافظة لا تحتوي إعدادات مصدّرة.")
        else t("Restored $n settings.", "تمت استعادة $n إعدادات."))
    }
}
