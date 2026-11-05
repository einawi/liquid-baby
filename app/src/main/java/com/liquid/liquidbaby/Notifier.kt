package com.liquid.liquidbaby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifier {
    private const val CHANNEL = "updates"

    @Suppress("DEPRECATION")
    fun updateReady(ctx: Context, info: Updater.Info) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder: Notification.Builder
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
                )
                builder = Notification.Builder(ctx, CHANNEL)
            } else {
                builder = Notification.Builder(ctx)
            }
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, UpdatesActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (info.required) "Required update " + info.name + " is ready" else "Version " + info.name + " is ready"
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Liquid Baby update")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
            nm.notify(1001, builder.build())
        } catch (e: Exception) {
            Diag.e("Could not show update notification", e)
        }
    }
}
