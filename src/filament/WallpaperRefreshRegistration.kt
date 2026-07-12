package com.phuchienngo.marblemarvelous.filament

internal class WallpaperRefreshRegistration(
  private val start: () -> Unit,
  private val stop: () -> Unit
) {
  private var registered = false

  fun updateVisibility(visible: Boolean) {
    if (visible == registered) {
      return
    }
    registered = visible
    if (visible) {
      start()
    } else {
      stop()
    }
  }

  fun close() {
    updateVisibility(visible = false)
  }
}
