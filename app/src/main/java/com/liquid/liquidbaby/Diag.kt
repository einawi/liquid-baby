package com.liquid.liquidbaby

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the last errors and crashes in a small text file. */
object Diag {
    private const val MAX_BYTES = 200_000L
    private var file: File? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "diagnostics.log")
    }

    @Synchronized
    fun log(level: String, msg: String) {
        val f = file ?: return
        try {
            f.appendText("[" + fmt.format(Date()) + "] " + level + ": " + msg + "\n")
            if (f.length() > MAX_BYTES) {
                val text = f.readText()
                val cut = text.substring(text.length / 2)
                val nl = cut.indexOf('\n')
                f.writeText(if (nl >= 0) cut.substring(nl + 1) else cut)
            }
        } catch (_: Throwable) {
        }
    }

    fun i(msg: String) = log("INFO", msg)

    fun e(msg: String, t: Throwable? = null) =
        log("ERROR", if (t == null) msg else msg + "\n" + stackTrace(t))

    fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    @Synchronized
    fun readTail(maxChars: Int): String {
        val f = file ?: return ""
        return try {
            val text = if (f.exists()) f.readText() else ""
            if (text.length > maxChars) text.substring(text.length - maxChars) else text
        } catch (_: Throwable) {
            ""
        }
    }

    @Synchronized
    fun clear() {
        try {
            file?.writeText("")
        } catch (_: Throwable) {
        }
    }

    fun deviceInfo(ctx: Context): String =
        "Liquid Baby " + AppInfo.versionName(ctx) + " (build " + AppInfo.versionCode(ctx) + ")\n" +
            "Device: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " +
            Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
}
