package com.liquid.liquidbaby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Finds the child phone by its identity on the local network, using Android's own service discovery (mDNS).
 * The child announces itself under its unit id, so the parent gets its current address without any saved address.
 */
object Nsd {
    private const val TYPE = "_liquidbaby._tcp."
    private var registration: NsdManager.RegistrationListener? = null
    private val resolver: Executor = Executors.newSingleThreadExecutor()

    private fun nameFor(unitId: String): String = "lb" + unitId.take(16)

    /** Child phone: announce this phone on the network. */
    fun register(ctx: Context, unitId: String) {
        try {
            val mgr = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = nameFor(unitId)
                serviceType = TYPE
                port = Discovery.TCP_PORT
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) { Diag.i("Announced on the network as " + i.serviceName) }
                override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Diag.i("Announce failed: code " + code) }
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
            }
            registration = listener
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Diag.i("Announce problem: " + e.message)
        }
    }

    fun unregister(ctx: Context) {
        val l = registration ?: return
        registration = null
        try {
            (ctx.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(l)
        } catch (_: Exception) {
        }
    }

    /**
     * Turns a found service into its IPv4 address and passes it to onIp.
     * Android 14 and later: the callback API, which keeps the address up to date.
     * Older phones: the classic resolve call.
     */
    private fun resolveIp(mgr: NsdManager, info: NsdServiceInfo, onIp: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Diag.i("Address callback failed: code $errorCode")
                }
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    val ip = serviceInfo.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                    if (ip != null) {
                        onIp(ip)
                        try {
                            mgr.unregisterServiceInfoCallback(this)
                        } catch (_: Exception) {
                        }
                    }
                }
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            }
            try {
                mgr.registerServiceInfoCallback(info, resolver, callback)
            } catch (e: Exception) {
                Diag.i("Address callback problem: " + e.message)
            }
        } else {
            @Suppress("DEPRECATION")
            mgr.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(x: NsdServiceInfo, errorCode: Int) {}
                @Suppress("DEPRECATION")
                override fun onServiceResolved(x: NsdServiceInfo) {
                    val ip = x.host?.hostAddress
                    if (ip != null && !ip.contains(':')) onIp(ip)
                }
            })
        }
    }

    /** Parent, pairing: every child phone announcing itself, as (name, ip). */
    fun findAll(ctx: Context, timeoutMs: Long = 4000L): List<Pair<String, String>> {
        return try {
            val mgr = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            val list = java.util.Collections.synchronizedList(ArrayList<Pair<String, String>>())
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(i: NsdServiceInfo) {}
                override fun onServiceFound(i: NsdServiceInfo) {
                    if (!i.serviceName.startsWith("lb")) return
                    resolveIp(mgr, i) { ip -> list.add(Pair(i.serviceName, ip)) }
                }
            }
            mgr.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            Thread.sleep(timeoutMs)
            try { mgr.stopServiceDiscovery(listener) } catch (_: Exception) {}
            synchronized(list) { list.distinctBy { it.second } }
        } catch (e: Exception) {
            Diag.i("Network announcement search problem: " + e.message)
            emptyList()
        }
    }

    /** Parent: the paired child's current IPv4 address, or null if it is not announced. */
    fun findIp(ctx: Context, unitId: String, timeoutMs: Long = 4000L): String? {
        return try {
            val mgr = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            val wanted = nameFor(unitId)
            val found = AtomicReference<String?>(null)
            val done = CountDownLatch(1)
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { done.countDown() }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(i: NsdServiceInfo) {}
                override fun onServiceFound(i: NsdServiceInfo) {
                    if (i.serviceName != wanted) return
                    resolveIp(mgr, i) { ip ->
                        found.compareAndSet(null, ip)
                        done.countDown()
                    }
                }
            }
            mgr.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
            try { mgr.stopServiceDiscovery(listener) } catch (_: Exception) {}
            val result = found.get()
            Diag.i(if (result != null) "Network announcement found the child phone at $result"
                   else "Network announcement: child phone not announced (search time ${timeoutMs} ms)")
            result
        } catch (e: Exception) {
            Diag.i("Network search problem: " + e.message)
            null
        }
    }
}
