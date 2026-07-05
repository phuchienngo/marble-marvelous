package com.phuchienngo.marblemarvelous.earth

import com.phuchienngo.marblemarvelous.math.Vec3
import org.junit.Assert.assertEquals
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
    private const val EPSILON: Float = 0.0001f
    private const val RADIUS: Float = 1.0f
  }
}
