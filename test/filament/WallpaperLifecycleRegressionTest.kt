package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperLifecycleRegressionTest {
  @Test
  fun motionClockPreservesElapsedVisibleTimeAcrossPauseAndResume() {
    val clock = FilamentEarthMotionClock()

    clock.setPaused(paused = false, frameTimeNanos = secondsToNanos(10L))
    assertEquals(5.0f, clock.elapsedSeconds(secondsToNanos(15L)), EPSILON)

    clock.setPaused(paused = true, frameTimeNanos = secondsToNanos(15L))
    clock.setPaused(paused = false, frameTimeNanos = secondsToNanos(30L))

    assertEquals(6.0f, clock.elapsedSeconds(secondsToNanos(31L)), EPSILON)
  }

  @Test
  fun motionClockIgnoresRepeatedResume() {
    val clock = FilamentEarthMotionClock()

    clock.setPaused(paused = false, frameTimeNanos = secondsToNanos(10L))
    clock.setPaused(paused = false, frameTimeNanos = secondsToNanos(12L))

    assertEquals(5.0f, clock.elapsedSeconds(secondsToNanos(15L)), EPSILON)
  }

  @Test
  fun refreshRegistrationTracksVisibilityTransitions() {
    var starts = 0
    var stops = 0
    val registration =
      WallpaperRefreshRegistration(
        start = startRefresh@{
          starts++
          return@startRefresh
        },
        stop = stopRefresh@{
          stops++
          return@stopRefresh
        }
      )

    registration.updateVisibility(visible = false)
    registration.updateVisibility(visible = true)
    registration.updateVisibility(visible = true)
    registration.updateVisibility(visible = false)

    assertEquals(1, starts)
    assertEquals(1, stops)
  }

  @Test
  fun closingRefreshRegistrationBalancesVisibleRegistration() {
    var starts = 0
    var stops = 0
    val registration =
      WallpaperRefreshRegistration(
        start = startRefresh@{
          starts++
          return@startRefresh
        },
        stop = stopRefresh@{
          stops++
          return@stopRefresh
        }
      )

    registration.updateVisibility(visible = true)
    registration.close()
    registration.close()

    assertEquals(1, starts)
    assertEquals(1, stops)
  }

  private fun secondsToNanos(seconds: Long): Long = seconds * NANOS_PER_SECOND

  private companion object {
    const val EPSILON: Float = 0.0001f
    const val NANOS_PER_SECOND: Long = 1_000_000_000L
  }
}
