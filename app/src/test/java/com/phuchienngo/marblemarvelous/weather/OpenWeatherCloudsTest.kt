package com.phuchienngo.marblemarvelous.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenWeatherCloudsTest {
    @Test
    fun sampleBilinearBlendsBetweenNeighboringCloudValues() {
        // The field holds cloud bytes (0..255); midpoint of 0 and 255 -> 128.
        val field: ByteArray = byteArrayOf(0, 255.toByte())

        val actual: Int =
            OpenWeatherClouds.sampleBilinearCloud(
                field = field,
                width = 2,
                height = 1,
                sourceX = 0.5,
                sourceY = 0.0,
            )

        assertEquals(MID_CLOUD, actual)
    }

    @Test
    fun getCloudValueReturnsLumaForOpaquePixels() {
        assertEquals(OPAQUE_WHITE_CLOUD, OpenWeatherClouds.getCloudValue(WHITE))
        assertEquals(NO_CLOUD, OpenWeatherClouds.getCloudValue(BLACK))
    }

    @Test
    fun getCloudValueScalesDownBySemiTransparency() {
        // 0x80404040: alpha 128, luma 63 (float-truncated) -> (128*63)/255 = 31.
        assertEquals(SUBTLE_DARK_CLOUD, OpenWeatherClouds.getCloudValue(SEMI_TRANSPARENT_DARK))
    }

    @Test
    fun smoothCloudFaceSoftensSingleBrightCloudPixel() {
        val pixels: IntArray =
            intArrayOf(
                BLACK,
                BLACK,
                BLACK,
                BLACK,
                WHITE,
                BLACK,
                BLACK,
                BLACK,
                BLACK,
            )

        val actual: IntArray = OpenWeatherClouds.smoothCloudFace(pixels, width = 3, height = 3)

        assertEquals(CENTER_SOFTENED, actual[4])
        assertEquals(NEIGHBOR_SOFTENED, actual[1])
        assertEquals(CORNER_SOFTENED, actual[0])
    }

    companion object {
        private const val BLACK: Int = -0x1000000
        private const val WHITE: Int = -0x1
        private const val SEMI_TRANSPARENT_DARK: Int = 0x80404040.toInt()
        // Cloud values (0..255), not ARGB.
        private const val MID_CLOUD: Int = 128
        private const val OPAQUE_WHITE_CLOUD: Int = 255
        private const val NO_CLOUD: Int = 0
        private const val SUBTLE_DARK_CLOUD: Int = 31
        // Center-heavy 2-8-2 kernel (weight 20): white center on black neighbours ->
        // center=(255*8+10)/20=102 (0x66), edge=(255*2+10)/20=26 (0x1a), corner=13 (0x0d).
        private const val CENTER_SOFTENED: Int = -0x99999a
        private const val NEIGHBOR_SOFTENED: Int = -0xe5e5e6
        private const val CORNER_SOFTENED: Int = -0xf2f2f3
    }
}
