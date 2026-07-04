package com.phuchienngo.marblemarvelous.wallpaper.listener

import com.phuchienngo.marblemarvelous.power.ThermalLevel

interface ThermalListener {
  fun onThermalLevelChanged(thermalLevel: ThermalLevel)
}
