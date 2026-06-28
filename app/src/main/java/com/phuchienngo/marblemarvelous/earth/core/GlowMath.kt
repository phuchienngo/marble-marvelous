package com.phuchienngo.marblemarvelous.earth.core

internal object GlowMath {
    fun aspectRatio(
        width: Int,
        height: Int,
    ): Float = width.toFloat() / height.toFloat()
}
