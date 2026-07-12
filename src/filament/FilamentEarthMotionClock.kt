package com.phuchienngo.marblemarvelous.filament

internal class FilamentEarthMotionClock {
  private var accumulatedVisibleNanos: Long = 0L
  private var visibleSinceNanos: Long? = null

  fun setPaused(
    paused: Boolean,
    frameTimeNanos: Long
  ) {
    val currentVisibleSinceNanos: Long? = visibleSinceNanos
    if (paused) {
      if (currentVisibleSinceNanos != null) {
        accumulatedVisibleNanos += (frameTimeNanos - currentVisibleSinceNanos).coerceAtLeast(0L)
        visibleSinceNanos = null
      }
      return
    }
    if (currentVisibleSinceNanos == null) {
      visibleSinceNanos = frameTimeNanos
    }
  }

  fun elapsedSeconds(frameTimeNanos: Long): Float {
    val currentVisibleSinceNanos: Long? = visibleSinceNanos
    val currentVisibleNanos: Long =
      if (currentVisibleSinceNanos == null) {
        0L
      } else {
        (frameTimeNanos - currentVisibleSinceNanos).coerceAtLeast(0L)
      }
    return (accumulatedVisibleNanos + currentVisibleNanos).toFloat() * NANOS_TO_SECONDS
  }

  private companion object {
    const val NANOS_TO_SECONDS: Float = 0.000000001f
  }
}
