package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilamentEarthMotionTest {
  @Test
  fun visualSpinChangesEarthYaw() {
    val initialYaw: Float =
      FilamentEarthMotion.earthYawRadians(
        utcDayRatio = UTC_DAY_RATIO,
        elapsedSeconds = 0.0f
      )
    val laterYaw: Float =
      FilamentEarthMotion.earthYawRadians(
        utcDayRatio = UTC_DAY_RATIO,
        elapsedSeconds = 120.0f
      )

    assertTrue(laterYaw > initialYaw)
  }

  @Test
  fun realtimeYawDoesNotIncludeVisualSpin() {
    val initialRealtimeYaw: Float =
      FilamentEarthMotion.earthYawRadians(
        utcDayRatio = UTC_DAY_RATIO,
        elapsedSeconds = 0.0f
      ) - FilamentEarthMotion.visualYawRadians(elapsedSeconds = 0.0f)
    val laterRealtimeYaw: Float =
      FilamentEarthMotion.earthYawRadians(
        utcDayRatio = UTC_DAY_RATIO,
        elapsedSeconds = 120.0f
      ) - FilamentEarthMotion.visualYawRadians(elapsedSeconds = 120.0f)

    assertEquals(initialRealtimeYaw, laterRealtimeYaw, EPSILON)
  }

  @Test
  fun utcTimeChangesRealtimeYaw() {
    val earlierYaw: Float =
      FilamentEarthMotion.realtimeYawRadians(utcDayRatio = UTC_DAY_RATIO)
    val laterYaw: Float =
      FilamentEarthMotion.earthYawRadians(
        utcDayRatio = UTC_DAY_RATIO + ONE_HOUR_DAY_RATIO,
        elapsedSeconds = 0.0f
      )

    assertTrue(laterYaw > earlierYaw)
  }

  companion object {
    private const val EPSILON: Float = 0.0001f
    private const val ONE_HOUR_DAY_RATIO: Float = 1.0f / 24.0f
    private const val UTC_DAY_RATIO: Float = 6.0f / 24.0f
  }
}
