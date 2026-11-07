package com.liquid.liquidbaby

import android.app.Application

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
        Diag.i("App started: " + AppInfo.versionName(this) + " build " + AppInfo.versionCode(this))
    }
}
