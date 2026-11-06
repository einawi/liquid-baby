package com.liquid.liquidbaby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Everything the child unit does: listens to the room, serves the parent phones. */
object ChildEngine {

    class PairRequest(val sas: String, val name: String) {
        val answer = LinkedBlockingQueue<Boolean>()
    }

    private const val LIVE_LIMIT_MS = 15L * 60 * 1000
    private const val FLASH_LIMIT_MS = 2L * 60 * 1000

    @Volatile
    var running = false
        private set

    @Volatile
    var status = "Stopped"

    @Volatile
    var pairingUntil = 0L

    @Volatile
    var pendingPair: PairRequest? = null

    @Volatile
    var lastClipMs = 0L

    @Volatile
    var onChange: (() -> Unit)? = null

    private val talkers = ConcurrentHashMap<String, TalkPlayer>()
    val talking: Boolean get() = talkers.isNotEmpty()

    private val viewers = ConcurrentHashMap<String, Viewer>()
    private var streamer: CameraStreamer? = null

    @Volatile
    private var viewQuality = 1

    @Volatile
    private var viewFront = false

    @Volatile
    private var viewFlash = false

    @Volatile
    private var flashOffAt = 0L

    private val connected = ConcurrentHashMap<String, Conn>()
    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private var monitor: AudioMonitor? = null
    private lateinit var appCtx: Context

    private fun changed() {
        onChange?.invoke()
    }

    private fun clipsDir(): File = File(appCtx.filesDir, "childclips")

    fun clipSeconds(): Int = Prefs.getInt("clipSeconds", 30).coerceIn(15, 60)

    fun sensitivity(): Int = Prefs.getInt("sensitivity", 3).coerceIn(1, 5)

    fun connectedCount(): Int = connected.size

    fun viewLine(): String =
        if (viewers.isEmpty()) "" else "Live view active: clip recording paused (" + viewers.size + " watching)"

    fun level(): String {
        val m = monitor ?: return ""
        return "Sound level " + m.lastRms.toInt() + " (a clip starts above " + m.detector.threshold.toInt() + ")"
    }

    @Synchronized
    fun start(ctx: Context) {
        if (running) return
        appCtx = ctx.applicationContext
        running = true
        status = "Starting"
        Diag.i("Child unit starting. " + Net.describe(appCtx).replace("\n", ", "))
        try {
            udp = Discovery.startResponder(appCtx, PairStore.unitId(), Build.MODEL)
        } catch (e: Exception) {
            Diag.e("Discovery responder failed", e)
        }
        Thread { serverLoop() }.start()
        val m = AudioMonitor(clipsDir(), { clipSeconds() }, { f, ts -> onClip(f, ts) }, { s ->
            status = s
            changed()
        })
        m.detector.sensitivity = sensitivity()
        monitor = m
        m.start()
        Thread { watchdog() }.start()
        changed()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        status = "Stopped"
        stopAllTalk()
        stopAllViews()
        monitor?.stopFlag = true
        monitor = null
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        udp?.close()
        udp = null
        for (c in connected.values) c.close()
        connected.clear()
        pendingPair?.answer?.offer(false)
        pendingPair = null
        Diag.i("Child unit stopped")
        changed()
    }

    /** Ends forgotten live views after 15 minutes and switches the flash off after 2 minutes. */
    private fun watchdog() {
        while (running) {
            try {
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
            }
            val now = System.currentTimeMillis()
            for (v in viewers.values) {
                if (now - v.startedAt > LIVE_LIMIT_MS) {
                    try {
                        v.conn.send(T.VIEW_ERROR, "Live view ended after 15 minutes. Open it again if you need it.".toByteArray())
                    } catch (_: Exception) {
                    }
                    stopView(v.id)
                }
            }
            synchronized(this) {
                if (viewFlash && flashOffAt > 0 && now > flashOffAt) {
                    viewFlash = false
                    flashOffAt = 0
                    streamer?.apply(viewFront, viewQuality, false)
                    broadcastViewState()
                    Diag.i("Flash switched off automatically")
                }
            }
        }
    }

    private fun serverLoop() {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(Discovery.TCP_PORT))
            server = ss
            while (running) {
                val s = ss.accept()
                Thread { handle(s) }.start()
            }
        } catch (e: Exception) {
            if (running) {
                Diag.e("Child server stopped", e)
                status = "Network error: " + (e.message ?: "")
                changed()
            }
        }
    }

    private fun handle(s: java.net.Socket) {
        val c = Conn(s)
        var id: String? = null
        try {
            s.tcpNoDelay = true
            s.soTimeout = 15_000
            val first = c.read()
            when (first.type) {
                T.HELLO_PAIR -> pair(c, first.data)
                T.HELLO_AUTH -> id = session(c, first.data)
                else -> c.send(T.ERROR, "Bad request".toByteArray())
            }
        } catch (e: Exception) {
            Diag.i("Connection ended: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            if (id != null && connected[id] === c) connected.remove(id)
            if (id != null) {
                stopTalk(id)
                if (viewers[id]?.conn === c) stopView(id)
            }
            c.close()
            changed()
        }
    }

    private fun pair(c: Conn, data: ByteArray) {
        val f = Wire.unpack(data)
        if (f.size < 3) throw java.io.IOException("Bad pairing request")
        if (System.currentTimeMillis() > pairingUntil) {
            c.send(T.ERROR, "The child phone is not in pairing mode. Tap 'Allow a new parent phone' on it first.".toByteArray())
            return
        }
        if (pendingPair != null) {
            c.send(T.ERROR, "Another pairing is in progress.".toByteArray())
            return
        }
        val parentId = Hex.encode(f[1])
        if (PairStore.parents().size >= 2 && PairStore.parents().none { it.id == parentId }) {
            c.send(T.ERROR, "Two parent phones are already paired. Remove one first.".toByteArray())
            return
        }
        val kp = Crypto.newKeyPair()
        val myPub = kp.public.encoded
        val secret = Crypto.agree(kp.private, f[0])
        c.send(T.PAIR_PUB, Wire.pack(myPub, Hex.decode(PairStore.unitId()), Build.MODEL.toByteArray()))
        val name = String(f[2])
        val req = PairRequest(Crypto.sas(secret, f[0], myPub), name)
        pendingPair = req
        changed()
        c.socket.soTimeout = 140_000
        val accepted = req.answer.poll(120, TimeUnit.SECONDS) == true
        pendingPair = null
        c.send(T.PAIR_CONFIRM, byteArrayOf(if (accepted) 1 else 0))
        if (!accepted) {
            changed()
            return
        }
        val reply = c.read()
        if (reply.type != T.PAIR_CONFIRM || reply.data.isEmpty() || reply.data[0].toInt() != 1) {
            Diag.i("Parent did not confirm the pairing")
            changed()
            return
        }
        PairStore.addParent(PairStore.Peer(parentId, name, Hex.encode(Crypto.pairKey(secret))))
        pairingUntil = 0L
        Diag.i("Paired with parent phone $name")
        changed()
    }

    /** Returns the parent id once logged in. */
    private fun session(c: Conn, data: ByteArray): String? {
        val f = Wire.unpack(data)
        if (f.size < 2) throw java.io.IOException("Bad login")
        val id = Hex.encode(f[0])
        val key = PairStore.keyFor(id)
        if (key == null) {
            c.send(T.ERROR, "This phone is not paired with the child phone.".toByteArray())
            return null
        }
        val ns = Crypto.random(16)
        c.send(T.AUTH_REPLY, ns)
        c.enableCrypto(Crypto.sessionKey(key, f[1], ns), false)
        c.socket.soTimeout = 40_000
        connected[id]?.close()
        connected[id] = c
        c.send(T.SETTINGS, settingsBytes())
        Diag.i("Parent phone logged in")
        changed()
        while (running) {
            val m = c.read()
            when (m.type) {
                T.PING -> c.send(T.PONG)
                T.SET_LENGTH -> if (m.data.isNotEmpty()) {
                    Prefs.putInt("clipSeconds", (m.data[0].toInt() and 255).coerceIn(15, 60))
                    broadcastSettings()
                }
                T.SET_SENS -> if (m.data.isNotEmpty()) {
                    val v = (m.data[0].toInt() and 255).coerceIn(1, 5)
                    Prefs.putInt("sensitivity", v)
                    monitor?.detector?.sensitivity = v
                    broadcastSettings()
                }
                T.TALK_START -> startTalk(id)
                T.TALK_DATA -> talkers[id]?.write(m.data)
                T.TALK_STOP -> stopTalk(id)
                T.VIEW_START -> startView(id, c, m.data)
                T.VIEW_STOP -> stopView(id)
                T.VIEW_SET -> setView(m.data)
                T.SYNC -> {
                    val after = if (m.data.size >= 8) Wire.bytesLong(m.data) else 0L
                    if (after < 0L) c.send(T.SYNC_ACK, Wire.longBytes(newestClipTs())) else sendBacklog(c, after)
                }
            }
        }
        return id
    }

    // ---- walkie-talkie: every talking parent has its own speaker stream, Android mixes them ----

    private fun updateSuppression() {
        monitor?.suppressUntil =
            if (viewers.isNotEmpty() || talkers.isNotEmpty()) Long.MAX_VALUE else System.currentTimeMillis() + 2500
    }

    @Synchronized
    private fun startTalk(id: String) {
        if (talkers.containsKey(id)) return
        try {
            val p = TalkPlayer()
            p.start(appCtx, Prefs.getInt("talkVolume", 7), true)
            talkers[id] = p
            updateSuppression()
            Diag.i("A parent started talking (" + talkers.size + " talking)")
            changed()
        } catch (e: Exception) {
            Diag.e("Could not start the speaker", e)
        }
    }

    @Synchronized
    private fun stopTalk(id: String) {
        val p = talkers.remove(id) ?: return
        p.stop()
        updateSuppression()
        Diag.i("A parent stopped talking")
        changed()
    }

    private fun stopAllTalk() {
        for (id in talkers.keys.toList()) stopTalk(id)
    }

    // ---- live view: camera pictures + live sound for every watching parent ----

    private fun viewStateBytes(): ByteArray = byteArrayOf(
        viewQuality.toByte(),
        (if (viewFront) 1 else 0).toByte(),
        (if (viewFlash) 1 else 0).toByte(),
        (if (streamer?.hasFlash == true) 1 else 0).toByte(),
        viewers.size.toByte()
    )

    private fun broadcastViewState() {
        val b = viewStateBytes()
        for (v in viewers.values) v.offerState(b)
    }

    @Synchronized
    private fun startView(id: String, c: Conn, data: ByteArray) {
        if (appCtx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            c.send(
                T.VIEW_ERROR,
                "The child phone has no camera permission. On the child phone tap 'Allow the camera'.".toByteArray()
            )
            return
        }
        viewers.remove(id)?.close()
        viewers[id] = Viewer(id, c) { dead -> stopView(dead.id) }
        if (streamer == null) {
            viewQuality = if (data.isNotEmpty()) data[0].toInt().coerceIn(0, 1) else 1
            viewFront = data.size > 1 && data[1].toInt() == 1
            viewFlash = data.size > 2 && data[2].toInt() == 1
            flashOffAt = if (viewFlash) System.currentTimeMillis() + FLASH_LIMIT_MS else 0L
            val s = CameraStreamer(
                appCtx,
                { jpeg -> for (v in viewers.values) v.offerVideo(jpeg) },
                { msg -> cameraFailed(msg) },
                { broadcastViewState() }
            )
            streamer = s
            s.start(viewFront, viewQuality, viewFlash)
            monitor?.liveSink = { pcm -> for (v in viewers.values) v.offerAudio(pcm) }
        }
        updateSuppression()
        broadcastViewState()
        Diag.i("Live view started (" + viewers.size + " watching)")
        changed()
    }

    @Synchronized
    private fun stopView(id: String) {
        val v = viewers.remove(id) ?: return
        v.close()
        if (viewers.isEmpty()) {
            streamer?.stop()
            streamer = null
            monitor?.liveSink = null
            viewFlash = false
            flashOffAt = 0L
        }
        updateSuppression()
        broadcastViewState()
        Diag.i("Live view ended (" + viewers.size + " still watching)")
        changed()
    }

    private fun stopAllViews() {
        for (id in viewers.keys.toList()) stopView(id)
    }

    private fun cameraFailed(msg: String) {
        Diag.i("Camera problem: $msg")
        for (v in viewers.values) {
            try {
                v.conn.send(T.VIEW_ERROR, msg.toByteArray())
            } catch (_: Exception) {
            }
        }
        Thread { stopAllViews() }.start()
    }

    @Synchronized
    private fun setView(data: ByteArray) {
        val s = streamer ?: return
        if (data.size < 3) return
        val q = data[0].toInt().coerceIn(0, 1)
        val front = data[1].toInt() == 1
        val flip = front != viewFront
        val flash = if (flip) false else (data[2].toInt() == 1 && s.hasFlash)
        viewQuality = q
        viewFront = front
        viewFlash = flash
        flashOffAt = if (flash) System.currentTimeMillis() + FLASH_LIMIT_MS else 0L
        s.apply(front, q, flash)
        broadcastViewState()
        changed()
    }

    // ---- settings and clips ----

    private fun settingsBytes(): ByteArray = byteArrayOf(clipSeconds().toByte(), sensitivity().toByte())

    private fun broadcastSettings() {
        for (c in connected.values) {
            try {
                c.send(T.SETTINGS, settingsBytes())
            } catch (_: Exception) {
            }
        }
        changed()
    }

    private fun sendClip(c: Conn, f: File) {
        val ts = f.name.removeSuffix(".wav").toLongOrNull() ?: return
        synchronized(c.clipLock) {
            c.send(T.CLIP_BEGIN, Wire.longBytes(ts))
            FileInputStream(f).use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    c.send(T.CLIP_DATA, buf.copyOf(n))
                }
            }
            c.send(T.CLIP_END)
        }
    }

    private fun newestClipTs(): Long {
        val files = clipsDir().listFiles { f -> f.name.endsWith(".wav") } ?: return 0L
        return files.maxOfOrNull { it.name.removeSuffix(".wav").toLongOrNull() ?: 0L } ?: 0L
    }

    private fun sendBacklog(c: Conn, afterTs: Long) {
        val files = clipsDir().listFiles { f -> f.name.endsWith(".wav") } ?: return
        val list = files.filter { (it.name.removeSuffix(".wav").toLongOrNull() ?: 0L) > afterTs }
            .sortedBy { it.name.removeSuffix(".wav").toLongOrNull() ?: 0L }
            .takeLast(20)
        for (f in list) sendClip(c, f)
    }

    private fun onClip(f: File, ts: Long) {
        lastClipMs = ts
        Diag.i("Clip recorded: " + f.name)
        val files = clipsDir().listFiles { x -> x.name.endsWith(".wav") }
        if (files != null && files.size > 30) {
            files.sortedBy { it.name }.take(files.size - 30).forEach { it.delete() }
        }
        val targets = connected.values.toList()
        if (targets.isNotEmpty()) {
            Thread {
                for (c in targets) {
                    try {
                        sendClip(c, f)
                    } catch (e: Exception) {
                        Diag.i("Could not send clip: " + e.message)
                    }
                }
            }.start()
        }
        changed()
    }
}
