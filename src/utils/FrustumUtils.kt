package com.phuchienngo.marblemarvelous.utils

import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.tan

object FrustumUtils {
  @JvmStatic
  fun vFovToHFov(
    vFov: Float,
    screenWidth: Float,
    screenHeight: Float
  ): Float = vFovToHFov(vFov, screenWidth / screenHeight)

  @JvmStatic
  fun vFovToHFov(
    vFov: Float,
    ar: Float
  ): Float =
    (114.59156036376953 * atan(tan(((vFov / 2.0f) * 0.017453292f).toDouble()) * ar.toDouble())).toFloat()

  @JvmStatic
  fun hFovToVFov(
    hFov: Float,
    screenWidth: Float,
    screenHeight: Float
  ): Float = hFovToVFov(hFov, screenWidth / screenHeight)

  @JvmStatic
  fun hFovToVFov(
    hFov: Float,
    ar: Float
  ): Float = (114.59156036376953 * atan2(
    tan(((hFov / 2.0f) * 0.017453292f).toDouble()),
    ar.toDouble()
  )).toFloat()
}
