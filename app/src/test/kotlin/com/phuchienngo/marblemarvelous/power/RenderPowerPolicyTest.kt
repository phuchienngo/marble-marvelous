package com.phuchienngo.marblemarvelous.power

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPowerPolicyTest {
  @Test
  fun usesFullQualityDuringWarmupOrTweenAnimation() {
    val profile: RenderPowerProfile =
      RenderPowerPolicy.select(
        RenderPowerInput(
          isResumeWarmupActive = true,
          isTweenAnimating = false,
          isScrollAnimating = false,
          isPowerSave = false,
          thermalLevel = ThermalLevel.NORMAL
        )
      )

    assertEquals(60, profile.targetFps)
    assertEquals(60f, profile.surfaceFrameRate, FLOAT_TOLERANCE)
    assertEquals(1.0f, profile.renderScale, FLOAT_TOLERANCE)
    assertEquals(2, profile.glowPasses)
    assertEquals(RenderShaderQuality.HIGH, profile.shaderQuality)
    assertFalse(profile.allowCloudRefresh)
  }

  @Test
  fun usesMediumQualityWhileScrollAnimationIsActive() {
    val profile: RenderPowerProfile =
      RenderPowerPolicy.select(
        RenderPowerInput(
          isResumeWarmupActive = false,
          isTweenAnimating = false,
          isScrollAnimating = true,
          isPowerSave = false,
          thermalLevel = ThermalLevel.NORMAL
        )
      )

    assertEquals(30, profile.targetFps)
    assertEquals(30f, profile.surfaceFrameRate, FLOAT_TOLERANCE)
    assertEquals(0.92f, profile.renderScale, FLOAT_TOLERANCE)
    assertEquals(1, profile.glowPasses)
    assertEquals(RenderShaderQuality.BALANCED, profile.shaderQuality)
    assertTrue(profile.allowCloudRefresh)
  }

  @Test
  fun lowersScaleAndSurfaceFrameRateForIdleRendering() {
    val profile: RenderPowerProfile =
      RenderPowerPolicy.select(
        RenderPowerInput(
          isResumeWarmupActive = false,
          isTweenAnimating = false,
          isScrollAnimating = false,
          isPowerSave = false,
          thermalLevel = ThermalLevel.NORMAL
        )
      )

    assertEquals(18, profile.targetFps)
    assertEquals(18f, profile.surfaceFrameRate, FLOAT_TOLERANCE)
    assertEquals(0.85f, profile.renderScale, FLOAT_TOLERANCE)
    assertEquals(1, profile.glowPasses)
    assertEquals(RenderShaderQuality.BALANCED, profile.shaderQuality)
    assertTrue(profile.allowCloudRefresh)
  }

  @Test
  fun appliesConstrainedProfileForPowerSaveMode() {
    val profile: RenderPowerProfile =
      RenderPowerPolicy.select(
        RenderPowerInput(
          isResumeWarmupActive = false,
          isTweenAnimating = false,
          isScrollAnimating = false,
          isPowerSave = true,
          thermalLevel = ThermalLevel.NORMAL
        )
      )

    assertEquals(18, profile.targetFps)
    assertEquals(9f, profile.surfaceFrameRate, FLOAT_TOLERANCE)
    assertEquals(0.75f, profile.renderScale, FLOAT_TOLERANCE)
    assertEquals(0, profile.glowPasses)
    assertEquals(RenderShaderQuality.LOW, profile.shaderQuality)
    assertFalse(profile.allowCloudRefresh)
  }

  @Test
  fun appliesConstrainedProfileForModerateThermalPressure() {
    val profile: RenderPowerProfile =
      RenderPowerPolicy.select(
        RenderPowerInput(
          isResumeWarmupActive = false,
          isTweenAnimating = false,
          isScrollAnimating = false,
          isPowerSave = false,
          thermalLevel = ThermalLevel.MODERATE
        )
      )

    assertEquals(15, profile.targetFps)
    assertEquals(15f, profile.surfaceFrameRate, FLOAT_TOLERANCE)
    assertEquals(0.75f, profile.renderScale, FLOAT_TOLERANCE)
    assertEquals(0, profile.glowPasses)
    assertEquals(RenderShaderQuality.LOW, profile.shaderQuality)
    assertFalse(profile.allowCloudRefresh)
  }

  @Test
  fun mapsAndroidThermalStatusToPolicyLevels() {
    assertEquals(ThermalLevel.NORMAL, ThermalLevel.fromAndroidStatus(PowerManager.THERMAL_STATUS_NONE))
    assertEquals(ThermalLevel.LIGHT, ThermalLevel.fromAndroidStatus(PowerManager.THERMAL_STATUS_LIGHT))
    assertEquals(
      ThermalLevel.MODERATE,
      ThermalLevel.fromAndroidStatus(PowerManager.THERMAL_STATUS_MODERATE)
    )
    assertEquals(ThermalLevel.SEVERE, ThermalLevel.fromAndroidStatus(PowerManager.THERMAL_STATUS_SEVERE))
    assertEquals(
      ThermalLevel.SEVERE,
      ThermalLevel.fromAndroidStatus(PowerManager.THERMAL_STATUS_CRITICAL)
    )
  }

  companion object {
    private const val FLOAT_TOLERANCE: Float = 0.0001f
  }
}
