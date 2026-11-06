package com.liquid.liquidbaby

import android.content.Context
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
    var childLength = 30

    @Volatile
    var childSens = 3

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
            if (ts == null) null else ClipItem(ts, f, Wav.durationMs(f.length()))
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
        changed()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        connected = false
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
            conn?.close()
            conn = null
            changed()
            pause(5000)
        }
    }

    private fun open(ip: String): Conn {
        val s = Socket()
        Net.bind(appCtx, s)
        s.connect(InetSocketAddress(ip, Discovery.TCP_PORT), 4000)
        s.tcpNoDelay = true
        s.soTimeout = 10_000
        return Conn(s)
    }

    private fun find(p: PairStore.Paired): Pair<Conn, String> {
        if (p.lastIp.isNotEmpty()) {
            try {
                return Pair(open(p.lastIp), p.lastIp)
            } catch (e: IOException) {
                Diag.i("Last address did not answer: " + e.message)
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
        c.socket.soTimeout = 40_000
        PairStore.setLastIp(ip)
        connected = true
        status = "Connected to " + p.childName
        Diag.i("Connected to child phone at $ip")
        changed()

        // -1 = "I am new here": the child only tells me where it is, and sends no old clips
        val last = PairStore.lastSyncTs()
        c.send(T.SYNC, Wire.longBytes(if (last == 0L) -1L else last))
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
        if (isNew) tmp.renameTo(done) else tmp.delete()
        val all = clips(appCtx)
        if (all.size > 100) all.drop(100).forEach { it.file.delete() }
        if (isNew) {
            Diag.i("Clip received: " + done.name)
            val fresh = System.currentTimeMillis() - ts < 10 * 60 * 1000L
            if (fresh) Notifier.alert(appCtx, "Liquid Baby", "A sound was recorded (" + (Wav.durationMs(done.length()) / 1000) + " s)")
        }
        changed()
    }

    private fun applySettings(d: ByteArray) {
        if (d.size >= 2) {
            childLength = (d[0].toInt() and 255).coerceIn(15, 60)
            childSens = (d[1].toInt() and 255).coerceIn(1, 5)
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
