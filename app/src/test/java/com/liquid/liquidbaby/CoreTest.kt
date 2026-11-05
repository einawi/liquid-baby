package com.liquid.liquidbaby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

class CoreTest {

    @Test
    fun hexRoundTrip() {
        val b = byteArrayOf(0, 1, 15, 16, 127, -128, -1)
        assertEquals("00010f107f80ff", Hex.encode(b))
        assertArrayEquals(b, Hex.decode("00010f107f80ff"))
    }

    @Test
    fun hexRejectsBadInput() {
        try {
            Hex.decode("abc")
            fail("odd length must fail")
        } catch (e: IllegalArgumentException) {
        }
        try {
            Hex.decode("zz")
            fail("bad digits must fail")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun wirePackUnpack() {
        val a = byteArrayOf(1, 2, 3)
        val b = ByteArray(0)
        val c = ByteArray(300) { it.toByte() }
        val out = Wire.unpack(Wire.pack(a, b, c))
        assertEquals(3, out.size)
        assertArrayEquals(a, out[0])
        assertEquals(0, out[1].size)
        assertArrayEquals(c, out[2])
    }

    @Test
    fun wireRejectsTruncated() {
        val p = Wire.pack(ByteArray(10))
        try {
            Wire.unpack(p.copyOf(5))
            fail("truncated data must fail")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun longBytes() {
        assertEquals(1234567890123L, Wire.bytesLong(Wire.longBytes(1234567890123L)))
    }

    @Test
    fun wavHeaderAndDuration() {
        val h = Wav.header(32000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4))
        assertEquals("WAVE", String(h, 8, 4))
        assertEquals(1000L, Wav.durationMs(32044))
        assertEquals(0L, Wav.durationMs(10))
    }

    @Test
    fun ecdhBothSidesAgreeAndShowSameNumber() {
        val parent = Crypto.newKeyPair()
        val child = Crypto.newKeyPair()
        val s1 = Crypto.agree(parent.private, child.public.encoded)
        val s2 = Crypto.agree(child.private, parent.public.encoded)
        assertArrayEquals(s1, s2)
        val sasParent = Crypto.sas(s1, parent.public.encoded, child.public.encoded)
        val sasChild = Crypto.sas(s2, parent.public.encoded, child.public.encoded)
        assertEquals(sasParent, sasChild)
        assertTrue(Regex("\\d{3} \\d{3}").matches(sasParent))
        assertArrayEquals(Crypto.pairKey(s1), Crypto.pairKey(s2))
    }

    @Test
    fun differentKeysGiveDifferentNumbers() {
        val p = Crypto.newKeyPair()
        val c = Crypto.newKeyPair()
        val attacker = Crypto.newKeyPair()
        val real = Crypto.agree(p.private, c.public.encoded)
        val mitm = Crypto.agree(p.private, attacker.public.encoded)
        val a = Crypto.sas(real, p.public.encoded, c.public.encoded)
        val b = Crypto.sas(mitm, p.public.encoded, attacker.public.encoded)
        assertFalse(a == b)
    }

    @Test
    fun aeadRoundTripBothDirections() {
        val key = Crypto.random(32)
        val parent = Aead(key, 0, 1)
        val child = Aead(key, 1, 0)
        for (i in 0 until 5) {
            val m = ("hello $i").toByteArray()
            assertArrayEquals(m, child.open(parent.seal(m)))
            val r = ("reply $i").toByteArray()
            assertArrayEquals(r, parent.open(child.seal(r)))
        }
    }

    @Test
    fun aeadWrongKeyFails() {
        val parent = Aead(Crypto.random(32), 0, 1)
        val child = Aead(Crypto.random(32), 1, 0)
        try {
            child.open(parent.seal("secret".toByteArray()))
            fail("wrong key must fail")
        } catch (e: GeneralSecurityException) {
        }
    }

    @Test
    fun aeadDetectsTampering() {
        val key = Crypto.random(32)
        val parent = Aead(key, 0, 1)
        val child = Aead(key, 1, 0)
        val sealed = parent.seal("secret".toByteArray())
        sealed[0] = (sealed[0].toInt() xor 1).toByte()
        try {
            child.open(sealed)
            fail("tampering must fail")
        } catch (e: GeneralSecurityException) {
        }
    }

    @Test
    fun sessionKeyDependsOnNonces() {
        val pk = Crypto.random(32)
        val a = Crypto.sessionKey(pk, byteArrayOf(1), byteArrayOf(2))
        val b = Crypto.sessionKey(pk, byteArrayOf(1), byteArrayOf(3))
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun quietRoomNeverTriggers() {
        val d = SoundDetector()
        for (i in 0 until 1000) assertFalse(d.feed(60.0 + (i % 7)))
    }

    @Test
    fun threeLoudBlocksTrigger() {
        val d = SoundDetector()
        repeat(20) { d.feed(80.0) }
        assertFalse(d.feed(4000.0))
        assertFalse(d.feed(4000.0))
        assertTrue(d.feed(4000.0))
    }

    @Test
    fun shortNoiseDoesNotTrigger() {
        val d = SoundDetector()
        repeat(20) { d.feed(80.0) }
        assertFalse(d.feed(4000.0))
        assertFalse(d.feed(4000.0))
        assertFalse(d.feed(80.0))
        assertFalse(d.feed(4000.0))
        assertFalse(d.feed(4000.0))
    }

    @Test
    fun sensitivityChangesTheThreshold() {
        val low = SoundDetector()
        low.sensitivity = 1
        val high = SoundDetector()
        high.sensitivity = 5
        var lowHit = false
        var highHit = false
        repeat(10) {
            if (low.feed(1000.0)) lowHit = true
            if (high.feed(1000.0)) highHit = true
        }
        assertFalse(lowHit)
        assertTrue(highHit)
    }
}
