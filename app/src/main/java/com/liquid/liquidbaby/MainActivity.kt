package com.liquid.liquidbaby

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 10)
        }
        UpdateJobService.schedule(this)
        Thread {
            try {
                Updater.backgroundCheck(applicationContext, false)
                runOnUiThread { if (!isFinishing) render() }
            } catch (e: Exception) {
                Diag.e("Update check failed", e)
            }
        }.start()
        render()
    }

    private var renderedKey = ""

    override fun onResume() {
        super.onResume()
        if (CrashGuard.shouldEnterSafeMode(this)) return
        val key = Prefs.getString("role", "") + "|" + Tr.isAr() + "|" + Themes.mode() + "|" + Themes.style()
        if (key != renderedKey) {
            renderedKey = key
            render()
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
            AlertDialog.Builder(this)
                .setTitle(Tr.t("Was updated to ") + AppInfo.versionName(this))
                .setMessage(if (notes.isBlank()) Tr.t("The app was updated successfully.") else Tr.t(notes))
                .setPositiveButton(Tr.t("OK"), null)
                .show()
        }
    }

    private fun render() {
        try {
            renderUnsafe()
        } catch (t: Throwable) {
            Safe.showCrash(this, t, "MainActivity.render")
        }
    }

    private fun renderUnsafe() {
        val local = AppInfo.versionCode(this)
        val info = Updater.cached()
        screen {
            addTitle("Liquid Baby")
            if (info != null && VersionUtil.shouldOffer(info.code, local, Prefs.getInt("skippedCode"), info.required)) {
                val label = if (info.required) "Required update " else "New version "
                addBanner(label + info.name + " is ready. Tap to open.") {
                    startActivity(Intent(this@MainActivity, UpdatesActivity::class.java))
                }
            }
            val role = Prefs.getString("role")
            when (role) {
                "child" -> addBody("This phone is the CHILD unit.")
                "parent" -> addBody("This phone is a PARENT unit.")
                else -> addBody("Choose what this phone will be:")
            }
            if (role == null) {
                addButton("Use this phone as the child unit") { setRole("child") }
                addButton("Use this phone as a parent unit") { setRole("parent") }
            }
            if (role == "child") {
                addButton("Open the child screen") { startActivity(Intent(this@MainActivity, ChildActivity::class.java)) }
            } else if (role == "parent") {
                addButton("Open the parent screen") { startActivity(Intent(this@MainActivity, ParentActivity::class.java)) }
            }
            addButton("Settings") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
    }

    private fun setRole(role: String) {
        Prefs.putString("role", role)
        Diag.i("Role set to $role")
        render()
        val msg = if (role == "child") Tr.pick(
            "1. Keep this phone on the charger and on the home Wi-Fi.\n2. Tap Start monitoring.\n3. On the parent phone, tap Pair with the child phone, then tap 'Allow a new parent phone to pair' here.",
            "1. خلّ هذا الجوال على الشاحن وعلى واي فاي البيت.\n2. اضغط ابدأ المراقبة.\n3. على جوال الأب اضغط اقرن مع جوال الطفل، ثم اضغط 'السماح بإقران جوال أبوين جديد' هنا."
        ) else Tr.pick(
            "1. Keep this phone on the same Wi-Fi as the child phone.\n2. On the child phone, tap Start monitoring.\n3. Here, tap Pair with the child phone, choose it, and check that both phones show the same code.",
            "1. خلّ هذا الجوال على نفس واي فاي جوال الطفل.\n2. على جوال الطفل اضغط ابدأ المراقبة.\n3. هنا اضغط اقرن مع جوال الطفل، واختره، وتأكد أن الرمز متطابق على الجوالين."
        )
        AlertDialog.Builder(this)
            .setTitle(Tr.pick("Next steps", "الخطوات التالية"))
            .setMessage(msg)
            .setPositiveButton(Tr.pick("OK", "حسناً"), null)
            .show()
    }
}
