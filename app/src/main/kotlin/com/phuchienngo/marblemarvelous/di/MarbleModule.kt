package com.phuchienngo.marblemarvelous.di

import android.content.Context
import com.phuchienngo.marblemarvelous.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object MarbleModule {
  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

  @Provides
  @Singleton
  @WeatherDispatcher
  fun provideWeatherDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @Provides
  @OpenWeatherApiKey
  fun provideOpenWeatherApiKey(
    @ApplicationContext context: Context
  ): String = context.getString(R.string.openweather_api_key)
}
