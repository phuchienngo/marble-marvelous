package com.phuchienngo.marblemarvelous.utils

import kotlin.math.*

object FrustumUtils {
    @JvmStatic
    fun getFrustumInsideTexture(
        textureSize: Size,
        screenSize: Size,
    ): Size = getFrustumInsideTexture(textureSize.getWidth(), textureSize.getHeight(), screenSize.getWidth(), screenSize.getHeight())

    @JvmStatic
    fun getFrustumInsideTexture(
        texW: Float,
        texH: Float,
        screenW: Float,
        screenH: Float,
    ): Size {
        val texAr = texW / texH
        val screenAr = screenW / screenH
        val size = Size()
        if (screenAr < texAr) {
            size.setWidth(screenAr * texH)
            size.setHeight(texH)
        } else {
            size.setWidth(texW)
            size.setHeight(texW / screenAr)
        }
        return size
    }

    @JvmStatic
    fun coverTexture(
        textureSize: Size,
        screenSize: Size,
    ): Size = coverTexture(textureSize.getWidth(), textureSize.getHeight(), screenSize.getWidth(), screenSize.getHeight())

    @JvmStatic
    fun coverTexture(
        texW: Float,
        texH: Float,
        screenW: Float,
        screenH: Float,
    ): Size {
        val texAr = texW / texH
        val screenAr = screenW / screenH
        val size = Size()
        if (screenAr < texAr) {
            size.setWidth(texAr * screenH)
            size.setHeight(screenH)
        } else {
            size.setWidth(screenW)
            size.setHeight(screenW / texAr)
        }
        return size
    }

    @JvmStatic
    fun containTexture(
        textureSize: Size,
        screenSize: Size,
    ): Size = containTexture(textureSize.getWidth(), textureSize.getHeight(), screenSize.getWidth(), screenSize.getHeight())

    @JvmStatic
    fun containTexture(
        texW: Float,
        texH: Float,
        screenW: Float,
        screenH: Float,
    ): Size {
        val texAr = texW / texH
        val screenAr = screenW / screenH
        val size = Size()
        if (screenAr < texAr) {
            size.setWidth(screenW)
            size.setHeight(screenW / texAr)
        } else {
            size.setWidth(texAr * screenH)
            size.setHeight(screenH)
        }
        return size
    }

    @JvmStatic
    fun vFovToHFov(
        vFov: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): Float = vFovToHFov(vFov, screenWidth / screenHeight)

    @JvmStatic
    fun vFovToHFov(
        vFov: Float,
        screenSize: Size,
    ): Float = vFovToHFov(vFov, screenSize.getAspectRatio())

    @JvmStatic
    fun vFovToHFov(
        vFov: Float,
        ar: Float,
    ): Float = (114.59156036376953 * atan(tan(((vFov / 2.0f) * 0.017453292f).toDouble()) * ar.toDouble())).toFloat()

    @JvmStatic
    fun hFovToVFov(
        hFov: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): Float = hFovToVFov(hFov, screenWidth / screenHeight)

    @JvmStatic
    fun hFovToVFov(
        hFov: Float,
        screenSize: Size,
    ): Float = hFovToVFov(hFov, screenSize.getAspectRatio())

    @JvmStatic
    fun hFovToVFov(
        hFov: Float,
        ar: Float,
    ): Float = (114.59156036376953 * atan2(tan(((hFov / 2.0f) * 0.017453292f).toDouble()), ar.toDouble())).toFloat()
}
