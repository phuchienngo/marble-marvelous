package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import android.util.Log
import com.phuchienngo.marblemarvelous.space.AuroraActivityProvider
import com.phuchienngo.marblemarvelous.weather.NasaClouds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Single, app-scoped owner of the periodic environment refresh (clouds + aurora).
 *
 * Every wallpaper engine used to run its own hourly loop, so multiple engines
 * (e.g. preview + the live wallpaper, or the overlap Android creates while
 * switching wallpapers) meant duplicated network work. This scheduler runs one
 * loop for the whole process and publishes the results via [StateFlow]s that each
 * engine observes; the engines only apply the values to their own renderer.
 *
 * The loop is reference-counted through [start]/[stop]: it wakes only while at
 * least one engine is attached and stops when the last one goes away.
 */
@Singleton
internal class WallpaperRefreshScheduler
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val nasaClouds: NasaClouds,
  private val auroraActivityProvider: AuroraActivityProvider
) {
  private val scope: CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var job: Job? = null
  private var attachedEngines = 0

  private val mutableAuroraActivity = MutableStateFlow<Float?>(null)

  /** Latest aurora activity (0..1), or null until the first successful fetch. */
  val auroraActivity: StateFlow<Float?> = mutableAuroraActivity.asStateFlow()

  private val mutableCloudRevision = MutableStateFlow(0)

  /**
   * Bumped every time a fresh cloud cache is written to disk. Engines reload
   * their cloud mask whenever this changes (the initial 0 also drives the first
   * load of whatever is already cached).
   */
  val cloudRevision: StateFlow<Int> = mutableCloudRevision.asStateFlow()

  @Synchronized
  fun start() {
    attachedEngines++
    if (job?.isActive == true) {
      return
    }
    job =
      scope.launch {
        while (true) {
          refreshClouds()
          refreshAurora()
          delay(REFRESH_INTERVAL)
        }
      }
  }

  @Synchronized
  fun stop() {
    if (attachedEngines > 0) {
      attachedEngines--
    }
    if (attachedEngines == 0) {
      job?.cancel()
      job = null
    }
  }

  private suspend fun refreshClouds() {
    val generated: Boolean =
      try {
        nasaClouds.generateCubeFaces(context)
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
          throw throwable
        }
        Log.e(TAG, "Failed to generate NASA cloud cache", throwable)
        return
      }
    if (generated) {
      mutableCloudRevision.value += 1
    }
  }

  private suspend fun refreshAurora() {
    val activity: Float =
      try {
        auroraActivityProvider.currentActivity()
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
          throw throwable
        }
        Log.e(TAG, "Failed to fetch aurora activity", throwable)
        return
      } ?: return
    mutableAuroraActivity.value = activity
  }

  companion object {
    private val REFRESH_INTERVAL: Duration = 1.hours
    private const val TAG: String = "WallpaperRefresh"
  }
}
