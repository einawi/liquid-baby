package com.liquid.liquidbaby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class YuvTest {

    private val expected = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 20, 10, 21, 11)

    @Test
    fun planarLayout() {
        val y = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val u = ByteBuffer.wrap(byteArrayOf(10, 11))
        val v = ByteBuffer.wrap(byteArrayOf(20, 21))
        assertArrayEquals(expected, Yuv.toNv21(4, 2, y, 4, u, 2, 1, v, 2, 1))
    }

    @Test
    fun semiPlanarLayoutWithPixelStrideTwo() {
        val y = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val u = ByteBuffer.wrap(byteArrayOf(10, 99, 11, 99))
        val v = ByteBuffer.wrap(byteArrayOf(20, 99, 21, 99))
        assertArrayEquals(expected, Yuv.toNv21(4, 2, y, 4, u, 4, 2, v, 4, 2))
    }

    @Test
    fun paddedRows() {
        val y = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 0, 0, 5, 6, 7, 8, 0, 0))
        val u = ByteBuffer.wrap(byteArrayOf(10, 11, 0))
        val v = ByteBuffer.wrap(byteArrayOf(20, 21, 0))
        assertArrayEquals(expected, Yuv.toNv21(4, 2, y, 6, u, 3, 1, v, 3, 1))
    }

    @Test
    fun picksClosestFourByThreeSize() {
        val sizes = listOf(
            intArrayOf(1920, 1080), intArrayOf(1280, 720), intArrayOf(640, 480),
            intArrayOf(320, 240), intArrayOf(176, 144), intArrayOf(480, 640)
        )
        assertArrayEquals(intArrayOf(640, 480), Yuv.pickSize(sizes, 1))
        assertArrayEquals(intArrayOf(320, 240), Yuv.pickSize(sizes, 0))
    }

    @Test
    fun prefersFourByThreeOverWide() {
        val sizes = listOf(intArrayOf(1920, 1080), intArrayOf(1280, 720), intArrayOf(800, 600))
        assertArrayEquals(intArrayOf(800, 600), Yuv.pickSize(sizes, 1))
    }

    @Test
    fun noSizesGivesNull() {
        assertNull(Yuv.pickSize(emptyList(), 1))
        assertEquals(null, Yuv.pickSize(listOf(intArrayOf(480, 640)), 1))
    }
}
