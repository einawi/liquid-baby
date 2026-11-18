package com.liquid.liquidbaby

import android.content.Context

object AppInfo {
    @Suppress("DEPRECATION")
    fun versionCode(ctx: Context): Int = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }

    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
