package com.phuchienngo.marblemarvelous.filament

import kotlin.math.PI

internal object FilamentEarthMotion {
  fun earthYawRadians(
    utcDayRatio: Float,
    elapsedSeconds: Float
  ): Float = realtimeYawRadians(utcDayRatio) + visualYawRadians(elapsedSeconds)

  fun realtimeYawRadians(utcDayRatio: Float): Float =
    utcDayRatio * FULL_ROTATION_RADIANS

  fun visualYawRadians(elapsedSeconds: Float): Float =
    elapsedSeconds * IDLE_ROTATION_RADIANS_PER_SECOND

  private const val IDLE_ROTATION_DEGREES_PER_SECOND: Float = 0.85f
  private const val FULL_ROTATION_RADIANS: Float = (PI * 2.0).toFloat()
  private const val RADIANS_PER_DEGREE: Float = (PI / 180.0).toFloat()
  private const val IDLE_ROTATION_RADIANS_PER_SECOND: Float =
    IDLE_ROTATION_DEGREES_PER_SECOND * RADIANS_PER_DEGREE
}
