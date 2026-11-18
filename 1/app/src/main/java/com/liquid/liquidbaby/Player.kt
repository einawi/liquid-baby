package com.liquid.liquidbaby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import java.io.File

/** Plays one recording at a time, started from its notification. */
object Player {
    private var mp: MediaPlayer? = null

    @Volatile
    var currentPath: String? = null

    private fun tsOf(path: String): Long = File(path).name.removeSuffix(".wav").toLongOrNull() ?: 0L

    @Synchronized
    fun toggle(ctx: Context, path: String) {
        if (currentPath == path && mp != null) stop(ctx, path) else play(ctx, path)
    }

    @Synchronized
    fun play(ctx: Context, path: String) {
        stopPlayer()
        try {
            val p = MediaPlayer()
            p.setDataSource(ClipCrypt.playablePath(ctx, File(path)))
            p.prepare()
            p.setOnCompletionListener {
                synchronized(Player) {
                    stopPlayer()
                }
                Notifier.clipReady(ctx.applicationContext, File(path), tsOf(path), false)
            }
            p.start()
            mp = p
            currentPath = path
        } catch (e: Exception) {
            Diag.e("Could not play recording", e)
        }
        Notifier.clipReady(ctx.applicationContext, File(path), tsOf(path), mp != null)
    }

    @Synchronized
    fun stop(ctx: Context, path: String?) {
        stopPlayer()
        if (path != null) Notifier.clipReady(ctx.applicationContext, File(path), tsOf(path), false)
    }

    private fun stopPlayer() {
        try {
            mp?.stop()
        } catch (_: Exception) {
        }
        try {
            mp?.release()
        } catch (_: Exception) {
        }
        mp = null
        currentPath = null
    }
}

class PlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TOGGLE -> {
                val p = intent.getStringExtra(EXTRA_PATH) ?: return
                Player.toggle(context, p)
            }
            STOP -> Player.stop(context, null)
        }
    }

    companion object {
        const val TOGGLE = "com.liquid.liquidbaby.PLAY_TOGGLE"
        const val STOP = "com.liquid.liquidbaby.PLAY_STOP"
        const val EXTRA_PATH = "path"
    }
}
