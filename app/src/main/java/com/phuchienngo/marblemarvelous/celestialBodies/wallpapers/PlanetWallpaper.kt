package com.phuchienngo.marblemarvelous.celestialBodies.wallpapers

import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.phuchienngo.marblemarvelous.view.UserAwareWallpaperService

abstract class PlanetWallpaper : UserAwareWallpaperService() {
    override fun onCreateAppConfig(): AndroidApplicationConfiguration {
        return AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
        }
    }
}
