package com.phuchienngo.marblemarvelous.filament

import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import com.phuchienngo.marblemarvelous.MarbleApplication
import com.phuchienngo.marblemarvelous.di.OpenWeatherApiKey
import com.phuchienngo.marblemarvelous.weather.OpenWeatherClouds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class FilamentWallpaperService : WallpaperService() {
  @Inject
  internal lateinit var openWeatherClouds: OpenWeatherClouds

  @Inject
  @field:OpenWeatherApiKey
  internal lateinit var openWeatherApiKey: String

  override fun onCreate() {
    super.onCreate()
    (application as MarbleApplication).component.inject(this)
  }

  override fun onCreateEngine(): Engine = FilamentWallpaperEngine()

  private inner class FilamentWallpaperEngine :
    Engine(),
    Choreographer.FrameCallback {
    private var renderer: FilamentEarthRenderer? = null
    private var frameCallbackPosted = false
    private var isDestroyed = false
    private var isSurfaceReady = false
    private var isVisible = false
    private var lastRenderedFrameNanos = 0L
    private val cloudRefreshScope: CoroutineScope =
      CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cloudRefreshJob: Job? = null

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
      cloudRefreshScope.cancel()
      destroyRenderer()
      super.onDestroy()
    }

    override fun doFrame(frameTimeNanos: Long) {
      frameCallbackPosted = false
      if (!canRender()) {
        updateFrameLoop()
        return
      }

      val frameElapsedNanos: Long = frameTimeNanos - lastRenderedFrameNanos
      if (lastRenderedFrameNanos == 0L || frameElapsedNanos >= TARGET_FRAME_INTERVAL_NANOS) {
        renderFrame(frameTimeNanos)
        lastRenderedFrameNanos = frameTimeNanos
      }
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
          isPreview = isPreview
        )
          .also { newRenderer ->
            newRenderer.setPaused(!isVisible)
            renderer = newRenderer
            startCloudRefresh()
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

    private fun startCloudRefresh() {
      val apiKey: String = openWeatherApiKey
      if (apiKey.isBlank()) {
        return
      }
      if (cloudRefreshJob?.isActive == true) {
        return
      }
      cloudRefreshJob =
        cloudRefreshScope.launch {
          val generated: Boolean =
            try {
              openWeatherClouds.generateCubeFaces(applicationContext, apiKey)
            } catch (throwable: Throwable) {
              if (throwable is CancellationException) {
                throw throwable
              }
              Log.e(TAG, "Failed to generate OpenWeather cloud cache", throwable)
              return@launch
            }
          if (!generated || isDestroyed) {
            return@launch
          }
          val currentRenderer: FilamentEarthRenderer = renderer ?: return@launch
          try {
            val reloaded: Boolean = currentRenderer.reloadCloudMask(applicationContext)
            if (reloaded) {
              renderOnce()
            }
          } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to reload OpenWeather cloud texture", throwable)
          }
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
      if (frameCallbackPosted) {
        return
      }
      frameCallbackPosted = true
      Choreographer
        .getInstance()
        .postFrameCallback(this)
    }

    private fun stopFrameLoop() {
      if (!frameCallbackPosted) {
        return
      }
      Choreographer
        .getInstance()
        .removeFrameCallback(this)
      frameCallbackPosted = false
      lastRenderedFrameNanos = 0L
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
    private const val NANOS_PER_SECOND: Long = 1_000_000_000L
    private const val TAG: String = "FilamentWallpaper"
    private const val TARGET_FPS: Int = 18
    private const val TARGET_FRAME_INTERVAL_NANOS: Long = NANOS_PER_SECOND / TARGET_FPS
  }
}
