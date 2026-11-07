package com.liquid.liquidbaby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.text.DateFormat
import java.util.Date

object Notifier {
    private val COLOR = 0xFF7FE8FF.toInt()

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun builder(ctx: Context, channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channel) else Notification.Builder(ctx)

    private fun openApp(ctx: Context, code: Int, cls: Class<*>): PendingIntent =
        PendingIntent.getActivity(
            ctx, code, Intent(ctx, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun toneUri(ctx: Context): Uri {
        val s = Prefs.getString("alertTone")
        return if (s.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(s)
    }

    private fun vibrateWhenRinging(ctx: Context): Boolean = try {
        Settings.System.getInt(ctx.contentResolver, "vibrate_when_ringing", 1) == 1
    } catch (e: Exception) {
        true
    }

    /**
     * Alerts follow the phone's mode: silent = vibrate only, normal = sound (+ vibration if it is on),
     * vibrate mode = vibration only. Notification channels are created per mode and per tone.
     */
    fun alertChannel(ctx: Context): String {
        if (Build.VERSION.SDK_INT < 26) return "alerts"
        val mode = (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode
        val tone = toneUri(ctx)
        val vib = vibrateWhenRinging(ctx)
        val id = "alerts_" + mode + "_" + (if (vib) 1 else 0) + "_" + Math.abs(tone.toString().hashCode())
        if (nm(ctx).getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "Baby alerts", NotificationManager.IMPORTANCE_HIGH)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (mode == AudioManager.RINGER_MODE_NORMAL) {
                ch.setSound(tone, attrs)
                ch.enableVibration(vib)
            } else {
                ch.setSound(null, null)
                ch.enableVibration(true)
            }
            nm(ctx).createNotificationChannel(ch)
        }
        return id
    }

    fun alert(ctx: Context, title: String, text: String) {
        try {
            val b = builder(ctx, alertChannel(ctx))
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(COLOR)
                .setContentTitle(Tr.t(title))
                .setContentText(Tr.t(text))
                .setContentIntent(openApp(ctx, 3, ParentActivity::class.java))
                .setAutoCancel(true)
            if (canPost(ctx)) nm(ctx).notify(1002, b.build())
        } catch (e: Exception) {
            Diag.e("Could not show alert", e)
        }
    }

    /** A new recording: one Play / Stop button, nothing else. */
    @Suppress("DEPRECATION")
    fun clipReady(ctx: Context, file: File, ts: Long, playing: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                nm(ctx).createNotificationChannel(NotificationChannel("clips", "New recordings", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val toggle = PendingIntent.getBroadcast(
                ctx, 11,
                Intent(ctx, PlayReceiver::class.java).setAction(PlayReceiver.TOGGLE)
                    .putExtra(PlayReceiver.EXTRA_PATH, file.absolutePath),
                flags
            )
            val gone = PendingIntent.getBroadcast(
                ctx, 12, Intent(ctx, PlayReceiver::class.java).setAction(PlayReceiver.STOP), flags
            )
            val when_ = DateFormat.getTimeInstance().format(Date(ts))
            val b = builder(ctx, "clips")
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(COLOR)
                .setContentTitle(Tr.t("New recording"))
                .setContentText(when_ + "  \u00b7  " + (Wav.durationMs(file.length()) / 1000) + " s")
                .setContentIntent(openApp(ctx, 4, ParentActivity::class.java))
                .setDeleteIntent(gone)
                .setAutoCancel(true)
            val icon = android.graphics.drawable.Icon.createWithResource(
                ctx, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            b.addAction(Notification.Action.Builder(icon, Tr.t(if (playing) "Stop" else "Play"), toggle).build())
            if (canPost(ctx)) {
                nm(ctx).notify(1004, b.build())
                Diag.i("Recording notice posted: " + file.name)
            } else {
                Diag.i("Recording notice not shown: notifications are not allowed")
            }
        } catch (e: Exception) {
            Diag.e("Could not show recording notice", e)
        }
    }

    fun updateReady(ctx: Context, info: Updater.Info) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                nm(ctx).createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val text = Tr.t(if (info.required) "Required update " + info.name + " is ready" else "Version " + info.name + " is ready")
            val b = builder(ctx, "updates")
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(COLOR)
                .setContentTitle(Tr.t("Liquid Baby update"))
                .setContentText(text)
                .setContentIntent(openApp(ctx, 1, UpdatesActivity::class.java))
                .setAutoCancel(true)
            if (canPost(ctx)) nm(ctx).notify(1001, b.build())
        } catch (e: Exception) {
            Diag.e("Could not show update notification", e)
        }
    }
}
