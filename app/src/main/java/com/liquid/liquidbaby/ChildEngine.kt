package com.liquid.liquidbaby

import android.content.Context
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
        changed()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        status = "Stopped"
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
                T.SYNC -> sendBacklog(c, if (m.data.size >= 8) Wire.bytesLong(m.data) else 0L)
            }
        }
        return id
    }

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
