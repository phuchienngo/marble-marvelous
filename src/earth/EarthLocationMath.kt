package com.phuchienngo.marblemarvelous.earth

import com.phuchienngo.marblemarvelous.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

object EarthLocationMath {
  fun sunDeclination(dayOfYear: Int): Float =
    (AXIAL_TILT * cos(SOLAR_YEAR_RADIANS_PER_DAY * (dayOfYear + SOLSTICE_OFFSET_DAYS))).toFloat()

  fun sunLightPosition(sunDeclinationDegrees: Float): Vec3 {
    val radians: Double = Math.toRadians(sunDeclinationDegrees.toDouble())
    return Vec3(
      x = INITIAL_LIGHT_POSITION.x,
      y = (INITIAL_LIGHT_POSITION.y * cos(radians) - INITIAL_LIGHT_POSITION.z * sin(radians)).toFloat(),
      z = (INITIAL_LIGHT_POSITION.y * sin(radians) + INITIAL_LIGHT_POSITION.z * cos(radians)).toFloat()
    )
  }

  fun locationSurface(
    longitudeDegrees: Float,
    latitudeDegrees: Float,
    radius: Float,
    earthRotationDegrees: Float
  ): Vec3 {
    val latitudeRadians: Double = Math.toRadians(latitudeDegrees.toDouble())
    val longitudeRadians: Double = Math.toRadians(longitudeDegrees.toDouble())
    val surface: Vec3 =
      Vec3(
        x = cos(latitudeRadians).toFloat() * sin(longitudeRadians).toFloat(),
        y = sin(latitudeRadians).toFloat(),
        z = cos(latitudeRadians).toFloat() * cos(longitudeRadians).toFloat()
      ).scale(radius)
    return rotateAroundY(surface, earthRotationDegrees)
  }

  private fun rotateAroundY(
    vector: Vec3,
    degrees: Float
  ): Vec3 {
    val radians: Double = Math.toRadians(degrees.toDouble())
    val cosine: Float = cos(radians).toFloat()
    val sine: Float = sin(radians).toFloat()
    return Vec3(
      x = vector.x * cosine + vector.z * sine,
      y = vector.y,
      z = -vector.x * sine + vector.z * cosine
    )
  }

  private const val AXIAL_TILT: Double = -23.439281463623047
  private const val SOLAR_YEAR_RADIANS_PER_DAY: Double = 0.01721420632103996
  private const val SOLSTICE_OFFSET_DAYS: Int = 10
  private val INITIAL_LIGHT_POSITION: Vec3 = Vec3(0.0f, 0.0f, -1.0f)
}
