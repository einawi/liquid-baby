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
    private val listeners = ConcurrentHashMap<String, Viewer>()
    private val levelSubs: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile
    private var viewNight = false
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

    fun clipSeconds(): Int = Prefs.getInt("clipSeconds", 30).coerceIn(5, 60)

    fun sensitivity(): Int = Prefs.getInt("sensitivity", 3).coerceIn(1, 5)

    fun connectedCount(): Int = connected.size

    fun viewLine(): String = when {
        viewers.isNotEmpty() -> "Live view active: clip recording paused (" + viewers.size + " watching)"
        listeners.isNotEmpty() -> "Live sound active: clip recording paused (" + listeners.size + " listening)"
        else -> ""
    }

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
        m.intervalSec = Prefs.getInt("interval", 0).coerceIn(0, 15)
        m.liveSink = { pcm -> fanOutAudio(pcm) }
        monitor = m
        m.start()
        Thread { watchdog() }.start()
        Thread { statusLoop() }.start()
        Thread { levelLoop() }.start()
        changed()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        status = "Stopped"
        stopAllTalk()
        stopAllViews()
        stopAllListen()
        monitor?.stopFlag = true
        monitor = null
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        Discovery.stopResponder()
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
            for (v in listeners.values) {
                if (now - v.startedAt > LIVE_LIMIT_MS) {
                    try {
                        v.conn.send(T.VIEW_ERROR, "Live sound ended after 15 minutes. Start it again if you need it.".toByteArray())
                    } catch (_: Exception) {
                    }
                    stopListen(v.id)
                }
            }
            synchronized(this) {
                if (viewFlash && flashOffAt > 0 && now > flashOffAt) {
                    viewFlash = false
                    flashOffAt = 0
                    streamer?.apply(viewFront, viewQuality, false, viewNight)
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
                levelSubs.remove(id)
                if (viewers[id]?.conn === c) stopView(id)
                if (listeners[id]?.conn === c) stopListen(id)
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
        c.socket.soTimeout = 120_000
        connected[id]?.close()
        connected[id] = c
        c.send(T.SETTINGS, settingsBytes())
        c.send(T.DELETED_LIST, deletedBytes())
        Diag.i("Parent phone logged in")
        changed()
        while (running) {
            val m = c.read()
            when (m.type) {
                T.PING -> c.send(T.PONG)
                T.SET_LENGTH -> if (m.data.isNotEmpty()) {
                    Prefs.putInt("clipSeconds", (m.data[0].toInt() and 255).coerceIn(5, 60))
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
                T.LISTEN_START -> startListen(id, c)
                T.LISTEN_STOP -> stopListen(id)
                T.VIEW_SET -> setView(m.data)
                T.SET_INTERVAL -> if (m.data.isNotEmpty()) {
                    val v = (m.data[0].toInt() and 255).coerceIn(0, 15)
                    Prefs.putInt("interval", v)
                    monitor?.intervalSec = v
                    broadcastSettings()
                }
                T.SET_TALKVOL -> if (m.data.isNotEmpty()) {
                    Prefs.putInt("talkVolume", (m.data[0].toInt() and 255).coerceIn(1, 10))
                    broadcastSettings()
                }
                T.DELETE_CLIP -> if (m.data.size >= 8) deleteClip(Wire.bytesLong(m.data))
                T.SUB_LEVEL -> if (m.data.isNotEmpty() && m.data[0].toInt() == 1) levelSubs.add(id) else levelSubs.remove(id)
                T.SYNC -> {
                    val after = if (m.data.size >= 8) Wire.bytesLong(m.data) else 0L
                    if (after < 0L) c.send(T.SYNC_ACK, Wire.longBytes(newestClipTs())) else sendBacklog(c, after)
                    c.send(T.SYNC_END)
                }
            }
        }
        return id
    }

    // ---- sound only: a parent listens to the room, no picture ----

    private fun fanOutAudio(pcm: ByteArray) {
        if (viewers.isEmpty() && listeners.isEmpty()) return
        val ids = HashSet<String>()
        ids.addAll(viewers.keys)
        ids.addAll(listeners.keys)
        for (id in ids) {
            val v = viewers[id] ?: listeners[id]
            v?.offerAudio(pcm)
        }
    }

    @Synchronized
    private fun startListen(id: String, c: Conn) {
        listeners.remove(id)?.close()
        listeners[id] = Viewer(id, c) { dead -> stopListen(dead.id) }
        updateSuppression()
        Diag.i("Listening started (" + listeners.size + " listening)")
        changed()
    }

    @Synchronized
    private fun stopListen(id: String) {
        val v = listeners.remove(id) ?: return
        v.close()
        updateSuppression()
        Diag.i("Listening ended (" + listeners.size + " still listening)")
        changed()
    }

    private fun stopAllListen() {
        for (id in listeners.keys.toList()) stopListen(id)
    }

    // ---- walkie-talkie: every talking parent has its own speaker stream, Android mixes them ----

    private fun updateSuppression() {
        monitor?.suppressUntil =
            if (viewers.isNotEmpty() || talkers.isNotEmpty() || listeners.isNotEmpty()) Long.MAX_VALUE else System.currentTimeMillis() + 2500
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
        viewers.size.toByte(),
        (if (viewNight) 1 else 0).toByte()
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
            viewNight = data.size > 3 && data[3].toInt() == 1
            flashOffAt = if (viewFlash) System.currentTimeMillis() + FLASH_LIMIT_MS else 0L
            val s = CameraStreamer(
                appCtx,
                { jpeg -> for (v in viewers.values) v.offerVideo(jpeg) },
                { msg -> cameraFailed(msg) },
                { broadcastViewState() }
            )
            streamer = s
            s.start(viewFront, viewQuality, viewFlash, viewNight)
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
        val flash = data[2].toInt() == 1
        viewQuality = q
        viewFront = front
        viewFlash = flash
        flashOffAt = if (flash) System.currentTimeMillis() + FLASH_LIMIT_MS else 0L
        viewNight = data.size > 3 && data[3].toInt() == 1
        s.apply(front, q, flash, viewNight)
        broadcastViewState()
        changed()
    }

    // ---- deleted clips, cleanup, status and live level ----

    private val DELETED_MAX = 500
    private val MAX_STORED = 3
    private val KEEP_MS = 8L * 60 * 60 * 1000

    private fun deletedFile(): File = File(appCtx.filesDir, "deleted.bin")

    @Synchronized
    private fun loadDeleted(): MutableList<Long> {
        val f = deletedFile()
        val out = ArrayList<Long>()
        if (!f.exists()) return out
        val b = f.readBytes()
        var i = 0
        while (i + 8 <= b.size) {
            out.add(Wire.bytesLong(b.copyOfRange(i, i + 8)))
            i += 8
        }
        return out
    }

    @Synchronized
    private fun markDeleted(ts: Long) {
        val list = loadDeleted()
        if (!list.contains(ts)) list.add(ts)
        val kept = list.takeLast(DELETED_MAX)
        val out = ByteArray(kept.size * 8)
        for ((idx, v) in kept.withIndex()) System.arraycopy(Wire.longBytes(v), 0, out, idx * 8, 8)
        deletedFile().writeBytes(out)
    }

    private fun deletedBytes(): ByteArray {
        val list = loadDeleted()
        val out = ByteArray(list.size * 8)
        for ((idx, v) in list.withIndex()) System.arraycopy(Wire.longBytes(v), 0, out, idx * 8, 8)
        return out
    }

    /** Deletes a clip from the child phone and tells every parent phone (offline ones get it on reconnect). */
    fun deleteClip(ts: Long) {
        File(clipsDir(), "$ts.wav").delete()
        markDeleted(ts)
        broadcastDeleted()
        changed()
    }

    private fun broadcastDeleted() {
        val b = deletedBytes()
        for (c in connected.values) {
            try {
                c.send(T.DELETED_LIST, b)
            } catch (_: Exception) {
            }
        }
    }

    private fun cleanupOld() {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val files = clipsDir().listFiles { f -> f.name.endsWith(".wav") } ?: return
        var any = false
        for (f in files) {
            val ts = f.name.removeSuffix(".wav").toLongOrNull() ?: continue
            if (ts < cutoff) {
                f.delete()
                markDeleted(ts)
                any = true
            }
        }
        if (any) {
            broadcastDeleted()
            changed()
        }
    }

    /** Every 30 seconds: battery and temperature to the parent phones, and the 8-hour cleanup. */
    private fun statusLoop() {
        var tick = 0
        while (running) {
            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
            }
            tick++
            if (tick % 30 != 0) continue
            try {
                val bi = appCtx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = bi?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bi?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
                val pct = if (level < 0 || scale <= 0) 0 else level * 100 / scale
                val st = bi?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = if (st == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == android.os.BatteryManager.BATTERY_STATUS_FULL) 1 else 0
                val temp = bi?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val payload = byteArrayOf(pct.toByte(), charging.toByte(), (temp shr 8).toByte(), temp.toByte())
                for (c in connected.values) {
                    try {
                        c.send(T.STATUS, payload)
                    } catch (_: Exception) {
                    }
                }
                cleanupOld()
            } catch (e: Exception) {
                Diag.i("Status: " + e.message)
            }
        }
    }

    /** Twice a second, while a parent is watching the sound meter. */
    private fun levelLoop() {
        while (running) {
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }
            val m = monitor ?: continue
            val lvl = (m.lastRms / 4000.0 * 100.0).coerceIn(0.0, 100.0).toInt()
            for (id in levelSubs) {
                val c = connected[id] ?: continue
                try {
                    c.send(T.LEVEL, byteArrayOf(lvl.toByte()))
                } catch (_: Exception) {
                }
            }
        }
    }

    // ---- settings and clips ----

    private fun settingsBytes(): ByteArray = byteArrayOf(
        clipSeconds().toByte(),
        sensitivity().toByte(),
        Prefs.getInt("interval", 0).coerceIn(0, 15).toByte(),
        Prefs.getInt("talkVolume", 7).coerceIn(1, 10).toByte()
    )

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
        // Keep only the newest MAX_STORED clips on the child phone. Older ones are removed
        // without being marked as deleted, so a parent that already received them keeps its copy.
        val files = clipsDir().listFiles { x -> x.name.endsWith(".wav") }
        if (files != null && files.size > MAX_STORED) {
            files.sortedBy { it.name.removeSuffix(".wav").toLongOrNull() ?: 0L }
                .take(files.size - MAX_STORED)
                .forEach { it.delete() }
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
