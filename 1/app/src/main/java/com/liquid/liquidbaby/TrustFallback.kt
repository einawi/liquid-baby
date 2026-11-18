package com.liquid.liquidbaby

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Old phones (for example Android 6) do not know some newer certificate authorities,
 * so GitHub downloads fail with "Trust anchor not found". This adds a built-in list of
 * well-known authorities (Mozilla list, res/raw/cacert.pem) to the ones the phone has.
 */
object TrustFallback {
    @Volatile
    private var factory: SSLSocketFactory? = null

    @Synchronized
    fun socketFactory(ctx: Context): SSLSocketFactory {
        factory?.let { return it }
        val cf = CertificateFactory.getInstance("X.509")
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)

        var added = 0
        try {
            val sys = KeyStore.getInstance("AndroidCAStore")
            sys.load(null, null)
            val aliases = sys.aliases()
            while (aliases.hasMoreElements()) {
                val a = aliases.nextElement()
                val c = sys.getCertificate(a)
                if (c != null) {
                    ks.setCertificateEntry("sys-$a", c)
                    added++
                }
            }
        } catch (e: Exception) {
            Diag.i("Could not read the phone's certificate list: " + e.message)
        }

        val text = ctx.resources.openRawResource(R.raw.cacert).bufferedReader().use { it.readText() }
        var bundled = 0
        for (part in text.split("-----END CERTIFICATE-----")) {
            val i = part.indexOf("-----BEGIN CERTIFICATE-----")
            if (i < 0) continue
            try {
                val b64 = part.substring(i + 27).replace("\\s".toRegex(), "")
                val der = Base64.decode(b64, Base64.DEFAULT)
                ks.setCertificateEntry("bundled-$bundled", cf.generateCertificate(ByteArrayInputStream(der)))
                bundled++
            } catch (e: Exception) {
                Diag.i("Skipped one built-in certificate: " + e.message)
            }
        }
        Diag.i("Certificate fallback ready: $added from the phone, $bundled built in")

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, tmf.trustManagers, null)
        val f = sc.socketFactory
        factory = f
        return f
    }
}
