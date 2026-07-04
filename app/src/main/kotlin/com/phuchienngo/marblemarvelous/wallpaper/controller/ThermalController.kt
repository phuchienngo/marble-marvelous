package com.phuchienngo.marblemarvelous.wallpaper.controller

import android.content.Context
import android.os.PowerManager
import com.phuchienngo.marblemarvelous.di.WallpaperServiceScope
import com.phuchienngo.marblemarvelous.power.ThermalLevel
import com.phuchienngo.marblemarvelous.wallpaper.listener.ThermalListener
import javax.inject.Inject

@WallpaperServiceScope
class ThermalController
@Inject
constructor(
  private val context: Context,
  private val listener: ThermalListener
) {
  private var powerManager: PowerManager? = null
  private var registered = false
  private var thermalLevel: ThermalLevel = ThermalLevel.NORMAL
  private val statusListener: PowerManager.OnThermalStatusChangedListener =
    PowerManager.OnThermalStatusChangedListener thermalStatusChanged@{ status: Int ->
      updateThermalStatus(status, fire = true)
      return@thermalStatusChanged
    }

  fun resume(fireStraightAway: Boolean) {
    val manager: PowerManager =
      powerManager ?: (context.getSystemService(Context.POWER_SERVICE) as PowerManager).also { service ->
        powerManager = service
      }
    updateThermalStatus(manager.currentThermalStatus, fireStraightAway)
    if (!registered) {
      manager.addThermalStatusListener(statusListener)
      registered = true
    }
  }

  fun dispose() {
    val manager: PowerManager? = powerManager
    if (registered && manager != null) {
      manager.removeThermalStatusListener(statusListener)
      registered = false
    }
  }

  private fun updateThermalStatus(
    status: Int,
    fire: Boolean
  ) {
    val nextThermalLevel: ThermalLevel = ThermalLevel.fromAndroidStatus(status)
    if (nextThermalLevel == thermalLevel) {
      return
    }
    thermalLevel = nextThermalLevel
    if (fire) {
      listener.onThermalLevelChanged(nextThermalLevel)
    }
  }
}
