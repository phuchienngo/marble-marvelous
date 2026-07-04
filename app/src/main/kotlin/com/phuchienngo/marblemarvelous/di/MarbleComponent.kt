package com.phuchienngo.marblemarvelous.di

import android.content.Context
import com.phuchienngo.marblemarvelous.filament.FilamentWallpaperService
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [MarbleModule::class])
internal interface MarbleComponent {
  fun inject(service: FilamentWallpaperService)

  @Component.Factory
  interface Factory {
    fun create(
      @BindsInstance
      @ApplicationContext
      context: Context
    ): MarbleComponent
  }
}
