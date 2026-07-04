package com.phuchienngo.marblemarvelous.wallpaper

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class WallpaperSurfaceOrigin(
  val x: Int,
  val y: Int
)

data class WallpaperSurfaceSize(
  val width: Int,
  val height: Int
)

object WallpaperSurfaceMath {
  fun visibleSize(
    surfaceWidth: Int,
    surfaceHeight: Int,
    displayWidth: Int,
    displayHeight: Int
  ): WallpaperSurfaceSize =
    WallpaperSurfaceSize(
      width = max(MIN_SIZE, min(surfaceWidth, displayWidth)),
      height = max(MIN_SIZE, min(surfaceHeight, displayHeight))
    )

  fun visibleSurfaceOrigin(pixelOffset: Int): Int =
    max(MIN_ORIGIN, -pixelOffset)

  fun scaledSize(
    baseSize: WallpaperSurfaceSize,
    scale: Float,
    minSize: Int
  ): WallpaperSurfaceSize =
    WallpaperSurfaceSize(
      width = max(minSize, (baseSize.width * scale).roundToInt()),
      height = max(minSize, (baseSize.height * scale).roundToInt())
    )

  fun visibleDrawOrigin(
    surfaceSize: WallpaperSurfaceSize,
    visibleSize: WallpaperSurfaceSize,
    offsetX: Int,
    offsetY: Int
  ): WallpaperSurfaceOrigin =
    WallpaperSurfaceOrigin(
      x = offsetX.coerceIn(MIN_ORIGIN, max(MIN_ORIGIN, surfaceSize.width - visibleSize.width)),
      y = offsetY.coerceIn(MIN_ORIGIN, max(MIN_ORIGIN, surfaceSize.height - visibleSize.height))
    )

  private const val MIN_ORIGIN: Int = 0
  private const val MIN_SIZE: Int = 1
}
