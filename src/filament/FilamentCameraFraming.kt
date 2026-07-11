package com.phuchienngo.marblemarvelous.filament

import com.phuchienngo.marblemarvelous.math.Vec3
import com.phuchienngo.marblemarvelous.utils.DateUtils
import java.time.Instant

internal object FilamentCameraFraming {
  fun offsetFor(
    date: Instant,
    isPreview: Boolean
  ): Vec3 {
    if (isPreview) {
      return PREVIEW_CENTERED_OFFSET
    }

    val localDayRatio: Float = DateUtils.localDayRatio(date)
    return when {
      localDayRatio > EVENING_CAMERA_START_RATIO -> LIVE_CENTERED_OFFSET
      localDayRatio > AFTERNOON_CAMERA_START_RATIO -> CAMERA_OFFSET_MEDIUM
      localDayRatio > MORNING_CAMERA_START_RATIO -> CAMERA_OFFSET_SIDE
      else -> LIVE_CENTERED_OFFSET
    }
  }

  val PREVIEW_CENTERED_OFFSET: Vec3 = Vec3(0.0f, 0.0f, 16.0f)
  private val LIVE_CENTERED_OFFSET: Vec3 = Vec3(0.0f, 0.0f, 16.0f)
  private const val AFTERNOON_CAMERA_START_RATIO: Float = 0.5f
  private const val EVENING_CAMERA_START_RATIO: Float = 0.7083333f
  private const val MORNING_CAMERA_START_RATIO: Float = 0.20833333f
  private val CAMERA_OFFSET_MEDIUM: Vec3 = Vec3(0.0f, 0.0f, 14.75f)
  private val CAMERA_OFFSET_SIDE: Vec3 = Vec3(7.2f, 0.0f, 10.6f)
}
