package com.liquid.liquidbaby

import kotlin.math.max

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Everything the parent unit does: connects to the child phone and stores clips. */
object ParentEngine {

    class ClipItem(val ts: Long, val file: File, val durationMs: Long)

    interface PairListener {
        fun onSas(sas: String, childName: String, decide: (Boolean) -> Unit)
        fun onResult(ok: Boolean, message: String)
    }

    @Volatile
    var running = false
        private set

    @Volatile
    var status = "Stopped"

    @Volatile
    var connected = false
        private set

    @Volatile
    var onChange: (() -> Unit)? = null

    @Volatile
    var talking = false
        private set

    @Volatile
    var talkNote = ""

    private var sender: TalkSender? = null

    // ---- live view ----

    @Volatile
    var viewing = false
        private set

    @Volatile
    var viewQuality = 1

    /** Target pictures per second, 10 to 20. Saved on the parent phone and sent to the child. */
    var viewFps = Prefs.getInt("viewFps", 15).coerceIn(10, 30)

    @Volatile
    var viewFront = false

    @Volatile
    var viewFlash = false

    @Volatile
    var viewHasFlash = false

    @Volatile
    var viewers = 0

    @Volatile
    var viewNote = ""

    @Volatile
    var viewNight = false
    /** 0 = off, 1 = low light (colour), 2 = pitch dark (black and white). Chosen by hand. */
    var viewNightLevel = 0
    /** Flash strength by hand: 0 off, 1 low, 2 medium, 3 full. */
    var viewFlashLevel = 0

    @Volatile
    var listening = false
        private set

    @Volatile
    var listenNote = ""

    @Volatile
    var viewRotate = 0

    @Volatile
    var liveSound = true

    @Volatile
    var frameListener: ((Bitmap) -> Unit)? = null

    private var livePlayer: TalkPlayer? = null

    @Volatile
    private var muteLiveUntil = 0L

    @Volatile
    var childLength = 30

    @Volatile
    var childSens = 3

    @Volatile
    var childInterval = 0

    @Volatile
    var childTalkVol = 7

    @Volatile
    var batteryPct = -1

    /** When the child last sent its status (battery, temperature). Used to grey out the dots when stale. */
    @Volatile
    var lastStatusMs = 0L

    /** Recorder feeds: room sound (heard only, not muted) and the parent's microphone while talking. */
    @Volatile
    var roomSink: ((ShortArray, Int) -> Unit)? = null

    @Volatile
    var micSink: ((ShortArray, Int) -> Unit)? = null

    /** Goes up when a recording is added or deleted, so screens refresh their list only when needed. */
    @Volatile
    var clipsVersion = 0

    @Volatile
    private var lastPongMs = 0L

    @Volatile
    var charging = false

    @Volatile
    var tempC = 0.0

    @Volatile
    var level = 0
    /** The other parent: connected to the child, and talking to it (from the child's status). */
    @Volatile
    private var partnerFlagA = false
    var partnerConnected: Boolean
        get() = partnerFlagA && System.currentTimeMillis() - lastStatusMs < 40_000L
        set(v) { partnerFlagA = v }
    @Volatile
    var partnerTalking = false
    /** When the last level arrived; the level is shown as zero when none has come for a while (monitoring stopped). */
    @Volatile
    var levelAt = 0L

    @Volatile
    var lastContact = System.currentTimeMillis()

    private var offlineAlerted = false
    private var inBacklog = false
    private var lowAlerted = false
    private var hotAlerted = false

    @Volatile
    private var conn: Conn? = null

    private lateinit var appCtx: Context

    private fun changed() {
        onChange?.invoke()
    }

    fun clipsDir(ctx: Context): File = File(ctx.filesDir, "clips")

    fun clips(ctx: Context): List<ClipItem> {
        val files = clipsDir(ctx).listFiles { f -> f.name.endsWith(".wav") } ?: return emptyList()
        return files.mapNotNull { f ->
            val ts = f.name.removeSuffix(".wav").toLongOrNull()
            if (ts == null) null else ClipItem(ts, f, Wav.durationMs(ClipCrypt.plainSize(f)))
        }.sortedByDescending { it.ts }
    }

    @Synchronized
    fun start(ctx: Context) {
        if (running) return
        appCtx = ctx.applicationContext
        running = true
        status = "Starting"
        Diag.i("Parent unit starting. " + Net.describe(appCtx).replace("\n", ", "))
        val gen = ++generation
        Thread { loop(gen) }.start()
        Thread { watchLoop() }.start()
        changed()
    }

    /** Each start() gets a new number; an older connection loop stops itself, so two loops never fight over the child. */
    @Volatile
    private var generation = 0

    private fun alive(gen: Int): Boolean = running && gen == generation

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        connected = false
        partnerConnected = false
        partnerTalking = false
        endTalk()
        dropView()
        status = "Stopped"
        conn?.close()
        conn = null
        Diag.i("Parent unit stopped")
        changed()
    }

    /** How long without an answer before the disconnect alert: 60, 100 or 180 seconds (Settings > Alerts). */
    fun offlineLimitMs(): Long = Prefs.getInt("offlineSec", 100).coerceIn(60, 180) * 1000L

    private fun pause(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (running && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun loop(gen: Int) {
        while (alive(gen)) {
            val p = PairStore.paired()
            if (p == null) {
                status = "Not paired with a child phone yet"
                changed()
                pause(2000)
                continue
            }
            status = "Looking for the child phone..."
            changed()
            try {
                run(p)
            } catch (e: Exception) {
                if (alive(gen)) {
                    status = "Disconnected: " + (e.message ?: e.javaClass.simpleName) + ". Retrying..."
                    Diag.i(status)
                }
            }
            connected = false
            partnerConnected = false
            partnerTalking = false
            partnerSending = false
            partnerSender?.stopFlag = true
            partnerSender = null
            endTalk()
            dropView()
            conn?.close()
            conn = null
            changed()
            pause(5000)
        }
    }

    private fun open(ip: String, timeoutMs: Int = 4000): Conn {
        val s = Socket()
        if (!Net.bind(appCtx, s)) Diag.i("No Wi-Fi network found: connecting to $ip without pinning to Wi-Fi")
        s.connect(InetSocketAddress(ip, Discovery.TCP_PORT), timeoutMs)
        s.tcpNoDelay = true
        s.soTimeout = 10_000
        return Conn(s)
    }

    /**
     * Tries the addresses the child phone had recently, then searches the network by its id.
     * A device is accepted only after it proves it holds our key, so another phone on a reused
     * address is skipped and the search continues. Nothing has to be re-paired after a network change.
     */
    private fun connectToChild(p: PairStore.Paired): Pair<Conn, String> {
        var last: IOException? = null
        for (ip in PairStore.rememberedIps()) {
            try {
                return Pair(handshake(p, ip, 1500), ip)
            } catch (e: IOException) {
                last = e
                Diag.limited("addr-$ip", "Address $ip is not our child phone: " + e.message, 30_000L)
                PairStore.forgetIp(ip)
            }
        }
        Nsd.findIp(appCtx, p.childId)?.let { ip ->
            try {
                return Pair(handshake(p, ip, 5000), ip)
            } catch (e: IOException) {
                last = e
            }
        }
        for (d in Discovery.scan(appCtx)) {
            if (d.id != p.childId) continue
            try {
                return Pair(handshake(p, d.ip, 5000), d.ip)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("Child phone not found on this Wi-Fi")
    }

    /** Opens a connection and proves the key. Throws if the device at that address is not this child. */
    private fun handshake(p: PairStore.Paired, ip: String, timeoutMs: Int): Conn {
        val c = open(ip, timeoutMs)
        try {
            c.socket.soTimeout = 5000
            val key = Hex.decode(p.key)
            val nc = Crypto.random(16)
            c.send(T.HELLO_AUTH, Wire.pack(Hex.decode(p.parentId), nc, Crypto.hmac(key, "LBAUTH".toByteArray(), nc)))
            val r = c.read()
            if (r.type == T.ERROR) throw IOException(String(r.data))
            if (r.type != T.AUTH_REPLY) throw IOException("Unexpected reply")
            c.enableCrypto(Crypto.sessionKey(key, nc, r.data), true)
            val ready = try {
                c.read()
            } catch (e: IOException) {
                throw IOException("login refused")
            }
            if (ready.type != T.SETTINGS) throw IOException("Unexpected login reply")
            applySettings(ready.data)
            return c
        } catch (e: Exception) {
            try { c.close() } catch (_: Exception) {}
            throw if (e is IOException) e else IOException(e.message ?: "login failed")
        }
    }

    private fun run(p: PairStore.Paired) {
        val (c, ip) = connectToChild(p)
        conn = c
        c.socket.soTimeout = 90_000
        // the child only sends the room level to parents that asked; ask again after every reconnection
        if (levelWanted) sendAsync(c, T.SUB_LEVEL, byteArrayOf(1))
        PairStore.setLastIp(ip)
        connected = true
        talkNote = ""
        listenNote = ""
        viewNote = ""
        offlineAlerted = false
        lastContact = System.currentTimeMillis()
        status = "Connected to " + p.childName
        Diag.i("Connected to child phone at $ip")
        changed()

        // -1 = "I am new here": the child only tells me where it is, and sends no old clips
        val last = PairStore.lastSyncTs()
        inBacklog = true
        c.send(T.SYNC, Wire.longBytes(if (last == 0L) -1L else last))
        flushPendingDeletes(c)
        Thread {
            lastPongMs = System.currentTimeMillis()
            while (running && connected && conn === c) {
                pause(10_000)
                // no answer for 25 seconds: the link is dead, so close it now instead of waiting for a long timeout
                if (System.currentTimeMillis() - lastPongMs > 25_000L) {
                    Diag.i("No answer from the child phone for 25 seconds: closing the connection")
                    c.close()
                    break
                }
                try {
                    if (running && connected && conn === c) c.send(T.PING)
                } catch (_: Exception) {
                    c.close()
                }
            }
        }.start()

        var rx: FileOutputStream? = null
        var rxTmp: File? = null
        var rxTs = 0L
        try {
            while (running) {
                val m = c.read()
                lastContact = System.currentTimeMillis()
                when (m.type) {
                    T.SETTINGS -> {
                        applySettings(m.data)
                        changed()
                    }
                    T.CLIP_BEGIN -> {
                        rx?.close()
                        rxTs = Wire.bytesLong(m.data)
                        val dir = clipsDir(appCtx)
                        if (!dir.exists()) dir.mkdirs()
                        rxTmp = File(dir, "$rxTs.part")
                        rx = FileOutputStream(rxTmp)
                    }
                    T.VIDEO_FRAME -> if (viewing) {
                        lastFrameMs = System.currentTimeMillis()
                        showFrame(m.data)
                    }
                    T.LIVE_AUDIO -> if ((viewing || listening) && liveSound && !talking && System.currentTimeMillis() > muteLiveUntil) {
                        livePlayer?.write(m.data)
                        roomSink?.let { sink ->
                            val n = m.data.size / 2
                            val s = ShortArray(n) { i -> ((m.data[2 * i].toInt() and 0xFF) or (m.data[2 * i + 1].toInt() shl 8)).toShort() }
                            sink(s, n)
                        }
                    }
                    T.VIEW_STATE -> if (m.data.size >= 5) {
                        viewQuality = m.data[0].toInt()
                        viewFront = m.data[1].toInt() == 1
                        viewFlash = m.data[2].toInt() == 1
                        viewHasFlash = m.data[3].toInt() == 1
                        viewers = m.data[4].toInt()
                        viewNight = m.data.size > 5 && m.data[5].toInt() == 1
                        // The child reports the real flash level too, so the parent never disagrees with it.
                        if (m.data.size >= 7) viewFlashLevel = if (viewFlash) m.data[6].toInt().coerceIn(1, 3) else 0
                        changed()
                    }
                    T.VIEW_ERROR -> {
                        viewNote = String(m.data)
                        dropView()
                        changed()
                    }
                    T.PARTNER_START -> partnerIncomingStart()
                    T.PARTNER_DATA -> partnerIncoming?.write(m.data)
                    T.PARTNER_STOP -> partnerIncomingStop()
                    T.TALK_DENIED -> {
                        talkNote = Tr.pick("The child phone could not start the speaker", "جوال الطفل ما قدر يشغّل السماعة")
                        endTalk()
                    }
                    T.PONG -> lastPongMs = System.currentTimeMillis()
                    T.STATUS -> if (m.data.size >= 4) onStatus(m.data)
                    T.LEVEL -> if (m.data.isNotEmpty()) {
                        level = m.data[0].toInt() and 255
                        levelAt = System.currentTimeMillis()
                    }
                    T.DELETED_LIST -> applyDeleted(m.data)
                    T.SYNC_END -> inBacklog = false
                    T.SYNC_ACK -> if (m.data.size >= 8) PairStore.raiseLastSyncTs(maxOf(1L, Wire.bytesLong(m.data)))
                    T.CLIP_DATA -> rx?.write(m.data)
                    T.CLIP_END -> {
                        rx?.close()
                        rx = null
                        val tmp = rxTmp
                        if (tmp != null) finishClip(tmp, rxTs)
                        rxTmp = null
                    }
                }
            }
        } finally {
            try {
                rx?.close()
            } catch (_: Exception) {
            }
            rxTmp?.delete()
        }
    }

    private fun finishClip(tmp: File, ts: Long) {
        PairStore.raiseLastSyncTs(ts)
        val done = File(tmp.parentFile, "$ts.wav")
        val isNew = !done.exists()
        if (isNew) {
            tmp.renameTo(done)
            ClipCrypt.encryptInPlace(done)
            clipsVersion++
        } else {
            tmp.delete()
        }
        val all = clips(appCtx)
        if (all.size > 100) all.drop(100).forEach { it.file.delete() }
        if (isNew) {
            Diag.i("Clip received: " + done.name)
            val fresh = System.currentTimeMillis() - ts < 10 * 60 * 1000L
            if (fresh && !inBacklog) Notifier.clipReady(appCtx, done, ts, false)
        }
        changed()
    }

    private fun applySettings(d: ByteArray) {
        if (d.size >= 2) {
            childLength = (d[0].toInt() and 255).coerceIn(5, 60)
            childSens = (d[1].toInt() and 255).coerceIn(1, 5)
        }
        if (d.size >= 4) {
            childInterval = (d[2].toInt() and 255).coerceIn(0, 15)
            childTalkVol = (d[3].toInt() and 255).coerceIn(1, 10)
        }
    }

    private fun sendAsync(c: Conn, type: Int, data: ByteArray) {
        Thread {
            try {
                c.send(type, data)
            } catch (e: Exception) {
                Diag.i("Could not send to the child phone: " + e.message)
            }
        }.start()
    }

    /** Asks the child phone to start the camera and the live sound. */
    @Synchronized
    fun beginView(ctx: Context): Boolean {
        val c = conn
        if (c == null || !connected) {
            viewNote = Tr.pick("Not connected to the child phone yet", "غير متصل بجوال الطفل بعد")
            changed()
            return false
        }
        viewNote = ""
        viewing = true
        // a few seconds of grace: the first picture can take a while to start
        lastFrameMs = System.currentTimeMillis() + 6000L
        lowQuality = false
        if (livePlayer == null) {
            try {
                val pl = TalkPlayer()
                pl.start(ctx.applicationContext, 0, false)
                livePlayer = pl
            } catch (e: Exception) {
                Diag.e("Could not start the live sound", e)
            }
        }
        sendAsync(
            c, T.VIEW_START,
            byteArrayOf(viewQuality.toByte(), (if (viewFront) 1 else 0).toByte(), (if (viewFlash) 1 else 0).toByte(), (if (viewNight) 1 else 0).toByte())
        )
        changed()
        return true
    }

    @Synchronized
    fun endView() {
        val c = conn
        if (viewing && c != null && connected) sendAsync(c, T.VIEW_STOP, ByteArray(0))
        dropView()
        changed()
    }

    @Synchronized
    private fun dropView() {
        viewing = false
        lowQuality = false
        listening = false
        viewers = 0
        livePlayer?.stop()
        livePlayer = null
    }

    /** Sound only: the child phone sends its live sound, no picture. */
    @Synchronized
    fun beginListen(ctx: Context): Boolean {
        val c = conn
        if (c == null || !connected) {
            listenNote = Tr.pick("Not connected to the child phone yet", "غير متصل بجوال الطفل بعد")
            changed()
            return false
        }
        listenNote = ""
        listening = true
        if (livePlayer == null) {
            try {
                val pl = TalkPlayer()
                pl.start(ctx.applicationContext, 0, false)
                livePlayer = pl
            } catch (e: Exception) {
                Diag.e("Could not start the live sound", e)
            }
        }
        sendAsync(c, T.LISTEN_START, ByteArray(0))
        changed()
        return true
    }

    @Synchronized
    fun endListen() {
        val c = conn
        if (listening && c != null && connected) sendAsync(c, T.LISTEN_STOP, ByteArray(0))
        listening = false
        if (!viewing) {
            livePlayer?.stop()
            livePlayer = null
        }
        changed()
    }

    /** Target frame rate from the dropdown (10 to 20). Applies at once while watching. */
    fun setFps(fps: Int) {
        viewFps = fps.coerceIn(10, 30)
        Prefs.putInt("viewFps", viewFps)
        if (viewing && connected) sendView(viewQuality, viewFront, viewFlash, viewNight)
    }

    /** The user changed the picture settings: this wins over the automatic quality. */
    /** Night mode by hand: 0 off, 1 low light. */
    fun setNightLevel(level: Int) {
        viewNightLevel = level.coerceIn(0, 1)
        viewNight = viewNightLevel != 0
        lowQuality = false
        sendView(userQuality, viewFront, viewFlash, viewNight)
    }

    /** Flash by hand: 0 off, 1 low, 2 medium, 3 full. */
    fun setFlashLevel(level: Int) {
        viewFlashLevel = level.coerceIn(0, 3)
        viewFlash = viewFlashLevel != 0
        sendView(userQuality, viewFront, viewFlash, viewNight)
    }

    fun setView(quality: Int, front: Boolean, flash: Boolean, night: Boolean = viewNight) {
        userQuality = quality
        lowQuality = false
        sendView(quality, front, flash, night)
    }

    private fun sendView(quality: Int, front: Boolean, flash: Boolean, night: Boolean) {
        val c = conn ?: return
        if (!connected || !viewing) return
        sendAsync(
            c, T.VIEW_SET,
            byteArrayOf(quality.toByte(), (if (front) 1 else 0).toByte(), (if (flash) 1 else 0).toByte(), (if (night) 1 else 0).toByte(), viewFps.toByte(), (if (flash) max(1, viewFlashLevel) else 0).toByte())
        )
    }

    @Volatile
    private var lastFrameMs = 0L

    @Volatile
    private var lowQuality = false

    private var goodSince = 0L
    private var lastStepMs = 0L

    /** The level the user picked; restored after a slow spell. */
    private var userQuality = 1

    /**
     * Pictures too slow for 4 seconds: ask the child phone for low quality. After 20 seconds of
     * good pictures, normal quality is requested again.
     */
    private fun adaptQuality() {
        if (!viewing || !connected) {
            goodSince = 0L
            return
        }
        val now = System.currentTimeMillis()
        val age = now - lastFrameMs
        // slow pictures: step down one level at a time (every 4 s), so the best quality the link can carry is kept
        if (age > 4000L && viewQuality >= 1 && now - lastStepMs > 4000L) {
            lowQuality = true
            goodSince = 0L
            lastStepMs = now
            val next = viewQuality - 1
            Diag.i("Pictures are slow: stepping down to quality " + next)
            sendView(next, viewFront, viewFlash, viewNight)
        } else if (lowQuality) {
            if (age < 1500L) {
                if (goodSince == 0L) goodSince = now
                if (now - goodSince > 20_000L) {
                    lowQuality = false
                    goodSince = 0L
                    Diag.i("Pictures are good again: asking for the chosen quality")
                    sendView(userQuality, viewFront, viewFlash, viewNight)
                }
            } else {
                goodSince = 0L
            }
        }
    }

    // Pictures are decoded on their own thread, so the network reader never waits for the decoder.
    // Only the newest picture is kept: if the phone is slow, older pictures are dropped.
    private val frameLock = Object()
    private var pendingFrame: ByteArray? = null
    private var frameWorker: Thread? = null

    private fun showFrame(d: ByteArray) {
        if (d.size < 100) return
        synchronized(frameLock) {
            pendingFrame = d
            frameLock.notifyAll()
            if (frameWorker == null) {
                frameWorker = Thread { frameLoop() }.also {
                    it.isDaemon = true
                    it.start()
                }
            }
        }
    }

    private fun frameLoop() {
        while (running) {
            val d: ByteArray = synchronized(frameLock) {
                while (pendingFrame == null && running) {
                    try {
                        frameLock.wait(1000)
                    } catch (_: InterruptedException) {
                    }
                }
                val x = pendingFrame
                pendingFrame = null
                x
            } ?: continue
            try {
                decodeAndShow(d)
            } catch (e: Exception) {
                Diag.i("Picture problem: " + e.message)
            }
        }
        synchronized(frameLock) {
            frameWorker = null
            pendingFrame = null
        }
    }

    private fun decodeAndShow(d: ByteArray) {
        val listener = frameListener ?: return
        val bmp = BitmapFactory.decodeByteArray(d, 1, d.size - 1) ?: return
        val quarter = ((d[0].toInt() and 3) + viewRotate) and 3
        val shown = if (quarter == 0) bmp else {
            val m = Matrix()
            m.postRotate(quarter * 90f)
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        listener(shown)
    }

    /** Starts sending the microphone to the child phone. Returns false if it cannot. */
    @Synchronized
    fun beginTalk(): Boolean {
        val c = conn
        if (c == null || !connected) {
            talkNote = Tr.pick("Not connected to the child phone yet", "غير متصل بجوال الطفل بعد")
            changed()
            return false
        }
        if (sender != null) return true
        talkNote = ""
        val s = TalkSender(c) { msg ->
            talkNote = msg
            synchronized(this) {
                talking = false
                sender = null
            }
            changed()
        }
        sender = s
        talking = true
        s.start()
        changed()
        return true
    }

    @Synchronized
    fun endTalk() {
        muteLiveUntil = System.currentTimeMillis() + 700
        sender?.stopFlag = true
        sender = null
        talking = false
        changed()
    }

    fun setInterval(v: Int) {
        val c = conn ?: return
        if (connected) sendAsync(c, T.SET_INTERVAL, byteArrayOf(v.toByte()))
    }

    // ---- partner talk (parent to parent, through the child phone) ----
    @Volatile
    var partnerSpeaking = false
    @Volatile
    var partnerSending = false
    private var partnerIncoming: TalkPlayer? = null
    private var partnerSender: TalkSender? = null

    @Synchronized
    private fun partnerIncomingStart() {
        if (partnerIncoming != null) return
        try {
            val p = TalkPlayer()
            p.start(appCtx, Prefs.getInt("talkVolume", 7), true)
            partnerIncoming = p
            partnerSpeaking = true
            changed()
        } catch (e: Exception) {
            Diag.e("Could not play the partner", e)
        }
    }

    @Synchronized
    private fun partnerIncomingStop() {
        partnerIncoming?.stop()
        partnerIncoming = null
        partnerSpeaking = false
        changed()
    }

    /** Hold or tap to talk to the partner. Refused unless the partner is connected and not already speaking. */
    fun beginPartnerTalk(): Boolean {
        val c = conn
        val stuck = partnerSender?.isAlive != true
        if (stuck) partnerSender = null
        if (c == null || !connected || !partnerConnected || partnerSpeaking || partnerSender != null) {
            Diag.i("Partner talk not started: connected=" + connected + ", partnerConnected=" + partnerConnected +
                ", partnerSpeaking=" + partnerSpeaking + ", busy=" + (partnerSender != null))
            return false
        }
        // sent from the background, like every other network write: a network write on the touch thread fails
        sendAsync(c, T.PARTNER_START, byteArrayOf())
        val s = TalkSender(c, T.PARTNER_DATA) { why ->
            Diag.i("Partner talk stopped: " + why)
            partnerSender = null
            partnerSending = false
        }
        partnerSender = s
        partnerSending = true
        s.start()
        return true
    }

    fun endPartnerTalk() {
        partnerSender?.stopFlag = true
        partnerSender = null
        partnerSending = false
    }

    fun setTalkVolume(v: Int) {
        val c = conn ?: return
        if (connected) sendAsync(c, T.SET_TALKVOL, byteArrayOf(v.toByte()))
    }

    @Volatile
    private var levelWanted = false

    fun setLevelSub(on: Boolean) {
        levelWanted = on
        val c = conn ?: return
        if (connected) sendAsync(c, T.SUB_LEVEL, byteArrayOf(if (on) 1 else 0))
    }

    /** Deletes a recording here and on the child phone; if the child is not reachable, it is done on reconnect. */
    fun deleteClip(ctx: Context, ts: Long) {
        File(clipsDir(ctx), "$ts.wav").delete()
        clipsVersion++
        val c = conn
        if (connected && c != null) {
            sendAsync(c, T.DELETE_CLIP, Wire.longBytes(ts))
        } else {
            PairStore.setPendingDeletes(PairStore.pendingDeletes() + ts)
        }
        changed()
    }

    private fun flushPendingDeletes(c: Conn) {
        val list = PairStore.pendingDeletes()
        if (list.isEmpty()) return
        for (ts in list) {
            try {
                c.send(T.DELETE_CLIP, Wire.longBytes(ts))
            } catch (_: Exception) {
            }
        }
        PairStore.setPendingDeletes(emptyList())
    }

    private fun applyDeleted(d: ByteArray) {
        var i = 0
        while (i + 8 <= d.size) {
            val ts = Wire.bytesLong(d.copyOfRange(i, i + 8))
            File(clipsDir(appCtx), "$ts.wav").delete()
            i += 8
        }
        changed()
    }

    /** Child phone status: battery, charging, temperature. Warns once per problem. */
    private fun onStatus(d: ByteArray) {
        val pct = d[0].toInt() and 255
        val chg = d[1].toInt() == 1
        val temp = (((d[2].toInt() and 255) shl 8) or (d[3].toInt() and 255)).toShort().toInt()
        batteryPct = pct
        lastStatusMs = System.currentTimeMillis()
        charging = chg
        tempC = temp / 10.0
        if (d.size >= 6) {
            val parentsOnChild = d[4].toInt() and 255
            val talkersOnChild = d[5].toInt() and 255
            val newConnected = parentsOnChild >= 2
            val newTalking = talkersOnChild - (if (talking) 1 else 0) > 0
            if (newConnected != partnerConnected || newTalking != partnerTalking) {
                partnerConnected = newConnected
                partnerTalking = newTalking
                changed()
            }
        }
        val low = !chg && pct < 20
        if (low && !lowAlerted) {
            lowAlerted = true
            Notifier.alert(appCtx, "Liquid Baby", "Child phone battery is low: $pct% and not charging", "battery")
        }
        if (!low) lowAlerted = false
        val hot = tempC > 42.0
        if (hot && !hotAlerted) {
            hotAlerted = true
            Notifier.alert(appCtx, "Liquid Baby", "Child phone is hot: " + String.format(java.util.Locale.US, "%.1f", tempC) + " °C", "temperature")
        }
        if (!hot) hotAlerted = false
        changed()
    }

    /** Watches the connection: an alert after 100 seconds without contact; deletes recordings older than 8 hours. */
    private fun watchLoop() {
        var tick = 0
        while (running) {
            try {
                Thread.sleep(2000)
            } catch (_: InterruptedException) {
            }
            if (!running) break
            tick++
            if (PairStore.paired() != null && !connected &&
                System.currentTimeMillis() - lastContact > offlineLimitMs() && !offlineAlerted
            ) {
                offlineAlerted = true
                Notifier.alert(appCtx, "Liquid Baby", "The child phone has not answered for " + (offlineLimitMs() / 1000) + " seconds. Check that it is on, monitoring, and on the same Wi-Fi.", "disconnect")
            }
            adaptQuality()
            if (tick % 15 == 0) {
                val cutoff = System.currentTimeMillis() - 8L * 60 * 60 * 1000
                for (item in clips(appCtx)) {
                    if (item.ts < cutoff) item.file.delete()
                }
            }
        }
    }

    fun sendSettings(length: Int, sens: Int) {
        val c = conn ?: return
        if (!connected) return
        Thread {
            try {
                c.send(T.SET_LENGTH, byteArrayOf(length.toByte()))
                c.send(T.SET_SENS, byteArrayOf(sens.toByte()))
            } catch (e: Exception) {
                Diag.i("Could not send settings: " + e.message)
            }
        }.start()
    }

    /** Pairing, started by the user. Runs on its own thread. */
    fun pair(ctx: Context, ip: String, code: String, listener: PairListener) {
        val app = ctx.applicationContext
        Thread {
            var c: Conn? = null
            if (!Vault.canStore()) {
                listener.onResult(false, "This phone cannot store pairing keys securely, so it cannot pair.")
                return@Thread
            }
            try {
                val s = Socket()
                Net.bind(app, s)
                s.connect(InetSocketAddress(ip, Discovery.TCP_PORT), 5000)
                s.soTimeout = 15_000
                c = Conn(s)
                val kp = Crypto.newKeyPair()
                val pub = kp.public.encoded
                val myId = PairStore.parentId()
                val myIdBytes = Hex.decode(myId)
                val proof = Crypto.hmac(code.toByteArray(), "LBPAIR".toByteArray(), pub, myIdBytes)
                c.send(T.HELLO_PAIR, Wire.pack(pub, myIdBytes, Build.MODEL.toByteArray(), proof))
                val r = c.read()
                if (r.type == T.ERROR) {
                    listener.onResult(false, String(r.data))
                    return@Thread
                }
                val f = Wire.unpack(r.data)
                if (r.type != T.PAIR_PUB || f.size < 3) throw IOException("Unexpected reply")
                val secret = Crypto.agree(kp.private, f[0])
                val sas = Crypto.sas(secret, pub, f[0])
                val childName = String(f[2])
                s.soTimeout = 140_000
                val q = LinkedBlockingQueue<Boolean>()
                listener.onSas(sas, childName) { q.offer(it) }
                val yes = q.poll(120, TimeUnit.SECONDS) == true
                c.send(T.PAIR_CONFIRM, byteArrayOf(if (yes) 1 else 0))
                if (!yes) {
                    listener.onResult(false, "Pairing cancelled")
                    return@Thread
                }
                val reply = c.read()
                if (reply.type != T.PAIR_CONFIRM || reply.data.isEmpty() || reply.data[0].toInt() != 1) {
                    listener.onResult(false, "The child phone did not accept")
                    return@Thread
                }
                PairStore.savePaired(Hex.encode(f[1]), childName, Hex.encode(Crypto.pairKey(secret)), ip)
                Diag.i("Paired with child phone $childName")
                listener.onResult(true, "Paired with $childName")
            } catch (e: java.net.SocketTimeoutException) {
                Diag.e("Pairing failed (no answer from $ip)", e)
                val why = if (Net.vpnBlocksLocal(app)) "A VPN is ON on this phone and blocks local connections. Turn the VPN off here (and on the child phone), then try again."
                else "No answer from $ip. Check the address, that the child phone shows 'Monitoring' and is on the same Wi-Fi. If the child phone has a VPN on, turn it off."
                listener.onResult(false, why)
            } catch (e: java.net.ConnectException) {
                Diag.e("Pairing failed (refused by $ip)", e)
                listener.onResult(false, "The phone at $ip refused the connection. Is Start monitoring on, on the child phone?")
            } catch (e: Exception) {
                Diag.e("Pairing failed", e)
                listener.onResult(false, "Pairing failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                c?.close()
            }
        }.start()
    }
}
