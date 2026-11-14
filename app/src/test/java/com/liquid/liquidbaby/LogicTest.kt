package com.liquid.liquidbaby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun retentionDeletesOldestAndKeepsNewest() {
        val names = listOf("300.wav", "100.wav", "200.wav", "400.wav")
        assertEquals(listOf("100.wav", "200.wav"), Retention.toDelete(names, 2))
    }

    @Test
    fun retentionKeepsEverythingUnderTheLimit() {
        assertTrue(Retention.toDelete(listOf("1.wav", "2.wav"), 3).isEmpty())
        assertTrue(Retention.toDelete(listOf("1.wav", "2.wav", "3.wav"), 3).isEmpty())
    }

    @Test
    fun rateGateLetsOneThroughPerInterval() {
        val g = RateGate()
        assertTrue(g.allow("a", 0L, 10_000L))
        assertFalse(g.allow("a", 5_000L, 10_000L))
        assertTrue(g.allow("a", 10_000L, 10_000L))
        assertTrue(g.allow("b", 5_000L, 10_000L))
    }

    @Test
    fun plainWavIsNotTakenAsEncrypted() {
        assertFalse(ClipCrypt.isEncrypted("RIFF0000".toByteArray()))
        assertTrue(ClipCrypt.isEncrypted(byteArrayOf(0x4C, 0x42, 0x45, 0x31, 1, 2, 3)))
    }

    @Test
    fun durationFromWavSize() {
        assertEquals(1000L, Wav.durationMs(44 + 32000L))
        assertEquals(0L, Wav.durationMs(10L))
    }

    @Test
    fun latestNotesKeepsOnlyTheNewestVersion() {
        val text = "Release notes:\n0.8.5: new thing\n- a\n\n0.8.5: arabic line\n\n0.8.4: old thing\n- b\n"
        val got = latestReleaseNotes(text)
        assertTrue(got.contains("0.8.5: new thing"))
        assertTrue(got.contains("0.8.5: arabic line"))
        assertFalse(got.contains("0.8.4"))
        assertFalse(got.contains("Release notes:"))
    }
}
