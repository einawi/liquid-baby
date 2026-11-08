package com.liquid.liquidbaby

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.DatagramSocket
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket

/**
 * Local network helpers. All local traffic is pinned to the Wi-Fi network,
 * so it goes around any VPN that is running on the phone.
 */
object Net {

    private fun cm(ctx: Context) = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** When this phone shares its own connection (hotspot), its local network is not the home Wi-Fi. */
    private fun isHotspotNet(c: ConnectivityManager, n: Network): Boolean {
        val name = c.getLinkProperties(n)?.interfaceName ?: return false
        return name.startsWith("ap_") || name.startsWith("swlan")
    }

    private var warnedBind = false

    private fun warnBindOnce(msg: String) {
        if (!warnedBind) {
            warnedBind = true
            Diag.i(msg)
        }
    }

    @Suppress("DEPRECATION")
    fun wifiNetwork(ctx: Context): Network? {
        return try {
            val c = cm(ctx)
            c.allNetworks.firstOrNull { n ->
                val caps = c.getNetworkCapabilities(n)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    !isHotspotNet(c, n)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tunnelInterfaceUp(): Boolean {
        return try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return false
            all.asSequence().any { it.isUp && (it.name.startsWith("tun") || it.name.startsWith("ppp") || it.name.startsWith("tap")) }
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun vpnActive(ctx: Context): Boolean {
        val byApi = try {
            val c = cm(ctx)
            c.allNetworks.any { n ->
                c.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (e: Exception) {
            false
        }
        return byApi || tunnelInterfaceUp()
    }

    /** What Android tells us about the networks. Added to the Diagnostics report. */
    @Suppress("DEPRECATION")
    fun dump(ctx: Context): String {
        val sb = StringBuilder("Networks seen by the app:\n")
        try {
            val c = cm(ctx)
            val nets = c.allNetworks
            if (nets.isEmpty()) sb.append("  (none)\n")
            for (n in nets) {
                val caps = c.getNetworkCapabilities(n)
                val lp = c.getLinkProperties(n)
                sb.append("  ").append(n).append(" ").append(caps?.toString() ?: "no capabilities")
                    .append(" | ").append(lp?.interfaceName).append(" ").append(lp?.linkAddresses).append("\n")
            }
        } catch (e: Exception) {
            sb.append("  error: ").append(e.message).append("\n")
        }
        try {
            val all = NetworkInterface.getNetworkInterfaces()
            if (all != null) {
                sb.append("Interfaces: ")
                for (ni in all) {
                    if (ni.isLoopback) continue
                    sb.append(ni.name).append(if (ni.isUp) " up " else " down ")
                    sb.append(ni.interfaceAddresses.filter { it.address is Inet4Address }.joinToString(",") { it.address.hostAddress ?: "" }).append("; ")
                }
                sb.append("\n")
            }
        } catch (e: Exception) {
            sb.append("Interfaces: error ").append(e.message).append("\n")
        }
        return sb.toString()
    }

    private fun ipViaNetworkApi(ctx: Context): String? {
        return try {
            val n = wifiNetwork(ctx) ?: return null
            val lp = cm(ctx).getLinkProperties(n) ?: return null
            lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun wifiInterface(): NetworkInterface? {
        return try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in all) {
                if (!ni.isUp || ni.isLoopback) continue
                if (!ni.name.startsWith("wlan")) continue
                if (ni.name.startsWith("ap_") || ni.name.startsWith("swlan")) continue
                if (ni.interfaceAddresses.any { it.address is Inet4Address }) return ni
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun ipViaWifiManager(ctx: Context): String? {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo?.ipAddress ?: 0
            if (ip == 0) null
            else (ip and 255).toString() + "." + ((ip shr 8) and 255) + "." + ((ip shr 16) and 255) + "." + ((ip shr 24) and 255)
        } catch (e: Exception) {
            null
        }
    }

    /** Wi-Fi address of this phone. Tries three ways because old phones answer differently. */
    fun wifiIp(ctx: Context): String? {
        ipViaNetworkApi(ctx)?.let { return it }
        wifiInterface()?.interfaceAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress?.let { return it }
        return ipViaWifiManager(ctx)
    }

    /** Subnet broadcast address such as 192.168.1.255, or null. */
    fun broadcastAddress(ctx: Context): String? {
        try {
            val ia = wifiInterface()?.interfaceAddresses?.firstOrNull { it.address is Inet4Address }
            val bc = ia?.broadcast?.hostAddress
            if (bc != null) return bc
        } catch (e: Exception) {
        }
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
            warnBindOnce("Could not pin a TCP socket to Wi-Fi: " + e.message)
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
            warnBindOnce("Could not pin a UDP socket to Wi-Fi (continuing without): " + e.message)
            false
        }
    }

    @Volatile
    private var bypassCheckedAt = 0L

    @Volatile
    private var bypassOk = true

    /** True when the app may send local traffic straight over Wi-Fi (no VPN, or a VPN that allows it). */
    fun canUseWifiDirectly(ctx: Context): Boolean {
        if (!vpnActive(ctx)) return true
        val now = System.currentTimeMillis()
        if (now - bypassCheckedAt < 10_000) return bypassOk
        var ok = false
        var ds: DatagramSocket? = null
        try {
            val n = wifiNetwork(ctx)
            if (n != null) {
                ds = DatagramSocket(null)
                n.bindSocket(ds)
                ok = true
            }
        } catch (e: Exception) {
            ok = false
        } finally {
            ds?.close()
        }
        bypassOk = ok
        bypassCheckedAt = now
        return ok
    }

    /** A VPN is on and Android does not let this app go around it: local connections will fail. */
    fun vpnBlocksLocal(ctx: Context): Boolean = vpnActive(ctx) && !canUseWifiDirectly(ctx)

    fun describe(ctx: Context): String {
        val ip = wifiIp(ctx)
        val wifi = if (ip == null) "Wi-Fi: not found" else "Wi-Fi IP: $ip"
        val wifiNoInternet = try {
            val w = wifiNetwork(ctx)
            w != null && cm(ctx).getNetworkCapabilities(w)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == false
        } catch (e: Exception) {
            false
        }
        val vpn = when {
            !vpnActive(ctx) -> "VPN: off"
            canUseWifiDirectly(ctx) -> "VPN: ON (the app goes around it over Wi-Fi)"
            else -> "VPN: ON - it BLOCKS local connections. Turn the VPN off on this phone."
        }
        val extra = if (wifiNoInternet) "\nThe Wi-Fi has no internet. Keep mobile data off on this phone for a steadier connection." else ""
        return "$wifi\n$vpn$extra"
    }
}
