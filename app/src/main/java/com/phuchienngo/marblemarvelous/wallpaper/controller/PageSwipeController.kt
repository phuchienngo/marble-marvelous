package com.phuchienngo.marblemarvelous.wallpaper.controller

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class PageSwipeController {
    private var minStepsToSwipe = 3
    private var pageSwipeDamping = 3.0f
    private var lastOffset = 0.0f
    private var easedOffset = 0.0f

    fun getPageOffset(): Float = easedOffset

    fun getPageOffsetRaw(): Float = lastOffset

    fun update(delta: Float): Boolean {
        val ease = (lastOffset - easedOffset) * delta * pageSwipeDamping
        easedOffset += ease
        return abs(ease) > 1.0E-4f
    }

    fun isScrollAnimating(): Boolean = isScrollAnimating(1.0E-4f)

    fun isScrollAnimating(threshold: Float): Boolean = abs(lastOffset - easedOffset) > threshold

    fun setPageSwipe(
        xOffset: Float,
        xOffsetStep: Float,
    ) {
        val numSteps = (1.0f / xOffsetStep).roundToInt()
        val offsetStretch = min((numSteps / minStepsToSwipe).toFloat(), 1.0f)
        lastOffset = xOffset * offsetStretch
    }

    fun differenceBiggerThan(value: Float): Boolean = abs(lastOffset - easedOffset) > value

    fun setMinPagesToSwipe(minPagesToSwipe: Int) {
        minStepsToSwipe = if (minPagesToSwipe < 2) 1 else minPagesToSwipe - 1
    }

    fun setPageSwipeDamping(pageSwipeDamping: Float) {
        this.pageSwipeDamping = pageSwipeDamping
    }
}
