package com.phuchienngo.marblemarvelous.weather

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

internal object CloudColorSampler {

    fun sampleBilinear(
        pixels: IntArray,
        width: Int,
        height: Int,
        sourceX: Double,
        sourceY: Double
    ): Int {
        require(width > 0)
        require(height > 0)
        require(pixels.size >= width * height)

        val wrappedX: Double = wrapHorizontal(sourceX, width)
        val clampedY: Double = sourceY.coerceIn(0.0, (height - 1).toDouble())
        val x0: Int = floor(wrappedX).toInt()
        val y0: Int = floor(clampedY).toInt()
        val x1: Int = (x0 + 1) % width
        val y1: Int = minOf(y0 + 1, height - 1)
        val xWeight: Double = wrappedX - x0
        val yWeight: Double = clampedY - y0

        val topLeft: Int = getCloudValue(pixels[y0 * width + x0])
        val topRight: Int = getCloudValue(pixels[y0 * width + x1])
        val bottomLeft: Int = getCloudValue(pixels[y1 * width + x0])
        val bottomRight: Int = getCloudValue(pixels[y1 * width + x1])
        val top: Double = lerp(topLeft.toDouble(), topRight.toDouble(), xWeight)
        val bottom: Double = lerp(bottomLeft.toDouble(), bottomRight.toDouble(), xWeight)
        val cloud: Int = lerp(top, bottom, yWeight)
            .roundToInt()
            .coerceIn(0, 255)

        return -0x1000000 or (cloud shl 16) or (cloud shl 8) or cloud
    }

    private fun wrapHorizontal(sourceX: Double, width: Int): Double {
        val widthDouble: Double = width.toDouble()
        return ((sourceX % widthDouble) + widthDouble) % widthDouble
    }

    private fun getCloudValue(argb: Int): Int {
        val alpha: Int = (argb ushr 24) and 0xFF
        val luma: Int = (
            0.299 * ((argb shr 16) and 0xFF) +
                0.587 * ((argb shr 8) and 0xFF) +
                0.114 * (argb and 0xFF)
            ).toInt()
        val sampledCloud: Int = if (alpha == 0) {
            luma
        } else {
            (alpha * luma) / 255
        }
        return max(alpha, sampledCloud)
    }

    private fun lerp(start: Double, end: Double, amount: Double): Double {
        return start + (end - start) * amount
    }
}
