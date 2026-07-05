package com.phuchienngo.marblemarvelous.filament

import com.phuchienngo.marblemarvelous.earth.EarthLocationMath
import org.junit.Assert.assertTrue
import org.junit.Test

class FilamentSunDirectionTest {
  @Test
  fun solarDirectionMatchesEarthDaylightConvention() {
    val morningDirection = FilamentSunDirection.solarDirectionForDayOfYear(JUNE_28_DAY_OF_YEAR)
    val morningSurface =
      EarthLocationMath
        .locationSurface(
          longitudeDegrees = HANOI_LONGITUDE,
          latitudeDegrees = HANOI_LATITUDE,
          radius = UNIT_RADIUS,
          earthRotationDegrees = FULL_ROTATION_DEGREES * VIETNAM_09_13_UTC_DAY_RATIO
        )
        .normalized()
    val midnightDirection = FilamentSunDirection.solarDirectionForDayOfYear(JUNE_27_DAY_OF_YEAR)
    val midnightSurface =
      EarthLocationMath
        .locationSurface(
          longitudeDegrees = HANOI_LONGITUDE,
          latitudeDegrees = HANOI_LATITUDE,
          radius = UNIT_RADIUS,
          earthRotationDegrees = FULL_ROTATION_DEGREES * VIETNAM_00_13_UTC_DAY_RATIO
        )
        .normalized()

    assertTrue(morningSurface.dot(morningDirection) > DAYLIGHT_THRESHOLD)
    assertTrue(midnightSurface.dot(midnightDirection) < NIGHT_THRESHOLD)
  }

  @Test
  fun localDirectionReflectsRealtimeDayAndNightForSameUtcTime() {
    val localSunDirection =
      FilamentSunDirection.localDirectionForEarthRotation(
        dayOfYear = JULY_04_DAY_OF_YEAR,
        earthRotationRadians =
          FilamentEarthMotion.realtimeYawRadians(
            utcDayRatio = JULY_04_06_00_UTC_DAY_RATIO
          )
      )
    val vietnamSurface =
      EarthLocationMath
        .locationSurface(
          longitudeDegrees = HO_CHI_MINH_LONGITUDE,
          latitudeDegrees = HO_CHI_MINH_LATITUDE,
          radius = UNIT_RADIUS,
          earthRotationDegrees = 0.0f
        )
        .normalized()
    val newYorkSurface =
      EarthLocationMath
        .locationSurface(
          longitudeDegrees = NEW_YORK_LONGITUDE,
          latitudeDegrees = NEW_YORK_LATITUDE,
          radius = UNIT_RADIUS,
          earthRotationDegrees = 0.0f
        )
        .normalized()

    assertTrue(vietnamSurface.dot(localSunDirection) > DAYLIGHT_THRESHOLD)
    assertTrue(newYorkSurface.dot(localSunDirection) < LOCAL_NIGHT_THRESHOLD)
  }

  companion object {
    private const val DAYLIGHT_THRESHOLD: Float = 0.5f
    private const val FULL_ROTATION_DEGREES: Float = 360.0f
    private const val HANOI_LATITUDE: Float = 21.0278f
    private const val HANOI_LONGITUDE: Float = 105.8342f
    private const val HO_CHI_MINH_LATITUDE: Float = 10.8231f
    private const val HO_CHI_MINH_LONGITUDE: Float = 106.6297f
    private const val JUNE_27_DAY_OF_YEAR: Int = 178
    private const val JUNE_28_DAY_OF_YEAR: Int = 179
    private const val JULY_04_06_00_UTC_DAY_RATIO: Float = 6.0f / 24.0f
    private const val JULY_04_DAY_OF_YEAR: Int = 185
    private const val LOCAL_NIGHT_THRESHOLD: Float = -0.3f
    private const val NEW_YORK_LATITUDE: Float = 40.7128f
    private const val NEW_YORK_LONGITUDE: Float = -74.0060f
    private const val NIGHT_THRESHOLD: Float = -0.5f
    private const val UNIT_RADIUS: Float = 1.0f
    private const val VIETNAM_00_13_UTC_DAY_RATIO: Float = (17.0f * 60.0f + 13.0f) / (24.0f * 60.0f)
    private const val VIETNAM_09_13_UTC_DAY_RATIO: Float = (2.0f * 60.0f + 13.0f) / (24.0f * 60.0f)
  }
}
