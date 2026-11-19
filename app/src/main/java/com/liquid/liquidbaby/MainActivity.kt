package com.liquid.liquidbaby

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Start screen: shows the update banner and lets this phone pick its role. Built with Compose. */
class MainActivity : ComponentActivity() {

    private var shownKey = ""

    private var refreshTick by mutableStateOf(0)
    private var renderedKey = ""

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLookTo()
        shownKey = ThemeWatch.key()
        setContent { MainScreen() }
        try {
            startMain()
        } catch (t: Throwable) {
            Safe.showCrash(this, t, "MainActivity.onCreate")
        }
    }

    private fun startMain() {
        if (CrashGuard.shouldEnterSafeMode(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        UpdateJobService.schedule(this)
        Thread {
            try {
                Updater.backgroundCheck(applicationContext, false)
                runOnUiThread { if (!isFinishing) refreshTick++ }
            } catch (e: Exception) {
                Diag.e("Update check failed", e)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (shownKey != ThemeWatch.key()) {
            recreate()
            return
        }
        if (CrashGuard.shouldEnterSafeMode(this)) return
        val key = Prefs.getString("role") + "|" + Tr.isAr() + "|" + Themes.mode() + "|" + Themes.style()
        if (key != renderedKey) {
            renderedKey = key
            refreshTick++
        }
        showUpdatedMessageOnce()
    }

    private fun showUpdatedMessageOnce() {
        val current = AppInfo.versionCode(this)
        val last = Prefs.getInt("lastSeenCode", 0)
        Prefs.putInt("lastSeenCode", current)
        if (last in 1 until current) {
            val info = Updater.cached()
            val notes = if (info != null && info.code == current) info.notes else ""
            Dialogs.show(
                this,
                Tr.t("Was updated to ") + AppInfo.versionName(this),
                if (notes.isBlank()) Tr.t("The app was updated successfully.") else Tr.t(notes),
                listOf(Tr.t("OK") to {})
            )
        }
    }

    private fun setRole(role: String) {
        Prefs.putString("role", role)
        Diag.i("Role set to $role")
        refreshTick++
        val msg = if (role == "child") Tr.pick(
            "1. Keep this phone on the charger and on the home Wi-Fi.\n2. Tap Start monitoring.\n3. On the parent phone, tap Pair with the child phone, then tap 'Allow a new parent phone to pair' here.",
            "1. خلّ هذا الجوال على الشاحن وعلى واي فاي البيت.\n2. اضغط ابدأ المراقبة.\n3. على جوال الأب اضغط اقرن مع جوال الطفل، ثم اضغط 'السماح بإقران جوال أبوين جديد' هنا."
        ) else Tr.pick(
            "1. Keep this phone on the same Wi-Fi as the child phone.\n2. On the child phone, tap Start monitoring.\n3. Here, tap Pair with the child phone, choose it, and check that both phones show the same code.",
            "1. خلّ هذا الجوال على نفس واي فاي جوال الطفل.\n2. على جوال الطفل اضغط ابدأ المراقبة.\n3. هنا اضغط اقرن مع جوال الطفل، واختره، وتأكد أن الرمز متطابق على الجوالين."
        )
        Dialogs.show(
            this,
            Tr.pick("Next steps", "الخطوات التالية"),
            msg,
            listOf(Tr.pick("OK", "حسناً") to {})
        )
    }

    @Composable
    private fun MainScreen() {
        val ctx = LocalContext.current
        val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
        val text = Color(Look.TEXT)
        val sub = Color(Look.SUB)
        val glass = RoundedCornerShape(18.dp)
        val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
        val accentColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)

        val tick = refreshTick
        val local = remember(tick) { AppInfo.versionCode(ctx) }
        val info = remember(tick) { Updater.cached() }
        val role = remember(tick) { Prefs.getString("role") }
        val skipped = remember(tick) { Prefs.getInt("skippedCode") }

        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Liquid Baby", fontSize = 28.sp, color = text)

                if (info != null && VersionUtil.shouldOffer(info.code, local, skipped, info.required)) {
                    val label = if (info.required) Tr.pick("Required update ", "تحديث إلزامي ") else Tr.pick("New version ", "إصدار جديد ")
                    Button(
                        onClick = { startActivity(Intent(this@MainActivity, UpdatesActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = glass,
                        colors = accentColors
                    ) {
                        Text(label + info.name + Tr.pick(" is ready. Tap to open.", " جاهز. اضغط للفتح."), fontSize = 15.sp)
                    }
                }

                Text(
                    when (role) {
                        "child" -> Tr.pick("This phone is the CHILD unit.", "هذا الجوال هو وحدة الطفل.")
                        "parent" -> Tr.pick("This phone is a PARENT unit.", "هذا الجوال هو وحدة الأبوين.")
                        else -> Tr.pick("Choose what this phone will be:", "اختر دور هذا الجوال:")
                    },
                    fontSize = 16.sp,
                    color = sub
                )

                if (role.isEmpty()) {
                    Button(
                        onClick = { setRole("child") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = glass,
                        colors = glassColors
                    ) { Text(Tr.pick("Use this phone as the child unit", "استخدم هذا الجوال كوحدة الطفل"), fontSize = 15.sp) }
                    Button(
                        onClick = { setRole("parent") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = glass,
                        colors = glassColors
                    ) { Text(Tr.pick("Use this phone as a parent unit", "استخدم هذا الجوال كوحدة أبوين"), fontSize = 15.sp) }
                }

                if (role == "child") {
                    Button(
                        onClick = { startActivity(Intent(this@MainActivity, ChildActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = glass,
                        colors = accentColors
                    ) { Text(Tr.pick("Open the child screen", "افتح شاشة الطفل"), fontSize = 16.sp) }
                } else if (role == "parent") {
                    Button(
                        onClick = { startActivity(Intent(this@MainActivity, ParentActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = glass,
                        colors = accentColors
                    ) { Text(Tr.pick("Open the parent screen", "افتح شاشة الأب"), fontSize = 16.sp) }
                }

                Button(
                    onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = glass,
                    colors = glassColors
                ) { Text(Tr.pick("Settings", "الإعدادات"), fontSize = 15.sp) }
            }
        }
    }
}
