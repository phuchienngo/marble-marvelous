package com.phuchienngo.marblemarvelous.earth

import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.phuchienngo.marblemarvelous.di.DaggerEngineComponent
import com.phuchienngo.marblemarvelous.wallpaper.UserAwareWallpaperService

class EarthWallpaperService : UserAwareWallpaperService() {
    override fun onCreateAppConfig(): AndroidApplicationConfiguration {
        val config: AndroidApplicationConfiguration = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        config.useGyroscope = false
        return config
    }

    override fun createEngine(): UserAwareWallpaperService.UserAwareEngine =
        DaggerEngineComponent
            .factory()
            .create(this, app)
            .earthEngineFactory()
            .create()

    companion object {
        const val TAG: String = "Earth"
    }
}
