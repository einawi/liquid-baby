package com.liquid.liquidbaby

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        Prefs.init(this)
        PairStore.init(this)
        CrashGuard.install(this)
        Diag.i("App started: " + AppInfo.versionName(this) + " build " + AppInfo.versionCode(this))
    }
}
