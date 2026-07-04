package com.phuchienngo.marblemarvelous.earth

import com.phuchienngo.marblemarvelous.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthLocationMathTest {
  @Test
  fun locationSurfaceMapsLongitudeAndLatitudeToExpectedAxes() {
    assertVector(
      expectedX = 0.0f,
      expectedY = 0.0f,
      expectedZ = 1.0f,
      actual =
        EarthLocationMath.locationSurface(
          longitudeDegrees = 0.0f,
          latitudeDegrees = 0.0f,
          radius = RADIUS,
          earthRotationDegrees = 0.0f
        )
    )
    assertVector(
      expectedX = 1.0f,
      expectedY = 0.0f,
      expectedZ = 0.0f,
      actual =
        EarthLocationMath.locationSurface(
          longitudeDegrees = 90.0f,
          latitudeDegrees = 0.0f,
          radius = RADIUS,
          earthRotationDegrees = 0.0f
        )
    )
    assertVector(
      expectedX = -1.0f,
      expectedY = 0.0f,
      expectedZ = 0.0f,
      actual =
        EarthLocationMath.locationSurface(
          longitudeDegrees = -90.0f,
          latitudeDegrees = 0.0f,
          radius = RADIUS,
          earthRotationDegrees = 0.0f
        )
    )
    assertVector(
      expectedX = 0.0f,
      expectedY = 1.0f,
      expectedZ = 0.0f,
      actual =
        EarthLocationMath.locationSurface(
          longitudeDegrees = 0.0f,
          latitudeDegrees = 90.0f,
          radius = RADIUS,
          earthRotationDegrees = 0.0f
        )
    )
  }

  @Test
  fun locationSurfaceAppliesEarthTransform() {
    assertVector(
      expectedX = 1.0f,
      expectedY = 0.0f,
      expectedZ = 0.0f,
      actual =
        EarthLocationMath.locationSurface(
          longitudeDegrees = 0.0f,
          latitudeDegrees = 0.0f,
          radius = RADIUS,
          earthRotationDegrees = 90.0f
        )
    )
  }

  @Test
  fun daylightFactorTreatsVietnamMorningAsDayAndVietnamMidnightAsNight() {
    val morningDeclination: Float = EarthLocationMath.sunDeclination(JUNE_28_DAY_OF_YEAR)
    val midnightDeclination: Float = EarthLocationMath.sunDeclination(JUNE_27_DAY_OF_YEAR)

    val morningLight: Float =
      EarthLocationMath.daylightFactor(
        longitudeDegrees = HANOI_LONGITUDE,
        latitudeDegrees = HANOI_LATITUDE,
        utcDayRatio = VIETNAM_09_13_UTC_DAY_RATIO,
        sunDeclinationDegrees = morningDeclination
      )
    val midnightLight: Float =
      EarthLocationMath.daylightFactor(
        longitudeDegrees = HANOI_LONGITUDE,
        latitudeDegrees = HANOI_LATITUDE,
        utcDayRatio = VIETNAM_00_13_UTC_DAY_RATIO,
        sunDeclinationDegrees = midnightDeclination
      )

    assertTrue(morningLight > DAYLIGHT_THRESHOLD)
    assertTrue(midnightLight < NIGHT_THRESHOLD)
  }

  private fun assertVector(
    expectedX: Float,
    expectedY: Float,
    expectedZ: Float,
    actual: Vec3
  ) {
    assertEquals(expectedX, actual.x, EPSILON)
    assertEquals(expectedY, actual.y, EPSILON)
    assertEquals(expectedZ, actual.z, EPSILON)
  }

  companion object {
    private const val DAYLIGHT_THRESHOLD: Float = 0.5f
    private const val EPSILON: Float = 0.0001f
    private const val HANOI_LATITUDE: Float = 21.0278f
    private const val HANOI_LONGITUDE: Float = 105.8342f
    private const val JUNE_27_DAY_OF_YEAR: Int = 178
    private const val JUNE_28_DAY_OF_YEAR: Int = 179
    private const val NIGHT_THRESHOLD: Float = -0.5f
    private const val RADIUS: Float = 1.0f
    private const val VIETNAM_00_13_UTC_DAY_RATIO: Float = (17.0f * 60.0f + 13.0f) / (24.0f * 60.0f)
    private const val VIETNAM_09_13_UTC_DAY_RATIO: Float = (2.0f * 60.0f + 13.0f) / (24.0f * 60.0f)
  }
}
