package com.liquid.liquidbaby

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Records crashes. Two crashes right after start (within 20 seconds each)
 * make the next launch open the safe-mode screen with the crash text.
 */
object CrashGuard {
    private const val PREFS = "crashguard"
    private const val QUICK_MS = 20_000L
    private const val STABLE_MS = 30_000L
    private var startMs = 0L

    fun install(app: Application) {
        startMs = SystemClock.elapsedRealtime()
        // A new app version starts with a clean crash record, so old crashes cannot keep the safe screen on.
        val sp0 = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = AppInfo.versionCode(app)
        if (sp0.getInt("ver", -1) != cur) {
            sp0.edit().putInt("ver", cur).putInt("quick", 0).remove("lastCrash").commit()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = "Time: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date()) +
                    "\nVersion: " + AppInfo.versionName(app) + " (build " + AppInfo.versionCode(app) + ")\n" +
                    Diag.stackTrace(error)
                Diag.log("CRASH", "Thread " + thread.name + "\n" + text)
                val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val quick = SystemClock.elapsedRealtime() - startMs < QUICK_MS
                val count = if (quick) sp.getInt("quick", 0) + 1 else 0
                sp.edit().putInt("quick", count).putString("lastCrash", text).commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("quick", 0).apply()
        }, STABLE_MS)
    }

    fun shouldEnterSafeMode(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("quick", 0) >= 2

    fun lastCrash(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lastCrash", "") ?: ""

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("quick", 0).commit()
    }
}
