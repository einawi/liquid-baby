package com.liquid.liquidbaby

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One parent phone watching live. Sends in its own thread so a slow phone never blocks the microphone or camera. */
class Viewer(val id: String, val conn: Conn, private val onDead: (Viewer) -> Unit) {
    val startedAt = System.currentTimeMillis()
    private val queue = LinkedBlockingQueue<Msg>(300)

    @Volatile
    private var alive = true

    init {
        Thread { run() }.start()
    }

    /** Pictures are dropped when the phone cannot keep up. */
    fun offerVideo(data: ByteArray) {
        if (queue.size < 20) queue.offer(Msg(T.VIDEO_FRAME, data))
    }

    fun offerAudio(data: ByteArray) {
        queue.offer(Msg(T.LIVE_AUDIO, data))
    }

    fun offerState(data: ByteArray) {
        queue.offer(Msg(T.VIEW_STATE, data))
    }

    private fun run() {
        try {
            while (alive) {
                val m = queue.poll(1, TimeUnit.SECONDS) ?: continue
                conn.send(m.type, m.data)
            }
        } catch (e: Exception) {
            if (alive) {
                alive = false
                onDead(this)
            }
        }
    }

    fun close() {
        alive = false
    }
}
