package com.phuchienngo.marblemarvelous.space

import android.util.Log
import com.phuchienngo.marblemarvelous.di.PlatformHttpClient
import com.phuchienngo.marblemarvelous.di.PlatformHttpResponse
import com.phuchienngo.marblemarvelous.di.awaitResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the NOAA SWPC planetary Kp index and maps it to a 0..1 aurora
 * activity level used to drive the aurora curtain in the earth shader.
 */
@Singleton
internal class AuroraActivityProvider
@Inject
constructor(
  private val httpClient: PlatformHttpClient
) {
  suspend fun currentActivity(): Float? =
    try {
      val response: PlatformHttpResponse = httpClient.get(KP_INDEX_URL).awaitResult()
      if (!response.isSuccessful) {
        return null
      }
      parseActivity(response.body.toString(Charsets.UTF_8))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch planetary Kp index", e)
      null
    }

  internal companion object {
    internal fun parseActivity(json: String): Float? {
      val latestKp: Double = latestObjectKp(json) ?: latestLegacyKp(json) ?: return null
      val normalized: Double = (latestKp / MAX_KP).coerceIn(0.0, 1.0)
      return (AURORA_BASELINE + (1.0 - AURORA_BASELINE) * normalized).toFloat().coerceIn(0.0f, 1.0f)
    }

    private fun latestObjectKp(json: String): Double? =
      OBJECT_KP_PATTERN
        .findAll(json)
        .lastOrNull()
        ?.groupValues
        ?.get(1)
        ?.toDoubleOrNull()

    private fun latestLegacyKp(json: String): Double? {
      val rows: List<MatchResult> = ARRAY_ROW_PATTERN.findAll(json).toList()
      if (rows.size < 2) {
        return null
      }
      val header: List<String> = parseRow(rows.first().groupValues[1])
      val kpColumn: Int = header.indexOfFirst { value: String ->
        return@indexOfFirst value.equals(KP_FIELD, ignoreCase = true)
      }
      if (kpColumn < 0) {
        return null
      }
      val latest: List<String> = parseRow(rows.last().groupValues[1])
      return latest.getOrNull(kpColumn)?.toDoubleOrNull()
    }

    private fun parseRow(row: String): List<String> {
      val values: MutableList<String> = mutableListOf()
      for (value: String in row.split(',')) {
        values.add(value.trim().trim('"'))
      }
      return values
    }

    private val ARRAY_ROW_PATTERN = Regex("\\[([^\\[\\]]*)]")
    private val OBJECT_KP_PATTERN = Regex("\\\"Kp\\\"\\s*:\\s*\\\"?(-?\\d+(?:\\.\\d+)?)\\\"?", RegexOption.IGNORE_CASE)
    private const val TAG: String = "AuroraActivity"
    private const val KP_INDEX_URL: String =
      "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    private const val KP_FIELD: String = "Kp"
    private const val MAX_KP: Double = 9.0
    private const val AURORA_BASELINE: Double = 0.15
  }
}
