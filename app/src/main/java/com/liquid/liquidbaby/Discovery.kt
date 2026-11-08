package com.liquid.liquidbaby

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** Finds the child phone on the Wi-Fi with a small UDP broadcast (no internet needed). */
object Discovery {
    const val UDP_PORT = 45454
    const val TCP_PORT = 45455
    private const val ASK = "LBQ1"
    private const val ANSWER = "LBR1"

    class Found(val id: String, val name: String, val ip: String)

    @Volatile
    private var responderRunning = false

    @Volatile
    private var lastLogMs = 0L

    /** Parent side. Blocking, about 2.5 seconds. */
    fun scan(ctx: Context): List<Found> {
        val found = LinkedHashMap<String, Found>()
        var ds: DatagramSocket? = null
        try {
            ds = DatagramSocket(null)
            Net.bind(ctx, ds)
            ds.bind(InetSocketAddress(0))
            ds.broadcast = true
            val data = ASK.toByteArray()
            val targets = ArrayList<String>()
            targets.add("255.255.255.255")
            Net.broadcastAddress(ctx)?.let { if (it !in targets) targets.add(it) }
            for (round in 0 until 3) {
                for (t in targets) {
                    try {
                        ds.send(DatagramPacket(data, data.size, InetAddress.getByName(t), UDP_PORT))
                    } catch (e: Exception) {
                        Diag.i("Broadcast to $t failed: " + e.message)
                    }
                }
                val until = System.currentTimeMillis() + 800
                ds.soTimeout = 300
                while (System.currentTimeMillis() < until) {
                    val buf = ByteArray(512)
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        ds.receive(p)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val parts = String(p.data, 0, p.length).split("|")
                    if (parts.size >= 4 && parts[0] == ANSWER) {
                        val ip = p.address.hostAddress ?: continue
                        found[parts[1]] = Found(parts[1], parts[2], ip)
                    }
                }
            }
        } catch (e: Exception) {
            Diag.e("Scan failed", e)
        } finally {
            ds?.close()
        }
        Diag.i("Scan finished: " + found.size + " child phone(s) found")
        return found.values.toList()
    }

    /**
     * Child side. Answers scans. The socket is re-created every few seconds and after any error,
     * so a Wi-Fi reconnect (new network, new address) never leaves it silently stuck.
     */
    fun startResponder(ctx: Context, id: String, name: String): DatagramSocket? {
        responderRunning = true
        Thread {
            val buf = ByteArray(512)
            var ds: DatagramSocket? = null
            while (responderRunning) {
                try {
                    val s = ds ?: openResponder(ctx).also { ds = it }
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    if (String(p.data, 0, p.length) == ASK) {
                        val reply = (ANSWER + "|" + id + "|" + name.replace("|", " ") + "|" + TCP_PORT).toByteArray()
                        s.send(DatagramPacket(reply, reply.size, p.address, p.port))
                    }
                } catch (e: SocketTimeoutException) {
                    // quiet period: re-create the socket so it follows the current Wi-Fi network
                    ds?.close()
                    ds = null
                } catch (e: Exception) {
                    if (!responderRunning) break
                    ds?.close()
                    ds = null
                    logThrottled("Responder restarted: " + e.message)
                    try {
                        Thread.sleep(1500)
                    } catch (_: InterruptedException) {
                    }
                }
            }
            ds?.close()
        }.start()
        return null
    }

    fun stopResponder() {
        responderRunning = false
    }

    private fun openResponder(ctx: Context): DatagramSocket {
        val ds = DatagramSocket(null)
        ds.reuseAddress = true
        ds.soTimeout = 8000
        Net.bind(ctx, ds)
        ds.bind(InetSocketAddress(UDP_PORT))
        return ds
    }

    private fun logThrottled(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastLogMs > 60_000L) {
            lastLogMs = now
            Diag.i(msg)
        }
    }
}
