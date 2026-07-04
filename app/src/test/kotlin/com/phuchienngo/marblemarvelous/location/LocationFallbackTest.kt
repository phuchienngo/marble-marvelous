package com.phuchienngo.marblemarvelous.location

import com.phuchienngo.marblemarvelous.location.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFallbackTest {
  @Test
  fun fromUtcOffsetMapsVietnamOffsetToEastLongitude() {
    val location: GeoLocation = LocationFallback.fromUtcOffset(VIETNAM_OFFSET_MILLIS)

    assertEquals(105.0f, location.longitudeDegrees, EPSILON)
    assertEquals(0.0f, location.latitudeDegrees, EPSILON)
  }

  companion object {
    private const val EPSILON: Float = 0.0001f
    private const val VIETNAM_OFFSET_MILLIS: Int = 7 * 60 * 60 * 1000
  }
}
