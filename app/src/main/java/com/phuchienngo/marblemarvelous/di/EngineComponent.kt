package com.phuchienngo.marblemarvelous.di

import android.content.Context
import com.badlogic.gdx.Application
import com.phuchienngo.marblemarvelous.celestialBodies.EarthEngine
import com.phuchienngo.marblemarvelous.celestialBodies.wallpapers.variations.MoonWallpaperService
import com.phuchienngo.marblemarvelous.celestialBodies.wallpapers.variations.PlutoWallpaperService
import dagger.BindsInstance
import dagger.Component

/**
 * Engine-scoped graph. Runtime callbacks stay outside the component and are supplied through
 * provider factories once the engine instance exists.
 */
@WallpaperEngineScope
@Component(modules = [EngineModule::class])
interface EngineComponent {

    fun earthEngineFactory(): EarthEngine.Factory

    fun moonEngine(): MoonWallpaperService.MoonEngine

    fun plutoEngine(): PlutoWallpaperService.PlutoEngine

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance context: Context,
            @BindsInstance app: Application
        ): EngineComponent
    }
}
