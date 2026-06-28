package com.phuchienngo.marblemarvelous.view.interfaces

import com.phuchienngo.marblemarvelous.view.controller.TouchController

interface TouchListener {
    fun onTouchProcessed(screenX: Int, screenY: Int, index: Int, type: TouchController.TouchType)
}
