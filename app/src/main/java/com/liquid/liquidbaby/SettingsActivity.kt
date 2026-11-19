package com.liquid.liquidbaby

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Settings in three groups: appearance and language, alerts, and advanced. Built with Compose. */
class SettingsActivity : ComponentActivity() {

    private var shownKey = ""

    private var refreshTick by mutableStateOf(0)
    private var builtAr: Boolean? = null

    private val tonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = pickedTone(result.data)
            Prefs.putString("alertTone", uri?.toString() ?: "")
            toast(t("Notification sound saved", "تم حفظ صوت الإشعار"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        animateScreenIn()
        applyLookTo()
        shownKey = ThemeWatch.key()
        builtAr = Tr.isAr()
        setContent { SettingsScreen() }
    }

    override fun onResume() {
        super.onResume()
        if (shownKey != ThemeWatch.key()) {
            recreate()
            return
        }
        if (builtAr != Tr.isAr()) recreate()
    }

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun changed() {
        refreshTick++
    }

    @Suppress("DEPRECATION")
    private fun pickedTone(data: Intent?): Uri? =
        if (Build.VERSION.SDK_INT >= 33) data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        else data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

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
        tonePicker.launch(i)
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
                    changed()
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
                    changed()
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
        toast(
            if (n < 0) t("The clipboard does not contain exported settings.", "الحافظة لا تحتوي إعدادات مصدّرة.")
            else t("Restored $n settings.", "تمت استعادة $n إعدادات.")
        )
        changed()
    }

    @Composable
    private fun SettingsScreen() {
        val ctx = LocalContext.current
        val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
        val text = Color(Look.TEXT)
        val sub = Color(Look.SUB)
        val glass = RoundedCornerShape(18.dp)
        val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
        val accentColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)
        val panel = Modifier.fillMaxWidth().background(Color(Look.BUTTON), RoundedCornerShape(22.dp)).padding(14.dp)

        // read once per recomposition so every change shows up
        val tick = refreshTick
        val currentMode = remember(tick) { Themes.mode() }
        val currentStyle = remember(tick) { Themes.style() }
        val currentVisual = remember(tick) { Prefs.getString("soundVisual", "wave") }
        val currentOffline = remember(tick) { Prefs.getInt("offlineSec", 100) }
        val pinSet = remember(tick) { Lock.pinSet() }

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

        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t("Settings", "الإعدادات"), fontSize = 28.sp, color = text)

                SectionTitle(t("Appearance and language", "المظهر واللغة"), sub)
                Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            Tr.toggle()
                            recreate()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = glass,
                        colors = glassColors
                    ) {
                        Text(
                            if (Tr.isAr()) "اللغة: العربية (اضغط للتغيير)" else "Language: English (tap to change)",
                            fontSize = 15.sp
                        )
                    }

                    Text(t("Mode", "الوضع"), color = sub, fontSize = 14.sp)
                    listOf("system", "dark", "light").forEach { m ->
                        val label = when (m) {
                            "dark" -> t("Dark", "داكن")
                            "light" -> t("Light", "فاتح")
                            else -> t("Follow the phone", "حسب الجوال")
                        }
                        Button(
                            onClick = {
                                Themes.setMode(m)
                                recreate()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = glass,
                            colors = if (currentMode == m) accentColors else glassColors
                        ) {
                            Text((if (currentMode == m) "✓ " else "") + label, fontSize = 15.sp)
                        }
                    }

                    Text(t("Theme", "السمة"), color = sub, fontSize = 14.sp)
                    looks.chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (st, name, colour) ->
                                val selected = currentStyle == st
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        Themes.setStyle(st)
                                        recreate()
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(colour), CircleShape)
                                            .then(if (selected) Modifier.border(3.dp, text, CircleShape) else Modifier)
                                    )
                                    Text(name, fontSize = 12.sp, color = text, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }

                SectionTitle(t("Sound visual", "شكل الصوت"), sub)
                Column(modifier = panel) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            "wave" to t("Calm wave", "موجة هادئة"),
                            "rings" to t("Ripple rings", "حلقات الانتشار"),
                            "orb" to t("Glowing orb", "كرة متوهجة")
                        ).forEach { (id, label) ->
                            val selected = currentVisual == id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    Prefs.putString("soundVisual", id)
                                    changed()
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 108.dp, height = 68.dp)
                                        .background(Color(0x33000000), RoundedCornerShape(14.dp))
                                        .then(if (selected) Modifier.border(3.dp, text, RoundedCornerShape(14.dp)) else Modifier)
                                        .padding(4.dp)
                                ) {
                                    AndroidView(
                                        factory = { c -> SoundVisual(c).apply { setStyle(id); setLevel(55) } },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Text(label, fontSize = 13.sp, color = text, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }

                SectionTitle(t("Alerts", "التنبيهات"), sub)
                Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pickTone() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = glass,
                        colors = glassColors
                    ) { Text(t("Notification sound", "صوت الإشعار"), fontSize = 15.sp) }

                    Text(t("Disconnect alert after", "تنبيه الانقطاع بعد"), color = sub, fontSize = 14.sp)
                    listOf(60, 100, 180).forEach { sec ->
                        Button(
                            onClick = {
                                Prefs.putInt("offlineSec", sec)
                                changed()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = glass,
                            colors = if (currentOffline == sec) accentColors else glassColors
                        ) {
                            Text((if (currentOffline == sec) "✓ " else "") + "$sec s", fontSize = 15.sp)
                        }
                    }
                    Text(
                        t(
                            "Each kind of alert has its own channel. You can mute one in Android settings.",
                            "لكل نوع تنبيه قناة خاصة، ويمكنك كتم واحدة منها من إعدادات أندرويد."
                        ),
                        color = sub,
                        fontSize = 13.sp
                    )
                }

                SectionTitle(t("Advanced", "متقدم"), sub)
                Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvancedButton(t("App updates", "تحديثات التطبيق"), glass, glassColors) {
                        startActivity(Intent(this@SettingsActivity, UpdatesActivity::class.java))
                    }
                    AdvancedButton(t("Diagnostics", "التشخيص"), glass, glassColors) {
                        startActivity(Intent(this@SettingsActivity, DiagnosticsActivity::class.java))
                    }
                    AdvancedButton(t("Change phone role", "تغيير دور الجوال"), glass, glassColors) { askRole() }
                    AdvancedButton(
                        if (pinSet) t("Change PIN", "تغيير الرمز") else t("Set PIN (lock the parent phone)", "ضع رمزاً (لقفل جوال الأب)"),
                        glass, glassColors
                    ) { setPin() }
                    if (pinSet) {
                        AdvancedButton(t("Remove PIN", "إزالة الرمز"), glass, glassColors) { removePin() }
                    }
                    AdvancedButton(t("Export settings (share)", "تصدير الإعدادات (مشاركة)"), glass, glassColors) {
                        shareText(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
                    }
                    AdvancedButton(t("Copy settings to clipboard", "نسخ الإعدادات إلى الحافظة"), glass, glassColors) {
                        copyToClipboard(this@SettingsActivity, "Liquid Baby settings", Prefs.exportJson())
                    }
                    AdvancedButton(t("Restore settings from clipboard", "استعادة الإعدادات من الحافظة"), glass, glassColors) { restore() }
                    AdvancedButton(t("Write a test line to Diagnostics", "اكتب سطر اختبار في التشخيص"), glass, glassColors) {
                        Diag.e("Test error line created from Settings")
                        toast(t("Done. Open Diagnostics to see it.", "تم. افتح التشخيص لرؤيته."))
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(label: String, color: Color) {
        Text(label, fontSize = 13.sp, color = color, modifier = Modifier.padding(top = 4.dp))
    }

    @Composable
    private fun AdvancedButton(
        label: String,
        shape: RoundedCornerShape,
        colors: androidx.compose.material3.ButtonColors,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = shape,
            colors = colors
        ) { Text(label, fontSize = 15.sp) }
    }
}
