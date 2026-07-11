package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

  @Test
  fun permissionActivityIsNotExported() {
    val manifest: String = File("AndroidManifest.xml").readText()
    val activityStart: Int = manifest.indexOf("android:name=\"com.phuchienngo.marblemarvelous.permissions.PermissionsActivity\"")
    val activityEnd: Int = manifest.indexOf("/>", startIndex = activityStart)
    val activityDeclaration: String = manifest.substring(activityStart, activityEnd)

    assertTrue(activityDeclaration.contains("android:exported=\"false\""))
  }

  @Test
  fun permissionActivityUsesOnlyPlatformActivityApis() {
    val source: String = File("src/permissions/PermissionsActivity.kt").readText()

    assertTrue(source.contains("import android.app.Activity"))
    assertTrue(!source.contains("import androidx.activity"))
  }

  @Test
  fun wallpaperEntryPointsUseHilt() {
    val serviceSource: String = File("src/filament/FilamentWallpaperService.kt").readText()
    val applicationSource: String = File("src/MarbleApplication.kt").readText()

    assertTrue(serviceSource.contains("@AndroidEntryPoint"))
    assertTrue(serviceSource.contains("Hilt_FilamentWallpaperService"))
    assertTrue(applicationSource.contains("@HiltAndroidApp"))
    assertTrue(applicationSource.contains("Hilt_MarbleApplication"))
  }

  private fun secondsToNanos(seconds: Long): Long = seconds * NANOS_PER_SECOND

  private companion object {
    const val EPSILON: Float = 0.0001f
    const val NANOS_PER_SECOND: Long = 1_000_000_000L
  }
}
