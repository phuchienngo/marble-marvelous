package com.phuchienngo.marblemarvelous.earth

import android.graphics.BitmapFactory
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.phuchienngo.marblemarvelous.R
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

    override fun createEngine(): UserAwareWallpaperService.UserAwareEngine {
        BitmapFactory.decodeResource(
            applicationContext.resources,
            R.drawable.earth_preview_color_extractor
        )
        return DaggerEngineComponent
            .factory()
            .create(this, app)
            .earthEngineFactory()
            .create()
    }

    companion object {
        const val TAG: String = "Earth"
    }
}
