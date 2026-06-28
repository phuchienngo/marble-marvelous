package com.phuchienngo.marblemarvelous.view.interfaces

import com.phuchienngo.marblemarvelous.view.controller.ScreenRotationController

interface ScreenOrientationListener {
    fun onWindowOrientationChanged(orientation: ScreenRotationController.ScreenRotation)
}
