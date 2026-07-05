package com.phuchienngo.marblemarvelous.utils

import android.util.Log
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

object DateUtils {
  @JvmStatic
  fun now(): Instant = Clock.System.now()

  /** Fraction of the UTC day elapsed at [instant], in `[0, 1)`. */
  @JvmStatic
  fun utcDayRatio(instant: Instant): Float = dayRatio(instant, TimeZone.UTC)

  /** Fraction of the device's local day elapsed at [instant], in `[0, 1)`. */
  @JvmStatic
  fun localDayRatio(instant: Instant): Float = dayRatio(instant, TimeZone.currentSystemDefault())

  @JvmStatic
  fun utcDayOfYear(instant: Instant): Int = instant.toLocalDateTime(TimeZone.UTC).date.dayOfYear

  /** Parses a "yyyy-MM-dd HH:mm:ss" wall-clock time in the device's local timezone. */
  @JvmStatic
  fun parseDate(strDate: String): Instant? =
    try {
      val localDateTime: LocalDateTime = LocalDateTime.parse(strDate.replace(' ', 'T'))
      localDateTime.toInstant(TimeZone.currentSystemDefault())
    } catch (e: IllegalArgumentException) {
      Log.e(TAG, "Failed to parse date", e)
      null
    }

  private fun dayRatio(
    instant: Instant,
    timeZone: TimeZone
  ): Float = instant.toLocalDateTime(timeZone).time.toSecondOfDay() / SECONDS_IN_A_DAY

  private const val SECONDS_IN_A_DAY: Float = 86400.0f
  private const val TAG: String = "DateUtils"
}
