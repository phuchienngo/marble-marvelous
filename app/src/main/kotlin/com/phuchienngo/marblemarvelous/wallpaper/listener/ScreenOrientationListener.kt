package com.phuchienngo.marblemarvelous.wallpaper.listener

import com.phuchienngo.marblemarvelous.wallpaper.controller.ScreenRotationController

interface ScreenOrientationListener {
    fun onWindowOrientationChanged(orientation: ScreenRotationController.ScreenRotation)
}
