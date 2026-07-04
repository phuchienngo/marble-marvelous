package com.phuchienngo.marblemarvelous.space

import android.util.Log
import com.phuchienngo.marblemarvelous.di.WeatherDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the NOAA SWPC planetary Kp index and maps it to a 0..1 aurora
 * activity level used to drive the aurora curtain in the earth shader.
 */
@Singleton
class AuroraActivityProvider
@Inject
constructor(
  private val httpClient: OkHttpClient,
  @param:WeatherDispatcher private val dispatcher: CoroutineDispatcher
) {
  suspend fun currentActivity(): Float? =
    withContext(dispatcher) fetchActivity@{
      val request: Request =
        Request
          .Builder()
          .url(KP_INDEX_URL)
          .build()
      return@fetchActivity try {
        httpClient
          .newCall(request)
          .execute()
          .use readActivity@{ response: Response ->
            if (!response.isSuccessful) {
              return@readActivity null
            }
            val body: String = response.body?.string() ?: return@readActivity null
            return@readActivity parseActivity(body)
          }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch planetary Kp index", e)
        null
      }
    }

  private fun parseActivity(json: String): Float? {
    val rows = JSONArray(json)
    if (rows.length() < 2) {
      return null
    }
    val latestKp: Double = latestKp(rows) ?: return null
    val normalized: Double = (latestKp / MAX_KP).coerceIn(0.0, 1.0)
    return (AURORA_BASELINE + (1.0 - AURORA_BASELINE) * normalized).toFloat().coerceIn(0.0f, 1.0f)
  }

  private fun latestKp(rows: JSONArray): Double? {
    // Newer feed: array of objects with a numeric "Kp" field.
    rows.optJSONObject(rows.length() - 1)?.let { latest ->
      return latest.optDouble(KP_FIELD, Double.NaN).takeIf { !it.isNaN() }
    }
    // Legacy feed: array of arrays whose first row is a header naming the Kp column.
    val header = rows.optJSONArray(0) ?: return null
    var kpColumn = -1
    for (column in 0 until header.length()) {
      if (header.optString(column).equals(KP_FIELD, ignoreCase = true)) {
        kpColumn = column
      }
    }
    if (kpColumn < 0) {
      return null
    }
    val latest = rows.optJSONArray(rows.length() - 1) ?: return null
    return latest.optString(kpColumn).toDoubleOrNull()
  }

  private companion object {
    private const val TAG: String = "AuroraActivity"
    private const val KP_INDEX_URL: String =
      "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    private const val KP_FIELD: String = "Kp"
    private const val MAX_KP: Double = 9.0
    private const val AURORA_BASELINE: Double = 0.15
  }
}
