package com.phuchienngo.marblemarvelous.di

import android.content.Context
import com.phuchienngo.marblemarvelous.view.controller.ChargingController
import com.phuchienngo.marblemarvelous.view.controller.PowerSaveController
import com.phuchienngo.marblemarvelous.view.controller.ScreenRotationController
import com.phuchienngo.marblemarvelous.view.controller.TouchController
import com.phuchienngo.marblemarvelous.view.controller.UserPresenceController
import com.phuchienngo.marblemarvelous.view.interfaces.ChargingListener
import com.phuchienngo.marblemarvelous.view.interfaces.PowerSaveListener
import com.phuchienngo.marblemarvelous.view.interfaces.ScreenOrientationListener
import com.phuchienngo.marblemarvelous.view.interfaces.TouchListener
import com.phuchienngo.marblemarvelous.view.interfaces.UserPresenceListener
import dagger.BindsInstance
import dagger.Component

/**
 * Service-scoped graph. Controllers use @Inject constructors, so no @Provides module is
 * needed. The service only binds itself (Context + the listeners it implements).
 */
@WallpaperServiceScope
@Component
interface WallpaperComponent {

    fun userPresenceController(): UserPresenceController

    fun powerSaveController(): PowerSaveController

    fun screenRotationController(): ScreenRotationController

    fun chargingController(): ChargingController

    fun touchController(): TouchController

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance context: Context,
            @BindsInstance userPresenceListener: UserPresenceListener,
            @BindsInstance powerSaveListener: PowerSaveListener,
            @BindsInstance screenOrientationListener: ScreenOrientationListener,
            @BindsInstance chargingListener: ChargingListener,
            @BindsInstance touchListener: TouchListener
        ): WallpaperComponent
    }
}
