package com.phuchienngo.marblemarvelous.utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DateUtils {
    private const val DATEFORMAT = "yyyy-MM-dd HH:mm:ss"
    const val MILLIS_IN_A_DAY = 8.64E7f
    const val MINUTES_TO_MILLIS = 60000.0f
    private var fixedDate: Date? = null

    @JvmStatic
    fun UTCDate(): Date? = getUTC(Date())

    @JvmStatic
    fun getUTC(date: Date): Date? {
        val sdf = SimpleDateFormat(DATEFORMAT, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return parseDate(sdf.format(fixedDate ?: date))
    }

    @JvmStatic
    fun getUTC(date: Date, timeZone: String): Date? {
        val sdf = SimpleDateFormat(DATEFORMAT, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone(timeZone)
        return parseDate(sdf.format(fixedDate ?: date))
    }

    @JvmStatic
    fun getHoursOffsetFromUTC(id: String): Int {
        val offset = TimeZone.getTimeZone(id).getOffset(Calendar.getInstance().timeInMillis)
        return TimeUnit.HOURS.convert(offset.toLong(), TimeUnit.MILLISECONDS).toInt()
    }

    @JvmStatic
    fun parseDate(strDate: String): Date? {
        val dateFormat = SimpleDateFormat(DATEFORMAT, Locale.US)
        return try {
            dateFormat.parse(strDate)
        } catch (e: ParseException) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getAtBeginningOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(calendar.get(1), calendar.get(2), calendar.get(5), 0, 0, 0)
        return calendar.time
    }

    @JvmStatic
    fun getAtBeginningOfDay(calendarIn: Calendar): Calendar {
        val calendar = calendarIn.clone() as Calendar
        calendar.set(calendar.get(1), calendar.get(2), calendar.get(5), 0, 0, 0)
        return calendar
    }

    @JvmStatic
    fun getDayOfYear(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar.get(6)
    }

    @JvmStatic
    fun getDayRatio(): Float = getDayRatio(now())

    @JvmStatic
    fun getDayRatio(localNow: Date): Float {
        val localToday = getAtBeginningOfDay(localNow)
        return (localNow.time - localToday.time) / 8.64E7f
    }

    @JvmStatic
    fun getDayRatio(calendar: Calendar): Float {
        val localToday = getAtBeginningOfDay(calendar)
        return (calendar.timeInMillis - localToday.timeInMillis) / 8.64E7f
    }

    @JvmStatic
    fun now(): Date = fixedDate ?: Date()

    @JvmStatic
    fun setFixedDate(date: Date?) {
        fixedDate = date
    }

    @JvmStatic
    fun printCalendar(calendar: Calendar): String =
        String.format(Locale.US, "%02d", calendar.get(2) + 1) + "/" +
            String.format(Locale.US, "%02d", calendar.get(5)) + "/" +
            String.format(Locale.US, "%02d", calendar.get(1)) + " " +
            String.format(Locale.US, "%02d", calendar.get(11)) + ":" +
            String.format(Locale.US, "%02d", calendar.get(12)) + ":" +
            String.format(Locale.US, "%02d", calendar.get(13)) + " " +
            calendar.timeZone.displayName

    @JvmStatic
    fun printCalendarHour(calendar: Calendar): String =
        String.format(Locale.US, "%02d", calendar.get(11)) + ":" +
            String.format(Locale.US, "%02d", calendar.get(12))
}
