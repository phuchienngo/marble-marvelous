package com.phuchienngo.marblemarvelous.space

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
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
  private val httpClient: HttpClient
) {
  suspend fun currentActivity(): Float? =
    try {
      val body: String = httpClient.get(KP_INDEX_URL).body()
      parseActivity(body)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch planetary Kp index", e)
      null
    }

  private fun parseActivity(json: String): Float? {
    val rows: JsonArray = Json.parseToJsonElement(json).jsonArray
    if (rows.size < 2) {
      return null
    }
    val latestKp: Double = latestKp(rows) ?: return null
    val normalized: Double = (latestKp / MAX_KP).coerceIn(0.0, 1.0)
    return (AURORA_BASELINE + (1.0 - AURORA_BASELINE) * normalized).toFloat().coerceIn(0.0f, 1.0f)
  }

  private fun latestKp(rows: JsonArray): Double? {
    // Newer feed: array of objects with a numeric "Kp" field.
    ((rows.last() as? JsonObject)?.get(KP_FIELD) as? JsonPrimitive)
      ?.content
      ?.toDoubleOrNull()
      ?.let { return it }
    // Legacy feed: array of arrays whose first row is a header naming the Kp column.
    val header: JsonArray = (rows.first() as? JsonArray) ?: return null
    var kpColumn = -1
    for (column in header.indices) {
      val headerValue: String = (header[column] as? JsonPrimitive)?.content ?: continue
      if (headerValue.equals(KP_FIELD, ignoreCase = true)) {
        kpColumn = column
      }
    }
    if (kpColumn < 0) {
      return null
    }
    val latest: JsonArray = (rows.last() as? JsonArray) ?: return null
    return (latest[kpColumn] as? JsonPrimitive)?.content?.toDoubleOrNull()
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
