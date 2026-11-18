package com.liquid.liquidbaby

import android.widget.FrameLayout

import android.widget.LinearLayout

import android.widget.TextView

import android.graphics.drawable.GradientDrawable

import android.view.View

import android.view.Gravity

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
        overridePendingTransition(R.anim.screen_in, R.anim.screen_out)
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
                    build()
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
                        build()
                    }
                }
                addBody(t("Theme", "السمة"))
                // colour swatches in two rows of five: each dot shows its look, the chosen one has a ring
                val looks = listOf(
                    Triple("minimal", t("Flat", "مسطّح"), 0xFF2A3A55.toInt()),
                    Triple("classic", t("Glass", "زجاجي"), 0xFF3D5A80.toInt()),
                    Triple("teal", t("Teal", "فيروزي"), 0xFF7FE3D6.toInt()),
                    Triple("bright", t("Bright", "مشرق"), 0xFFFF6B6B.toInt()),
                    Triple("midnight", t("Midnight", "منتصف الليل"), 0xFF141F44.toInt()),
                    Triple("aurora", t("Aurora", "الشفق"), 0xFF1A4D5C.toInt()),
                    Triple("sunset", t("Sunset", "الغروب"), 0xFFE0734D.toInt()),
                    Triple("rose", t("Rose", "وردي"), 0xFFC2446F.toInt()),
                    Triple("forest", t("Forest", "غابة"), 0xFF2F7A3E.toInt()),
                    Triple("graphite", t("Graphite", "جرافيت"), 0xFF4A5563.toInt())
                )
                for (chunk in looks.chunked(5)) {
                    val row = LinearLayout(context)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER
                    row.setPadding(0, context.dp(4), 0, context.dp(4))
                    for ((st, name, colour) in chunk) {
                        val item = LinearLayout(context)
                        item.orientation = LinearLayout.VERTICAL
                        item.gravity = Gravity.CENTER_HORIZONTAL
                        val dot = View(context)
                        val selected = Themes.style() == st
                        dot.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(colour)
                            if (selected) setStroke(context.dp(3), Look.TEXT)
                        }
                        item.addView(dot, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
                        val label = TextView(context)
                        label.text = name
                        label.textSize = 12f
                        label.setTextColor(Look.TEXT)
                        label.gravity = Gravity.CENTER
                        label.setPadding(0, context.dp(4), 0, 0)
                        item.addView(label)
                        item.setOnClickListener {
                            Themes.setStyle(st)
                            build()
                        }
                        row.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    addView(row)
                }
            }

            sectionLabel(t("Sound visual", "شكل الصوت"))
            card(14) {
                val current = Prefs.getString("soundVisual", "wave")
                // three live previews: each one moves with a sample level; the chosen one has a ring
                val previews = LinearLayout(context)
                previews.orientation = LinearLayout.HORIZONTAL
                previews.gravity = Gravity.CENTER
                previews.setPadding(0, context.dp(6), 0, context.dp(6))
                for ((id, label) in listOf(
                    "wave" to t("Calm wave", "موجة هادئة"),
                    "rings" to t("Ripple rings", "حلقات الانتشار"),
                    "orb" to t("Glowing orb", "كرة متوهجة"))) {
                    val item = LinearLayout(context)
                    item.orientation = LinearLayout.VERTICAL
                    item.gravity = Gravity.CENTER_HORIZONTAL
                    val box = FrameLayout(context)
                    box.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = context.dp(14).toFloat()
                        setColor(0x33000000)
                        if (current == id) setStroke(context.dp(3), Look.TEXT)
                    }
                    val live = SoundVisual(context)
                    live.setStyle(id)
                    live.setLevel(55)
                    box.addView(live, FrameLayout.LayoutParams(context.dp(96), context.dp(60)))
                    item.addView(box, LinearLayout.LayoutParams(context.dp(108), context.dp(68)))
                    val text = TextView(context)
                    text.text = label
                    text.textSize = 13f
                    text.setTextColor(Look.TEXT)
                    text.gravity = Gravity.CENTER
                    text.setPadding(0, context.dp(4), 0, 0)
                    item.addView(text)
                    item.setOnClickListener {
                        Prefs.putString("soundVisual", id)
                        build()
                    }
                    previews.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                addView(previews)
            }

            sectionLabel(t("Alerts", "التنبيهات"))
            card(14) {
                addButton(t("Notification sound", "صوت الإشعار")) { pickTone() }
                addBody(t("Disconnect alert after", "تنبيه الانقطاع بعد"))
                val cur = Prefs.getInt("offlineSec", 100)
                for (sec in listOf(60, 100, 180)) {
                    addButton((if (cur == sec) "✓ " else "") + "$sec s") {
                        Prefs.putInt("offlineSec", sec)
                        build()
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
        Dialogs.show(
            this,
            t("This phone is the", "هذا الجوال هو"),
            null,
            items.mapIndexed { i, label ->
                label to {
                    Prefs.putString("role", if (i == 0) "child" else "parent")
                    toast(t("Saved", "تم الحفظ"))
                }
            }
        )
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
