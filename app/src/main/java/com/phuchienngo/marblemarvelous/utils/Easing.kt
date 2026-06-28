package com.phuchienngo.marblemarvelous.utils

import kotlin.math.pow

object Easing {
    @JvmStatic
    fun linear(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float = ((c * t) / d) + b

    @JvmStatic
    fun expoEaseIn(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float =
        if (t == 0.0f) {
            b
        } else {
            (2.0.pow((10.0f * ((t / d) - 1.0f)).toDouble()).toFloat() * c) + b
        }

    @JvmStatic
    fun expoEaseOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float =
        if (t == d) {
            b + c
        } else {
            ((-2.0.pow((((-10.0f) * t) / d).toDouble()).toFloat() + 1.0f) * c) + b
        }

    @JvmStatic
    fun expoEaseInOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        if (t == 0.0f) {
            return b
        }
        if (t == d) {
            return b + c
        }
        return if (t / (d / 2.0f) < 1.0f) {
            ((c / 2.0f) * 2.0.pow((10.0f * ((t / (d / 2.0f)) - 1.0f)).toDouble()).toFloat()) + b
        } else {
            ((c / 2.0f) * (-2.0.pow(((-10.0f) * ((t / (d / 2.0f)) - 1.0f)).toDouble()).toFloat() + 2.0f)) + b
        }
    }

    @JvmStatic
    fun quadEaseOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / d
        return ((-c) * t2 * (t2 - 2.0f)) + b
    }

    @JvmStatic
    fun quadEaseIn(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / d
        return (t2 * c * t2) + b
    }

    @JvmStatic
    fun quadEaseInOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / (d / 2.0f)
        if (t2 < 1.0f) {
            return ((c / 2.0f) * t2 * t2) + b
        }
        val t3: Float = t2 - 1.0f
        return (((-c) / 2.0f) * ((t3 * (t3 - 2.0f)) - 1.0f)) + b
    }

    @JvmStatic
    fun quartEaseIn(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / d
        return (t2 * c * t2 * t2 * t2) + b
    }

    @JvmStatic
    fun quartEaseOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = (t / d) - 1.0f
        return ((-c) * ((((t2 * t2) * t2) * t2) - 1.0f)) + b
    }

    @JvmStatic
    fun quartEaseInOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / (d / 2.0f)
        if (t2 < 1.0f) {
            return ((c / 2.0f) * t2 * t2 * t2 * t2) + b
        }
        val t3: Float = t2 - 2.0f
        return (((-c) / 2.0f) * ((((t3 * t3) * t3) * t3) - 2.0f)) + b
    }

    @JvmStatic
    fun cubicEaseIn(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / d
        return (t2 * c * t2 * t2) + b
    }

    @JvmStatic
    fun cubicEaseOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = (t / d) - 1.0f
        return (((t2 * t2 * t2) + 1.0f) * c) + b
    }

    @JvmStatic
    fun cubicEaseInOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / (d / 2.0f)
        if (t2 < 1.0f) {
            return ((c / 2.0f) * t2 * t2 * t2) + b
        }
        val t3: Float = t2 - 2.0f
        return ((c / 2.0f) * ((t3 * t3 * t3) + 2.0f)) + b
    }

    @JvmStatic
    fun backEaseIn(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / d
        return (t2 * c * t2 * (((1.0f + 1.70158f) * t2) - 1.70158f)) + b
    }

    @JvmStatic
    fun backEaseOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = (t / d) - 1.0f
        return (((t2 * t2 * (((1.70158f + 1.0f) * t2) + 1.70158f)) + 1.0f) * c) + b
    }

    @JvmStatic
    fun backEaseInOut(
        t: Float,
        b: Float,
        c: Float,
        d: Float
    ): Float {
        val t2: Float = t / (d / 2.0f)
        if (t2 < 1.0f) {
            val s: Float = 1.525f * 1.70158f
            return ((c / 2.0f) * t2 * t2 * (((s + 1.0f) * t2) - s)) + b
        }
        val t3: Float = t2 - 2.0f
        val s2: Float = 1.525f * 1.70158f
        return ((c / 2.0f) * ((t3 * t3 * (((s2 + 1.0f) * t3) + s2)) + 2.0f)) + b
    }
}
