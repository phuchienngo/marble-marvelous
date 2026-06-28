package com.phuchienngo.marblemarvelous.wallpaper.listener

import com.phuchienngo.marblemarvelous.wallpaper.controller.TouchController

interface TouchListener {
    fun onTouchProcessed(
        screenX: Int,
        screenY: Int,
        index: Int,
        type: TouchController.TouchType,
    )
}
