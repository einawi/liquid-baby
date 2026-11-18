package com.liquid.liquidbaby

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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

/** App updates: installed version, check, download, install, skip and the Wi-Fi pre-download option. Built with Compose. */
class UpdatesActivity : ComponentActivity() {

    private var message by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var refreshTick by mutableStateOf(0)
    private var wifiPredownload by mutableStateOf(true)
    private var builtAr: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLookTo()
        builtAr = Tr.isAr()
        wifiPredownload = Prefs.getBool("wifiPredownload", true)
        setContent { UpdatesScreen() }
    }

    override fun onResume() {
        super.onResume()
        if (builtAr != Tr.isAr()) {
            recreate()
            return
        }
        refreshTick++
    }

    /** Sets the status line in the language the screen is using right now. */
    private fun setStatus(en: String, ar: String) {
        message = Tr.pick(en, ar)
    }

    private fun check() {
        if (busy) return
        busy = true
        setStatus("Checking...", "جارٍ التحقق...")
        Thread {
            var en: String
            var ar: String
            try {
                val info = Updater.backgroundCheck(applicationContext, true)
                val local = AppInfo.versionCode(this)
                when {
                    info == null -> {
                        en = "No release found yet."
                        ar = "لم يُعثر على إصدار بعد."
                    }
                    info.code > local -> {
                        en = "Update found: " + info.name
                        ar = "تم العثور على تحديث: " + info.name
                    }
                    else -> {
                        en = "You have the latest version."
                        ar = "أنت على أحدث إصدار."
                    }
                }
            } catch (e: Exception) {
                Diag.e("Update check failed", e)
                en = "Check failed: " + (e.message ?: e.javaClass.simpleName)
                ar = "فشل التحقق: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                busy = false
                setStatus(en, ar)
                refreshTick++
            }
        }.start()
    }

    private fun download(info: Updater.Info) {
        if (busy) return
        busy = true
        setStatus("Downloading...", "جارٍ التنزيل...")
        Thread {
            var en = "Download finished."
            var ar = "اكتمل التنزيل."
            try {
                Updater.download(applicationContext, info) { pct ->
                    runOnUiThread { message = Tr.pick("Downloading... $pct%", "جارٍ التنزيل... $pct%") }
                }
            } catch (e: Exception) {
                Diag.e("Download failed", e)
                en = "Download failed: " + (e.message ?: e.javaClass.simpleName)
                ar = "فشل التنزيل: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                busy = false
                setStatus(en, ar)
                refreshTick++
            }
        }.start()
    }

    private fun install(info: Updater.Info) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            setStatus(
                "Allow Liquid Baby to install apps on the next screen, then come back and tap Install again.",
                "اسمح لـ Liquid Baby بتثبيت التطبيقات في الشاشة التالية، ثم ارجع واضغط تثبيت مرة أخرى."
            )
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
            setStatus("Opening the installer...", "جارٍ فتح المثبّت...")
        } catch (e: Exception) {
            Diag.e("Install failed", e)
            setStatus(
                "Install failed: " + (e.message ?: e.javaClass.simpleName),
                "فشل التثبيت: " + (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    private fun skip(info: Updater.Info) {
        Prefs.putInt("skippedCode", info.code)
        setStatus("Version " + info.name + " skipped.", "تم تخطي الإصدار " + info.name + ".")
        refreshTick++
    }

    @Composable
    private fun UpdatesScreen() {
        val ctx = LocalContext.current
        val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
        val text = Color(Look.TEXT)
        val sub = Color(Look.SUB)
        val glass = RoundedCornerShape(18.dp)
        val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
        val accentColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)
        val panel = Modifier.fillMaxWidth().background(Color(Look.BUTTON), RoundedCornerShape(22.dp)).padding(16.dp)

        val tick = refreshTick
        val local = remember(tick) { AppInfo.versionCode(ctx) }
        val info = remember(tick) { Updater.cached() }
        val installed = remember(tick) { AppInfo.versionName(ctx) }
        val haveFile = info != null && Updater.apkFile(ctx, info).exists()

        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(Tr.pick("App updates", "تحديثات التطبيق"), fontSize = 28.sp, color = text)
                Text(
                    Tr.pick("Installed: ", "المثبّت: ") + installed + " (build $local)",
                    fontSize = 15.sp,
                    color = sub
                )
                Text(message, fontSize = 15.sp, color = text)

                if (info != null && info.code > local) {
                    val req = if (info.required) Tr.pick(" - REQUIRED", " - إلزامي") else ""
                    Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            Tr.pick("New version: ", "إصدار جديد: ") + info.name + " (build " + info.code + ")" + req,
                            fontSize = 16.sp,
                            color = text
                        )
                        Text(
                            Tr.pick("Size: ", "الحجم: ") + VersionUtil.formatSize(info.apkSize),
                            fontSize = 14.sp,
                            color = sub
                        )
                    }
                    Column(modifier = panel) {
                        Text(Tr.pick("Release notes:", "ملاحظات الإصدار:"), fontSize = 14.sp, color = sub)
                        Text(
                            if (info.notes.isBlank()) Tr.pick("(none)", "(لا توجد)") else info.notes,
                            fontSize = 15.sp,
                            color = text
                        )
                    }
                    Button(
                        onClick = { if (haveFile) install(info) else download(info) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = glass,
                        colors = accentColors
                    ) {
                        Text(
                            if (haveFile) Tr.pick("Install update", "تثبيت التحديث")
                            else Tr.pick("Download update", "تنزيل التحديث"),
                            fontSize = 16.sp
                        )
                    }
                    if (!info.required) {
                        Button(
                            onClick = { skip(info) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = glass,
                            colors = glassColors
                        ) { Text(Tr.pick("Skip this version", "تخطي هذا الإصدار"), fontSize = 15.sp) }
                    }
                } else if (info != null) {
                    Column(modifier = panel) {
                        Text(Tr.pick("You have the latest version.", "أنت على أحدث إصدار."), fontSize = 16.sp, color = text)
                    }
                }

                Button(
                    onClick = { check() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = glass,
                    colors = glassColors
                ) { Text(Tr.pick("Check now", "تحقق الآن"), fontSize = 15.sp) }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = wifiPredownload,
                        onCheckedChange = {
                            wifiPredownload = it
                            Prefs.putBool("wifiPredownload", it)
                        }
                    )
                    Text(Tr.pick("Pre-download updates on Wi-Fi", "التنزيل المسبق للتحديثات عبر Wi-Fi"), fontSize = 15.sp, color = text)
                }
            }
        }
    }
}
