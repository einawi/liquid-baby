package com.liquid.liquidbaby

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Asks Android to keep the Wi-Fi connection alive while monitoring runs. A Wi-Fi network that has no
 * internet is "not needed" by any app, so some phones disconnect it and reconnect it a little later.
 * That breaks the link between the phones. An open request for the Wi-Fi network prevents that.
 * It also logs when the Wi-Fi network appears, disappears or changes its internet state.
 */
object WifiKeeper {
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val lastValidated = HashMap<Network, Boolean>()

    @Synchronized
    fun start(ctx: Context) {
        if (callback != null) return
        try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Diag.i("Wi-Fi network available: $network")
                }

                override fun onLost(network: Network) {
                    synchronized(WifiKeeper) { lastValidated.remove(network) }
                    Diag.i("Wi-Fi network LOST: $network")
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val changed = synchronized(WifiKeeper) {
                        val before = lastValidated.put(network, ok)
                        before == null || before != ok
                    }
                    if (changed) Diag.i("Wi-Fi network $network: " + (if (ok) "has internet" else "has NO internet"))
                }
            }
            cm.requestNetwork(request, cb)
            callback = cb
            Diag.i("Keeping the Wi-Fi network alive")
        } catch (t: Throwable) {
            Diag.i("Could not keep the Wi-Fi network alive: " + t.message)
        }
    }

    @Synchronized
    fun stop(ctx: Context) {
        val cb = callback ?: return
        callback = null
        try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        } catch (_: Throwable) {
        }
        lastValidated.clear()
    }
}
