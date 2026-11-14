package com.liquid.liquidbaby

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

    @Volatile
    var charging = false

    @Volatile
    var tempC = 0.0

    @Volatile
    var level = 0

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
        Thread { loop() }.start()
        Thread { watchLoop() }.start()
        changed()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        connected = false
        endTalk()
        dropView()
        status = "Stopped"
        conn?.close()
        conn = null
        Diag.i("Parent unit stopped")
        changed()
    }

    private fun pause(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (running && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun loop() {
        while (running) {
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
                if (running) {
                    status = "Disconnected: " + (e.message ?: e.javaClass.simpleName) + ". Retrying..."
                    Diag.i(status)
                }
            }
            connected = false
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

    private fun find(p: PairStore.Paired): Pair<Conn, String> {
        // Addresses remembered from earlier connections, most recent first (short timeout each).
        for (ip in PairStore.rememberedIps()) {
            try {
                return Pair(open(ip, 1500), ip)
            } catch (e: IOException) {
                Diag.limited("addr-$ip", "Address $ip did not answer", 30_000L)
            }
        }
        for (d in Discovery.scan(appCtx)) {
            if (d.id == p.childId) return Pair(open(d.ip), d.ip)
        }
        throw IOException("Child phone not found on this Wi-Fi")
    }

    private fun run(p: PairStore.Paired) {
        val (c, ip) = find(p)
        conn = c
        val key = Hex.decode(p.key)
        val nc = Crypto.random(16)
        c.send(T.HELLO_AUTH, Wire.pack(Hex.decode(p.parentId), nc))
        val r = c.read()
        if (r.type == T.ERROR) throw IOException(String(r.data))
        if (r.type != T.AUTH_REPLY) throw IOException("Unexpected reply")
        c.enableCrypto(Crypto.sessionKey(key, nc, r.data), true)
        val ready = try {
            c.read()
        } catch (e: IOException) {
            throw IOException("Pairing is no longer valid. Remove the pairing and pair again.")
        }
        if (ready.type != T.SETTINGS) throw IOException("Unexpected login reply")
        applySettings(ready.data)
        c.socket.soTimeout = 90_000
        PairStore.setLastIp(ip)
        connected = true
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
            while (running && connected && conn === c) {
                pause(10_000)
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
                    }
                    T.VIEW_STATE -> if (m.data.size >= 5) {
                        viewQuality = m.data[0].toInt()
                        viewFront = m.data[1].toInt() == 1
                        viewFlash = m.data[2].toInt() == 1
                        viewHasFlash = m.data[3].toInt() == 1
                        viewers = m.data[4].toInt()
                        viewNight = m.data.size > 5 && m.data[5].toInt() == 1
                        changed()
                    }
                    T.VIEW_ERROR -> {
                        viewNote = String(m.data)
                        dropView()
                        changed()
                    }
                    T.TALK_DENIED -> {
                        talkNote = "The child phone could not start the speaker"
                        endTalk()
                    }
                    T.STATUS -> if (m.data.size >= 4) onStatus(m.data)
                    T.LEVEL -> if (m.data.isNotEmpty()) level = m.data[0].toInt() and 255
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
            viewNote = "Not connected to the child phone yet"
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
            listenNote = "Not connected to the child phone yet"
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

    /** The user changed the picture settings: this wins over the automatic quality. */
    fun setView(quality: Int, front: Boolean, flash: Boolean, night: Boolean = viewNight) {
        lowQuality = false
        sendView(quality, front, flash, night)
    }

    private fun sendView(quality: Int, front: Boolean, flash: Boolean, night: Boolean) {
        val c = conn ?: return
        if (!connected || !viewing) return
        sendAsync(
            c, T.VIEW_SET,
            byteArrayOf(quality.toByte(), (if (front) 1 else 0).toByte(), (if (flash) 1 else 0).toByte(), (if (night) 1 else 0).toByte())
        )
    }

    @Volatile
    private var lastFrameMs = 0L

    @Volatile
    private var lowQuality = false

    private var goodSince = 0L

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
        if (!lowQuality && viewQuality == 1 && age > 4000L) {
            lowQuality = true
            goodSince = 0L
            Diag.i("Pictures are slow: asking for low quality")
            sendView(0, viewFront, viewFlash, viewNight)
        } else if (lowQuality) {
            if (age < 1500L) {
                if (goodSince == 0L) goodSince = now
                if (now - goodSince > 20_000L) {
                    lowQuality = false
                    goodSince = 0L
                    Diag.i("Pictures are good again: asking for normal quality")
                    sendView(1, viewFront, viewFlash, viewNight)
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
            talkNote = "Not connected to the child phone yet"
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

    fun setTalkVolume(v: Int) {
        val c = conn ?: return
        if (connected) sendAsync(c, T.SET_TALKVOL, byteArrayOf(v.toByte()))
    }

    fun setLevelSub(on: Boolean) {
        val c = conn ?: return
        if (connected) sendAsync(c, T.SUB_LEVEL, byteArrayOf(if (on) 1 else 0))
    }

    /** Deletes a recording here and on the child phone; if the child is not reachable, it is done on reconnect. */
    fun deleteClip(ctx: Context, ts: Long) {
        File(clipsDir(ctx), "$ts.wav").delete()
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
        charging = chg
        tempC = temp / 10.0
        val low = !chg && pct < 20
        if (low && !lowAlerted) {
            lowAlerted = true
            Notifier.alert(appCtx, "Liquid Baby", "Child phone battery is low: $pct% and not charging")
        }
        if (!low) lowAlerted = false
        val hot = tempC > 42.0
        if (hot && !hotAlerted) {
            hotAlerted = true
            Notifier.alert(appCtx, "Liquid Baby", "Child phone is hot: " + String.format(java.util.Locale.US, "%.1f", tempC) + " °C")
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
                System.currentTimeMillis() - lastContact > 100_000L && !offlineAlerted
            ) {
                offlineAlerted = true
                Notifier.alert(appCtx, "Liquid Baby", "No answer from the child phone for 100 seconds. Check that it is on, on Wi-Fi, and monitoring.")
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
    fun pair(ctx: Context, ip: String, listener: PairListener) {
        val app = ctx.applicationContext
        Thread {
            var c: Conn? = null
            try {
                val s = Socket()
                Net.bind(app, s)
                s.connect(InetSocketAddress(ip, Discovery.TCP_PORT), 5000)
                s.soTimeout = 15_000
                c = Conn(s)
                val kp = Crypto.newKeyPair()
                val pub = kp.public.encoded
                val myId = PairStore.parentId()
                c.send(T.HELLO_PAIR, Wire.pack(pub, Hex.decode(myId), Build.MODEL.toByteArray()))
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
