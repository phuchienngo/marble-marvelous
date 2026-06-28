package com.phuchienngo.marblemarvelous.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudColorSamplerTest {

    @Test
    fun sampleBilinearBlendsBetweenNeighboringPixels() {
        val pixels: IntArray = intArrayOf(
            CLEAR,
            WHITE
        )

        val actual: Int = CloudColorSampler.sampleBilinear(
            pixels = pixels,
            width = 2,
            height = 1,
            sourceX = 0.5,
            sourceY = 0.0
        )

        assertEquals(GRAY, actual)
    }

    companion object {
        private const val CLEAR: Int = 0x00000000
        private const val WHITE: Int = -0x1
        private const val GRAY: Int = -0x7f7f80
    }
}
