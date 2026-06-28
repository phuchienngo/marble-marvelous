package com.phuchienngo.marblemarvelous.utils

import kotlin.random.Random

object BaseMathUtils {
    const val DEGREES_TO_RAD = 0.017453292f
    const val RAD_TO_DEGREES = 57.29578f

    @JvmStatic
    fun map(
        value: Float,
        inMin: Float,
        inMax: Float,
        outMin: Float,
        outMax: Float,
    ): Float =
        if (value < inMin) {
            outMin
        } else if (value > inMax) {
            outMax
        } else {
            (((value - inMin) / (inMax - inMin)) * (outMax - outMin)) + outMin
        }

    @JvmStatic
    fun map(
        value: Double,
        inMin: Double,
        inMax: Double,
        outMin: Double,
        outMax: Double,
    ): Double =
        if (value < inMin) {
            outMin
        } else if (value > inMax) {
            outMax
        } else {
            (((value - inMin) / (inMax - inMin)) * (outMax - outMin)) + outMin
        }

    @JvmStatic
    fun mix(
        fromValue: Float,
        toValue: Float,
        progress: Float,
    ): Float =
        if (progress < 0.0f) {
            fromValue
        } else if (progress > 1.0f) {
            toValue
        } else {
            ((toValue - fromValue) * progress) + fromValue
        }

    @JvmStatic
    fun mix(
        fromValue: Double,
        toValue: Double,
        progress: Double,
    ): Double =
        if (progress < 0.0) {
            fromValue
        } else if (progress > 1.0) {
            toValue
        } else {
            ((toValue - fromValue) * progress) + fromValue
        }

    @JvmStatic
    fun smoothstep(
        edge0: Float,
        edge1: Float,
        x: Float,
    ): Float {
        val x2 = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f)
        return x2 * x2 * (3.0f - (2.0f * x2))
    }

    @JvmStatic
    fun smootherstep(
        edge0: Float,
        edge1: Float,
        x: Float,
    ): Float {
        val x2 = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f)
        return x2 * x2 * x2 * ((((6.0f * x2) - 15.0f) * x2) + 10.0f)
    }

    @JvmStatic
    fun clamp(
        x: Float,
        lowerLimit: Float,
        upperLimit: Float,
    ): Float =
        if (x < lowerLimit) {
            lowerLimit
        } else if (x > upperLimit) {
            upperLimit
        } else {
            x
        }

    @JvmStatic
    fun randomMapped(
        min: Float,
        max: Float,
    ): Float = map(Random.nextDouble().toFloat(), 0.0f, 1.0f, min, max)

    @JvmStatic
    fun isPointInsideRectangle(
        posX: Float,
        posY: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean = posX >= left && posX <= right && posY >= top && posY <= bottom
}
