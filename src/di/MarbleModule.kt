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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Module
@InstallIn(SingletonComponent::class)
internal object MarbleModule {
  @Provides
  @Singleton
  fun provideHttpExecutor(): ExecutorService = Executors.newFixedThreadPool(HTTP_THREAD_COUNT)

  @Provides
  @Singleton
  @WeatherDispatcher
  fun provideWeatherDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @Provides
  @OpenWeatherApiKey
  fun provideOpenWeatherApiKey(
    @ApplicationContext context: Context
  ): String = context.getString(R.string.openweather_api_key)

  private const val HTTP_THREAD_COUNT: Int = 6
}

@Singleton
internal class PlatformHttpClient
@Inject
constructor(
  private val executor: ExecutorService
) {
  fun get(url: String): CompletableFuture<PlatformHttpResponse> =
    CompletableFuture.supplyAsync(
      {
        val connection: HttpsURLConnection = URI(url).toURL().openConnection() as HttpsURLConnection
        try {
          connection.connectTimeout = TIMEOUT_MILLIS
          connection.readTimeout = TIMEOUT_MILLIS
          connection.requestMethod = "GET"
          val statusCode: Int = connection.responseCode
          val body: ByteArray =
            if (statusCode in SUCCESS_STATUS_RANGE) {
              connection.inputStream.use { inputStream ->
                return@use inputStream.readBytes()
              }
            } else {
              ByteArray(0)
            }
          PlatformHttpResponse(statusCode = statusCode, body = body)
        } finally {
          connection.disconnect()
        }
      },
      executor
    )

  private companion object {
    const val TIMEOUT_MILLIS: Int = 15_000
    val SUCCESS_STATUS_RANGE: IntRange = 200..299
  }
}

internal data class PlatformHttpResponse(
  val statusCode: Int,
  val body: ByteArray
) {
  val isSuccessful: Boolean = statusCode in 200..299
}

internal suspend fun <T> CompletableFuture<T>.awaitResult(): T =
  suspendCancellableCoroutine { continuation ->
    whenComplete { value: T?, throwable: Throwable? ->
      if (throwable != null) {
        continuation.resumeWithException(throwable)
      } else {
        continuation.resume(requireNotNull(value))
      }
    }
    continuation.invokeOnCancellation {
      cancel(true)
    }
  }
