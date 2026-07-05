package com.phuchienngo.marblemarvelous.filament

import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import com.phuchienngo.marblemarvelous.location.UserLocationEarth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FilamentWallpaperService : WallpaperService() {
  @Inject
  internal lateinit var refreshScheduler: WallpaperRefreshScheduler

  @Inject
  internal lateinit var userLocationEarth: UserLocationEarth

  override fun onCreate() {
    // @AndroidEntryPoint injects the @Inject fields during super.onCreate().
    super.onCreate()
  }

  override fun onCreateEngine(): Engine = FilamentWallpaperEngine()

  private inner class FilamentWallpaperEngine :
    Engine(),
    Choreographer.FrameCallback {
    private var renderer: FilamentEarthRenderer? = null
    private var frameScheduled = false
    private var isDestroyed = false
    private var isSurfaceReady = false
    private var isVisible = false
    private val refreshScope: CoroutineScope =
      CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onCreate(surfaceHolder: SurfaceHolder) {
      super.onCreate(surfaceHolder)
      setTouchEventsEnabled(false)
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
      super.onSurfaceCreated(holder)
      isSurfaceReady = true
      attachSurface(holder)
    }

    override fun onSurfaceChanged(
      holder: SurfaceHolder,
      format: Int,
      width: Int,
      height: Int
    ) {
      super.onSurfaceChanged(holder, format, width, height)
      isSurfaceReady = true
      val currentRenderer: FilamentEarthRenderer = attachSurface(holder) ?: return
      currentRenderer.resize(width, height)
      setSurfaceFrameRate(holder.surface, TARGET_FPS.toFloat())
      renderOnce()
      updateFrameLoop()
    }

    override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
      super.onSurfaceRedrawNeeded(holder)
      renderOnce()
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
      isSurfaceReady = false
      stopFrameLoop()
      clearSurfaceFrameRate(holder.surface)
      // Keep the Filament engine (and its textures/meshes) alive across surface
      // loss; only the surface-bound swap chain is released here so a later
      // onSurfaceCreated can reattach without reloading assets.
      detachSurface()
      super.onSurfaceDestroyed(holder)
    }

    override fun onVisibilityChanged(visible: Boolean) {
      super.onVisibilityChanged(visible)
      isVisible = visible
      renderer?.setPaused(!visible)
      updateFrameLoop()
    }

    override fun onDestroy() {
      isDestroyed = true
      stopFrameLoop()
      // Balance the start() from ensureRenderer() so the shared scheduler can
      // stop its loop once the last engine is gone.
      if (refreshJob != null) {
        refreshScheduler.stop()
      }
      refreshScope.cancel()
      destroyRenderer()
      super.onDestroy()
    }

    override fun doFrame(frameTimeNanos: Long) {
      frameScheduled = false
      if (!canRender()) {
        return
      }
      renderFrame(frameTimeNanos)
      updateFrameLoop()
    }

    private fun ensureRenderer(): FilamentEarthRenderer? {
      val existingRenderer: FilamentEarthRenderer? = renderer
      if (existingRenderer != null) {
        return existingRenderer
      }
      return try {
        FilamentEarthRenderer(
          context = applicationContext,
          isPreview = isPreview,
          userLocation = userLocationEarth
        )
          .also { newRenderer ->
            newRenderer.setPaused(!isVisible)
            renderer = newRenderer
            startRefresh()
          }
      } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to create Filament renderer", throwable)
        null
      }
    }

    private fun attachSurface(holder: SurfaceHolder): FilamentEarthRenderer? {
      val surface: Surface = holder.surface
      if (!surface.isValid) {
        return null
      }
      val currentRenderer: FilamentEarthRenderer = ensureRenderer() ?: return null
      return try {
        currentRenderer.attachSurface(surface)
        currentRenderer.setPaused(!isVisible)
        currentRenderer
      } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to attach surface to Filament renderer", throwable)
        null
      }
    }

    private fun detachSurface() {
      val currentRenderer: FilamentEarthRenderer = renderer ?: return
      try {
        currentRenderer.detachSurface()
      } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to detach surface from Filament renderer", throwable)
      }
    }

    // The hourly clouds+aurora fetch is owned by the shared, app-scoped
    // WallpaperRefreshScheduler; the engine just observes its results and
    // applies them to its own renderer. cloudRevision's initial value also
    // drives the first load of the already-cached clouds, so a fresh engine
    // doesn't linger on the bundled fallback while the network refresh runs.
    private fun startRefresh() {
      if (refreshJob != null) {
        return
      }
      refreshScheduler.start()
      refreshJob =
        refreshScope.launch {
          launch {
            refreshScheduler.cloudRevision.collect {
              reloadCloudMaskAndRender()
            }
          }
          launch {
            refreshScheduler.auroraActivity.collect { activity ->
              applyAuroraActivity(activity)
            }
          }
        }
    }

    private fun applyAuroraActivity(activity: Float?) {
      if (activity == null || isDestroyed) {
        return
      }
      renderer?.setAuroraActivity(activity)
      renderOnce()
    }

    private suspend fun reloadCloudMaskAndRender() {
      if (isDestroyed) {
        return
      }
      val currentRenderer: FilamentEarthRenderer = renderer ?: return
      try {
        if (currentRenderer.reloadCloudMask(applicationContext)) {
          renderOnce()
        }
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
          throw throwable
        }
        Log.e(TAG, "Failed to reload OpenWeather cloud texture", throwable)
      }
    }

    private fun renderOnce() {
      if (!canRender()) {
        return
      }
      renderFrame(System.nanoTime())
    }

    private fun renderFrame(frameTimeNanos: Long) {
      try {
        renderer?.render(frameTimeNanos)
      } catch (throwable: Throwable) {
        Log.e(TAG, "Filament frame failed", throwable)
        stopFrameLoop()
      }
    }

    private fun updateFrameLoop() {
      if (!canRender()) {
        stopFrameLoop()
        return
      }
      if (frameScheduled) {
        return
      }
      frameScheduled = true
      // First vsync after the delay: keeps the ~18 FPS cadence (few wake-ups)
      // while still presenting aligned to the display refresh.
      Choreographer
        .getInstance()
        .postFrameCallbackDelayed(this, FRAME_INTERVAL_MILLIS)
    }

    private fun stopFrameLoop() {
      if (!frameScheduled) {
        return
      }
      Choreographer
        .getInstance()
        .removeFrameCallback(this)
      frameScheduled = false
    }

    private fun canRender(): Boolean =
      !isDestroyed && isSurfaceReady && isVisible && renderer != null

    private fun destroyRenderer() {
      val currentRenderer: FilamentEarthRenderer = renderer ?: return
      renderer = null
      try {
        currentRenderer.destroy()
      } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to destroy Filament renderer", throwable)
      }
    }

    private fun setSurfaceFrameRate(
      surface: Surface,
      frameRate: Float
    ) {
      if (!surface.isValid) {
        return
      }
      surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
    }

    private fun clearSurfaceFrameRate(surface: Surface) {
      if (!surface.isValid) {
        return
      }
      surface.setFrameRate(CLEAR_SURFACE_FRAME_RATE, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
    }
  }

  companion object {
    private const val CLEAR_SURFACE_FRAME_RATE: Float = 0.0f
    private const val MILLIS_PER_SECOND: Long = 1000L
    private const val TAG: String = "FilamentWallpaper"
    private const val TARGET_FPS: Int = 18
    private const val FRAME_INTERVAL_MILLIS: Long = MILLIS_PER_SECOND / TARGET_FPS
  }
}
