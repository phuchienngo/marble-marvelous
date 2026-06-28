package com.phuchienngo.marblemarvelous.utils

import android.icu.util.TimeZone
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale

object TimeUtils {
    @JvmStatic
    fun getDate(timeInMillis: Long): String {
        val cal: Calendar = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        return DateFormat.format("dd/MM/yyyy hh:mm a", cal).toString()
    }

    @JvmStatic
    fun getTimeString(): String {
        val cal: android.icu.util.Calendar =
            android.icu.util.Calendar
                .getInstance()
        cal.timeZone = TimeZone.getTimeZone("PST")
        return String.format(
            Locale.US,
            "%04d%02d%02d%02d",
            cal.get(1),
            cal.get(2) + 1,
            cal.get(5),
            cal.get(10)
        )
    }
}
