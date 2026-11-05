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

    /** Parent side. Blocking, about 2.5 seconds. */
    fun scan(ctx: Context): List<Found> {
        val found = LinkedHashMap<String, Found>()
        var ds: DatagramSocket? = null
        try {
            ds = DatagramSocket()
            Net.bind(ctx, ds)
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
        return found.values.toList()
    }

    /** Child side. Runs until the socket is closed. */
    fun startResponder(ctx: Context, id: String, name: String): DatagramSocket {
        val ds = DatagramSocket(null)
        ds.reuseAddress = true
        ds.bind(InetSocketAddress(UDP_PORT))
        Net.bind(ctx, ds)
        Thread {
            val buf = ByteArray(512)
            while (!ds.isClosed) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    ds.receive(p)
                    if (String(p.data, 0, p.length) == ASK) {
                        val reply = (ANSWER + "|" + id + "|" + name.replace("|", " ") + "|" + TCP_PORT).toByteArray()
                        ds.send(DatagramPacket(reply, reply.size, p.address, p.port))
                    }
                } catch (e: Exception) {
                    if (!ds.isClosed) Diag.i("Responder: " + e.message)
                }
            }
        }.start()
        return ds
    }
}
