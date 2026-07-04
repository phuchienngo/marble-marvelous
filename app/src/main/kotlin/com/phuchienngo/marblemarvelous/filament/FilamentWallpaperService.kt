package com.phuchienngo.marblemarvelous.filament

import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder

class FilamentWallpaperService : WallpaperService() {
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

    override fun onCreate(surfaceHolder: SurfaceHolder) {
      super.onCreate(surfaceHolder)
      setTouchEventsEnabled(false)
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
      super.onSurfaceCreated(holder)
      isSurfaceReady = true
      createRendererIfNeeded(holder)
    }

    override fun onSurfaceChanged(
      holder: SurfaceHolder,
      format: Int,
      width: Int,
      height: Int
    ) {
      super.onSurfaceChanged(holder, format, width, height)
      isSurfaceReady = true
      val currentRenderer: FilamentEarthRenderer = createRendererIfNeeded(holder) ?: return
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
      destroyRenderer()
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

    private fun createRendererIfNeeded(holder: SurfaceHolder): FilamentEarthRenderer? {
      val existingRenderer: FilamentEarthRenderer? = renderer
      if (existingRenderer != null) {
        return existingRenderer
      }

      val surface: Surface = holder.surface
      if (!surface.isValid) {
        return null
      }

      return try {
        FilamentEarthRenderer(
          context = applicationContext,
          surface = surface,
          isPreview = isPreview
        )
          .also { newRenderer ->
            newRenderer.setPaused(!isVisible)
            renderer = newRenderer
          }
      } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to create Filament renderer", throwable)
        null
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
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !surface.isValid) {
        return
      }
      surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
    }

    private fun clearSurfaceFrameRate(surface: Surface) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !surface.isValid) {
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
