package com.liquid.liquidbaby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilTest {

    @Test
    fun parsesTag() {
        val t = VersionUtil.parseTag("v0.1.0-build7")
        assertEquals("0.1.0", t?.name)
        assertEquals(7, t?.code)
    }

    @Test
    fun parsesTagWithoutV() {
        val t = VersionUtil.parseTag("1.2.3-build12")
        assertEquals("1.2.3", t?.name)
        assertEquals(12, t?.code)
    }

    @Test
    fun rejectsBadTag() {
        assertNull(VersionUtil.parseTag("release-1"))
        assertNull(VersionUtil.parseTag("v1.0.0"))
    }

    @Test
    fun detectsRequiredMarker() {
        assertTrue(VersionUtil.isRequired("Fixes things [required]"))
        assertTrue(VersionUtil.isRequired("[REQUIRED] security"))
        assertFalse(VersionUtil.isRequired("Normal update"))
        assertFalse(VersionUtil.isRequired(null))
    }

    @Test
    fun buildsApkName() {
        assertEquals("LiquidBaby-0_1_0-build3.apk", VersionUtil.apkFileName("LiquidBaby", "0.1.0", 3))
    }

    @Test
    fun offerRules() {
        assertFalse(VersionUtil.shouldOffer(5, 5, 0, false))
        assertFalse(VersionUtil.shouldOffer(4, 5, 0, false))
        assertTrue(VersionUtil.shouldOffer(6, 5, 0, false))
        assertFalse(VersionUtil.shouldOffer(6, 5, 6, false))
        assertTrue(VersionUtil.shouldOffer(6, 5, 6, true))
    }

    @Test
    fun parsesSha256() {
        val h = "ABCDEF0123456789abcdef0123456789ABCDEF0123456789abcdef0123456789"
        assertEquals(h.lowercase(), VersionUtil.parseSha256("$h  app.apk\n"))
        assertNull(VersionUtil.parseSha256("not a hash"))
    }

    @Test
    fun formatsSize() {
        assertEquals("500 B", VersionUtil.formatSize(500))
        assertEquals("2 KB", VersionUtil.formatSize(2048))
        assertEquals("1.5 MB", VersionUtil.formatSize(1572864))
    }
}
