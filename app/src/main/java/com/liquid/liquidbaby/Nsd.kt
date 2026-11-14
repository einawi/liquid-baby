package com.liquid.liquidbaby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Finds the child phone by its identity on the local network, using Android's own service discovery (mDNS).
 * The child announces itself under its unit id, so the parent gets its current address without any saved address.
 */
object Nsd {
    private const val TYPE = "_liquidbaby._tcp."
    private var registration: NsdManager.RegistrationListener? = null

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
                    mgr.resolveService(i, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(x: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(x: NsdServiceInfo) {
                            val ip = x.host?.hostAddress
                            if (ip != null && !ip.contains(':')) {
                                found.compareAndSet(null, ip)
                                done.countDown()
                            }
                        }
                    })
                }
            }
            mgr.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
            try { mgr.stopServiceDiscovery(listener) } catch (_: Exception) {}
            found.get()
        } catch (e: Exception) {
            Diag.i("Network search problem: " + e.message)
            null
        }
    }
}
