package com.phuchienngo.marblemarvelous.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperSurfaceMathTest {
  @Test
  fun usesVisibleDisplaySizeWhenWallpaperSurfaceIsWiderThanTheScreen() {
    val visibleSize: WallpaperSurfaceSize =
      WallpaperSurfaceMath.visibleSize(
        surfaceWidth = 4800,
        surfaceHeight = 2400,
        displayWidth = 1080,
        displayHeight = 2400
      )

    assertEquals(WallpaperSurfaceSize(width = 1080, height = 2400), visibleSize)
  }

  @Test
  fun keepsSurfaceSizeWhenItIsNotLargerThanTheDisplay() {
    val visibleSize: WallpaperSurfaceSize =
      WallpaperSurfaceMath.visibleSize(
        surfaceWidth = 1080,
        surfaceHeight = 2400,
        displayWidth = 1080,
        displayHeight = 2400
      )

    assertEquals(WallpaperSurfaceSize(width = 1080, height = 2400), visibleSize)
  }

  @Test
  fun convertsWallpaperPixelOffsetToVisibleSurfaceOrigin() {
    assertEquals(0, WallpaperSurfaceMath.visibleSurfaceOrigin(pixelOffset = 0))
    assertEquals(1860, WallpaperSurfaceMath.visibleSurfaceOrigin(pixelOffset = -1860))
  }

  @Test
  fun scalesRenderTargetFromVisibleBaseSize() {
    val scaledSize: WallpaperSurfaceSize =
      WallpaperSurfaceMath.scaledSize(
        baseSize = WallpaperSurfaceSize(width = 1080, height = 2304),
        scale = 0.75f,
        minSize = 1
      )

    assertEquals(WallpaperSurfaceSize(width = 810, height = 1728), scaledSize)
  }

  @Test
  fun clampsVisibleDrawOriginInsideWallpaperSurface() {
    val drawOrigin: WallpaperSurfaceOrigin =
      WallpaperSurfaceMath.visibleDrawOrigin(
        surfaceSize = WallpaperSurfaceSize(width = 4800, height = 2400),
        visibleSize = WallpaperSurfaceSize(width = 1080, height = 2400),
        offsetX = 5000,
        offsetY = 300
      )

    assertEquals(WallpaperSurfaceOrigin(x = 3720, y = 0), drawOrigin)
  }
}
