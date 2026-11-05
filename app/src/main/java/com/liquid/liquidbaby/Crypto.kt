package com.liquid.liquidbaby

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pairing: both phones do an ECDH key exchange and show the same 6-digit
 * number (derived from the shared secret). The user checks that both phones
 * show the same number, which defeats anyone listening on the network.
 * Afterwards every message is encrypted with AES-256-GCM.
 */
object Crypto {
    private val rnd = SecureRandom()

    fun random(n: Int): ByteArray {
        val b = ByteArray(n)
        rnd.nextBytes(b)
        return b
    }

    fun newKeyPair(): KeyPair {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        return g.generateKeyPair()
    }

    fun agree(priv: PrivateKey, peerPub: ByteArray): ByteArray {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPub))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) md.update(p)
        return md.digest()
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        for (p in parts) m.update(p)
        return m.doFinal()
    }

    /** Six digits shown on both phones, e.g. "123 456". */
    fun sas(secret: ByteArray, parentPub: ByteArray, childPub: ByteArray): String {
        val h = sha256("LBSAS".toByteArray(), secret, parentPub, childPub)
        val v = ByteBuffer.wrap(h, 0, 4).int.toLong() and 0xffffffffL
        val s = String.format(Locale.US, "%06d", v % 1_000_000L)
        return s.substring(0, 3) + " " + s.substring(3)
    }

    /** Long-term key saved by both phones after a successful pairing. */
    fun pairKey(secret: ByteArray): ByteArray = sha256("LBKEY".toByteArray(), secret)

    fun sessionKey(pairKey: ByteArray, nonceParent: ByteArray, nonceChild: ByteArray): ByteArray =
        hmac(pairKey, "LBSESS".toByteArray(), nonceParent, nonceChild)
}

/** One direction pair of AES-GCM with counters used as nonces. */
class Aead(key: ByteArray, private val sendPrefix: Int, private val recvPrefix: Int) {
    private val k = SecretKeySpec(key, "AES")
    private var sendCtr = 0L
    private var recvCtr = 0L

    private fun nonce(prefix: Int, ctr: Long): ByteArray =
        ByteBuffer.allocate(12).putInt(prefix).putLong(ctr).array()

    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, nonce(sendPrefix, sendCtr)))
        sendCtr++
        return c.doFinal(plain)
    }

    fun open(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, nonce(recvPrefix, recvCtr)))
        recvCtr++
        return c.doFinal(data)
    }
}
