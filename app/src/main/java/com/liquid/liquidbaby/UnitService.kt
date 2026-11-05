package com.liquid.liquidbaby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/** Keeps the child unit listening / the parent unit connected while the screen is off. */
class UnitService : Service() {

    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var multicast: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.putBool("serviceWanted", false)
            stopSelf()
            return START_NOT_STICKY
        }
        val role = Prefs.getString("role")
        if (role != "child" && role != "parent") {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            goForeground(role)
        } catch (e: Exception) {
            Diag.e("Could not start the background service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireLocks()
        Prefs.putBool("serviceWanted", true)
        if (role == "child") ChildEngine.start(this) else ParentEngine.start(this)
        return START_STICKY
    }

    private fun goForeground(role: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Monitoring", NotificationManager.IMPORTANCE_LOW))
            builder = Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            builder = Notification.Builder(this)
        }
        val target = if (role == "child") ChildActivity::class.java else ParentActivity::class.java
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (role == "child") "Child unit is listening" else "Parent unit is connected"
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Liquid Baby")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
        val n = builder.build()
        if (Build.VERSION.SDK_INT >= 30) {
            val type = if (role == "child") 128 else 16 // microphone / connectedDevice
            startForeground(NOTE_ID, n, type)
        } else {
            startForeground(NOTE_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            if (wake == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiquidBaby:unit")
                wake?.setReferenceCounted(false)
                wake?.acquire()
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifi == null) {
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LiquidBaby")
                wifi?.setReferenceCounted(false)
                wifi?.acquire()
            }
            if (multicast == null) {
                multicast = wm.createMulticastLock("LiquidBaby")
                multicast?.setReferenceCounted(false)
                multicast?.acquire()
            }
        } catch (e: Exception) {
            Diag.e("Could not hold wake locks", e)
        }
    }

    override fun onDestroy() {
        ChildEngine.stop()
        ParentEngine.stop()
        try {
            wake?.release()
            wifi?.release()
            multicast?.release()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.liquid.liquidbaby.STOP"
        private const val CHANNEL = "monitor"
        private const val NOTE_ID = 2001

        fun start(ctx: Context) {
            val i = Intent(ctx, UnitService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            Prefs.putBool("serviceWanted", false)
            ctx.stopService(Intent(ctx, UnitService::class.java))
        }
    }
}
