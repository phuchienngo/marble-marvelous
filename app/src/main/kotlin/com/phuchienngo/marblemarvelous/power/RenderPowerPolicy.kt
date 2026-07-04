package com.phuchienngo.marblemarvelous.power

import android.os.PowerManager

data class RenderPowerInput(
  val isResumeWarmupActive: Boolean,
  val isTweenAnimating: Boolean,
  val isScrollAnimating: Boolean,
  val isPowerSave: Boolean,
  val thermalLevel: ThermalLevel
)

data class RenderPowerProfile(
  val targetFps: Int,
  val surfaceFrameRate: Float,
  val renderScale: Float,
  val glowPasses: Int,
  val shaderQuality: RenderShaderQuality,
  val allowCloudRefresh: Boolean
)

enum class RenderShaderQuality(
  val uniformValue: Int
) {
  LOW(0),
  BALANCED(1),
  HIGH(2)
}

enum class ThermalLevel {
  NORMAL,
  LIGHT,
  MODERATE,
  SEVERE;

  fun constrainsRendering(): Boolean =
    this == MODERATE || this == SEVERE

  companion object {
    fun fromAndroidStatus(status: Int): ThermalLevel =
      when (status) {
        PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> SEVERE

        else -> NORMAL
      }
  }
}

object RenderPowerPolicy {
  fun select(input: RenderPowerInput): RenderPowerProfile {
    val constrained: Boolean =
      input.isPowerSave || input.thermalLevel.constrainsRendering()
    val animating: Boolean = input.isResumeWarmupActive || input.isTweenAnimating
    val baseProfile: RenderPowerProfile =
      when {
        constrained -> constrainedProfile(input.thermalLevel)
        animating -> highProfile()
        input.isScrollAnimating -> mediumProfile()
        else -> idleProfile()
      }
    return baseProfile.copy(
      surfaceFrameRate = effectiveSurfaceFrameRate(baseProfile.targetFps, input.isPowerSave),
      allowCloudRefresh = allowCloudRefresh(input)
    )
  }

  private fun highProfile(): RenderPowerProfile =
    RenderPowerProfile(
      targetFps = 60,
      surfaceFrameRate = 60.0f,
      renderScale = 1.0f,
      glowPasses = 2,
      shaderQuality = RenderShaderQuality.HIGH,
      allowCloudRefresh = false
    )

  private fun mediumProfile(): RenderPowerProfile =
    RenderPowerProfile(
      targetFps = 30,
      surfaceFrameRate = 30.0f,
      renderScale = 0.92f,
      glowPasses = 1,
      shaderQuality = RenderShaderQuality.BALANCED,
      allowCloudRefresh = true
    )

  private fun idleProfile(): RenderPowerProfile =
    RenderPowerProfile(
      targetFps = 18,
      surfaceFrameRate = 18.0f,
      renderScale = 0.85f,
      glowPasses = 1,
      shaderQuality = RenderShaderQuality.BALANCED,
      allowCloudRefresh = true
    )

  private fun constrainedProfile(thermalLevel: ThermalLevel): RenderPowerProfile {
    val targetFps: Int =
      if (thermalLevel.constrainsRendering()) {
        15
      } else {
        18
      }
    return RenderPowerProfile(
      targetFps = targetFps,
      surfaceFrameRate = targetFps.toFloat(),
      renderScale = 0.75f,
      glowPasses = 0,
      shaderQuality = RenderShaderQuality.LOW,
      allowCloudRefresh = false
    )
  }

  private fun effectiveSurfaceFrameRate(
    targetFps: Int,
    isPowerSave: Boolean
  ): Float {
    if (!isPowerSave) {
      return targetFps.toFloat()
    }
    return (targetFps / 2)
      .coerceAtLeast(MIN_SURFACE_FRAME_RATE)
      .toFloat()
  }

  private fun allowCloudRefresh(input: RenderPowerInput): Boolean {
    if (input.isResumeWarmupActive || input.isPowerSave) {
      return false
    }
    if (input.thermalLevel.constrainsRendering()) {
      return false
    }
    return true
  }

  private const val MIN_SURFACE_FRAME_RATE: Int = 1
}
