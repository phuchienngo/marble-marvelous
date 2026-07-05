package com.phuchienngo.marblemarvelous.filament

import com.phuchienngo.marblemarvelous.earth.EarthLocationMath
import com.phuchienngo.marblemarvelous.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

internal object FilamentSunDirection {
  fun solarDirectionForDayOfYear(dayOfYear: Int): Vec3 {
    val sunDeclination: Float = EarthLocationMath.sunDeclination(dayOfYear)
    return EarthLocationMath
      .sunLightPosition(sunDeclination)
      .normalized()
  }

  fun localDirectionForEarthRotation(
    dayOfYear: Int,
    earthRotationRadians: Float
  ): Vec3 =
    rotateAroundY(
      vector = solarDirectionForDayOfYear(dayOfYear),
      radians = -earthRotationRadians
    ).normalized()

  private fun rotateAroundY(
    vector: Vec3,
    radians: Float
  ): Vec3 {
    val cosine: Float = cos(radians)
    val sine: Float = sin(radians)
    return Vec3(
      x = vector.x * cosine + vector.z * sine,
      y = vector.y,
      z = -vector.x * sine + vector.z * cosine
    )
  }

}
