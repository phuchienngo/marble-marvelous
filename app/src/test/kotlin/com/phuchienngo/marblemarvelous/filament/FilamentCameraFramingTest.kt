package com.phuchienngo.marblemarvelous.filament

import com.phuchienngo.marblemarvelous.math.Vec3
import com.phuchienngo.marblemarvelous.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FilamentCameraFramingTest {
  @Test
  fun usesCenteredLockedOffsetForPreview() {
    val midday = requireNotNull(DateUtils.parseDate("2026-07-04 11:55:00"))

    assertEquals(
      Vec3(0.0f, 0.0f, 16.0f),
      FilamentCameraFraming.offsetFor(midday, isPreview = true)
    )
  }

  @Test
  fun keepsTimeBasedSideOffsetForLiveWallpaper() {
    val midday = requireNotNull(DateUtils.parseDate("2026-07-04 11:55:00"))

    assertEquals(
      Vec3(7.2f, 0.0f, 10.6f),
      FilamentCameraFraming.offsetFor(midday, isPreview = false)
    )
  }
}
