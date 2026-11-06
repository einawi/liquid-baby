package com.liquid.liquidbaby

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Socket

/**
 * Local network helpers. All local traffic is pinned to the Wi-Fi network,
 * so it goes around any VPN that is running on the phone.
 */
object Net {

    private fun cm(ctx: Context) = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    fun wifiNetwork(ctx: Context): Network? {
        return try {
            val c = cm(ctx)
            c.allNetworks.firstOrNull { n ->
                val caps = c.getNetworkCapabilities(n)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun vpnActive(ctx: Context): Boolean {
        return try {
            val c = cm(ctx)
            c.allNetworks.any { n ->
                c.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun wifiIp(ctx: Context): String? {
        return try {
            val n = wifiNetwork(ctx) ?: return null
            val lp = cm(ctx).getLinkProperties(n) ?: return null
            lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    /** Subnet broadcast address such as 192.168.1.255, or null. */
    fun broadcastAddress(ctx: Context): String? {
        return try {
            val n = wifiNetwork(ctx) ?: return null
            val lp = cm(ctx).getLinkProperties(n) ?: return null
            val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
            val b = la.address.address
            var ip = 0
            for (x in b) ip = (ip shl 8) or (x.toInt() and 255)
            val prefix = la.prefixLength
            val mask = if (prefix <= 0) 0 else (-1 shl (32 - prefix))
            val bc = ip or mask.inv()
            ((bc ushr 24) and 255).toString() + "." + ((bc ushr 16) and 255) + "." +
                ((bc ushr 8) and 255) + "." + (bc and 255)
        } catch (e: Exception) {
            null
        }
    }

    fun bind(ctx: Context, s: Socket): Boolean {
        return try {
            val n = wifiNetwork(ctx)
            if (n == null) {
                false
            } else {
                n.bindSocket(s)
                true
            }
        } catch (e: Exception) {
            Diag.i("Could not pin a TCP socket to Wi-Fi: " + e.message)
            false
        }
    }

    fun bind(ctx: Context, s: DatagramSocket): Boolean {
        return try {
            val n = wifiNetwork(ctx)
            if (n == null) {
                false
            } else {
                n.bindSocket(s)
                true
            }
        } catch (e: Exception) {
            Diag.i("Could not pin a UDP socket to Wi-Fi (continuing without): " + e.message)
            false
        }
    }

    fun describe(ctx: Context): String {
        val ip = wifiIp(ctx)
        val wifi = if (ip == null) "Wi-Fi: not found" else "Wi-Fi IP: $ip"
        val vpn = if (vpnActive(ctx)) "VPN: ON (app talks directly over Wi-Fi)" else "VPN: off"
        return "$wifi\n$vpn"
    }
}
