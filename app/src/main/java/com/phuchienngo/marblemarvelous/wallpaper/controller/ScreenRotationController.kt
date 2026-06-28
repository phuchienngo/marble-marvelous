package com.phuchienngo.marblemarvelous.wallpaper.controller

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.phuchienngo.marblemarvelous.di.WallpaperServiceScope
import com.phuchienngo.marblemarvelous.wallpaper.listener.ScreenOrientationListener
import java.lang.ref.WeakReference
import javax.inject.Inject

@WallpaperServiceScope
class ScreenRotationController
    @Inject
    constructor(
        context: Context,
        private val listener: ScreenOrientationListener,
    ) {
        enum class ScreenRotation {
            PORTRAIT,
            LANDSCAPE,
            INV_PORTRAIT,
            INV_LANDSCAPE,
        }

        private val displayManager: DisplayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        private val uiThreadHandler: Handler = Handler(Looper.getMainLooper())
        private val mDisplayListener: DisplayOrientationListener = DisplayOrientationListener(this)

        var rotation: ScreenRotation = DEFAULT_SCREEN_ROTATION
            private set

        private class DisplayOrientationListener(
            controller: ScreenRotationController,
        ) : DisplayManager.DisplayListener {
            private val mWeakReference: WeakReference<ScreenRotationController> = WeakReference(controller)

            override fun onDisplayAdded(displayId: Int) {
                updateRotation()
            }

            override fun onDisplayRemoved(displayId: Int) {
                updateRotation()
            }

            override fun onDisplayChanged(displayId: Int) {
                updateRotation()
            }

            private fun updateRotation() {
                mWeakReference.get()?.updateRotation()
            }
        }

        fun resume(fireStraightAway: Boolean) {
            displayManager.unregisterDisplayListener(mDisplayListener)
            if (fireStraightAway) {
                updateRotation()
            }
            displayManager.registerDisplayListener(mDisplayListener, uiThreadHandler)
        }

        fun pause() {
            displayManager.unregisterDisplayListener(mDisplayListener)
        }

        fun dispose() {
            pause()
        }

        fun updateRotation() {
            val rotationTmp: ScreenRotation =
                when (displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
                    0 -> ScreenRotation.PORTRAIT
                    1 -> ScreenRotation.LANDSCAPE
                    2 -> ScreenRotation.INV_PORTRAIT
                    3 -> ScreenRotation.INV_LANDSCAPE
                    else -> ScreenRotation.PORTRAIT
                }
            if (rotation != rotationTmp) {
                rotation = rotationTmp
                listener.onWindowOrientationChanged(rotation)
            }
        }

        companion object {
            @JvmField val DEFAULT_SCREEN_ROTATION: ScreenRotation = ScreenRotation.PORTRAIT
        }
    }
