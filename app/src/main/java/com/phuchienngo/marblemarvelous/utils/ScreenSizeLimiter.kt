package com.phuchienngo.marblemarvelous.utils

import kotlin.math.*

object ScreenSizeLimiter {
    const val MAX_HEIGHT = 2160
    const val MAX_WIDTH = 1080

    @JvmStatic
    fun getScale(
        w: Int,
        h: Int,
    ): Float = getScale(w, h, MAX_WIDTH, MAX_HEIGHT)

    @JvmStatic
    fun getScale(
        w: Int,
        h: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Float {
        // Compare the screen's short side against maxWidth and long side against
        // maxHeight so the cap is orientation-independent. (The original decompiled
        // code had a broken no-op swap here that only affected landscape.)
        // Division is intentionally integer — it only downscales screens >= ~2x the
        // cap, leaving normal phones at full render resolution (keeps the globe sharp).
        val shortSide = min(w, h)
        val longSide = max(w, h)
        return max(max(shortSide / maxWidth, longSide / maxHeight).toFloat(), 1.0f)
    }

    @JvmStatic
    fun getScaledSize(
        w: Int,
        h: Int,
    ): IntArray = getScaledSize(w, h, MAX_WIDTH, MAX_HEIGHT)

    @JvmStatic
    fun getScaledSize(
        w: Int,
        h: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): IntArray {
        val scale = getScale(w, h, maxWidth, maxHeight)
        return intArrayOf((w / scale).roundToInt(), (h / scale).roundToInt())
    }
}
