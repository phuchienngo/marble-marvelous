package com.phuchienngo.marblemarvelous.earth.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GlowMathTest {
    @Test
    fun aspectRatioPreservesPortraitFraction() {
        val aspectRatio: Float = GlowMath.aspectRatio(width = 1080, height = 2400)

        assertEquals(0.45f, aspectRatio, EPSILON)
    }

    companion object {
        private const val EPSILON: Float = 0.0001f
    }
}
