package com.phuchienngo.marblemarvelous.utils

import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateUtils {
  @JvmStatic
  fun now(): Instant = Instant.now()

  /** Fraction of the UTC day elapsed at [instant], in `[0, 1)`. */
  @JvmStatic
  fun utcDayRatio(instant: Instant): Float = dayRatio(instant, ZoneOffset.UTC)

  /** Fraction of the device's local day elapsed at [instant], in `[0, 1)`. */
  @JvmStatic
  fun localDayRatio(instant: Instant): Float = dayRatio(instant, ZoneId.systemDefault())

  @JvmStatic
  fun utcDayOfYear(instant: Instant): Int = instant.atZone(ZoneOffset.UTC).dayOfYear

  /** Parses a "yyyy-MM-dd HH:mm:ss" wall-clock time in the device's local timezone. */
  @JvmStatic
  fun parseDate(strDate: String): Instant? =
    try {
      val localDateTime: LocalDateTime = LocalDateTime.parse(strDate, DATE_FORMATTER)
      localDateTime.atZone(ZoneId.systemDefault()).toInstant()
    } catch (e: RuntimeException) {
      Log.e(TAG, "Failed to parse date", e)
      null
    }

  private fun dayRatio(
    instant: Instant,
    timeZone: ZoneId
  ): Float = instant.atZone(timeZone).toLocalTime().toSecondOfDay() / SECONDS_IN_A_DAY

  private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private const val SECONDS_IN_A_DAY: Float = 86400.0f
  private const val TAG: String = "DateUtils"
}
