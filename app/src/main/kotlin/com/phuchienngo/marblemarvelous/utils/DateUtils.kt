package com.phuchienngo.marblemarvelous.utils

import android.util.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
  private const val DATEFORMAT: String = "yyyy-MM-dd HH:mm:ss"
  const val MILLIS_IN_A_DAY: Float = 8.64E7f

  @JvmStatic
  fun getUTC(date: Date): Date? {
    val sdf: SimpleDateFormat = SimpleDateFormat(DATEFORMAT, Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return parseDate(sdf.format(date))
  }

  @JvmStatic
  fun parseDate(strDate: String): Date? {
    val dateFormat: SimpleDateFormat = SimpleDateFormat(DATEFORMAT, Locale.US)
    return try {
      dateFormat.parse(strDate)
    } catch (e: ParseException) {
      Log.e(TAG, "Failed to parse date", e)
      null
    }
  }

  @JvmStatic
  fun getAtBeginningOfDay(date: Date): Date {
    val calendar: Calendar = Calendar.getInstance()
    calendar.time = date
    calendar.set(calendar.get(1), calendar.get(2), calendar.get(5), 0, 0, 0)
    return calendar.time
  }

  @JvmStatic
  fun getDayOfYear(date: Date): Int {
    val calendar: Calendar = Calendar.getInstance()
    calendar.time = date
    return calendar.get(6)
  }

  @JvmStatic
  fun getDayRatio(localNow: Date): Float {
    val localToday: Date = getAtBeginningOfDay(localNow)
    return (localNow.time - localToday.time) / MILLIS_IN_A_DAY
  }

  @JvmStatic
  fun now(): Date = Date()

  private const val TAG: String = "DateUtils"
}
