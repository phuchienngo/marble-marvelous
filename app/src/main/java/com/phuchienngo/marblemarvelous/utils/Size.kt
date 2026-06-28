package com.phuchienngo.marblemarvelous.utils

import kotlin.math.*

class Size(
    private var width: Float,
    private var height: Float,
) {
    constructor() : this(1.0f, 1.0f)

    fun getWidth(): Float = width

    fun setWidth(width: Float) {
        this.width = width
    }

    fun getHeight(): Float = height

    fun setHeight(height: Float) {
        this.height = height
    }

    fun set(size: Size) = set(size.width, size.height)

    fun switchSizes() {
        val weightTmp = width
        width = height
        height = weightTmp
    }

    fun set(
        width: Float,
        height: Float,
    ) {
        this.width = width
        this.height = height
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Size) return false
        return other.width == width && other.height == height
    }

    override fun hashCode(): Int = 31 * width.hashCode() + height.hashCode()

    fun getAspectRatio(): Float = if (height == 0.0f) -1.0f else width / height

    fun getDiagonalLength(): Float = sqrt((width * width + height * height).toDouble()).toFloat()

    companion object {
        @JvmField
        val Unit = Size()
    }
}
