package com.phuchienngo.marblemarvelous.space

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches the NOAA SWPC planetary Kp index and maps it to a 0..1 aurora
 * activity level used to drive the aurora curtain in the earth shader.
 */
@Singleton
internal class AuroraActivityProvider
@Inject
constructor() {
  suspend fun currentActivity(): Float? =
    withContext(Dispatchers.IO) {
      var connection: HttpsURLConnection? = null
      try {
        connection = URI(KP_INDEX_URL).toURL().openConnection() as HttpsURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        connection.requestMethod = HTTP_GET_METHOD
        if (connection.responseCode !in SUCCESS_STATUS_RANGE) {
          return@withContext null
        }
        val json: String = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        parseActivity(json)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch planetary Kp index", e)
        null
      } finally {
        connection?.disconnect()
      }
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
    private const val HTTP_TIMEOUT_MILLIS: Int = 15_000
    private const val HTTP_GET_METHOD: String = "GET"
    private val SUCCESS_STATUS_RANGE: IntRange = 200..299
  }
}
