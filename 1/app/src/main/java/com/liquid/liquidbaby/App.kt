package com.liquid.liquidbaby

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        try {
            Prefs.init(this)
            PairStore.init(this)
            Updater.init(this)
        } catch (t: Throwable) {
            Diag.e("App init failed", t)
        }
        CrashGuard.install(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                if (a is LockActivity || a is SafeModeActivity) return
                try {
                    if (Lock.needsUnlock()) a.startActivity(Intent(a, LockActivity::class.java))
                } catch (_: Throwable) {
                }
            }

            override fun onActivityStopped(a: Activity) {
                Lock.leftAt = System.currentTimeMillis()
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        Diag.i("App started: " + AppInfo.versionName(this) + " build " + AppInfo.versionCode(this))
    }
}
